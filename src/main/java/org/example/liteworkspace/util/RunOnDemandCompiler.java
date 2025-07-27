package org.example.liteworkspace.util;

import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.example.liteworkspace.notification.CompilerStatusNotification;

import java.io.File;
import java.util.*;

public class RunOnDemandCompiler {

    public static void run(Project project, String mainClass, List<String> javaFilePaths) {
        CompilerStatusNotification.showNotification(project, "[RunOnDemand] 开始编译运行");

        List<VirtualFile> filesToCompile = new ArrayList<>();
        Set<Module> modules = new HashSet<>();
        // 根据路径找到VirtualFile和所属模块
        for (String path : javaFilePaths) {
            VirtualFile vf = LocalFileSystem.getInstance().findFileByPath(path.replace(File.separatorChar, '/'));
            if (vf != null && !vf.isDirectory() && "java".equalsIgnoreCase(vf.getExtension())) {
                filesToCompile.add(vf);
                Module module = ModuleUtilCore.findModuleForFile(vf, project);
                if (module != null) {
                    modules.add(module);
                }
            }
        }

        if (filesToCompile.isEmpty()) {
            CompilerStatusNotification.showNotification(project, "[ERROR] 没有找到有效的 Java 文件");
            return;
        }

        CompileScope scope = CompilerManager.getInstance(project).createFilesCompileScope(filesToCompile.toArray(new VirtualFile[0]));

        CompilerManager.getInstance(project).make(scope, (aborted, errors, warnings, compileContext) -> {
            if (aborted || errors > 0) {
                CompilerStatusNotification.showNotification(project, "[ERROR] 编译失败，错误数：" + errors);
                return;
            }
            CompilerStatusNotification.showNotification(project, "[INFO] 编译成功，准备运行主类：" + mainClass);

            try {
                Set<String> classpathEntries = new LinkedHashSet<>();
                for (Module module : modules) {
                    String outputPath = CompilerPaths.getModuleOutputPath(module, false); // false = 主代码
                    if (outputPath != null) {
                        classpathEntries.add(outputPath);
                    }
                }

                String classpath = String.join(File.pathSeparator, classpathEntries);
                ProcessBuilder pb = new ProcessBuilder("java", "-cp", classpath, mainClass);
                pb.inheritIO();
                Process process = pb.start();
                int exitCode = process.waitFor();
                CompilerStatusNotification.showNotification(project, "[INFO] 运行结束，退出码: " + exitCode);
            } catch (Exception e) {
                CompilerStatusNotification.showNotification(project, "[ERROR] 运行失败: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public static VirtualFile[] toVirtualFiles(List<String> javaFilePaths) {
        return javaFilePaths.stream()
                .map(path -> {
                    String normalizedPath = path.replace(File.separatorChar, '/'); // IDEA 路径需是 /
                    return LocalFileSystem.getInstance().findFileByPath(normalizedPath);
                })
                .filter(Objects::nonNull)
                .toArray(VirtualFile[]::new);
    }
}
