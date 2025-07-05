package org.example.liteworkspace.util;

import com.intellij.psi.PsiClass;

import java.util.Set;

public interface BeanDefinitionBuilder {
    boolean supports(PsiClass clazz);
    String buildBeanXml(PsiClass clazz, Set<String> visited, XmlBeanAssembler assembler);
}
