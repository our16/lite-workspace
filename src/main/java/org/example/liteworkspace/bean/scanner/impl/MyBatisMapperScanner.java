package org.example.liteworkspace.bean.scanner.impl;

import com.intellij.psi.*;
import org.example.liteworkspace.bean.core.LiteProjectContext;
import org.example.liteworkspace.bean.scanner.BeanScanner;

import java.util.HashSet;
import java.util.Set;

public class MyBatisMapperScanner implements BeanScanner {

    private final LiteProjectContext context;

    public MyBatisMapperScanner(LiteProjectContext context) {
        this.context = context;
    }

    @Override
    public Set<PsiClass> collectDependencies(PsiClass clazz) {
        Set<PsiClass> result = new HashSet<>();
        for (PsiField field : clazz.getAllFields()) {
            PsiType type = field.getType();
            if (!(type instanceof PsiClassType ct)) continue;

            PsiClass dep = ct.resolve();
            if (dep != null && isMapper(dep)) {
                result.add(dep);
            }
        }
        return result;
    }

    /**
     * 判断该类是否为 MyBatis Mapper
     */
    private boolean isMapper(PsiClass clazz) {
        if (!clazz.isInterface()) return false;
        String name = clazz.getName();
        return clazz.hasAnnotation("org.apache.ibatis.annotations.Mapper") ||
                (name != null && name.endsWith("Mapper"));
    }
}
