package org.example.liteworkspace.bean.engine;


import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import org.example.liteworkspace.bean.core.LiteProjectContext;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

public class LiteFileWriter {

    private final LiteProjectContext context;

    public LiteFileWriter(LiteProjectContext context) {
        this.context = context;
    }

    public void write(Project project, PsiClass clazz,  Map<String, String> beanMap) {
        try {
            Module module = ModuleUtilCore.findModuleForPsiElement(clazz);
            if (module == null) return;

            String qualifiedName = clazz.getQualifiedName();
            String className = clazz.getName();
            String packageName = qualifiedName.substring(0, qualifiedName.lastIndexOf('.'));
            String testClassName = className + "Test";
            String relativePath = packageName.replace('.', '/');

            VirtualFile[] roots = ModuleRootManager.getInstance(module).getSourceRoots();
            final VirtualFile[] testJava = {null};
            final VirtualFile[] testRes = {null};

            for (VirtualFile root : roots) {
                String path = root.getPath().replace("\\", "/");
                if (path.endsWith("/src/test/java")) testJava[0] = root;
                if (path.endsWith("/src/test/resources")) testRes[0] = root;
            }

            if (testJava[0] == null || testRes[0] == null) {
                VirtualFile baseDir = module.getModuleFile() != null ? module.getModuleFile().getParent() : null;
                if (baseDir == null) {
                    showError(project, "无法确定 module 路径");
                    return;
                }
                createTestPath(project, baseDir, testJava, testRes);
            }

            File javaDir = new File(testJava[0].getPath(), relativePath);
            File resDir = new File(testRes[0].getPath(), relativePath);
            javaDir.mkdirs();
            resDir.mkdirs();

            File xmlFile = writeSpringXmlFile(beanMap, resDir, testClassName);
            File testFile = writeJUnitTestFile(packageName, className, testClassName, relativePath, javaDir);

            VfsUtil.findFileByIoFile(xmlFile, true).refresh(false, false);
            VfsUtil.findFileByIoFile(testFile, true).refresh(false, false);

            ApplicationManager.getApplication().invokeLater(() ->
                    Messages.showInfoMessage(project, "✅ 已生成：\n" + testFile.getAbsolutePath(), "LiteWorkspace"));

        } catch (Exception ex) {
            showError(project, "❌ 生成失败：" + ex.getMessage());
        }
    }

    private void showError(Project project, String message) {
        ApplicationManager.getApplication().invokeLater(() ->
                Messages.showErrorDialog(project, message, "LiteWorkspace"));
    }

    private void createTestPath(Project project, VirtualFile baseDir, VirtualFile[] testJava, VirtualFile[] testRes) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                VirtualFile srcDir = baseDir.findChild("src");
                if (srcDir == null) srcDir = baseDir.createChildDirectory(null, "src");
                VirtualFile testDir = srcDir.findChild("test");
                if (testDir == null) testDir = srcDir.createChildDirectory(null, "test");
                if (testJava[0] == null) testJava[0] = testDir.findOrCreateChildData(null, "java");
                if (testRes[0] == null) testRes[0] = testDir.findOrCreateChildData(null, "resources");
            } catch (IOException e) {
                showError(project, "创建目录失败: " + e.getMessage());
            }
        });
    }

    private File writeSpringXmlFile(Map<String, String> beanMap, File resDir, String testClassName) throws IOException {
        File xmlFile = new File(resDir, testClassName + ".xml");
        try (FileWriter fw = new FileWriter(xmlFile)) {
            fw.write("<beans xmlns=\"http://www.springframework.org/schema/beans\"\n");
            fw.write("       xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
            fw.write("       xsi:schemaLocation=\"http://www.springframework.org/schema/beans\n");
            fw.write("        http://www.springframework.org/schema/beans/spring-beans.xsd\">\n\n");
            for (String xml : beanMap.values()) {
                fw.write(xml);
                fw.write("\n");
            }
            fw.write("</beans>");
        }
        return xmlFile;
    }

    private File writeJUnitTestFile(String packageName, String className, String testClassName,
                                    String relativePath, File javaDir) throws IOException {
        File testFile = new File(javaDir, testClassName + ".java");
        String configLine = String.format("@ContextConfiguration(locations = \"classpath:%s/%s.xml\")",
                relativePath, testClassName);

        if (!testFile.exists()) {
            try (FileWriter fw = new FileWriter(testFile)) {
                fw.write(String.format("""
                package %s;

                import org.junit.Test;
                import org.junit.runner.RunWith;
                import org.springframework.test.context.ContextConfiguration;
                import org.springframework.test.context.junit4.SpringRunner;
                import javax.annotation.Resource;

                @RunWith(SpringRunner.class)
                %s
                public class %s {

                    @Resource
                    private %s %s;

                    @Test
                    public void testContextLoads() {
                        System.out.println("%s = " + %s);
                    }
                }
                """,
                        packageName,
                        configLine,
                        testClassName,
                        className,
                        decapitalize(className),
                        decapitalize(className),
                        decapitalize(className)
                ));
            }
        } else {
            String content = new String(java.nio.file.Files.readAllBytes(testFile.toPath()));
            content = content.replaceAll("@ContextConfiguration\\(locations\\s*=\\s*\"[^\"]*\"\\)", configLine);
            try (FileWriter fw = new FileWriter(testFile)) {
                fw.write(content);
            }
        }
        return testFile;
    }

    private String decapitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}

