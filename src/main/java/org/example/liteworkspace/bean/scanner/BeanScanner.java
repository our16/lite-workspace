package org.example.liteworkspace.bean.scanner;

import com.intellij.psi.PsiClass;

import java.util.Set;

public interface BeanScanner {

    Set<PsiClass> collectDependencies(PsiClass clazz);
}
