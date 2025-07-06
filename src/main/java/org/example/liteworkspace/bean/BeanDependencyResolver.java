package org.example.liteworkspace.bean;

import com.intellij.psi.PsiClass;

import java.util.Set;

public interface BeanDependencyResolver {
    Set<PsiClass> resolveDependencies(PsiClass clazz);
}