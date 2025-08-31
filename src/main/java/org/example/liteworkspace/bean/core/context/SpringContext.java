package org.example.liteworkspace.bean.core.context;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.example.liteworkspace.bean.core.DatasourceConfig;
import org.example.liteworkspace.util.LogUtil;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.*;

public class SpringContext {
    private final Set<String> componentScanPackages = new HashSet<>();
    private DatasourceConfig datasourceConfig = new DatasourceConfig();
    private final Set<PsiClass> configurationClasses = new HashSet<>();
    private final Map<String, PsiClass> bean2configuration = new HashMap<>();
    private final Project project;

    public SpringContext(Project project) {
        this.project = project;
    }

    public void refresh(Set<String> miniPackages) {
        LogUtil.info("miniPackages：{}", miniPackages);
        // 收集配置的bean扫描目录
        componentScanPackages.addAll(scanComponentScanPackages());
        LogUtil.info("componentScanPackages：{}", componentScanPackages);
        // 收集数据源配置
        datasourceConfig = scanSpringDatasourceConfigs();
        LogUtil.info("datasourceConfig：{}", datasourceConfig);
        Map<String, PsiClass> configs = getConfigurationClasses(miniPackages);
        bean2configuration.putAll(configs);
        configurationClasses.addAll(configs.values());
        LogUtil.info("configs：{}", configs);
    }

    public Set<String> scanComponentScanPackages() {
        Set<String> result = new HashSet<>();
        for (XmlFile xml : findRelevantXmlFiles()) {
            scanXmlForComponentScan(xml, result, new HashSet<>());
        }
        return result;
    }

    public Map<String, PsiClass> getConfigurationClasses(Set<String> packagePrefixes) {
        Map<String, PsiClass> beanToConfiguration = new HashMap<>();
        Project project = this.project;

        Collection<PsiClass> classesToScan = new ArrayList<>();
        // 默认：全量搜索
        GlobalSearchScope baseScope = GlobalSearchScope.allScope(project);

        if (packagePrefixes == null || packagePrefixes.isEmpty()) {
            classesToScan.addAll(AllClassesSearch.search(baseScope, project).findAll());
        } else {
            for (String pkgOrJar : packagePrefixes) {
                // 1️⃣ 先尝试当作包名前缀
                PsiPackage psiPackage = JavaPsiFacade.getInstance(project).findPackage(pkgOrJar);
                if (psiPackage != null) {
                    for (PsiDirectory dir : psiPackage.getDirectories(baseScope)) {
                        classesToScan.addAll(List.of(JavaDirectoryService.getInstance().getClasses(dir)));
                        addSubPackageClasses(dir, classesToScan);
                    }
                    continue;
                }

                // 2️⃣ 如果不是包，再尝试匹配 JAR
                VirtualFile jarFile = findJarByName(project, pkgOrJar);
                if (jarFile != null) {
                    GlobalSearchScope jarScope = GlobalSearchScope.filesScope(project, Set.of(jarFile));
                    classesToScan.addAll(AllClassesSearch.search(jarScope, project).findAll());
                }
            }
        }

        // 遍历类，找到 @Configuration + @Bean 方法
        for (PsiClass clazz : classesToScan) {
            if (!hasAnnotation(clazz, "org.springframework.context.annotation.Configuration")) {
                continue;
            }

            for (PsiMethod method : clazz.getMethods()) {
                if (!hasAnnotation(method, "org.springframework.context.annotation.Bean")) {
                    continue;
                }

                PsiType returnType = method.getReturnType();
                if (returnType == null) continue;

                String beanName = getBeanName(method);
                String returnTypeName = returnType.getCanonicalText();

                beanToConfiguration.put(method.getName(), clazz);
                beanToConfiguration.put(returnTypeName, clazz);
                if (!beanName.equals(method.getName())) {
                    beanToConfiguration.put(beanName, clazz);
                }
            }
        }

        return beanToConfiguration;
    }

    /**
     * 查找项目依赖中是否存在给定名字的 JAR
     */
    /**
     * 在项目依赖库中查找指定名字的 JAR
     */
    private VirtualFile findJarByName(Project project, String jarName) {
        com.intellij.openapi.module.Module[] modules = ModuleManager.getInstance(project).getModules();
        for (com.intellij.openapi.module.Module module : modules) {
            OrderEnumerator enumerator = OrderEnumerator.orderEntries(module).withoutSdk().librariesOnly();
            for (VirtualFile root : enumerator.getClassesRoots()) {
                // root 可能是 jar://...!/ 这样的路径
                String name = root.getName();
                if (name.equals(jarName) || name.startsWith(jarName)) {
                    return root;
                }
            }
        }
        return null;
    }




    // 递归扫描子包
    private void addSubPackageClasses(PsiDirectory dir, Collection<PsiClass> classes) {
        for (PsiDirectory subDir : dir.getSubdirectories()) {
            classes.addAll(List.of(JavaDirectoryService.getInstance().getClasses(subDir)));
            addSubPackageClasses(subDir, classes);
        }
    }


    /**
     * 判断类或方法是否有指定注解（支持 jar 中的类）
     */
    private boolean hasAnnotation(PsiModifierListOwner owner, String annotationFqn) {
        PsiModifierList list = owner.getModifierList();
        if (list == null) {
            return false;
        }
        return list.findAnnotation(annotationFqn) != null;
    }


    /**
     * 从@Bean注解中提取Bean名称
     */
    private String getBeanName(PsiMethod method) {
        PsiAnnotation beanAnnotation = method.getAnnotation("org.springframework.context.annotation.Bean");
        if (beanAnnotation == null) {
            return method.getName();
        }

        // 查找@Bean的value或name属性
        PsiAnnotationMemberValue valueAttr = beanAnnotation.findAttributeValue("value");
        if (valueAttr == null) {
            valueAttr = beanAnnotation.findAttributeValue("name");
        }

        if (valueAttr instanceof PsiLiteralExpression literal) {
            String value = literal.getValue() instanceof String ? (String) literal.getValue() : null;
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }

        // 默认返回方法名
        return method.getName();
    }

    /**
     * 只读取 test/resource/configs/testDatasource.xml 的数据库配置，
     * 如果文件不存在，则返回默认配置
     *
     * @return Map<String, String> 数据源配置
     */
    public DatasourceConfig scanSpringDatasourceConfigs() {
        // 1. 优先检查是否有指定的测试数据源文件
        String configFile = findTestDatasourceXml(project);
        if (configFile != null ) {
            // 如果找到指定文件，返回一个特殊标识表示使用导入文件
            return DatasourceConfig.createImportedConfig(configFile);
        }
        // 2. 如果没有找到文件，返回默认配置
        return DatasourceConfig.createDefaultConfig(
                "jdbc:mysql://localhost:3306/default_db",
                "root",
                "123456",
                "com.mysql.cj.jdbc.Driver"
        );
    }


    /**
     * 查找多模块项目下的 test/resources/configs/datasource.xml 文件
     */
    private String findTestDatasourceXml(Project project) {
        String relativePath = "configs/datasource.xml";

        // 1. 遍历所有模块 Source Roots
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
            for (VirtualFile root : sourceRoots) {
                // 只关心 test/resources 目录
                if (root.getPath().contains("test")) {
                    VirtualFile file = root.findFileByRelativePath(relativePath);
                    if (file != null && file.exists() && file.isValid()) {
                        return relativePath;
                    }
                }
            }
        }

        // 2. 全局索引搜索兜底
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(
                project, "datasource.xml", scope
        );

        for (VirtualFile file : files) {
            if (file.getPath().contains("configs")) {
                return relativePath;
            }
        }

        // 3. 类加载器兜底（运行时资源）
        try {
            URL resourceUrl = getClass().getClassLoader()
                    .getResource("configs/datasource.xml");
            if (resourceUrl != null) {
                return relativePath;
            }
        } catch (Exception ignored) {}

        return null;
    }

    private List<XmlFile> findRelevantXmlFiles() {
        List<XmlFile> xmlFiles = new ArrayList<>();
        for (VirtualFile vf : FilenameIndex.getAllFilesByExt(project, "xml", GlobalSearchScope.projectScope(project))) {
            if (!vf.getPath().contains("/resources/")) continue;

            PsiFile psi = PsiManager.getInstance(project).findFile(vf);
            if (psi instanceof XmlFile xml) {
                XmlTag root = xml.getRootTag();
                if (root != null && ("beans".equals(root.getName()) || "context:component-scan".equals(root.getName()))) {
                    xmlFiles.add(xml);
                }
            }
        }
        return xmlFiles;
    }

    private void scanXmlForComponentScan(XmlFile xml, Set<String> scanPackages, Set<String> visitedPaths) {
        String path = xml.getVirtualFile().getPath();
        if (!visitedPaths.add(path)) return;

        XmlTag root = xml.getRootTag();
        if (root == null) return;

        for (XmlTag tag : root.findSubTags("context:component-scan")) {
            String basePackage = tag.getAttributeValue("base-package");
            if (basePackage != null) scanPackages.add(basePackage);
        }

        for (XmlTag tag : root.findSubTags("import")) {
            String resource = tag.getAttributeValue("resource");
            if (resource != null) {
                VirtualFile imported = resolveResourcePath(resource, xml.getVirtualFile());
                if (imported != null) {
                    PsiFile psi = PsiManager.getInstance(project).findFile(imported);
                    if (psi instanceof XmlFile importedXml) {
                        scanXmlForComponentScan(importedXml, scanPackages, visitedPaths);
                    }
                }
            }
        }
    }

    @Nullable
    private VirtualFile resolveResourcePath(String path, VirtualFile base) {
        VirtualFile baseDir = base.getParent();
        if (path.startsWith("classpath:")) path = path.replace("classpath:", "");
        VirtualFile resolved = baseDir.findFileByRelativePath(path);
        if (resolved == null) {
            resolved = base.getFileSystem().findFileByPath(project.getBasePath() + "/src/main/resources/" + path);
        }
        return resolved;
    }


    public Set<String> getComponentScanPackages() {
        return componentScanPackages;
    }

    public DatasourceConfig getDatasourceConfig() {
        return datasourceConfig;
    }

    public void setDatasourceConfig(DatasourceConfig datasourceConfig) {
        this.datasourceConfig = datasourceConfig;
    }

    public Set<PsiClass> getConfigurationClasses() {
        return configurationClasses;
    }

    public Map<String, PsiClass> getBean2configuration() {
        return bean2configuration;
    }
}

