package org.example.liteworkspace.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.example.liteworkspace.bean.BeanDefinitionBuilder;
import org.example.liteworkspace.bean.builder.AnnotationBeanBuilder;
import org.example.liteworkspace.bean.builder.MyBatisMapperBuilder;
import org.example.liteworkspace.bean.builder.XmlBeanBuilder;
import org.example.liteworkspace.bean.core.*;
import org.example.liteworkspace.bean.recognizer.AnnotationBeanRecognizer;
import org.example.liteworkspace.bean.recognizer.BeanMethodRecognizer;
import org.example.liteworkspace.bean.recognizer.XmlBeanRecognizer;
import org.example.liteworkspace.bean.resolver.*;
import org.example.liteworkspace.bean.dependency.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
        BeanScanCoordinator coordinator = getBeanScanCoordinator(project);
        // 扫描和注册
        coordinator.scanAndBuild(targetClass, registry);
        // 写入到文件
        // ✅ 写入 XML
        writeXml(project, targetClass, registry.getBeanXmlMap());
    }

    private static @NotNull BeanScanCoordinator getBeanScanCoordinator(Project project) {
        BeanScanCoordinator coordinator = new BeanScanCoordinator(
                List.of(
                        new AnnotationBeanRecognizer(),
                        new BeanMethodRecognizer(project),
                        new XmlBeanRecognizer(project)
                ),
                List.of(new DefaultDependencyResolver(project)),
                List.of()
        );
        // ✅ 现在创建需要依赖 coordinator 的 builder
        AnnotationBeanBuilder annotationBuilder = new AnnotationBeanBuilder(coordinator);

// ✅ 组装所有 builders
        List<BeanDefinitionBuilder> builders = List.of(
                annotationBuilder,
                new MyBatisMapperBuilder(),
                new XmlBeanBuilder(new XmlBeanResolver(project))
        );
        // ✅ 回填 builders 到 coordinator
        coordinator.setBuilders(builders);
        return coordinator;
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
            final VirtualFile[] testJava = {null};
            final VirtualFile[] testRes = { null };

            for (VirtualFile root : roots) {
                String path = root.getPath().replace("\\", "/");
                if (path.endsWith("/src/test/java")) {
                    testJava[0] = root;
                }
                if (path.endsWith("/src/test/resources")) {
                    testRes[0] = root;
                }
            }

            if (testJava[0] == null || testRes[0] == null) {
                // 获取 module 的 baseDir（通常是 src 的上级）
                VirtualFile baseDir = module.getModuleFile() != null ? module.getModuleFile().getParent() : null;
                if (baseDir == null) {
                    Messages.showErrorDialog(project, "无法确定 module 路径", "LiteWorkspace 错误");
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

            Messages.showInfoMessage(project,
                    "✅ 已生成：\n" + testFile.getAbsolutePath(),
                    "LiteWorkspace");

        } catch (Exception ex) {
            Messages.showErrorDialog(project,
                    "❌ 生成失败：" + ex.getMessage(),
                    "LiteWorkspace");
        }
    }

    private static void createTestPath(Project project, VirtualFile baseDir, VirtualFile[] testJava, VirtualFile[] testRes) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                VirtualFile srcDir = baseDir.findChild("src");
                if (srcDir == null) {
                    srcDir = baseDir.createChildDirectory(null, "src");
                }

                VirtualFile testDir = srcDir.findChild("test");
                if (testDir == null) {
                    testDir = srcDir.createChildDirectory(null, "test");
                }

                if (testJava[0] == null) {
                    VirtualFile javaDir = testDir.findChild("java");
                    if (javaDir == null) {
                        testJava[0] = testDir.createChildDirectory(null, "java");
                    } else {
                        testJava[0] = javaDir;
                    }
                }

                if (testRes[0] == null) {
                    VirtualFile resDir = testDir.findChild("resources");
                    if (resDir == null) {
                        testRes[0] = testDir.createChildDirectory(null, "resources");
                    } else {
                        testRes[0] = resDir;
                    }
                }

            } catch (IOException e) {
                Messages.showErrorDialog(project, "创建目录失败: " + e.getMessage(), "LiteWorkspace 错误");
            }
        });
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
