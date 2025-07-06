package org.example.liteworkspace.bean;

import com.intellij.psi.PsiClass;

public interface BeanDefinitionResolver {
    boolean isBean(PsiClass clazz);
    boolean isProvidedByBeanMethod(PsiClass clazz);
    boolean isXmlDefined(PsiClass clazz);
}