package org.example.liteworkspace.bean.core.context;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.example.liteworkspace.bean.core.BuildToolDetector;
import org.example.liteworkspace.bean.core.enums.BuildToolType;
import org.example.liteworkspace.cache.CacheVersionChecker;
import org.example.liteworkspace.datasource.DataSourceConfigLoader;
import org.example.liteworkspace.datasource.SqlSessionConfig;

import java.util.List;
import java.util.Set;

public class LiteProjectContext {
    private final PsiClass targetClass;
    private final PsiMethod targetMethod;
    private final ProjectContext projectContext;
    private final BuildToolType buildToolType;
    private final SpringContext springContext;
    private final MyBatisContext myBatisContext;
    private final CacheVersionChecker versionChecker = new CacheVersionChecker();
    private final List<SqlSessionConfig> sqlSessionConfigList;

    public LiteProjectContext(Project project, PsiClass targetClass, PsiMethod targetMethod, Set<String> miniPackages) {
        this.targetClass = targetClass;
        this.targetMethod = targetMethod;
        this.projectContext = new ProjectContext(project);
        this.buildToolType = BuildToolDetector.detect(project);
        this.springContext = new SpringContext(project);
        this.springContext.scan(miniPackages);
        this.sqlSessionConfigList = DataSourceConfigLoader.load(project);
        this.myBatisContext = new MyBatisContext(project, sqlSessionConfigList);
        this.myBatisContext.scan();
    }

    public ProjectContext getProjectContext() { return projectContext; }
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