package org.example.liteworkspace.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class SpringXmlGenerator {

    public static void generateXmlForClass(Project project, PsiClass clazz) {
        XmlBeanAssembler assembler = new XmlBeanAssembler();
        Map<String, String> beanMap = assembler.buildAll(clazz);

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<beans xmlns=\"http://www.springframework.org/schema/beans\"\n");
        xml.append("       xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        xml.append("       xsi:schemaLocation=\"http://www.springframework.org/schema/beans\n");
        xml.append("        https://www.springframework.org/schema/beans/spring-beans.xsd\">\n\n");

        for (String beanXml : beanMap.values()) {
            xml.append(beanXml).append("\n");
        }

        xml.append("</beans>\n");

        writeXmlToResources(clazz, xml.toString());
        generateTestClass(clazz); // ✅ 自动生成测试类
    }

    private static void writeXmlToResources(PsiClass clazz, String content) {
        PsiFile psiFile = clazz.getContainingFile();
        if (psiFile == null) return;

        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile == null) return;

        File javaFile = new File(virtualFile.getPath());
        String path = javaFile.getAbsolutePath();

        String testRes = path.replace("src\\main\\java", "src\\test\\resources")
                .replace("src/main/java", "src/test/resources");

        String packagePath = clazz.getQualifiedName()
                .replace("." + clazz.getName(), "")
                .replace('.', File.separatorChar);

        File outputFile = new File(
                testRes.substring(0, testRes.indexOf("src" + File.separator + "test" + File.separator + "resources") + "src/test/resources".length()) +
                        File.separator + packagePath + File.separator + clazz.getName() + "Test.xml"
        );

        outputFile.getParentFile().mkdirs();
        try (FileWriter fw = new FileWriter(outputFile)) {
            fw.write(content);
            System.out.println("✅ XML 写入成功: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void generateTestClass(PsiClass clazz) {
        String packageName = clazz.getQualifiedName().replace("." + clazz.getName(), "");
        String className = clazz.getName();
        String testClassName = className + "Test";

        String fieldName = decapitalize(className);
        PsiFile psiFile = clazz.getContainingFile();
        VirtualFile virtualFile = psiFile.getVirtualFile();
        File sourceFile = new File(virtualFile.getPath());

        String testJavaPath = sourceFile.getAbsolutePath()
                .replace("src\\main\\java", "src\\test\\java")
                .replace("src/main/java", "src/test/java")
                .replace(className + ".java", testClassName + ".java");

        File outputFile = new File(testJavaPath);
        outputFile.getParentFile().mkdirs();

        StringBuilder newContent = new StringBuilder();

        if (!outputFile.exists()) {
            newContent.append("package ").append(packageName).append(";\n\n")
                    .append("import org.junit.Test;\n")
                    .append("import org.junit.runner.RunWith;\n")
                    .append("import org.springframework.test.context.ContextConfiguration;\n")
                    .append("import org.springframework.test.context.junit4.SpringRunner;\n")
                    .append("import javax.annotation.Resource;\n\n")
                    .append("@RunWith(SpringRunner.class)\n")
                    .append("@ContextConfiguration(locations = \"classpath:")
                    .append(packageName.replace('.', '/')).append("/").append(testClassName).append(".xml\")\n")
                    .append("public class ").append(testClassName).append(" {\n\n")
                    .append("    @Resource\n")
                    .append("    private ").append(className).append(" ").append(fieldName).append(";\n\n")
                    .append("    @Test\n")
                    .append("    public void testContextLoads() {\n")
                    .append("        System.out.println(\"").append(fieldName).append(" = \" + ").append(fieldName).append(");\n")
                    .append("    }\n")
                    .append("}\n");
        } else {
            try {
                String old = java.nio.file.Files.readString(outputFile.toPath(), StandardCharsets.UTF_8);
                boolean modified = false;

                if (!old.contains("@" + "RunWith")) {
                    newContent.append("import org.junit.runner.RunWith;\n")
                            .append("@RunWith(SpringRunner.class)\n");
                    modified = true;
                }

                if (!old.contains("@" + "ContextConfiguration")) {
                    newContent.append("import org.springframework.test.context.ContextConfiguration;\n")
                            .append("@ContextConfiguration(locations = \"classpath:")
                            .append(packageName.replace('.', '/')).append("/").append(className).append(".xml\")\n");
                    modified = true;
                }

                if (!old.contains("@" + "Resource") && !old.contains("private " + className)) {
                    newContent.append("import javax.annotation.Resource;\n")
                            .append("    @Resource\n")
                            .append("    private ").append(className).append(" ").append(fieldName).append(";\n");
                    modified = true;
                }

                if (!old.contains("testContextLoads")) {
                    newContent.append("    @Test\n")
                            .append("    public void testContextLoads() {\n")
                            .append("        System.out.println(\"").append(fieldName).append(" = \" + ").append(fieldName).append(");\n")
                            .append("    }\n");
                    modified = true;
                }

                if (modified) {
                    // 插入到 class {...} 体中（简单插入尾部前一行）
                    int insertIndex = old.lastIndexOf("}");
                    if (insertIndex > 0) {
                        String updated = old.substring(0, insertIndex) +
                                "\n\n" + newContent +
                                "\n" + old.substring(insertIndex);
                        try (FileWriter fw = new FileWriter(outputFile)) {
                            fw.write(updated);
                        }
                        System.out.println("✅ Test 类已更新（补充内容）: " + outputFile.getAbsolutePath());
                    }
                } else {
                    System.out.println("✅ Test 类已存在且完整，无需修改: " + outputFile.getAbsolutePath());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        try (FileWriter fw = new FileWriter(outputFile)) {
            fw.write(newContent.toString());
            System.out.println("✅ Test 类写入成功: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    private static String decapitalize(String name) {
        return name == null || name.isEmpty() ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
