package org.example.liteworkspace.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class BeanReturnTypeIndex extends StringStubIndexExtension<PsiClass> {

    public static final StubIndexKey<String, PsiClass> KEY =
            StubIndexKey.createIndexKey("spring.bean.returnType");
    private static final BeanReturnTypeIndex INSTANCE = new BeanReturnTypeIndex();

    public static BeanReturnTypeIndex getInstance() {
        return INSTANCE;
    }

    @Override
    public @NotNull StubIndexKey<String, PsiClass> getKey() {
        return KEY;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    public static Collection<PsiClass> getBeanProviders(String beanFqcn, Project project) {
        return StubIndex.getElements(
                KEY,
                beanFqcn,
                project,
                GlobalSearchScope.projectScope(project),
                PsiClass.class
        );
    }

}
