package org.example.liteworkspace.util;

import com.intellij.psi.PsiClass;

import java.util.Map;
import java.util.Set;

public interface BeanDefinitionBuilder {
    boolean supports(PsiClass clazz);
    void buildBeanXml(PsiClass clazz, Set<String> visited, Map<String, String> beanMap, XmlBeanAssembler assembler);
}
