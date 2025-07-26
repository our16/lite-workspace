package org.example.liteworkspace.bean.core;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import org.example.liteworkspace.cache.CacheVersionChecker;
import org.example.liteworkspace.util.MyBatisXmlFinder;
import org.example.liteworkspace.util.ResourceConfigAnalyzer;

import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.*;

public class LiteProjectContext {

    private final Project project;
    private final List<Module> modules;
    private final boolean isMultiModule;

    private final BuildToolType buildToolType;

    private final ResourceConfigAnalyzer resourceAnalyzer;

    private final MyBatisXmlFinder mybatisContext;

    // Spring
    private final Set<String> springScanPackages = new HashSet<>();

    private DatasourceConfig datasourceConfig = new DatasourceConfig();

    // Configuration
    private final Set<PsiClass> configurationClasses = new HashSet<>();
    private final Set<String> configProvidedFqcns = new HashSet<>();

    private final CacheVersionChecker versionChecker = new CacheVersionChecker();

    public LiteProjectContext(Project project) {
        this.project = project;

        // ===================== 步骤 1 =====================
        // 获取所有模块，判断是否多模块
        this.modules = Arrays.asList(ModuleManager.getInstance(project).getModules());
        this.isMultiModule = this.modules.size() > 1;

        // ===================== 步骤 2 =====================
        // 分析构建工具类型：Maven or Gradle
        this.buildToolType = detectBuildToolType();

        // ===================== 步骤 3 =====================
        // 初始化分析器：用于扫描 Spring、MyBatis 相关配置
        this.resourceAnalyzer = new ResourceConfigAnalyzer(project, "");
        // mybatis 上下文
        this.mybatisContext =  new MyBatisXmlFinder(project);
        // 主动加载 namespace -> mapper路径 映射
        this.mybatisContext.loadMapperNamespaceMap();
        // ===================== 步骤 4 =====================
        // 执行核心元数据扫描，填充上下文信息
        // Spring 相关
        this.springScanPackages.addAll(this.resourceAnalyzer.scanComponentScanPackages());

        this.datasourceConfig = this.resourceAnalyzer.scanSpringDatasourceConfigs();
        // Configuration 类
        this.configurationClasses.addAll(this.resourceAnalyzer.scanConfigurationClasses());
    }

    /**
     * 步骤 2 衍生方法：检测当前项目是 Maven 还是 Gradle
     */
    private BuildToolType detectBuildToolType() {
        VirtualFile[] contentRoots = ProjectRootManager.getInstance(project).getContentRoots();
        if (contentRoots == null || contentRoots.length == 0) {
            return BuildToolType.UNKNOWN;
        }

        // 遍历所有内容根目录（一般项目可能有多个 module，但我们只判断是否存在构建文件）
        for (VirtualFile root : contentRoots) {
            if (root == null || !root.isValid()) {
                continue;
            }

            // 检测 Maven
            VirtualFile pomXml = root.findChild("pom.xml");
            if (pomXml != null && pomXml.exists()) {
                return BuildToolType.MAVEN;
            }

            // 检测 Gradle
            VirtualFile buildGradle = root.findChild("build.gradle");
            if (buildGradle != null && buildGradle.exists()) {
                return BuildToolType.GRADLE;
            }

            VirtualFile buildGradleKts = root.findChild("build.gradle.kts");
            if (buildGradleKts != null && buildGradleKts.exists()) {
                return BuildToolType.GRADLE;
            }
        }

        // 默认未知
        return BuildToolType.UNKNOWN;
    }

    // ========== Getters ==========

    public Project getProject() {
        return project;
    }

    public List<Module> getModules() {
        return modules;
    }

    public boolean isMultiModule() {
        return isMultiModule;
    }

    public BuildToolType getBuildToolType() {
        return buildToolType;
    }

    public Set<String> getSpringScanPackages() {
        return springScanPackages;
    }

    public ResourceConfigAnalyzer getResourceAnalyzer() {
        return resourceAnalyzer;
    }

    public MyBatisXmlFinder getMybatisContext() {
        return mybatisContext;
    }

    public Set<PsiClass> getAllConfigurationClasses() {
        return configurationClasses;
    }

    public void addConfigurationClass(PsiClass clazz) {
        this.configurationClasses.add(clazz);
    }

    public void registerConfigProvidedClass(String fqcn) {
        configProvidedFqcns.add(fqcn);
    }

    public boolean isProvidedByConfiguration(PsiClass clazz) {
        String qname = clazz.getQualifiedName();
        return qname != null && configProvidedFqcns.contains(qname);
    }

    public CacheVersionChecker getVersionChecker() {
        return versionChecker;
    }

    public DatasourceConfig getDatasourceConfig() {
        return datasourceConfig;
    }
}