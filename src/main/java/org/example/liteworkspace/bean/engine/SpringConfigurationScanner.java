package org.example.liteworkspace.bean.engine; // 请替换为你的实际包名

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.*;
import java.util.Properties;

// ... (包声明和 import 保持不变) ...

public class SpringConfigurationScanner {

    private static final String COMPONENT_SCAN_ANNOTATION = "org.springframework.context.annotation.ComponentScan";
    private static final String CONFIGURATION_ANNOTATION = "org.springframework.context.annotation.Configuration";
    private static final String SPRING_BOOT_APP_ANNOTATION = "org.springframework.boot.autoconfigure.SpringBootApplication";
    private static final String SPRING_FACTORIES_PATH = "META-INF/spring.factories";
    private static final String ENABLE_AUTO_CONFIGURATION_KEY = "org.springframework.boot.autoconfigure.EnableAutoConfiguration";

    /**
     * 扫描项目中的所有模块和依赖 JAR，获取有效的 Spring 组件扫描包路径。
     * 这包括显式配置的路径和默认路径（如 @ComponentScan 无参数时扫描其所在包）。
     *
     * @param project 当前 IntelliJ IDEA 项目
     * @return 去重后的有效扫描包路径集合
     */
    @NotNull
    public Set<String> scanEffectiveComponentScanPackages(@NotNull Project project) {
        Set<String> allScanPackages = new HashSet<>();

        // 1. 扫描项目源代码中的配置
        Set<String> projectScanPackages = scanProjectSourceForComponentScan(project);
        allScanPackages.addAll(projectScanPackages);

        // 2. 扫描依赖库 (JARs) 中的 spring.factories
        Set<String> jarScanPackages = scanJarsForSpringFactories(project);
        allScanPackages.addAll(jarScanPackages);

        return allScanPackages;
    }

    /**
     * 扫描项目源代码中的 @ComponentScan 和 XML <context:component-scan>。
     *
     * @param project 当前项目
     * @return 项目源代码中定义的扫描包路径
     */
    @NotNull
    private Set<String> scanProjectSourceForComponentScan(@NotNull Project project) {
        Set<String> scanPackages = new HashSet<>();

        ModuleManager moduleManager = ModuleManager.getInstance(project);
        Module[] modules = moduleManager.getModules();

        for (Module module : modules) {
            VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(false);

            for (VirtualFile sourceRoot : sourceRoots) {
                // --- 修正点：使用递归遍历来查找 Java 文件 ---
                collectComponentScanPackagesFromJava(project, sourceRoot, scanPackages);
                // --- 修正点结束 ---

                // 扫描 XML 配置 (这部分保持不变)
                scanPackages.addAll(getComponentScanPackagesFromXml(sourceRoot));
            }
        }

        return scanPackages;
    }

    /**
     * --- 修正点：新增方法，递归遍历 VirtualFile 目录结构来查找 Java 文件并处理 ---
     * 递归地收集源根目录下所有 Java 文件中定义的 @ComponentScan 包路径。
     *
     * @param project       当前项目
     * @param directory     当前遍历的 VirtualFile 目录
     * @param scanPackages  用于收集扫描包路径的集合
     */
    private void collectComponentScanPackagesFromJava(@NotNull Project project, @NotNull VirtualFile directory, @NotNull Set<String> scanPackages) {
        // 遍历目录下的所有子文件/文件夹
        for (VirtualFile file : directory.getChildren()) {
            if (file.isDirectory()) {
                // 如果是目录，递归调用
                collectComponentScanPackagesFromJava(project, file, scanPackages);
            } else if ("java".equals(file.getExtension())) {
                // 如果是 .java 文件，处理它
                processJavaFileForComponentScan(project, file, scanPackages);
            }
        }
    }

    /**
     * --- 修正点：新增方法，处理单个 Java 文件 ---
     * 处理单个 Java 文件，查找其中的 @ComponentScan, @SpringBootApplication, @MapperScan 等注解并提取相关包路径。
     *
     * @param project       当前项目
     * @param javaFile      要处理的 Java VirtualFile
     * @param scanPackages  用于收集扫描包路径的集合 (用于 @ComponentScan, @SpringBootApplication)
     */
    private void processJavaFileForComponentScan(@NotNull Project project, @NotNull VirtualFile javaFile, @NotNull Set<String> scanPackages) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(javaFile);
        if (!(psiFile instanceof PsiJavaFile)) {
            return; // 不是有效的 Java 文件
        }
        PsiJavaFile javaPsiFile = (PsiJavaFile) psiFile;

        // 遍历文件中的所有类 (包括内部类，尽管主类通常在顶层)
        for (PsiClass psiClass : javaPsiFile.getClasses()) {

            // --- 处理 @SpringBootApplication ---
            PsiAnnotation springBootAppAnnotation = psiClass.getAnnotation(SPRING_BOOT_APP_ANNOTATION);
            if (springBootAppAnnotation != null) {
                boolean hasExplicitBasePackages = false;
                boolean hasExplicitBasePackageClasses = false;

                // 检查 @SpringBootApplication 的 basePackages 属性 (覆盖内部 @ComponentScan 的)
                PsiAnnotationMemberValue sbBasePackagesValue = springBootAppAnnotation.findDeclaredAttributeValue("basePackages");
                if (sbBasePackagesValue != null) {
                    hasExplicitBasePackages = true;
                    List<String> packages = parseStringArrayOrList(sbBasePackagesValue);
                    scanPackages.addAll(packages);
                }

                // 检查 @SpringBootApplication 的 basePackageClasses 属性 (覆盖内部 @ComponentScan 的)
                PsiAnnotationMemberValue sbBasePackageClassesValue = springBootAppAnnotation.findDeclaredAttributeValue("basePackageClasses");
                if (sbBasePackageClassesValue != null) {
                    hasExplicitBasePackageClasses = true;
                    List<String> packageFromClassRefs = parseClassArray(sbBasePackageClassesValue, project);
                    scanPackages.addAll(packageFromClassRefs);
                }

                // 如果 @SpringBootApplication 没有显式指定 basePackages 或 basePackageClasses，
                // 则默认扫描主应用类所在的包及其子包
                if (!hasExplicitBasePackages && !hasExplicitBasePackageClasses) {
                    String defaultPackage = getPackageName(psiClass);
                    if (defaultPackage != null && !defaultPackage.isEmpty()) {
                        scanPackages.add(defaultPackage);
                    }
                }
                // 注意：即使 @SpringBootApplication 没有显式指定扫描路径，
                // 它内部的 @ComponentScan 也不会被触发，因为外部的属性已经处理了。
            }

            // --- 处理独立的 @ComponentScan ---
            // (通常在非 Spring Boot 项目或需要额外扫描路径时使用)
            // 只有在没有 @SpringBootApplication 或者 @SpringBootApplication 没有覆盖时才考虑
            // 为了简化，我们假设两者可以独立存在并都生效
            PsiAnnotation componentScanAnnotation = psiClass.getAnnotation(COMPONENT_SCAN_ANNOTATION);
            if (componentScanAnnotation != null) {
                boolean hasExplicitBasePackages = false;
                boolean hasExplicitBasePackageClasses = false;

                // 1. 检查 basePackages 属性
                PsiAnnotationMemberValue basePackagesValue = componentScanAnnotation.findDeclaredAttributeValue("basePackages");
                if (basePackagesValue != null) {
                    hasExplicitBasePackages = true;
                    List<String> packages = parseStringArrayOrList(basePackagesValue);
                    scanPackages.addAll(packages);
                }

                // 2. 检查 basePackageClasses 属性
                PsiAnnotationMemberValue basePackageClassesValue = componentScanAnnotation.findDeclaredAttributeValue("basePackageClasses");
                if (basePackageClassesValue != null) {
                    hasExplicitBasePackageClasses = true;
                    List<String> packageFromClassRefs = parseClassArray(basePackageClassesValue, project);
                    scanPackages.addAll(packageFromClassRefs);
                }

                // 3. 如果 @ComponentScan 没有显式指定，则使用默认包 (当前类所在的包)
                // 注意：如果类上同时有 @SpringBootApplication 和 @ComponentScan，
                // 且 @SpringBootApplication 没有指定路径，那么这里的默认路径和上面的默认路径是同一个。
                // 这是符合 Spring 行为的，因为它们都指向同一个包。
                if (!hasExplicitBasePackages && !hasExplicitBasePackageClasses) {
                    String defaultPackage = getPackageName(psiClass);
                    if (defaultPackage != null && !defaultPackage.isEmpty()) {
                        scanPackages.add(defaultPackage);
                    }
                }
            }
        }
    }

    /**
     * 从模块的资源文件中提取 XML 配置定义的 <context:component-scan> 包路径。
     * (此方法保持不变)
     * @param sourceRoot 模块的源代码根目录
     * @return 从 XML 配置中提取的扫描包路径集合
     */
    @NotNull
    private Set<String> getComponentScanPackagesFromXml(@NotNull VirtualFile sourceRoot) {
        Set<String> scanPackages = new HashSet<>();
        collectAndParseXmlFiles(sourceRoot, scanPackages);
        return scanPackages;
    }

    /**
     * 递归收集并解析目录下的 XML 文件
     * (此方法保持不变)
     */
    private void collectAndParseXmlFiles(VirtualFile directory, Set<String> scanPackages) {
        for (VirtualFile file : directory.getChildren()) {
            if (file.isDirectory()) {
                collectAndParseXmlFiles(file, scanPackages);
            } else if ("xml".equalsIgnoreCase(file.getExtension())) {
                parseXmlFileForComponentScan(file, scanPackages);
            }
        }
    }

    /**
     * 解析单个 XML 文件，查找 <context:component-scan> 并提取 base-package
     * (此方法保持不变)
     */
    private void parseXmlFileForComponentScan(VirtualFile xmlFile, Set<String> scanPackages) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            File file = new File(xmlFile.getPath());
            org.w3c.dom.Document document = builder.parse(file);

            org.w3c.dom.NodeList componentScanNodes = document.getElementsByTagNameNS("http://www.springframework.org/schema/context", "component-scan");
            if (componentScanNodes.getLength() == 0) {
                componentScanNodes = document.getElementsByTagName("context:component-scan");
            }
            if (componentScanNodes.getLength() == 0) {
                componentScanNodes = document.getElementsByTagName("component-scan");
            }

            for (int i = 0; i < componentScanNodes.getLength(); i++) {
                org.w3c.dom.Node node = componentScanNodes.item(i);
                if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    org.w3c.dom.Element element = (org.w3c.dom.Element) node;
                    String basePackageAttr = element.getAttribute("base-package");
                    if (basePackageAttr != null && !basePackageAttr.isEmpty()) {
                        String[] packages = basePackageAttr.split("[,;\\s]+");
                        for (String pkg : packages) {
                            String trimmedPkg = pkg.trim();
                            if (!trimmedPkg.isEmpty()) {
                                scanPackages.add(trimmedPkg);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing XML file for component scan: " + xmlFile.getPath() + " - " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 扫描项目依赖的 JAR 包中的 META-INF/spring.factories 文件。
     * (此方法保持不变，除了可能的 import 调整)
     * @param project 当前项目
     * @return 从 JAR 包 spring.factories 中推断出的扫描包路径
     */
    @NotNull
    private Set<String> scanJarsForSpringFactories(@NotNull Project project) {
        Set<String> scanPackagesFromJars = new HashSet<>();

        OrderEnumerator orderEnumerator = OrderEnumerator.orderEntries(project);
        orderEnumerator.forEachLibrary(library -> {
            VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
            for (VirtualFile file : files) {
                if (file.getFileSystem() instanceof JarFileSystem) {
                    VirtualFile jarRoot = file;
                    VirtualFile springFactoriesFile = jarRoot.findFileByRelativePath(SPRING_FACTORIES_PATH);
                    if (springFactoriesFile != null) {
                        try {
                            Properties properties = new Properties();
                            try (InputStream is = springFactoriesFile.getInputStream()) {
                                properties.load(is);
                            }

                            String autoConfigClassesStr = properties.getProperty(ENABLE_AUTO_CONFIGURATION_KEY);
                            if (autoConfigClassesStr != null && !autoConfigClassesStr.isEmpty()) {
                                String[] autoConfigClasses = autoConfigClassesStr.split("[,\\s]+");
                                for (String autoConfigClassName : autoConfigClasses) {
                                    String trimmedClassName = autoConfigClassName.trim();
                                    if (!trimmedClassName.isEmpty()) {
                                        PsiClass autoConfigPsiClass = JavaPsiFacade.getInstance(project)
                                                .findClass(trimmedClassName, GlobalSearchScope.allScope(project));

                                        if (autoConfigPsiClass != null) {
                                            PsiAnnotation csAnnotation = autoConfigPsiClass.getAnnotation(COMPONENT_SCAN_ANNOTATION);
                                            if (csAnnotation != null) {
                                                boolean hasExplicitBasePackages = false;
                                                boolean hasExplicitBasePackageClasses = false;

                                                PsiAnnotationMemberValue basePackagesValue = csAnnotation.findDeclaredAttributeValue("basePackages");
                                                if (basePackagesValue != null) {
                                                    hasExplicitBasePackages = true;
                                                    List<String> packages = parseStringArrayOrList(basePackagesValue);
                                                    scanPackagesFromJars.addAll(packages);
                                                }

                                                PsiAnnotationMemberValue basePackageClassesValue = csAnnotation.findDeclaredAttributeValue("basePackageClasses");
                                                if (basePackageClassesValue != null) {
                                                    hasExplicitBasePackageClasses = true;
                                                    List<String> packageFromClassRefs = parseClassArray(basePackageClassesValue, project);
                                                    scanPackagesFromJars.addAll(packageFromClassRefs);
                                                }

                                                if (!hasExplicitBasePackages && !hasExplicitBasePackageClasses) {
                                                    String defaultPackage = getPackageName(autoConfigPsiClass);
                                                    if (defaultPackage != null && !defaultPackage.isEmpty()) {
                                                        scanPackagesFromJars.add(defaultPackage);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (IOException e) {
                            System.err.println("Error reading spring.factories from JAR: " + springFactoriesFile.getPath() + " - " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            }
            return true;
        });

        return scanPackagesFromJars;
    }


    // --- 辅助方法 (保持不变) ---

    @Nullable
    private String getPackageName(PsiClass psiClass) {
        PsiFile containingFile = psiClass.getContainingFile();
        if (containingFile instanceof PsiJavaFile javaFile) {
            String packageName = javaFile.getPackageName();
            return packageName != null ? packageName : "";
        }
        return null;
    }

    private List<String> parseStringArrayOrList(PsiAnnotationMemberValue value) {
        List<String> result = new ArrayList<>();
        if (value instanceof PsiLiteralExpression literal) {
            Object val = literal.getValue();
            if (val instanceof String strVal) {
                String[] parts = strVal.split("[,;\\s]+");
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() > 1) {
                        result.add(trimmed.substring(1, trimmed.length() - 1));
                    } else if (!trimmed.startsWith("\"") && !trimmed.endsWith("\"")) {
                        result.add(trimmed);
                    }
                }
            }
        } else if (value instanceof PsiArrayInitializerMemberValue arrayValue) {
            PsiAnnotationMemberValue[] initializers = arrayValue.getInitializers();
            for (PsiAnnotationMemberValue initializer : initializers) {
                if (initializer instanceof PsiLiteralExpression literal && literal.getValue() instanceof String strVal) {
                    if (strVal.startsWith("\"") && strVal.endsWith("\"") && strVal.length() > 1) {
                        result.add(strVal.substring(1, strVal.length() - 1));
                    } else {
                        result.add(strVal);
                    }
                }
            }
        }
        return result;
    }

    private List<String> parseClassArray(PsiAnnotationMemberValue value, Project project) {
        List<String> result = new ArrayList<>();
        if (value instanceof PsiClassObjectAccessExpression classObjectAccess) {
            PsiTypeElement operand = classObjectAccess.getOperand();
            if (operand != null) {
                PsiClass psiClass = PsiUtil.resolveClassInType(operand.getType());
                if (psiClass != null) {
                    String pkgName = getPackageName(psiClass);
                    if (pkgName != null && !pkgName.isEmpty()) {
                        result.add(pkgName);
                    }
                }
            }
        }
        else if (value instanceof PsiArrayInitializerMemberValue arrayValue) {
            PsiAnnotationMemberValue[] initializers = arrayValue.getInitializers();
            for (PsiAnnotationMemberValue initializer : initializers) {
                if (initializer instanceof PsiClassObjectAccessExpression classObjectAccess) {
                    PsiTypeElement operand = classObjectAccess.getOperand();
                    if (operand != null) {
                        PsiClass psiClass = PsiUtil.resolveClassInType(operand.getType());
                        if (psiClass != null) {
                            String pkgName = getPackageName(psiClass);
                            if (pkgName != null && !pkgName.isEmpty()) {
                                result.add(pkgName);
                            }
                        }
                    }
                }
            }
        }
        return result;
    }
}