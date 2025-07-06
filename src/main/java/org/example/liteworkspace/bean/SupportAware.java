package org.example.liteworkspace.bean;

/**
 * 可选接口：用于标识某些 BeanDefinitionBuilder 仅支持特定类型的类
 */
public interface SupportAware {
    boolean supports(com.intellij.psi.PsiClass clazz);
}

