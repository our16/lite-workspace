package org.example.liteworkspace.bean.engine;

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
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import org.example.liteworkspace.bean.core.DatasourceConfig;
import org.example.liteworkspace.bean.core.context.LiteProjectContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

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
                            showError(project, "未能定位当前类所属的模块");
                            return;
                        }

                        String qualifiedName = clazz.getQualifiedName();
                        String className = clazz.getName();
                        if (qualifiedName == null || className == null) {
                            showError(project, "类名无法解析，生成终止");
                            return;
                        }

                        String packageName = qualifiedName.substring(0, qualifiedName.lastIndexOf('.'));
                        String testClassName = className + "Test";
                        String relativePath = packageName.replace('.', '/');

                        // 🔍 查找标准的 src/test/java 和 src/test/resources 目录
                        VirtualFile testJavaDir = findTestSourceFolder(module, clazz, "java");
                        VirtualFile testResourcesDir = findTestSourceFolder(module, clazz,"resources");

                        if (testJavaDir == null || testResourcesDir == null) {
                            showError(project, "未找到标准的测试目录（src/test/java 或 src/test/resources），请确保项目是基于 Maven/Gradle 标准结构。\n" +
                                    "或者手动创建这些目录后再试。");
                            return;
                        }

                        // 确保相对路径目录存在（在测试目录下）
                        File javaTestDir = new File(testJavaDir.getPath(), relativePath);
                        File resourcesTestDir = new File(testResourcesDir.getPath(), relativePath);

                        if (!javaTestDir.exists()) {
                            javaTestDir.mkdirs();
                        }
                        if (!resourcesTestDir.exists()) {
                            resourcesTestDir.mkdirs();
                        }
                        DatasourceConfig datasourceConfig = context.getSpringContext().getDatasourceConfig();
                        String defaultConfigXmlPath = datasourceConfig.getImportPath();
                        Set<String> definedBeanClasses = new HashSet<>();

                        if (defaultConfigXmlPath != null) {
                            File xml = new File(defaultConfigXmlPath);
                            if (xml.exists()) {
                                try {
                                    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                                    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                                    Document doc = dBuilder.parse(xml);
                                    doc.getDocumentElement().normalize();

                                    NodeList beanList = doc.getElementsByTagName("bean");
                                    for (int i = 0; i < beanList.getLength(); i++) {
                                        Element beanElement = (Element) beanList.item(i);
                                        if (beanElement.hasAttribute("class")) {
                                            definedBeanClasses.add(beanElement.getAttribute("class"));
                                        }
                                    }

                                } catch (Exception e) {
                                    System.err.println("Failed to parse config XML: " + defaultConfigXmlPath);
                                    e.printStackTrace();
                                }
                            } else {
                                System.err.println("Default config XML file does not exist: " + defaultConfigXmlPath);
                            }
                        }

                        // 过滤 beanMap 中已被默认配置定义的 class
                        Iterator<String> iter = beanMap.keySet().iterator();
                        while (iter.hasNext()) {
                            String definedClassName = iter.next();
                            if (definedBeanClasses.contains(definedClassName)) {
                                iter.remove(); // 移除重复定义的类
                            }
                        }
                        File xmlFile = writeSpringXmlFile(beanMap, resourcesTestDir, testClassName);
                        File testFile = writeJUnitTestFile(packageName, className, testClassName, relativePath, javaTestDir);

                        // 刷新虚拟文件系统
                        VfsUtil.findFileByIoFile(xmlFile, true).refresh(false, false);
                        VfsUtil.findFileByIoFile(testFile, true).refresh(false, false);

                        Messages.showInfoMessage(project,
                                "✅ 已生成：\n" +
                                        "测试类: " + testFile.getAbsolutePath() + "\n" +
                                        "配置文件: " + xmlFile.getAbsolutePath(),
                                "LiteWorkspace");
                        // 打开测试文件
                        VirtualFile virtualFile = VfsUtil.findFileByIoFile(testFile, true);
                        if (virtualFile != null) {
                            FileEditorManager.getInstance(project).openFile(virtualFile, true);
                        }

                    } catch (Exception ex) {
                        showError(project, "❌ 生成失败：" + ex.getMessage());
                    }
                })
        );
    }

    private VirtualFile findTestSourceFolder(Module module ,PsiClass clazz, String type) {
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
                        if (testPath.startsWith(ce.getFile().getPath())) {
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

    private File writeJUnitTestFile(String packageName, String className, String testClassName,
                                    String relativePath, File javaTestDir) throws IOException {
        File testFile = new File(javaTestDir, testClassName + ".java");
        try (FileWriter fw = new FileWriter(testFile)) {
            fw.write(String.format("""
                            package %s;
                            
                            import org.junit.Test;
                            import org.junit.runner.RunWith;
                            import org.springframework.test.context.ContextConfiguration;
                            import org.springframework.test.context.junit4.SpringRunner;
                            import javax.annotation.Resource;
                            
                            @RunWith(SpringRunner.class)
                            @ContextConfiguration(locations = "classpath:%s/%s.xml")
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
                    relativePath, testClassName,
                    testClassName,
                    className,
                    decapitalize(className),
                    decapitalize(className),
                    decapitalize(className)
            ));
        }
        return testFile;
    }

    private String decapitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}