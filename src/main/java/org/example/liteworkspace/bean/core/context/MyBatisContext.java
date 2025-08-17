package org.example.liteworkspace.bean.core.context;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import org.example.liteworkspace.util.MyBatisXmlFinder;

import java.util.*;

public class MyBatisContext {

    private final MyBatisXmlFinder myBatisXmlFinder;

    private final Map<String, String> namespaceMap = new HashMap();

    public MyBatisContext(Project project) {
        this.myBatisXmlFinder = new MyBatisXmlFinder(project);
    }

    public void scan() {
        Map<String, String> result = myBatisXmlFinder.scanAllMapperXml();
        namespaceMap.putAll(result);
    }

    public Map<String, String> getNamespaceMap() {
        return namespaceMap;
    }

    public boolean hasMatchingMapperXml(PsiClass clazz) {
        return namespaceMap.containsKey(clazz.getQualifiedName());
    }
}

