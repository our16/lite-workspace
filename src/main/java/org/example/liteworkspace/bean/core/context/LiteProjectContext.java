package org.example.liteworkspace.bean.core.context;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.example.liteworkspace.bean.core.DatasourceConfig;
import org.example.liteworkspace.bean.core.enums.BuildToolType;
import org.example.liteworkspace.cache.CacheVersionChecker;
import org.example.liteworkspace.datasource.DataSourceConfigLoader;
import org.example.liteworkspace.datasource.SqlSessionConfig;
import org.example.liteworkspace.util.LogUtil;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class LiteProjectContext {
    private final Project project;
    private final List<Module> modules;
    private final boolean multiModule;
    private final BuildToolType buildToolType;

    /**
     * 目标类
     */
    private final PsiClass targetClass;

    /**
     * 目标方法
     */
    private final PsiMethod targetMethod;

    /**
     * 数据源配置
     */
    private final DatasourceConfig datasourceConfig;

    /**
     * spring 上下文
     */
    private final SpringContext springContext;

    /**
     * mybatis 上下文
     */
    private final MyBatisContext myBatisContext;
    private final CacheVersionChecker versionChecker = new CacheVersionChecker();
    private final List<SqlSessionConfig> sqlSessionConfigList;

    public LiteProjectContext(Project project, PsiClass targetClass, PsiMethod targetMethod, Set<String> miniPackages) {
        this.targetClass = targetClass;
        this.targetMethod = targetMethod;
        this.project = project;
        this.modules = Arrays.asList(ModuleManager.getInstance(project).getModules());
        this.multiModule = modules.size() > 1;
        this.buildToolType = detect(project);
        // 数据源配置
        this.datasourceConfig = refreshDatasourceConfig();
        LogUtil.info("datasourceConfig：{}", datasourceConfig);
        // spring 上下下文初始化
        this.springContext = new SpringContext(project);
        this.springContext.refresh(miniPackages);
        // 数据源配置初始化
        this.sqlSessionConfigList = DataSourceConfigLoader.load(project);

        // mybatis 上下文初始化
        this.myBatisContext = new MyBatisContext(project, sqlSessionConfigList);
        this.myBatisContext.refresh();
    }

    public static BuildToolType detect(Project project) {
        VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentRoots();
        if (roots == null || roots.length == 0) {
            return BuildToolType.UNKNOWN;
        }

        for (VirtualFile root : roots) {
            if (root.findChild("pom.xml") != null) return BuildToolType.MAVEN;
            if (root.findChild("build.gradle") != null) return BuildToolType.GRADLE;
            if (root.findChild("build.gradle.kts") != null) return BuildToolType.GRADLE;
        }
        return BuildToolType.UNKNOWN;
    }

    /**
     * 只读取 test/resource/configs/testDatasource.xml 的数据库配置，
     * 如果文件不存在，则返回默认配置
     *
     * @return Map<String, String> 数据源配置
     */
    public DatasourceConfig refreshDatasourceConfig() {
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
        Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName("datasource.xml", scope);

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

    public Project getProject() {
        return project;
    }

    public List<Module> getModules() {
        return modules;
    }

    public boolean isMultiModule() {
        return multiModule;
    }

    public BuildToolType getBuildToolType() { return buildToolType; }
    public SpringContext getSpringContext() { return springContext; }
    public MyBatisContext getMyBatisContext() { return myBatisContext; }
    public CacheVersionChecker getVersionChecker() { return versionChecker; }

    public PsiClass getTargetClass() {
        return targetClass;
    }

    public PsiMethod getTargetMethod() {
        return targetMethod;
    }

    public List<SqlSessionConfig> getSqlSessionConfigList() {
        return sqlSessionConfigList;
    }

    public DatasourceConfig getDatasourceConfig() {
        return datasourceConfig;
    }
}