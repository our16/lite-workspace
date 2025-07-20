package org.example.liteworkspace.bean.core;

import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import org.example.liteworkspace.util.MyBatisXmlFinder;
import org.example.liteworkspace.util.ResourceConfigAnalyzer;

import java.util.*;

public class LiteProjectContext {

    private final Project project;
    private final List<Module> modules;
    private final boolean isMultiModule;
    private final ResourceConfigAnalyzer resourceAnalyzer;
    private final MyBatisXmlFinder xmlFinder;

    private final Set<PsiClass> configurationClasses = new HashSet<>();

    private final Set<String> springScanPackages = new HashSet<>();
    private final Set<String> mybatisMapperLocations = new HashSet<>();

    private final Set<String> configProvidedClassFqns = new HashSet<>(); // 存储由 JavaConfig 提供的 FQCN

    public LiteProjectContext(Project project) {
        this.project = project;
        this.modules = Arrays.asList(ModuleManager.getInstance(project).getModules());
        this.isMultiModule = modules.size() > 1;
        this.resourceAnalyzer = new ResourceConfigAnalyzer(project);
        this.xmlFinder = MyBatisXmlFinder.build(project);
        initMetadata();
    }

    private void initMetadata() {
        Map<String, Object> springMeta = resourceAnalyzer.analyzeSpringConfig();
        springScanPackages.addAll((Collection<String>) springMeta.getOrDefault("componentScan", List.of()));
        mybatisMapperLocations.addAll((Collection<String>) springMeta.getOrDefault("mapperLocations", List.of()));
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

    public MyBatisXmlFinder getXmlFinder() {
        return xmlFinder;
    }

    public ResourceConfigAnalyzer getResourceAnalyzer() {
        return resourceAnalyzer;
    }

    public void addConfigurationClass(PsiClass clazz) {
        this.configurationClasses.add(clazz);
    }

    public Collection<PsiClass> getAllConfigurationClasses() {
        return configurationClasses;
    }

    /**
     * 注册一个由 @Configuration @Bean 方法提供的 class 名称（FQCN）
     */
    public void registerConfigProvidedClass(String fqcn) {
        configProvidedClassFqns.add(fqcn);
    }

    /**
     * 判断某个类是否是由 Configuration 提供的 Bean
     */
    public boolean isProvidedByConfiguration(PsiClass clazz) {
        String qname = clazz.getQualifiedName();
        return qname != null && configProvidedClassFqns.contains(qname);
    }
}

