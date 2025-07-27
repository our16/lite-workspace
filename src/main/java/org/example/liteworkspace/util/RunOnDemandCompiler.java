package org.example.liteworkspace.util;

import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang3.StringUtils;
import org.example.liteworkspace.config.LiteWorkspaceSettings;
import org.example.liteworkspace.notification.CompilerStatusNotification;
import org.jetbrains.annotations.Nullable;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RunOnDemandCompiler {

    public static void run(Project project, String mainClass, List<String> javaFilePaths) throws IOException {
        CompilerStatusNotification.showNotification(project, "[RunOnDemand] 开始编译运行");
        LiteWorkspaceSettings settings = LiteWorkspaceSettings.getInstance();
        String configJavaHome = settings.getJavaHome();
        JavaCompiler compiler = getJavaCompiler(project, configJavaHome);
        if (compiler == null) {
            return;
        }

        List<String> dependencePath = getClasspathFromJavaFiles(project, javaFilePaths);
        javaFilePaths.addAll(dependencePath);
        // === 2. 准备源码文件 ===
        Iterable<? extends JavaFileObject> compilationUnits = javaFilePaths.stream()
                .map(File::new)
                .filter(File::exists)
                .filter(f -> f.getName().endsWith(".java"))
                .map(f -> {
                    CompilerStatusNotification.showNotification(project, "[INFO] 添加源码文件: " + f.getAbsolutePath());
                    return new MyJavaFileObject(f.toPath(), f.getName());
                })
                .toList();

        if (!compilationUnits.iterator().hasNext()) {
            CompilerStatusNotification.showNotification(project, "[ERROR] 没有有效的 .java 文件可编译");
            return;
        }

        // === 3. 配置编译任务 ===
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        List<String> options = new ArrayList<>();
        //        输出目录
        options.add("-d");
        options.add("out/classes");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler.CompilationTask task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                options,
                null,
                compilationUnits
        );

        boolean success = task.call();
        try {
            fileManager.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (!success) {
            StringBuilder errorMsg = new StringBuilder("[ERROR] 编译失败:\n");
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                errorMsg.append(String.format(
                        "在 %s 第%d行: %s\n",
                        diagnostic.getSource().getName(),
                        diagnostic.getLineNumber(),
                        diagnostic.getMessage(null)
                ));
            }
            CompilerStatusNotification.showNotification(project, "[ERROR] 编译失败");
            return;
        }

        CompilerStatusNotification.showNotification(project, "[INFO] 编译成功，类文件输出到: " + "out/classes");

        // === 5. 组装 classpath：临时目录 + 依赖 jars
        List<String> classpathEntries = new ArrayList<>();
        classpathEntries.add("out/classes");

        String classpath = String.join(System.getProperty("path.separator"), classpathEntries);

        // === 6. 运行主类
        CompilerStatusNotification.showNotification(project, "[INFO] 准备运行主类: " + mainClass + " | Classpath: " + classpath);

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "java",
                    "-cp",
                    classpath,
                    mainClass
            );
            pb.inheritIO(); // 输出到 IDEA 控制台
            Process process = pb.start();
            int exitCode = process.waitFor();
            CompilerStatusNotification.showNotification(project, "[INFO] 进程退出，code = " + exitCode);
        } catch (Exception ex) {
            CompilerStatusNotification.showNotification(project, "[ERROR] 运行失败: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public static List<String> getClasspathFromJavaFiles(Project project, List<String> javaFilePaths) {
        Set<Module> modules = new HashSet<>();

        for (String path : javaFilePaths) {
            VirtualFile vf = LocalFileSystem.getInstance().findFileByPath(path.replace(File.separatorChar, '/'));
            if (vf != null) {
                Module module = ModuleUtilCore.findModuleForFile(vf, project);
                if (module != null) {
                    modules.add(module);
                }
            }
        }

        Set<String> allPaths = new HashSet<>();
        for (Module module : modules) {
            List<String> paths = OrderEnumerator.orderEntries(module)
                    .recursively()
                    .exportedOnly()
                    .classes()
                    .getPathsList()
                    .getPathList();
            allPaths.addAll(paths);
        }

        return new ArrayList<>(allPaths);
    }


    private static @Nullable JavaCompiler getJavaCompiler(Project project, String configJavaHome) {
        JavaCompiler compiler = null;
        if (StringUtils.isNotBlank(configJavaHome)) {
            // 根据配置路径构建javaCompiler
            File jdkHome = new File(configJavaHome);
            File toolsJar = new File(jdkHome, "lib/tools.jar");

            if (!jdkHome.exists() || !jdkHome.isDirectory()) {
                CompilerStatusNotification.showNotification(project,
                        "[ERROR] 配置的 JAVA_HOME 路径不存在或不是目录: " + configJavaHome);
                return null;
            }

            try {
                // 使用自定义类加载器加载 tools.jar
                URLClassLoader classLoader = new URLClassLoader(
                        new URL[]{toolsJar.toURI().toURL()},
                        RunOnDemandCompiler.class.getClassLoader()
                );

                Class<?> toolProviderClass = classLoader.loadClass("javax.tools.ToolProvider");
                Method getSystemJavaCompilerMethod = toolProviderClass.getMethod("getSystemJavaCompiler");
                compiler = (JavaCompiler) getSystemJavaCompilerMethod.invoke(null);
                if (compiler == null) {
                    Class<?> javacToolClass = classLoader.loadClass("com.sun.tools.javac.api.JavacTool");
                    compiler = (JavaCompiler) javacToolClass.getDeclaredConstructor().newInstance();
                }
            } catch (Exception e) {
                CompilerStatusNotification.showNotification(project,
                        "[ERROR] 初始化自定义 JDK 编译器失败: " + e.getMessage());
                return null;
            }
        } else {
            compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                String javaHome = System.getProperty("java.home");
                String message = String.format(
                        "[ERROR] 未找到 JDK 编译器 (JAVA_HOME=%s)。可能原因：\n" +
                                "1. 使用 JRE 而非 JDK 运行\n" +
                                "2. 环境变量配置错误\n" +
                                "3. JDK 安装不完整",
                        javaHome);
                CompilerStatusNotification.showNotification(project, message);
                return null;
            }
        }
        return compiler;
    }
}
