package org.example.liteworkspace.util;

import com.intellij.psi.PsiClass;

import java.util.*;

public class XmlBeanAssembler {

    private final List<BeanDefinitionBuilder> builders;
    private final Map<String, String> beanMap;
    private final Set<String> visited;

    public XmlBeanAssembler() {
        this.builders = Collections.unmodifiableList(Arrays.asList(
                new SpringBeanBuilder(),
                new MyBatisMapperBuilder()
        ));
        this.beanMap = new LinkedHashMap<>();
        this.visited = new HashSet<>();
    }

    public Map<String, String> buildAll(PsiClass root) {
        buildBeanIfNecessary(root);
        return beanMap;
    }

    public void buildBeanIfNecessary(PsiClass clazz) {
        String qName = clazz.getQualifiedName();
        if (qName == null || visited.contains(qName)) {
            return;
        }
        for (BeanDefinitionBuilder builder : builders) {
            if (builder.supports(clazz)) {
                builder.buildBeanXml(clazz, visited, beanMap, this);
                return;
            }
        }
    }

    public void putBeanXml(String id, String xml) {
        beanMap.putIfAbsent(id, xml);
    }

    private String decapitalize(String name) {
        return name == null || name.isEmpty() ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
