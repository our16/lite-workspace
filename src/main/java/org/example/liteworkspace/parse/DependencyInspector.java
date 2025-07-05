package org.example.liteworkspace.parse;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

public class DependencyInspector {

    public static String analyze(File javaFile) {
        if (!javaFile.exists()) {
            return "❌ 文件不存在: " + javaFile.getAbsolutePath();
        }

        try {
            String fullClassName = getFullClassName(javaFile);
            if (fullClassName == null) {
                return "❌ 无法解析类名或 package: " + javaFile.getName();
            }

            Class<?> clazz = Class.forName(fullClassName);
            Set<String> visited = new HashSet<String>();
            StringBuilder md = new StringBuilder("# 依赖分析报告\n\n");
            inspectClass(clazz, md, 0, visited);
            return md.toString();
        } catch (Exception e) {
            return "❌ 分析出错: " + e.getMessage();
        }
    }

    private static String getFullClassName(File javaFile) throws IOException {
        String className = javaFile.getName().replace(".java", "");
        String pkg = null;

        List<String> lines = Files.readAllLines(javaFile.toPath());
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("package ")) {
                pkg = line.replace("package", "").replace(";", "").trim();
                break;
            }
        }

        return pkg != null ? pkg + "." + className : null;
    }

    private static void inspectClass(Class<?> clazz, StringBuilder md, int indent, Set<String> visited) {
        if (clazz == null || clazz.getName().startsWith("java.") || visited.contains(clazz.getName())) return;
        visited.add(clazz.getName());

        for (int i = 0; i < indent; i++) md.append("  ");
        md.append("- ").append(clazz.getSimpleName()).append(" (`").append(clazz.getName()).append("`)\n");

        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            for (Class<?> paramType : constructor.getParameterTypes()) {
                inspectClass(paramType, md, indent + 1, visited);
            }
        }

        for (Field field : clazz.getDeclaredFields()) {
            inspectClass(field.getType(), md, indent + 1, visited);
        }
    }
}
