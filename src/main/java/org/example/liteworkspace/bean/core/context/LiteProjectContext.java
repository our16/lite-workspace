package org.example.liteworkspace.bean.core.context;

import com.intellij.openapi.project.Project;
import org.example.liteworkspace.bean.core.BuildToolDetector;
import org.example.liteworkspace.bean.core.enums.BuildToolType;
import org.example.liteworkspace.cache.CacheVersionChecker;

import java.util.Set;

public class LiteProjectContext {
    private final ProjectContext projectContext;
    private final BuildToolType buildToolType;
    private final SpringContext springContext;
    private final MyBatisContext myBatisContext;
    private final CacheVersionChecker versionChecker = new CacheVersionChecker();

    public LiteProjectContext(Project project, Set<String> miniPackages) {
        this.projectContext = new ProjectContext(project);
        this.buildToolType = BuildToolDetector.detect(project);
        this.springContext = new SpringContext(project);
        this.springContext.scan(miniPackages);
        this.myBatisContext = new MyBatisContext(project);
        this.myBatisContext.scan();
    }

    public ProjectContext getProjectContext() { return projectContext; }
    public BuildToolType getBuildToolType() { return buildToolType; }
    public SpringContext getSpringContext() { return springContext; }
    public MyBatisContext getMyBatisContext() { return myBatisContext; }
    public CacheVersionChecker getVersionChecker() { return versionChecker; }
}