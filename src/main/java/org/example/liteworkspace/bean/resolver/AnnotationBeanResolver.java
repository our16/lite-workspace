package org.example.liteworkspace.bean.resolver;

import com.intellij.psi.*;
import org.example.liteworkspace.bean.BeanDefinitionResolver;

/**
 * 判断一个类是否通过 Spring 注解定义为 Bean，例如：
 * - @Component
 * - @Service
 * - @Repository
 * - @Controller / @RestController
 * - @Configuration
 */
public class AnnotationBeanResolver implements BeanDefinitionResolver {

    @Override
    public boolean isBean(PsiClass clazz) {
        PsiModifierList modifierList = clazz.getModifierList();
        if (modifierList == null) return false;

        for (PsiAnnotation annotation : modifierList.getAnnotations()) {
            String qName = annotation.getQualifiedName();
            if (qName == null) continue;

            if (qName.startsWith("org.springframework.stereotype.") || // @Component, @Service, @Repository
                    qName.equals("org.springframework.context.annotation.Configuration") ||
                    qName.equals("org.springframework.web.bind.annotation.RestController") ||
                    qName.equals("org.springframework.stereotype.Controller")) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isProvidedByBeanMethod(PsiClass clazz) {
        return false; // 不负责判断 @Bean 方法
    }

    @Override
    public boolean isXmlDefined(PsiClass clazz) {
        return false; // 不负责 XML 定义判断
    }
}
