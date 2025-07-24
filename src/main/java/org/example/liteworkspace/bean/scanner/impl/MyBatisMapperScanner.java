package org.example.liteworkspace.bean.scanner.impl;

import com.intellij.psi.*;
import org.example.liteworkspace.bean.core.BeanType;
import org.example.liteworkspace.bean.core.LiteProjectContext;
import org.example.liteworkspace.bean.scanner.BeanScanner;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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
            if (!(type instanceof PsiClassType ct)) {
                continue;
            }

            PsiClass dep = ct.resolve();
            if (dep != null && isMapper(dep)) {
                result.add(dep);
            }
        }
        return result;
    }

    @Override
    public List<BeanType> supportedType() {
        return Arrays.asList(BeanType.MAPPER, BeanType.MYBATIS);
    }

    /**
     * 判断该类是否为 MyBatis Mapper
     */
    private boolean isMapper(PsiClass clazz) {
        if (!clazz.isInterface()) {
            return false;
        }
        // 4. Mapper 接口（XML 形式）,优先xml，支持混合模式
        if (clazz.isInterface() && this.context.getMybatisContext().hasMatchingMapperXml(clazz)) {
            return true;
        }

        // 3. MyBatis @Mapper 判定
        if (clazz.hasAnnotation("org.apache.ibatis.annotations.Mapper") ||
                clazz.getName() != null && clazz.getName().endsWith("Mapper") ||
                clazz.getName() != null && clazz.getName().endsWith("Dao")) {
            for (PsiMethod method : clazz.getMethods()) {
                if (hasMyBatisSqlAnnotation(method)) {
                    return true;
                }
            }

            return false;
        }
        String name = clazz.getName();
        return clazz.hasAnnotation("org.apache.ibatis.annotations.Mapper") ||
                (name != null && name.endsWith("Mapper"));
    }

    private boolean hasMyBatisSqlAnnotation(PsiMethod method) {
        return method.hasAnnotation("org.apache.ibatis.annotations.Select") ||
                method.hasAnnotation("org.apache.ibatis.annotations.Insert") ||
                method.hasAnnotation("org.apache.ibatis.annotations.Update") ||
                method.hasAnnotation("org.apache.ibatis.annotations.Delete");
    }
}
