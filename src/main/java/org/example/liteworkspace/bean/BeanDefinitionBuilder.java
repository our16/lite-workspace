package org.example.liteworkspace.bean;

import com.intellij.psi.PsiClass;
import org.example.liteworkspace.bean.core.BeanRegistry;

public interface BeanDefinitionBuilder {
    void buildBean(PsiClass clazz, BeanRegistry registry);
}