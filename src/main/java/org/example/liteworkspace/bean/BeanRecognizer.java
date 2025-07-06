package org.example.liteworkspace.bean;

import com.intellij.psi.PsiClass;
import org.example.liteworkspace.bean.core.BeanOrigin;

public interface BeanRecognizer {
    /**
     * 如果返回 true，表示该类是一个 Spring Bean（注解、XML、Bean 方法、MyBatis 等）
     */
    boolean isBean(PsiClass clazz);

    /**
     * 获取定义来源：注解、XML、Bean 方法、MyBatis 等
     * 如果不是 Bean，则返回 null
     */
    BeanOrigin getOrigin(PsiClass clazz);

    /**
     * 获取提供该类定义的额外提供者类，例如 @Bean 方法所在的配置类（可选）
     */
    default PsiClass getProviderClass(PsiClass clazz) {
        return null;
    }
}
