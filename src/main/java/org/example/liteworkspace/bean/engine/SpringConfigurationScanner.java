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
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
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
    private static final String MAPPER_SCAN_ANNOTATION = "org.mybatis.spring.annotation.MapperScan";
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
                // --- 使用索引查找带有特定注解的类 ---
                collectComponentScanPackagesFromJava(project, module, scanPackages);
                // --- 修正点结束 ---

                // 扫描 XML 配置 (这部分保持不变)
                scanPackages.addAll(getComponentScanPackagesFromXml(sourceRoot));
            }
        }

        return scanPackages;
    }

    /**
     * 使用 IntelliJ IDEA 索引系统查找模块中带有特定注解的类
     *
     * @param project       当前项目
     * @param module        要扫描的模块
     * @param scanPackages  用于收集扫描包路径的集合
     */
    private void collectComponentScanPackagesFromJava(@NotNull Project project, @NotNull Module module, @NotNull Set<String> scanPackages) {
        // 创建模块范围的搜索范围
        GlobalSearchScope moduleScope = GlobalSearchScope.moduleScope(module);
        
        // 查找带有 @SpringBootApplication 注解的类
        findAndProcessAnnotatedClasses(project, moduleScope, SPRING_BOOT_APP_ANNOTATION, scanPackages);
        
        // 查找带有 @ComponentScan 注解的类
        findAndProcessAnnotatedClasses(project, moduleScope, COMPONENT_SCAN_ANNOTATION, scanPackages);
        
        // 查找带有 @MapperScan 注解的类
        findAndProcessAnnotatedClasses(project, moduleScope, MAPPER_SCAN_ANNOTATION, scanPackages);
    }
    
    /**
     * 查找并处理带有特定注解的类
     *
     * @param project       当前项目
     * @param searchScope   搜索范围
     * @param annotationName 注解全限定名
     * @param scanPackages  用于收集扫描包路径的集合
     */
    private void findAndProcessAnnotatedClasses(@NotNull Project project, @NotNull GlobalSearchScope searchScope,
                                               @NotNull String annotationName, @NotNull Set<String> scanPackages) {
        // 使用索引查找所有带有指定注解的类
        // 首先获取注解类，使用项目范围而不是模块范围，因为注解类可能在依赖库中
        PsiClass annotationClass = JavaPsiFacade.getInstance(project).findClass(annotationName, GlobalSearchScope.allScope(project));
        if (annotationClass == null) {
            // 如果找不到注解类，尝试使用另一种方法查找带有该注解的类
            findAnnotatedClassesByAnnotationName(project, searchScope, annotationName, scanPackages);
            return;
        }
        
        // 使用索引查找所有带有指定注解的类
        Collection<PsiClass> annotatedClasses = AnnotatedElementsSearch.searchPsiClasses(
                annotationClass,
                searchScope
        ).findAll();
        
        // 处理每个带有注解的类
        for (PsiClass psiClass : annotatedClasses) {
            processAnnotatedClass(psiClass, annotationName, scanPackages, project);
        }
    }
    
    /**
     * 当无法直接获取注解类时，通过注解名称查找带有该注解的类
     *
     * @param project       当前项目
     * @param searchScope   搜索范围
     * @param annotationName 注解全限定名
     * @param scanPackages  用于收集扫描包路径的集合
     */
    private void findAnnotatedClassesByAnnotationName(@NotNull Project project, @NotNull GlobalSearchScope searchScope,
                                                    @NotNull String annotationName, @NotNull Set<String> scanPackages) {
        // 使用 JavaPsiFacade 搜索所有类，然后检查它们是否带有指定的注解
        JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
        
        // 获取搜索范围内的所有类
        PsiClass[] allClasses = javaPsiFacade.findClasses("*", searchScope);
        
        for (PsiClass psiClass : allClasses) {
            // 检查类是否带有指定的注解
            PsiAnnotation annotation = psiClass.getAnnotation(annotationName);
            if (annotation != null) {
                processAnnotatedClass(psiClass, annotationName, scanPackages, project);
            }
        }
    }
    
    /**
     * 处理带有特定注解的类，提取包路径信息
     *
     * @param psiClass      带有注解的类
     * @param annotationName 注解全限定名
     * @param scanPackages  用于收集扫描包路径的集合
     * @param project       当前项目
     */
    private void processAnnotatedClass(@NotNull PsiClass psiClass, @NotNull String annotationName,
                                      @NotNull Set<String> scanPackages, @NotNull Project project) {
        // 根据注解类型进行不同的处理
        if (SPRING_BOOT_APP_ANNOTATION.equals(annotationName)) {
            processSpringBootApplicationAnnotation(psiClass, scanPackages, project);
        } else if (COMPONENT_SCAN_ANNOTATION.equals(annotationName)) {
            processComponentScanAnnotation(psiClass, scanPackages, project);
        } else if (MAPPER_SCAN_ANNOTATION.equals(annotationName)) {
            processMapperScanAnnotation(psiClass, scanPackages, project);
        }
    }
    
    /**
     * 处理 @SpringBootApplication 注解
     *
     * @param psiClass      带有注解的类
     * @param scanPackages  用于收集扫描包路径的集合
     * @param project       当前项目
     */
    private void processSpringBootApplicationAnnotation(@NotNull PsiClass psiClass, @NotNull Set<String> scanPackages,
                                                      @NotNull Project project) {
        PsiAnnotation springBootAppAnnotation = psiClass.getAnnotation(SPRING_BOOT_APP_ANNOTATION);
        if (springBootAppAnnotation != null) {
            boolean hasExplicitBasePackages = false;
            boolean hasExplicitBasePackageClasses = false;

            // 检查 @SpringBootApplication 的 basePackages 属性
            PsiAnnotationMemberValue sbBasePackagesValue = springBootAppAnnotation.findDeclaredAttributeValue("basePackages");
            if (sbBasePackagesValue != null) {
                hasExplicitBasePackages = true;
                List<String> packages = parseStringArrayOrList(sbBasePackagesValue);
                scanPackages.addAll(packages);
            }

            // 检查 @SpringBootApplication 的 basePackageClasses 属性
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
        }
    }
    
    /**
     * 处理 @ComponentScan 注解
     *
     * @param psiClass      带有注解的类
     * @param scanPackages  用于收集扫描包路径的集合
     * @param project       当前项目
     */
    private void processComponentScanAnnotation(@NotNull PsiClass psiClass, @NotNull Set<String> scanPackages,
                                              @NotNull Project project) {
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

            // 2. 检查 value 属性（basePackages 的别名）
            if (!hasExplicitBasePackages) {
                PsiAnnotationMemberValue valueAttr = componentScanAnnotation.findDeclaredAttributeValue("value");
                if (valueAttr != null) {
                    hasExplicitBasePackages = true;
                    List<String> packages = parseStringArrayOrList(valueAttr);
                    scanPackages.addAll(packages);
                }
            }

            // 3. 检查 basePackageClasses 属性
            PsiAnnotationMemberValue basePackageClassesValue = componentScanAnnotation.findDeclaredAttributeValue("basePackageClasses");
            if (basePackageClassesValue != null) {
                hasExplicitBasePackageClasses = true;
                List<String> packageFromClassRefs = parseClassArray(basePackageClassesValue, project);
                scanPackages.addAll(packageFromClassRefs);
            }

            // 4. 如果 @ComponentScan 没有显式指定，则使用默认包 (当前类所在的包)
            if (!hasExplicitBasePackages && !hasExplicitBasePackageClasses) {
                String defaultPackage = getPackageName(psiClass);
                if (defaultPackage != null && !defaultPackage.isEmpty()) {
                    scanPackages.add(defaultPackage);
                }
            }
        }
    }
    
    /**
     * 处理 @MapperScan 注解
     *
     * @param psiClass      带有注解的类
     * @param scanPackages  用于收集扫描包路径的集合
     * @param project       当前项目
     */
    private void processMapperScanAnnotation(@NotNull PsiClass psiClass, @NotNull Set<String> scanPackages,
                                           @NotNull Project project) {
        PsiAnnotation mapperScanAnnotation = psiClass.getAnnotation(MAPPER_SCAN_ANNOTATION);
        if (mapperScanAnnotation != null) {
            // 处理 basePackages 属性
            PsiAnnotationMemberValue basePackagesValue = mapperScanAnnotation.findDeclaredAttributeValue("basePackages");
            if (basePackagesValue != null) {
                List<String> packages = parseStringArrayOrList(basePackagesValue);
                scanPackages.addAll(packages);
            }
            
            // 处理 value 属性（basePackages 的别名）
            PsiAnnotationMemberValue valueAttr = mapperScanAnnotation.findDeclaredAttributeValue("value");
            if (valueAttr != null) {
                List<String> packages = parseStringArrayOrList(valueAttr);
                scanPackages.addAll(packages);
            }
            
            // 处理 basePackageClasses 属性
            PsiAnnotationMemberValue basePackageClassesValue = mapperScanAnnotation.findDeclaredAttributeValue("basePackageClasses");
            if (basePackageClassesValue != null) {
                List<String> packageFromClassRefs = parseClassArray(basePackageClassesValue, project);
                scanPackages.addAll(packageFromClassRefs);
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
     * 检查XML文件是否是Spring配置文件（包含bean依赖相关配置）
     * 通过内容判断而非文件名
     *
     * @param xmlFile 要检查的XML文件
     * @return 如果是Spring配置文件返回true，否则返回false
     */
    private boolean isSpringConfigurationFile(VirtualFile xmlFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            File file = new File(xmlFile.getPath());
            org.w3c.dom.Document document = builder.parse(file);
            
            // 检查是否包含Spring相关的命名空间
            org.w3c.dom.Element rootElement = document.getDocumentElement();
            if (rootElement == null) {
                return false;
            }
            
            // 检查根元素的命名空间
            String namespaceURI = rootElement.getNamespaceURI();
            if (namespaceURI != null && (
                    namespaceURI.contains("springframework.org") ||
                    namespaceURI.contains("spring.io"))) {
                return true;
            }
            
            // 检查根元素的标签名
            String tagName = rootElement.getTagName();
            if (tagName != null && (
                    tagName.contains("beans") ||
                    tagName.contains("context") ||
                    tagName.contains("component-scan") ||
                    tagName.contains("bean") ||
                    tagName.contains("import"))) {
                return true;
            }
            
            // 检查是否包含Spring相关的元素
            // 检查component-scan元素
            org.w3c.dom.NodeList componentScanNodes = document.getElementsByTagNameNS("http://www.springframework.org/schema/context", "component-scan");
            if (componentScanNodes.getLength() > 0) {
                return true;
            }
            componentScanNodes = document.getElementsByTagName("context:component-scan");
            if (componentScanNodes.getLength() > 0) {
                return true;
            }
            componentScanNodes = document.getElementsByTagName("component-scan");
            if (componentScanNodes.getLength() > 0) {
                return true;
            }
            
            // 检查bean元素
            org.w3c.dom.NodeList beanNodes = document.getElementsByTagNameNS("http://www.springframework.org/schema/beans", "bean");
            if (beanNodes.getLength() > 0) {
                return true;
            }
            beanNodes = document.getElementsByTagName("bean");
            if (beanNodes.getLength() > 0) {
                return true;
            }
            
            // 检查import元素
            org.w3c.dom.NodeList importNodes = document.getElementsByTagNameNS("http://www.springframework.org/schema/beans", "import");
            if (importNodes.getLength() > 0) {
                return true;
            }
            importNodes = document.getElementsByTagName("import");
            if (importNodes.getLength() > 0) {
                return true;
            }
            
            // 检查是否包含MyBatis相关的元素（这些通常不是Spring bean配置）
            org.w3c.dom.NodeList mapperNodes = document.getElementsByTagName("mapper");
            if (mapperNodes.getLength() > 0) {
                return false; // 这是MyBatis mapper文件，不是Spring配置文件
            }
            
            return false;
        } catch (Exception e) {
            // 如果解析出错，默认认为不是Spring配置文件
            return false;
        }
    }

    /**
     * 递归收集并解析目录下的 XML 文件
     * 优化：排除mapper.xml文件，只扫描bean依赖相关的XML文件，通过内容判断而非文件名
     */
    private void collectAndParseXmlFiles(VirtualFile directory, Set<String> scanPackages) {
        for (VirtualFile file : directory.getChildren()) {
            if (file.isDirectory()) {
                collectAndParseXmlFiles(file, scanPackages);
            } else if ("xml".equalsIgnoreCase(file.getExtension())) {
                // 首先检查是否是Spring配置文件（通过内容判断）
                if (isSpringConfigurationFile(file)) {
                    parseXmlFileForComponentScan(file, scanPackages);
                }
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