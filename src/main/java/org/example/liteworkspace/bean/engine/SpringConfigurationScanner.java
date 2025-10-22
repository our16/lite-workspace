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
import org.example.liteworkspace.util.CostUtil;
import org.example.liteworkspace.util.LogUtil;
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
        long startTime = System.currentTimeMillis();
        LogUtil.info("开始扫描Spring组件扫描包路径，项目: {}", project.getName());
        
        Set<String> allScanPackages = new HashSet<>();

        try {
            // 1. 扫描项目源代码中的配置
            LogUtil.debug("开始扫描项目源代码中的配置");
            Set<String> projectScanPackages = scanProjectSourceForComponentScan(project);
            LogUtil.debug("从项目源代码中扫描到 {} 个包路径", projectScanPackages.size());
            allScanPackages.addAll(projectScanPackages);

            // TODO jar 包里面按照配置的方式，不用去扫描
    //        // 2. 扫描依赖库 (JARs) 中的 spring.factories
    //        Set<String> jarScanPackages = scanJarsForSpringFactories(project);
    //        allScanPackages.addAll(jarScanPackages);
            
            LogUtil.info("Spring组件扫描包路径扫描完成，共找到 {} 个包路径: {}", allScanPackages.size(), allScanPackages);
        } catch (Exception e) {
            LogUtil.error("扫描Spring组件扫描包路径时发生错误", e);
            throw e;
        } finally {
            long cost = System.currentTimeMillis() - startTime;
            LogUtil.info("扫描Spring组件扫描包路径完成，耗时: {} ms", cost);
        }

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
        long startTime = System.currentTimeMillis();
        LogUtil.debug("开始扫描项目源代码中的Spring配置");
        
        Set<String> scanPackages = new HashSet<>();
        int initialSize = scanPackages.size();

        try {
            ModuleManager moduleManager = ModuleManager.getInstance(project);
            Module[] modules = moduleManager.getModules();
            LogUtil.debug("项目中共有 {} 个模块", modules.length);

            for (Module module : modules) {
                LogUtil.debug("正在扫描模块: {}", module.getName());
                VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(false);
                LogUtil.debug("模块 {} 中有 {} 个源代码根目录", module.getName(), sourceRoots.length);

                for (VirtualFile sourceRoot : sourceRoots) {
                    LogUtil.debug("正在扫描源代码根目录: {}", sourceRoot.getPath());
                    
                    // --- 使用索引查找带有特定注解的类 ---
                    int beforeJavaScan = scanPackages.size();
                    collectComponentScanPackagesFromJava(project, module, scanPackages);
                    LogUtil.debug("从Java配置中扫描到 {} 个新的包路径", scanPackages.size() - beforeJavaScan);
                    // --- 修正点结束 ---

                    // 扫描 XML 配置 (这部分保持不变)
                    int beforeXmlScan = scanPackages.size();
                    scanPackages.addAll(getComponentScanPackagesFromXml(sourceRoot));
                    LogUtil.debug("从XML配置中扫描到 {} 个新的包路径", scanPackages.size() - beforeXmlScan);
                }
            }
            
            LogUtil.debug("项目源代码扫描完成，共找到 {} 个包路径", scanPackages.size() - initialSize);
        } catch (Exception e) {
            LogUtil.error("扫描项目源代码中的Spring配置时发生错误", e);
            throw e;
        } finally {
            long cost = System.currentTimeMillis() - startTime;
            LogUtil.debug("扫描项目源代码中的Spring配置完成，耗时: {} ms", cost);
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
        long startTime = System.currentTimeMillis();
        LogUtil.debug("开始从Java配置中收集组件扫描包路径，模块: {}", module.getName());
        
        try {
            // 创建模块范围的搜索范围
            GlobalSearchScope moduleScope = GlobalSearchScope.moduleScope(module);
            
            // 查找带有 @SpringBootApplication 注解的类
            LogUtil.debug("正在查找带有 @SpringBootApplication 注解的类");
            findAndProcessAnnotatedClasses(project, moduleScope, SPRING_BOOT_APP_ANNOTATION, scanPackages);
            
            // 查找带有 @ComponentScan 注解的类
            LogUtil.debug("正在查找带有 @ComponentScan 注解的类");
            findAndProcessAnnotatedClasses(project, moduleScope, COMPONENT_SCAN_ANNOTATION, scanPackages);
            
            // 查找带有 @MapperScan 注解的类
            LogUtil.debug("正在查找带有 @MapperScan 注解的类");
            findAndProcessAnnotatedClasses(project, moduleScope, MAPPER_SCAN_ANNOTATION, scanPackages);
            
            LogUtil.debug("从Java配置中收集组件扫描包路径完成");
        } catch (Exception e) {
            LogUtil.error("从Java配置中收集组件扫描包路径时发生错误，模块: " + module.getName(), e);
            throw e;
        } finally {
            long cost = System.currentTimeMillis() - startTime;
            LogUtil.debug("从Java配置中收集组件扫描包路径完成，耗时: {} ms", cost);
        }
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
        long startTime = System.currentTimeMillis();
        LogUtil.debug("开始查找并处理带有 {} 注解的类", annotationName);
        
        try {
            // 使用索引查找所有带有指定注解的类
            // 首先获取注解类，使用项目范围而不是模块范围，因为注解类可能在依赖库中
            PsiClass annotationClass = JavaPsiFacade.getInstance(project).findClass(annotationName, GlobalSearchScope.allScope(project));
            if (annotationClass == null) {
                LogUtil.warn("找不到注解类: {}，将使用备用方法查找", annotationName);
                // 如果找不到注解类，尝试使用另一种方法查找带有该注解的类
                findAnnotatedClassesByAnnotationName(project, searchScope, annotationName, scanPackages);
                return;
            }
            
            // 使用索引查找所有带有指定注解的类
            Collection<PsiClass> annotatedClasses = AnnotatedElementsSearch.searchPsiClasses(
                    annotationClass,
                    searchScope
            ).findAll();
            
            LogUtil.debug("找到 {} 个带有 {} 注解的类", annotatedClasses.size(), annotationName);
            
            // 处理每个带有注解的类
            for (PsiClass psiClass : annotatedClasses) {
                LogUtil.debug("正在处理带有 {} 注解的类: {}", annotationName, psiClass.getQualifiedName());
                processAnnotatedClass(psiClass, annotationName, scanPackages, project);
            }
            
            LogUtil.debug("查找并处理带有 {} 注解的类完成", annotationName);
        } catch (Exception e) {
            LogUtil.error("查找并处理带有 " + annotationName + " 注解的类时发生错误", e);
            throw e;
        } finally {
            long cost = System.currentTimeMillis() - startTime;
            LogUtil.debug("查找并处理带有 {} 注解的类完成，耗时: {} ms", annotationName, cost);
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
        long startTime = System.currentTimeMillis();
        LogUtil.debug("开始通过注解名称查找带有 {} 注解的类", annotationName);
        
        try {
            // 使用 JavaPsiFacade 搜索所有类，然后检查它们是否带有指定的注解
            JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
            
            // 获取搜索范围内的所有类
            PsiClass[] allClasses = javaPsiFacade.findClasses("*", searchScope);
            LogUtil.debug("搜索范围内共有 {} 个类", allClasses.length);
            
            int annotatedClassCount = 0;
            for (PsiClass psiClass : allClasses) {
                // 检查类是否带有指定的注解
                PsiAnnotation annotation = psiClass.getAnnotation(annotationName);
                if (annotation != null) {
                    annotatedClassCount++;
                    LogUtil.debug("找到带有 {} 注解的类: {}", annotationName, psiClass.getQualifiedName());
                    processAnnotatedClass(psiClass, annotationName, scanPackages, project);
                }
            }
            
            LogUtil.debug("通过注解名称查找完成，共找到 {} 个带有 {} 注解的类", annotatedClassCount, annotationName);
        } catch (Exception e) {
            LogUtil.error("通过注解名称查找带有 " + annotationName + " 注解的类时发生错误", e);
            throw e;
        } finally {
            long cost = System.currentTimeMillis() - startTime;
            LogUtil.debug("通过注解名称查找带有 {} 注解的类完成，耗时: {} ms", annotationName, cost);
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
        LogUtil.debug("正在处理 @SpringBootApplication 注解，类: {}", psiClass.getQualifiedName());
        
        PsiAnnotation springBootAppAnnotation = psiClass.getAnnotation(SPRING_BOOT_APP_ANNOTATION);
        if (springBootAppAnnotation != null) {
            boolean hasExplicitBasePackages = false;
            boolean hasExplicitBasePackageClasses = false;
            int initialSize = scanPackages.size();

            // 检查 @SpringBootApplication 的 basePackages 属性
            PsiAnnotationMemberValue sbBasePackagesValue = springBootAppAnnotation.findDeclaredAttributeValue("basePackages");
            if (sbBasePackagesValue != null) {
                hasExplicitBasePackages = true;
                List<String> packages = parseStringArrayOrList(sbBasePackagesValue);
                LogUtil.debug("从 @SpringBootApplication 的 basePackages 属性中解析到包路径: {}", packages);
                scanPackages.addAll(packages);
            }

            // 检查 @SpringBootApplication 的 basePackageClasses 属性
            PsiAnnotationMemberValue sbBasePackageClassesValue = springBootAppAnnotation.findDeclaredAttributeValue("basePackageClasses");
            if (sbBasePackageClassesValue != null) {
                hasExplicitBasePackageClasses = true;
                List<String> packageFromClassRefs = parseClassArray(sbBasePackageClassesValue, project);
                LogUtil.debug("从 @SpringBootApplication 的 basePackageClasses 属性中解析到包路径: {}", packageFromClassRefs);
                scanPackages.addAll(packageFromClassRefs);
            }

            // 如果 @SpringBootApplication 没有显式指定 basePackages 或 basePackageClasses，
            // 则默认扫描主应用类所在的包及其子包
            if (!hasExplicitBasePackages && !hasExplicitBasePackageClasses) {
                String defaultPackage = getPackageName(psiClass);
                if (defaultPackage != null && !defaultPackage.isEmpty()) {
                    LogUtil.debug("@SpringBootApplication 未显式指定扫描包路径，使用默认包路径: {}", defaultPackage);
                    scanPackages.add(defaultPackage);
                }
            }
            
            LogUtil.debug("@SpringBootApplication 处理完成，新增 {} 个包路径", scanPackages.size() - initialSize);
        } else {
            LogUtil.warn("类 {} 声明有 @SpringBootApplication 注解但未找到注解实例", psiClass.getQualifiedName());
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
        LogUtil.debug("正在处理 @ComponentScan 注解，类: {}", psiClass.getQualifiedName());
        
        PsiAnnotation componentScanAnnotation = psiClass.getAnnotation(COMPONENT_SCAN_ANNOTATION);
        if (componentScanAnnotation != null) {
            boolean hasExplicitBasePackages = false;
            boolean hasExplicitBasePackageClasses = false;
            int initialSize = scanPackages.size();

            // 1. 检查 basePackages 属性
            PsiAnnotationMemberValue basePackagesValue = componentScanAnnotation.findDeclaredAttributeValue("basePackages");
            if (basePackagesValue != null) {
                hasExplicitBasePackages = true;
                List<String> packages = parseStringArrayOrList(basePackagesValue);
                LogUtil.debug("从 @ComponentScan 的 basePackages 属性中解析到包路径: {}", packages);
                scanPackages.addAll(packages);
            }

            // 2. 检查 value 属性（basePackages 的别名）
            if (!hasExplicitBasePackages) {
                PsiAnnotationMemberValue valueAttr = componentScanAnnotation.findDeclaredAttributeValue("value");
                if (valueAttr != null) {
                    hasExplicitBasePackages = true;
                    List<String> packages = parseStringArrayOrList(valueAttr);
                    LogUtil.debug("从 @ComponentScan 的 value 属性中解析到包路径: {}", packages);
                    scanPackages.addAll(packages);
                }
            }

            // 3. 检查 basePackageClasses 属性
            PsiAnnotationMemberValue basePackageClassesValue = componentScanAnnotation.findDeclaredAttributeValue("basePackageClasses");
            if (basePackageClassesValue != null) {
                hasExplicitBasePackageClasses = true;
                List<String> packageFromClassRefs = parseClassArray(basePackageClassesValue, project);
                LogUtil.debug("从 @ComponentScan 的 basePackageClasses 属性中解析到包路径: {}", packageFromClassRefs);
                scanPackages.addAll(packageFromClassRefs);
            }

            // 4. 如果 @ComponentScan 没有显式指定，则使用默认包 (当前类所在的包)
            if (!hasExplicitBasePackages && !hasExplicitBasePackageClasses) {
                String defaultPackage = getPackageName(psiClass);
                if (defaultPackage != null && !defaultPackage.isEmpty()) {
                    LogUtil.debug("@ComponentScan 未显式指定扫描包路径，使用默认包路径: {}", defaultPackage);
                    scanPackages.add(defaultPackage);
                }
            }
            
            LogUtil.debug("@ComponentScan 处理完成，新增 {} 个包路径", scanPackages.size() - initialSize);
        } else {
            LogUtil.warn("类 {} 声明有 @ComponentScan 注解但未找到注解实例", psiClass.getQualifiedName());
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
        LogUtil.debug("正在处理 @MapperScan 注解，类: {}", psiClass.getQualifiedName());
        
        PsiAnnotation mapperScanAnnotation = psiClass.getAnnotation(MAPPER_SCAN_ANNOTATION);
        if (mapperScanAnnotation != null) {
            int initialSize = scanPackages.size();
            
            // 处理 basePackages 属性
            PsiAnnotationMemberValue basePackagesValue = mapperScanAnnotation.findDeclaredAttributeValue("basePackages");
            if (basePackagesValue != null) {
                List<String> packages = parseStringArrayOrList(basePackagesValue);
                LogUtil.debug("从 @MapperScan 的 basePackages 属性中解析到包路径: {}", packages);
                scanPackages.addAll(packages);
            }
            
            // 处理 value 属性（basePackages 的别名）
            PsiAnnotationMemberValue valueAttr = mapperScanAnnotation.findDeclaredAttributeValue("value");
            if (valueAttr != null) {
                List<String> packages = parseStringArrayOrList(valueAttr);
                LogUtil.debug("从 @MapperScan 的 value 属性中解析到包路径: {}", packages);
                scanPackages.addAll(packages);
            }
            
            // 处理 basePackageClasses 属性
            PsiAnnotationMemberValue basePackageClassesValue = mapperScanAnnotation.findDeclaredAttributeValue("basePackageClasses");
            if (basePackageClassesValue != null) {
                List<String> packageFromClassRefs = parseClassArray(basePackageClassesValue, project);
                LogUtil.debug("从 @MapperScan 的 basePackageClasses 属性中解析到包路径: {}", packageFromClassRefs);
                scanPackages.addAll(packageFromClassRefs);
            }
            
            LogUtil.debug("@MapperScan 处理完成，新增 {} 个包路径", scanPackages.size() - initialSize);
        } else {
            LogUtil.warn("类 {} 声明有 @MapperScan 注解但未找到注解实例", psiClass.getQualifiedName());
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
        long startTime = System.currentTimeMillis();
        LogUtil.debug("开始从XML配置中获取组件扫描包路径，源代码根目录: {}", sourceRoot.getPath());
        
        Set<String> scanPackages = new HashSet<>();
        try {
            collectAndParseXmlFiles(sourceRoot, scanPackages);
            LogUtil.debug("从XML配置中获取到 {} 个组件扫描包路径", scanPackages.size());
        } catch (Exception e) {
            LogUtil.error("从XML配置中获取组件扫描包路径时发生错误，源代码根目录: " + sourceRoot.getPath(), e);
            throw e;
        } finally {
            long cost = System.currentTimeMillis() - startTime;
            LogUtil.debug("从XML配置中获取组件扫描包路径完成，耗时: {} ms", cost);
        }
        
        return scanPackages;
    }

    // XML文件检查结果缓存，避免重复解析
    private final Map<String, Boolean> xmlFileCheckCache = new HashMap<>();
    
    /**
     * 检查XML文件是否是Spring配置文件（包含bean依赖相关配置）
     * 优化：使用快速预检查 + 缓存机制
     *
     * @param xmlFile 要检查的XML文件
     * @return 如果是Spring配置文件返回true，否则返回false
     */
    private boolean isSpringConfigurationFile(VirtualFile xmlFile) {
        String filePath = xmlFile.getPath();
        
        // 检查缓存
        Boolean cachedResult = xmlFileCheckCache.get(filePath);
        if (cachedResult != null) {
            return cachedResult;
        }
        
        // 1. 快速文件名预检查
        if (!isLikelySpringConfigFile(xmlFile)) {
            xmlFileCheckCache.put(filePath, false);
            return false;
        }
        
        // 2. 快速内容预检查（只读取前1KB）
        if (!quickContentCheck(xmlFile)) {
            xmlFileCheckCache.put(filePath, false);
            return false;
        }
        
        // 3. 完整XML解析（仅对通过预检查的文件）
        boolean result = isSpringConfigurationFileFull(xmlFile);
        xmlFileCheckCache.put(filePath, result);
        return result;
    }
    
    /**
     * 快速文件名预检查，排除明显不是Spring配置的文件
     */
    private boolean isLikelySpringConfigFile(VirtualFile xmlFile) {
        String fileName = xmlFile.getName().toLowerCase();
        
        // 排除明显不是Spring配置的文件
        if (fileName.contains("mapper") || fileName.contains("mybatis") ||
            fileName.contains("ibatis") || fileName.contains("sqlmap") ||
            fileName.endsWith("-mapper.xml") || fileName.endsWith("mapper.xml")) {
            return false;
        }
        
        // 包含常见Spring配置文件特征的
        return fileName.contains("application") || fileName.contains("spring") ||
               fileName.contains("config") || fileName.contains("context") ||
               fileName.contains("beans") || fileName.equals("beans.xml");
    }
    
    /**
     * 快速内容预检查，只读取文件前1KB进行判断
     */
    private boolean quickContentCheck(VirtualFile xmlFile) {
        try {
            byte[] content = xmlFile.contentsToByteArray();
            if (content.length == 0) {
                return false;
            }
            
            // 只读取前1KB内容
            int readLength = Math.min(1024, content.length);
            String header = new String(content, 0, readLength, "UTF-8").toLowerCase();
            
            // 快速检查是否包含Spring相关关键字
            return header.contains("spring") || header.contains("beans") ||
                   header.contains("context") || header.contains("component-scan") ||
                   header.contains("www.springframework.org") || header.contains("spring.io");
                   
        } catch (Exception e) {
            // 读取失败时保守处理，返回true让后续完整检查
            return true;
        }
    }
    
    /**
     * 完整的XML文件解析检查（仅对通过预检查的文件执行）
     */
    private boolean isSpringConfigurationFileFull(VirtualFile xmlFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // 优化：禁用一些不需要的功能以提高性能
            factory.setValidating(false);
            factory.setFeature("http://xml.org/sax/features/namespaces", false);
            factory.setFeature("http://xml.org/sax/features/validation", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            
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
        long startTime = System.currentTimeMillis();
        LogUtil.debug("开始收集并解析目录下的XML文件: {}", directory.getPath());
        
        try {
            int xmlFileCount = 0;
            int springConfigFileCount = 0;
            
            for (VirtualFile file : directory.getChildren()) {
                if (file.isDirectory()) {
                    collectAndParseXmlFiles(file, scanPackages);
                } else if ("xml".equalsIgnoreCase(file.getExtension())) {
                    xmlFileCount++;
                    LogUtil.debug("发现XML文件: {}", file.getPath());
                    
                    // 首先检查是否是Spring配置文件（通过内容判断）
                    if (isSpringConfigurationFile(file)) {
                        springConfigFileCount++;
                        LogUtil.debug("识别为Spring配置文件: {}", file.getPath());
                        parseXmlFileForComponentScan(file, scanPackages);
                    } else {
                        LogUtil.debug("跳过非Spring配置XML文件: {}", file.getPath());
                    }
                }
            }
            
            LogUtil.debug("目录 {} 下的XML文件处理完成，共发现 {} 个XML文件，其中 {} 个是Spring配置文件",
                         directory.getPath(), xmlFileCount, springConfigFileCount);
        } catch (Exception e) {
            LogUtil.error("收集并解析目录下的XML文件时发生错误，目录: " + directory.getPath(), e);
            throw e;
        } finally {
            long cost = System.currentTimeMillis() - startTime;
            LogUtil.debug("收集并解析目录下的XML文件完成，耗时: {} ms", cost);
        }
    }

    /**
     * 解析单个 XML 文件，查找 <context:component-scan> 并提取 base-package
     * (此方法保持不变)
     */
    private void parseXmlFileForComponentScan(VirtualFile xmlFile, Set<String> scanPackages) {
        long startTime = System.currentTimeMillis();
        LogUtil.debug("开始解析XML文件中的组件扫描配置: {}", xmlFile.getPath());
        
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

            LogUtil.debug("在XML文件中找到 {} 个 component-scan 节点", componentScanNodes.getLength());
            int packageCount = 0;

            for (int i = 0; i < componentScanNodes.getLength(); i++) {
                org.w3c.dom.Node node = componentScanNodes.item(i);
                if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    org.w3c.dom.Element element = (org.w3c.dom.Element) node;
                    String basePackageAttr = element.getAttribute("base-package");
                    if (basePackageAttr != null && !basePackageAttr.isEmpty()) {
                        String[] packages = basePackageAttr.split("[,;\\s]+");
                        LogUtil.debug("从 component-scan 节点解析到 base-package 属性值: {}", basePackageAttr);
                        
                        for (String pkg : packages) {
                            String trimmedPkg = pkg.trim();
                            if (!trimmedPkg.isEmpty()) {
                                scanPackages.add(trimmedPkg);
                                packageCount++;
                                LogUtil.debug("添加包路径: {}", trimmedPkg);
                            }
                        }
                    } else {
                        LogUtil.debug("component-scan 节点没有 base-package 属性或属性值为空");
                    }
                }
            }
            
            LogUtil.debug("XML文件解析完成，共添加 {} 个包路径", packageCount);
        } catch (Exception e) {
            LogUtil.error("解析XML文件中的组件扫描配置时发生错误，文件: " + xmlFile.getPath(), e);
            // 保留原有的错误输出
            System.err.println("Error parsing XML file for component scan: " + xmlFile.getPath() + " - " + e.getMessage());
            e.printStackTrace();
        } finally {
            long cost = System.currentTimeMillis() - startTime;
            LogUtil.debug("解析XML文件中的组件扫描配置完成，耗时: {} ms", cost);
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
