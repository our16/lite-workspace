package org.example.liteworkspace.bean.core;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import org.example.liteworkspace.cache.CacheVersionChecker;
import org.example.liteworkspace.util.MyBatisXmlFinder;
import org.example.liteworkspace.util.ResourceConfigAnalyzer;

import java.util.*;

public class LiteProjectContext {

    private final Project project;
    private final List<Module> modules;
    private final boolean isMultiModule;

    private final ResourceConfigAnalyzer resourceAnalyzer;
    private final MyBatisXmlFinder xmlFinder;

    // 从 XML 中提取的 Spring 扫描包路径
    private final Set<String> springScanPackages = new HashSet<>();

    // 从 XML 中提取的 MyBatis mapper 路径
    private final Set<String> mybatisMapperLocations = new HashSet<>();

    // mapper namespace -> resource 相对路径
    private final Map<String, String> mybatisNamespaceMap = new HashMap<>();

    // 所有的 @Configuration 类
    private final Set<PsiClass> configurationClasses = new HashSet<>();

    // 被 @Bean 提供的类（FQCN）
    private final Set<String> configProvidedFqcns = new HashSet<>();

    private final CacheVersionChecker versionChecker = new CacheVersionChecker();

    public CacheVersionChecker getVersionChecker() {
        return versionChecker;
    }

    public LiteProjectContext(Project project) {
        this.project = project;
        this.modules = Arrays.asList(ModuleManager.getInstance(project).getModules());
        this.isMultiModule = modules.size() > 1;

        this.resourceAnalyzer = new ResourceConfigAnalyzer(project, "");
        this.xmlFinder = MyBatisXmlFinder.build(project);
        initMetadata();
    }

    /**
     * 初始化配置信息，包括 spring 扫描包、mybatis 映射文件路径和 namespace 映射
     */
    private void initMetadata() {
        springScanPackages.addAll(resourceAnalyzer.scanComponentScanPackages());
        configurationClasses.addAll(resourceAnalyzer.scanConfigurationClasses());
        mybatisNamespaceMap.putAll(resourceAnalyzer.scanMyBatisNamespaceMap());
    }

    public Project getProject() {
        return project;
    }

    public List<Module> getModules() {
        return modules;
    }

    public boolean isMultiModule() {
        return isMultiModule;
    }

    public Set<String> getSpringScanPackages() {
        return springScanPackages;
    }

    public Set<String> getMybatisMapperLocations() {
        return mybatisMapperLocations;
    }

    public Map<String, String> getMybatisNamespaceMap() {
        return mybatisNamespaceMap;
    }

    public ResourceConfigAnalyzer getResourceAnalyzer() {
        return resourceAnalyzer;
    }

    public MyBatisXmlFinder getXmlFinder() {
        return xmlFinder;
    }

    public void addConfigurationClass(PsiClass clazz) {
        this.configurationClasses.add(clazz);
    }

    public Collection<PsiClass> getAllConfigurationClasses() {
        return configurationClasses;
    }

    public void registerConfigProvidedClass(String fqcn) {
        configProvidedFqcns.add(fqcn);
    }

    public boolean isProvidedByConfiguration(PsiClass clazz) {
        String qname = clazz.getQualifiedName();
        return qname != null && configProvidedFqcns.contains(qname);
    }
}
