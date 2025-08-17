package org.example.liteworkspace.bean.core;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.example.liteworkspace.bean.core.enums.BuildToolType;

public class BuildToolDetector {
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
}
