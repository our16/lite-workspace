package org.example.liteworkspace.bean.core;

import com.intellij.psi.PsiElement;

public class BeanDefinition {
    private final String beanName;
    private final String className;
    private final BeanType type;
    private final PsiElement source;

    public BeanDefinition(String beanName, String className, BeanType type, PsiElement source) {
        this.beanName = beanName;
        this.className = className;
        this.type = type;
        this.source = source;
    }

    public String getBeanName() {
        return beanName;
    }

    public String getClassName() {
        return className;
    }

    public BeanType getType() {
        return type;
    }

    public PsiElement getSource() {
        return source;
    }

    @Override
    public String toString() {
        return "BeanDefinition{" +
                "beanName='" + beanName + '\'' +
                ", className='" + className + '\'' +
                ", type=" + type +
                '}';
    }
}
