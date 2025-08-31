package org.example.liteworkspace.bean.core.context;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.example.liteworkspace.bean.core.enums.BuildToolType;
import org.example.liteworkspace.cache.CacheVersionChecker;
import org.example.liteworkspace.datasource.DataSourceConfigLoader;
import org.example.liteworkspace.datasource.SqlSessionConfig;

import java.util.Arrays;
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
}