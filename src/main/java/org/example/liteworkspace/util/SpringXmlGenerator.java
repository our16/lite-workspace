package org.example.liteworkspace.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;

import java.io.File;
import java.io.FileWriter;
import java.util.*;

public class SpringXmlGenerator {

    public static void generateXmlForClass(Project project, PsiClass clazz) {
        Set<String> visited = new HashSet<>();
        Map<String, String> beanMap = new LinkedHashMap<>(); // 保持顺序

        buildBean(clazz, beanMap, visited);

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
    }

    private static void buildBean(PsiClass clazz, Map<String, String> beanMap, Set<String> visited) {
        if (clazz == null || visited.contains(clazz.getQualifiedName()) || clazz.getQualifiedName() == null)
            return;
        visited.add(clazz.getQualifiedName());

        String id = decapitalize(clazz.getName());
        String className = clazz.getQualifiedName();
        StringBuilder beanXml = new StringBuilder();
        beanXml.append("    <bean id=\"").append(id).append("\" class=\"").append(className).append("\">\n");

        // 构造器依赖
        for (PsiMethod constructor : clazz.getConstructors()) {
            for (PsiParameter param : constructor.getParameterList().getParameters()) {
                PsiClass dep = resolveClass(param.getType());
                if (dep != null) {
                    beanXml.append("        <constructor-arg ref=\"")
                            .append(decapitalize(dep.getName())).append("\"/>\n");
                    buildBean(dep, beanMap, visited);
                }
            }
        }

        // 字段依赖
        for (PsiField field : clazz.getFields()) {
            PsiClass dep = resolveClass(field.getType());
            if (dep != null) {
                beanXml.append("        <property name=\"").append(field.getName())
                        .append("\" ref=\"").append(decapitalize(dep.getName())).append("\"/>\n");
                buildBean(dep, beanMap, visited);
            }
        }

        beanXml.append("    </bean>");
        beanMap.put(id, beanXml.toString());
    }

    private static PsiClass resolveClass(PsiType type) {
        if (type instanceof PsiClassType) {
            return ((PsiClassType) type).resolve();
        }
        return null;
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
                        File.separator + packagePath + File.separator + clazz.getName() + ".xml"
        );

        outputFile.getParentFile().mkdirs();
        try (FileWriter fw = new FileWriter(outputFile)) {
            fw.write(content);
            System.out.println("✅ XML 写入成功: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String decapitalize(String name) {
        return name == null || name.isEmpty() ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
