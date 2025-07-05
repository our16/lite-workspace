package org.example.liteworkspace.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.util.Query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SpringInterfaceResolver {

    /**
     * 判断是否是 MyBatis Mapper 接口
     */
    public static boolean isMyBatisMapper(PsiClass clazz) {
        if (clazz.getModifierList() == null) return false;

        // 注解方式
        for (PsiAnnotation annotation : clazz.getModifierList().getAnnotations()) {
            String qualifiedName = annotation.getQualifiedName();
            if ("org.apache.ibatis.annotations.Mapper".equals(qualifiedName)) {
                return true;
            }
        }

        // 包路径关键词判断（可选优化）
        String qName = clazz.getQualifiedName();
        return qName != null && qName.contains(".mapper.");
    }

    /**
     * 查找接口的所有实现类
     */
    public static List<PsiClass> findImplementations(Project project, PsiClass interfaceClass) {
        if (!interfaceClass.isInterface()) return Collections.emptyList();
        if (isMyBatisMapper(interfaceClass)) return Collections.emptyList();

        Query<PsiClass> query = ClassInheritorsSearch.search(interfaceClass, GlobalSearchScope.projectScope(project), true);
        return new ArrayList<>(query.findAll());
    }
}
