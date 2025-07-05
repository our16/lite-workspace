package org.example.liteworkspace.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;

import java.io.File;
import java.io.FileWriter;
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

    private static void generateTestClass(PsiClass clazz) {
        String packageName = clazz.getQualifiedName().replace("." + clazz.getName(), "");
        String className = clazz.getName();
        String testClassName = className + "Test";

        String testCode = "package " + packageName + ";\n\n" +
                "import org.junit.Test;\n" +
                "import org.junit.runner.RunWith;\n" +
                "import org.springframework.test.context.ContextConfiguration;\n" +
                "import org.springframework.test.context.junit4.SpringRunner;\n" +
                "import javax.annotation.Resource;\n\n" +
                "@RunWith(SpringRunner.class)\n" +
                "@ContextConfiguration(locations = \"classpath:" + packageName.replace('.', '/') + "/" + className + ".xml\")\n" +
                "public class " + testClassName + " {\n\n" +
                "    @Resource\n" +
                "    private " + className + " " + decapitalize(className) + ";\n\n" +
                "    @Test\n" +
                "    public void testContextLoads() {\n" +
                "        System.out.println(\"" + decapitalize(className) + " = \" + " + decapitalize(className) + ");\n" +
                "    }\n" +
                "}";

        PsiFile psiFile = clazz.getContainingFile();
        VirtualFile virtualFile = psiFile.getVirtualFile();
        File sourceFile = new File(virtualFile.getPath());
        String testJavaPath = sourceFile.getAbsolutePath()
                .replace("src\\main\\java", "src\\test\\java")
                .replace("src/main/java", "src/test/java")
                .replace(className + ".java", testClassName + ".java");

        File outputFile = new File(testJavaPath);
        outputFile.getParentFile().mkdirs();

        try (FileWriter fw = new FileWriter(outputFile)) {
            fw.write(testCode);
            System.out.println("✅ Test 类写入成功: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String decapitalize(String name) {
        return name == null || name.isEmpty() ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
