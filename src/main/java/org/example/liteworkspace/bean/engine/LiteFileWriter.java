package org.example.liteworkspace.bean.engine;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.example.liteworkspace.bean.core.DatasourceConfig;
import org.example.liteworkspace.bean.core.context.LiteProjectContext;
import org.example.liteworkspace.util.LogUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class LiteFileWriter {

    private final LiteProjectContext context;

    public LiteFileWriter(LiteProjectContext context) {
        this.context = context;
    }

    public void write(Project project, PsiClass clazz, Map<String, String> beanMap) {
        ApplicationManager.getApplication().invokeLater(() ->
                WriteCommandAction.runWriteCommandAction(project, () -> {
                    try {
                        Module module = ModuleUtilCore.findModuleForPsiElement(clazz);
                        if (module == null) {
                            notifyError(project, "未能定位当前类所属的模块");
                            return;
                        }

                        String qualifiedName = clazz.getQualifiedName();
                        String className = clazz.getName();
                        if (qualifiedName == null || className == null) {
                            notifyError(project, "类名无法解析，生成终止");
                            return;
                        }

                        String packageName = qualifiedName.substring(0, qualifiedName.lastIndexOf('.'));
                        String testClassName = className + "Test";
                        String relativePath = packageName.replace('.', '/');

                        // 查找测试目录
                        VirtualFile testJavaDir = findTestSourceFolder(module, clazz, "java");
                        VirtualFile testResourcesDir = findTestSourceFolder(module, clazz, "resources");

                        if (testJavaDir == null || testResourcesDir == null) {
                            notifyError(project, "未找到 src/test/java 或 src/test/resources，请检查项目结构");
                            return;
                        }

                        File javaTestDir = new File(testJavaDir.getPath(), relativePath);
                        File resourcesTestDir = new File(testResourcesDir.getPath(), relativePath);
                        javaTestDir.mkdirs();
                        resourcesTestDir.mkdirs();

                        // 解析默认 XML 配置
                        Set<String> definedBeanClasses = parseDefinedBeans(context.getDatasourceConfig().getImportPath());

                        // 过滤重复 bean
                        beanMap.keySet().removeIf(definedBeanClasses::contains);

                        // 写文件
                        File xmlFile = writeSpringXmlFile(beanMap, resourcesTestDir, testClassName);
                        File testFile = writeJUnitTestFile(packageName, className, testClassName, relativePath, javaTestDir);

                        Objects.requireNonNull(VfsUtil.findFileByIoFile(xmlFile, true)).refresh(false, false);
                        VirtualFile virtualTestFile = VfsUtil.findFileByIoFile(testFile, true);
                        if (virtualTestFile != null) {
                            virtualTestFile.refresh(false, false);
                            FileEditorManager.getInstance(project).openFile(virtualTestFile, true);
                        }

                        notifyInfo(project,
                                "测试类与配置已生成",
                                "测试类: " + testFile.getAbsolutePath() + "\n配置文件: " + xmlFile.getAbsolutePath());
                        LogUtil.info("已生成测试类={} 配置文件={}", testFile.getAbsolutePath(), xmlFile.getAbsolutePath());
                    } catch (Exception ex) {
                        notifyError(project, "生成失败: " + ex.getMessage());
                        LogUtil.error("生成测试文件失败", ex);
                    }
                })
        );
    }

    private Set<String> parseDefinedBeans(String xmlPath) {
        Set<String> definedBeans = new HashSet<>();
        if (xmlPath == null) {
            return definedBeans;
        }

        File xml = new File(xmlPath);
        if (!xml.exists()) {
            LogUtil.warn("配置文件不存在: {}", xmlPath);
            return definedBeans;
        }
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xml);
            doc.getDocumentElement().normalize();

            NodeList beanList = doc.getElementsByTagName("bean");
            for (int i = 0; i < beanList.getLength(); i++) {
                Element beanElement = (Element) beanList.item(i);
                if (beanElement.hasAttribute("class")) {
                    definedBeans.add(beanElement.getAttribute("class"));
                }
            }
        } catch (Exception e) {
            LogUtil.error("解析配置文件失败: " + xmlPath, e);
        }
        return definedBeans;
    }

    private void notifyInfo(Project project, String title, String content) {
        Notification notification = NotificationGroupManager.getInstance()
                .getNotificationGroup("LiteWorkspace")
                .createNotification(title, content, NotificationType.INFORMATION);
        notification.notify(project);
    }

    private void notifyError(Project project, String content) {
        Notification notification = NotificationGroupManager.getInstance()
                .getNotificationGroup("LiteWorkspace")
                .createNotification("生成失败", content, NotificationType.ERROR);
        notification.notify(project);
    }

    private VirtualFile findTestSourceFolder(Module module, PsiClass clazz, String type) {
        // 先尝试查找已经标记的测试目录
        ContentEntry[] contentEntries = ModuleRootManager.getInstance(module).getContentEntries();
        for (ContentEntry entry : contentEntries) {
            for (SourceFolder folder : entry.getSourceFolders()) {
                if (folder.isTestSource() && folder.getFile() != null) {
                    VirtualFile file = folder.getFile();
                    String path = file.getPath();
                    if (path != null && path.contains("/" + type)) {
                        return file;
                    }
                }
            }
        }

        // 👉 如果找不到，尝试查找物理路径
        String basePath = getPhysicalModuleBasePath(clazz);
        if (basePath != null) {
            String testPath = basePath + "/src/test/" + type;
            File testDir = new File(testPath);
            if (!testDir.exists()) {
                // 自动创建
                boolean created = testDir.mkdirs();
                if (!created) return null;
            }

            VirtualFile vf = VfsUtil.findFileByIoFile(testDir, true);
            if (vf != null) {
                // ✅ 可选：注册为测试目录（添加为 SourceFolder）
                WriteAction.run(() -> {
                    ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
                    ContentEntry contentEntry = null;
                    for (ContentEntry ce : model.getContentEntries()) {
                        VirtualFile file = ce.getFile();
                        if (file == null) {
                            continue;
                        }
                        if (testPath.startsWith(file.getPath())) {
                            contentEntry = ce;
                            break;
                        }
                    }
                    if (contentEntry != null) {
                        contentEntry.addSourceFolder(vf, /* isTestSource= */ true);
                        model.commit();
                    } else {
                        model.dispose();
                    }
                });
                return vf;
            }
        }

        return null;
    }

    /**
     * 通过类所在路径，推断当前模块的真实物理路径
     */
    private String getPhysicalModuleBasePath(PsiClass clazz) {
        VirtualFile file = clazz.getContainingFile().getVirtualFile();
        if (file == null) return null;

        VirtualFile parent = file.getParent();
        while (parent != null && !parent.getName().equals("src")) {
            parent = parent.getParent();
        }

        if (parent != null) {
            return parent.getParent().getPath(); // 模块目录
        }

        return null;
    }

    private void showError(Project project, String message) {
        ApplicationManager.getApplication().invokeLater(() ->
                Messages.showErrorDialog(project, message, "LiteWorkspace"));
    }

    private File writeSpringXmlFile(Map<String, String> beanMap, File resourcesTestDir, String testClassName) throws IOException {
        File xmlFile = new File(resourcesTestDir, testClassName + ".xml");
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

    private File writeJUnitTestFile(String packageName,
                                    String className,
                                    String testClassName,
                                    String relativePath,
                                    File javaTestDir) throws IOException {
        File testFile = new File(javaTestDir, testClassName + ".java");
        String methodName = getMethodName(context.findTargetMethod());
        String beanName = decapitalize(className);
        if (testFile.exists()) {
            Project project = context.getProject();
            // 已存在：用 PSI 解析 testFile，判断是否已有该方法的测试方法
            PsiFile psiFile = PsiManager.getInstance(project)
                    .findFile(Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(testFile)));
            if (psiFile instanceof PsiJavaFile javaFile) {
                PsiClass[] classes = javaFile.getClasses();
                if (classes.length > 0) {
                    PsiClass testClass = classes[0];
                    // 目标方法名 -> 测试方法名
                    String testMethodName = "test" + methodName;
                    boolean exists = Arrays.stream(testClass.getMethods())
                            .anyMatch(m -> m.getName().equals(testMethodName));

                    if (!exists) {
                        // 新建测试方法
                        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
                        PsiMethod newMethod = factory.createMethodFromText(String.format("""
                                @Test
                                public void %s() {
                                    // TODO: add assertions
                                    System.out.println("Run %s()");
                                }
                                """, testMethodName, methodName), testClass);

                        WriteCommandAction.runWriteCommandAction(project, () -> {
                            testClass.add(newMethod);
                        });
                    }
                }
            }
        } else {
            // 文件不存在：用模板创建测试类
            String template = loadTemplate(); // 从 resources 加载
            if (template == null) {
                throw new RuntimeException("缺少模板文件");
            }
            String content = template
                    .replace("${PACKAGE}", packageName)
                    .replace("${RELATIVE_PATH}", relativePath)
                    .replace("${TEST_CLASS}", testClassName)
                    .replace("${CLASS}", className)
                    .replace("${BEAN}", beanName)
                    .replace("${METHOD}", methodName);

            try (FileWriter fw = new FileWriter(testFile)) {
                fw.write(content);
            }
        }

        return testFile;
    }

    private String getMethodName(PsiMethod targetMethod) {
        String defaultName = "ContextLoads";
        if (targetMethod == null) {
            return defaultName;
        }
        return capitalize(targetMethod.getName());
    }

    private String loadTemplate() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("templates/TestClassTemplate.java")) {
            if (is == null) return null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private String capitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }


    private String decapitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}