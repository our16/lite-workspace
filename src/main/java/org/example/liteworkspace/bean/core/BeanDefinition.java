package org.example.liteworkspace.bean.core;

import com.intellij.psi.PsiClass;
import org.example.liteworkspace.bean.core.enums.BeanType;
import org.example.liteworkspace.dto.ClassSignatureDTO;
import org.example.liteworkspace.dto.PsiToDtoConverter;

public class BeanDefinition {
    private final String beanName;
    private final String className;
    private final BeanType type;
    private final ClassSignatureDTO sourceDto; // 轻量级DTO替代PSI对象

    public BeanDefinition(String beanName, String className, BeanType type, PsiClass source) {
        this.beanName = beanName;
        this.className = className;
        this.type = type;
        this.sourceDto = PsiToDtoConverter.convertToClassSignature(source);
    }

    public BeanDefinition(String beanName, String className, BeanType type, ClassSignatureDTO sourceDto) {
        this.beanName = beanName;
        this.className = className;
        this.type = type;
        this.sourceDto = sourceDto;
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

    public ClassSignatureDTO getSourceDto() {
        return sourceDto;
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
