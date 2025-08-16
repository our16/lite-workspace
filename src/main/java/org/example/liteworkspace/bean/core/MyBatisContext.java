package org.example.liteworkspace.bean.core;

import com.intellij.openapi.project.Project;
import org.example.liteworkspace.util.MyBatisXmlFinder;

import java.util.Set;

public class MyBatisContext {
    private final MyBatisXmlFinder myBatisXmlFinder;

    public MyBatisContext(Project project) {
        this.myBatisXmlFinder = new MyBatisXmlFinder(project);
    }

    public void scan(Set<String> miniPackages) {
        myBatisXmlFinder.loadMapperNamespaceMap(miniPackages);
    }

    public MyBatisXmlFinder getContext() {
        return myBatisXmlFinder;
    }
}

