package org.example.liteworkspace.bean.scanner;

import com.intellij.psi.PsiClass;
import org.example.liteworkspace.bean.core.BeanType;

import java.util.List;
import java.util.Set;

public interface BeanScanner {

    Set<PsiClass> collectDependencies(PsiClass clazz);

    List<BeanType> supportedType();
}
