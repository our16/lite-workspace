package org.example.liteworkspace.util;

import com.intellij.psi.PsiClass;

import java.util.*;

public class XmlBeanAssembler {

    private final List<BeanDefinitionBuilder> builders;
    private final Map<String, String> beanMap = new LinkedHashMap<>();

    public XmlBeanAssembler() {
        this.builders = Arrays.asList(
                new SpringBeanBuilder(),
                new MyBatisMapperBuilder()
        );
    }

    public Map<String, String> buildAll(PsiClass root) {
        buildBeanIfNecessary(root, new HashSet<>());
        return beanMap;
    }

    public void buildBeanIfNecessary(PsiClass clazz, Set<String> visited) {
        String qName = clazz.getQualifiedName();
        if (qName == null || visited.contains(qName)) return;

        visited.add(qName);
        for (BeanDefinitionBuilder builder : builders) {
            if (builder.supports(clazz)) {
                String xml = builder.buildBeanXml(clazz, visited, this);
                beanMap.put(decapitalize(clazz.getName()), xml);
                return;
            }
        }
    }

    private String decapitalize(String name) {
        return name == null || name.isEmpty() ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}