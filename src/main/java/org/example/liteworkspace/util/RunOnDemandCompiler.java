package org.example.liteworkspace.util;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RunOnDemandCompiler {

    public static void run(String mainClass, String[] javaFilePaths) {
        System.out.println("[RunOnDemand] 开始编译运行");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            System.err.println("[ERROR] 未找到 JDK 编译器，请使用 JDK 而非 JRE");
            return;
        }

        // === 1. 创建临时输出目录 ===
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("idea-run-classes");
            System.out.println("[INFO] 临时编译输出目录: " + tempDir);
        } catch (IOException e) {
            System.err.println("[ERROR] 无法创建临时目录: " + e.getMessage());
            return;
        }

        // === 2. 准备源码文件 ===
        Iterable<? extends JavaFileObject> compilationUnits = Arrays.stream(javaFilePaths)
                .map(File::new)
                .filter(File::exists)
                .filter(f -> f.getName().endsWith(".java"))
                .map(f -> {
                    System.out.println("[INFO] 添加源码文件: " + f.getAbsolutePath());
                    return new MyJavaFileObject(f.toPath(), f.getName());
                })
                .toList();

        if (!compilationUnits.iterator().hasNext()) {
            System.err.println("[ERROR] 没有有效的 .java 文件可编译");
            return;
        }

        // === 3. 配置编译任务 ===
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        List<String> options = new ArrayList<>();
        options.add("-d");
        options.add(tempDir.toString());

        JavaCompiler.CompilationTask task = compiler.getTask(
                null,
                fileManager,
                null,
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
            System.err.println("[ERROR] 编译失败");
            return;
        }

        System.out.println("[INFO] 编译成功，类文件输出到: " + tempDir);

        // === 4. TODO: 替换为自动获取依赖 jar，这里先手动指定（可为空）
        String[] dependencyJars = new String[]{
                // 例如："/path/to/guava.jar", "/path/to/your-lib.jar"
                // 请根据你的实际依赖修改
        };

        // === 5. 组装 classpath：临时目录 + 依赖 jars
        List<String> classpathEntries = new ArrayList<>();
        classpathEntries.add(tempDir.toString());
        for (String jar : dependencyJars) {
            File f = new File(jar);
            if (f.exists()) {
                classpathEntries.add(f.getAbsolutePath());
                System.out.println("[INFO] 添加依赖 jar: " + jar);
            } else {
                System.err.println("[WARN] 依赖 jar 不存在: " + jar);
            }
        }

        String classpath = String.join(System.getProperty("path.separator"), classpathEntries);

        // === 6. 运行主类
        System.out.println("[INFO] 准备运行主类: " + mainClass + " | Classpath: " + classpath);

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
            System.out.println("[INFO] 进程退出，code = " + exitCode);
        } catch (Exception ex) {
            System.err.println("[ERROR] 运行失败: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
