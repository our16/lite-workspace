package org.example.liteworkspace.bean.core.context;

import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.module.Module;

import java.util.Arrays;
import java.util.List;

public class ProjectContext {
    private final Project project;
    private final List<Module> modules;
    private final boolean multiModule;

    public ProjectContext(Project project) {
        this.project = project;
        this.modules = Arrays.asList(ModuleManager.getInstance(project).getModules());
        this.multiModule = modules.size() > 1;
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
}

