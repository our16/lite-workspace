package org.example.liteworkspace.bean.core;

import com.intellij.openapi.project.Project;
import org.example.liteworkspace.util.MyBatisXmlFinder;

public class MyBatisContext {
    private final MyBatisXmlFinder myBatisXmlFinder;

    public MyBatisContext(Project project) {
        this.myBatisXmlFinder = new MyBatisXmlFinder(project);
    }

    public void scan() {
        myBatisXmlFinder.loadMapperNamespaceMap();
    }

    public MyBatisXmlFinder getContext() {
        return myBatisXmlFinder;
    }
}

