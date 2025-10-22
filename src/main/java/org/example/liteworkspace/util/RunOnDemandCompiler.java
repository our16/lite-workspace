package org.example.liteworkspace.util;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;

import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.Executors;

public class RunOnDemandCompiler {

    public static void run(Project project, String mainClass, List<String> javaFilePaths) {
        Set<VirtualFile> filesToCompile = new HashSet<>();
        Set<Module> modules = new HashSet<>();

        for (String path : javaFilePaths) {
            VirtualFile vf = LocalFileSystem.getInstance().findFileByPath(path.replace(File.separatorChar, '/'));
            if (vf != null && !vf.isDirectory() && "java".equalsIgnoreCase(vf.getExtension())) {
                filesToCompile.add(vf);
                Module module = ModuleUtilCore.findModuleForFile(vf, project);
                if (module != null) {
                    modules.add(module);
                }

                // 自动解析 import 的依赖类
                collectImportsAndRelatedFiles(project, vf, filesToCompile);
            }
        }

        if (filesToCompile.isEmpty()) {
            System.out.println("[ERROR] 未找到需要编译的 Java 文件");
            return;
        }

        CompileScope scope = CompilerManager.getInstance(project)
                .createFilesCompileScope(filesToCompile.toArray(new VirtualFile[0]));

        CompilerManager.getInstance(project).make(scope, (aborted, errors, warnings, compileContext) -> {
            if (aborted || errors > 0) {
                ConsoleService .print(project,"[ERROR] 编译失败，错误数：" + errors,ConsoleViewContentType.NORMAL_OUTPUT);
                return;
            }

            ConsoleService .print(project,"[INFO] 编译成功，准备运行主类：" + mainClass , ConsoleViewContentType.NORMAL_OUTPUT);

            try {
                Set<String> classpathEntries = new LinkedHashSet<>();
                
                // 1. 添加模块输出路径
                for (Module module : modules) {
                    CompilerModuleExtension compilerExtension = CompilerModuleExtension.getInstance(module);
                    if (compilerExtension != null) {
                        // 测试输出路径
                        VirtualFile testOutput = compilerExtension.getCompilerOutputPathForTests();
                        if (testOutput != null) {
                            classpathEntries.add(testOutput.getPath());
                        }
                        // 生产输出路径
                        VirtualFile output = compilerExtension.getCompilerOutputPath();
                        if (output != null) {
                            classpathEntries.add(output.getPath());
                        }
                    }
                }
                
                // 2. 智能查找Maven和Gradle输出目录
                for (Module module : modules) {
                    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
                    String moduleBasePath = rootManager.getContentRoots()[0].getPath();
                    
                    // Maven输出目录
                    addIfExists(classpathEntries, moduleBasePath + "/target/classes");
                    addIfExists(classpathEntries, moduleBasePath + "/target/test-classes");
                    
                    // Gradle输出目录
                    addIfExists(classpathEntries, moduleBasePath + "/build/classes/java/main");
                    addIfExists(classpathEntries, moduleBasePath + "/build/classes/java/test");
                    addIfExists(classpathEntries, moduleBasePath + "/build/resources/main");
                    addIfExists(classpathEntries, moduleBasePath + "/build/resources/test");
                    
                    // IDEA输出目录
                    addIfExists(classpathEntries, moduleBasePath + "/out/production/classes");
                    addIfExists(classpathEntries, moduleBasePath + "/out/test/classes");
                }

                String classpath = String.join(File.pathSeparator, classpathEntries);
                System.out.println("[DEBUG] 类路径: " + classpath);
                System.out.println("[DEBUG] 主类: " + mainClass);
                
                // 验证主类文件是否存在
                boolean mainClassFound = false;
                for (String classpathEntry : classpathEntries) {
                    File classFile = new File(classpathEntry, mainClass.replace('.', '/') + ".class");
                    if (classFile.exists()) {
                        mainClassFound = true;
                        System.out.println("[DEBUG] 找到主类文件: " + classFile.getAbsolutePath());
                        break;
                    }
                }
                
                if (!mainClassFound) {
                    System.out.println("[ERROR] 未找到主类文件: " + mainClass);
                    ConsoleService.print(project, "[ERROR] 未找到主类文件: " + mainClass, ConsoleViewContentType.ERROR_OUTPUT);
                    return;
                }
                
                List<String> command = Arrays.asList("java", "-cp", classpath, mainClass);

                ProcessBuilder pb = new ProcessBuilder(command);
                Process process = pb.start();

                Executors.newSingleThreadExecutor().submit(() -> printStream(project, process.getInputStream(), ConsoleViewContentType.NORMAL_OUTPUT));
                Executors.newSingleThreadExecutor().submit(() -> printStream(project, process.getErrorStream(), ConsoleViewContentType.ERROR_OUTPUT));

                int exitCode = process.waitFor();
                System.out.println("[INFO] 运行结束，退出码: " + exitCode);
            } catch (Exception e) {
                System.out.println("[ERROR] 运行失败: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private static void printStream(Project project, InputStream stream, ConsoleViewContentType contentType) {
        try (Scanner scanner = new Scanner(stream)) {
            while (scanner.hasNextLine()) {
                ConsoleService.print(project, scanner.nextLine(), contentType);
            }
        }
    }

    /**
     * 如果路径存在则添加到集合中
     */
    private static void addIfExists(Set<String> classpathEntries, String path) {
        File dir = new File(path);
        if (dir.exists() && dir.isDirectory()) {
            classpathEntries.add(path);
        }
    }

    private static void collectImportsAndRelatedFiles(Project project, VirtualFile vf, Set<VirtualFile> resultSet) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(vf);
        if (!(psiFile instanceof PsiJavaFile javaFile)) return;

        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        PsiManager psiManager = PsiManager.getInstance(project);
        Set<String> fqcnSet = new HashSet<>();

        // 提取 import 声明
        for (PsiImportStatement imp : javaFile.getImportList().getImportStatements()) {
            String fqcn = imp.getQualifiedName();
            if (fqcn != null) fqcnSet.add(fqcn);
        }

        // 自身类也加进来（避免只编译子类）
        for (PsiClass cls : javaFile.getClasses()) {
            if (cls.getQualifiedName() != null) fqcnSet.add(cls.getQualifiedName());
        }

        for (String fqcn : fqcnSet) {
            PsiClass cls = JavaPsiFacade.getInstance(project).findClass(fqcn, scope);
            if (cls != null) {
                PsiFile f = cls.getContainingFile();
                if (f != null) {
                    VirtualFile dependentFile = f.getVirtualFile();
                    if (dependentFile != null) resultSet.add(dependentFile);
                }
            }
        }
    }
}
