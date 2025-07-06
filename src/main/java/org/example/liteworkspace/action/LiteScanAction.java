package org.example.liteworkspace.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.example.liteworkspace.bean.builder.AnnotationBeanBuilder;
import org.example.liteworkspace.bean.builder.MyBatisMapperBuilder;
import org.example.liteworkspace.bean.builder.XmlBeanBuilder;
import org.example.liteworkspace.bean.core.*;
import org.example.liteworkspace.bean.recognizer.AnnotationBeanRecognizer;
import org.example.liteworkspace.bean.recognizer.BeanMethodRecognizer;
import org.example.liteworkspace.bean.recognizer.XmlBeanRecognizer;
import org.example.liteworkspace.bean.resolver.*;
import org.example.liteworkspace.bean.dependency.*;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Map;

public class LiteScanAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        if (project == null || !(psiFile instanceof PsiJavaFile)) {
            return;
        }

        PsiClass[] classes = ((PsiJavaFile) psiFile).getClasses();
        if (classes.length == 0) return;

        PsiClass targetClass = classes[0];

        if (targetClass == null) {
            Messages.showDialog(
                    project,
                    "请右键在类名上运行LiteWorkspace",
                    "LiteWorkspace 工具",
                    new String[]{"确定"},
                    0,
                    Messages.getInformationIcon()
            );
            return;
        }

        // 初始化组件
        BeanRegistry registry = new BeanRegistry();
        BeanScanCoordinator coordinator = new BeanScanCoordinator(
                List.of(
                        new AnnotationBeanRecognizer(),
                        new BeanMethodRecognizer(project),
                        new XmlBeanRecognizer(project)
                ),
                List.of(new DefaultDependencyResolver(project)),
                List.of(
                        new AnnotationBeanBuilder(),
                        new MyBatisMapperBuilder(),
                        new XmlBeanBuilder(new XmlBeanResolver(project))
                )
        );
        // 扫描和注册
        coordinator.scanAndBuild(targetClass, registry);
        // 写入到文件
        // ✅ 写入 XML
        writeXml(project, targetClass, registry.getBeanXmlMap());
    }

    private void writeXml(Project project, PsiClass clazz, Map<String, String> beanMap) {
        try {
            Module module = ModuleUtilCore.findModuleForPsiElement(clazz);
            if (module == null) return;

            String qualifiedName = clazz.getQualifiedName();
            String className = clazz.getName();
            String packageName = qualifiedName.substring(0, qualifiedName.lastIndexOf('.'));
            String testClassName = className + "Test";
            String relativePath = packageName.replace('.', '/');

            // 定位 test/resources 和 test/java 目录
            VirtualFile[] roots = ModuleRootManager.getInstance(module).getSourceRoots();
            VirtualFile testJava = null, testRes = null;
            for (VirtualFile root : roots) {
                String path = root.getPath().replace("\\", "/");
                if (path.endsWith("/src/test/java")) testJava = root;
                if (path.endsWith("/src/test/resources")) testRes = root;
            }

            if (testJava == null || testRes == null) {
                Messages.showErrorDialog(project, "未找到 test/java 或 test/resources 目录", "LiteWorkspace 错误");
                return;
            }

            File javaDir = new File(testJava.getPath(), relativePath);
            File resDir = new File(testRes.getPath(), relativePath);
            javaDir.mkdirs();
            resDir.mkdirs();

            File xmlFile = writeSpringXmlFile(beanMap, resDir, testClassName);
            File testFile = writeJUnitTestFile(packageName, className, testClassName, relativePath, javaDir);

            VfsUtil.findFileByIoFile(xmlFile, true).refresh(false, false);
            VfsUtil.findFileByIoFile(testFile, true).refresh(false, false);

            Messages.showInfoMessage(project,
                    "✅ 已生成：\n" + testFile.getAbsolutePath(),
                    "LiteWorkspace");

        } catch (Exception ex) {
            Messages.showErrorDialog(project,
                    "❌ 生成失败：" + ex.getMessage(),
                    "LiteWorkspace");
        }
    }

    private File writeSpringXmlFile(Map<String, String> beanMap, File resDir, String testClassName) throws Exception {
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
                                    String relativePath, File javaDir) throws Exception {
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
            // 替换注解
            String content = new String(java.nio.file.Files.readAllBytes(testFile.toPath()));
            content = content.replaceAll(
                    "@ContextConfiguration\\(locations\\s*=\\s*\"[^\"]*\"\\)",
                    configLine
            );
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
