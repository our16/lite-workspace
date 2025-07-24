package org.example.liteworkspace.util;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 使用 JDK 内置的 JavaCompiler API，将指定的 .java 源码文件编译到临时目录
 */
public class OnDemandJavaCompiler {

    /**
     * 编译给定的 .java 源码文件列表，并输出到临时目录
     *
     * @param sourceFiles 需要编译的 .java 文件列表（File 对象）
     * @return Path 编译输出的目录（包含所有生成的 .class 文件），如果编译失败则返回 null
     */
    public static Path compileToTempDirectory(List<File> sourceFiles) {
        // 1. 获取系统 JavaCompiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            System.err.println("[ERROR] 未找到 Java 编译器，请确保使用的是 JDK，而不是 JRE");
            return null;
        }

        // 2. 创建标准文件管理器
        StandardJavaFileManager fileManager;
        try {
            fileManager = compiler.getStandardFileManager(null, null, null);
        } catch (Exception e) {
            System.err.println("[ERROR] 创建文件管理器失败: " + e.getMessage());
            return null;
        }

        // 3. 创建临时输出目录（如系统 temp 下的 idea-run-classes）
        Path outputDir;
        try {
            outputDir = Files.createTempDirectory("idea-run-classes");
            System.out.println("[INFO] 临时编译输出目录: " + outputDir.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[ERROR] 创建临时目录失败: " + e.getMessage());
            return null;
        }

        // 4. 设置编译选项：指定 -d 输出目录
        List<String> options = new ArrayList<>();
        options.add("-d");
        options.add(outputDir.toString());

        // 5. 准备编译任务输入：源码文件
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(sourceFiles);

        // 6. 创建编译任务
        JavaCompiler.CompilationTask task = compiler.getTask(
                null,               // 不需要 Writer
                fileManager,        // 文件管理器
                null,               // DiagnosticListener（可添加日志）
                options,            // 编译参数，如 -d
                null,               // 不指定 classes（注解处理等）
                compilationUnits    // 要编译的源码文件
        );

        // 7. 执行编译
        boolean success = task.call();

        try {
            fileManager.close(); // 一定要关闭
        } catch (IOException e) {
            System.err.println("[WARN] 关闭文件管理器时出错: " + e.getMessage());
        }

        if (!success) {
            System.err.println("[ERROR] 按需编译失败，请检查源码是否有语法错误等");
            return null;
        }

        // 8. 编译成功，返回输出目录（里面包含所有 .class 文件）
        System.out.println("[INFO] 按需编译成功，类文件输出到: " + outputDir);
        return outputDir;
    }
}