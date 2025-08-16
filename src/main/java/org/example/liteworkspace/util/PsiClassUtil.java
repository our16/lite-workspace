package org.example.liteworkspace.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;

public class PsiClassUtil {

    /**
     * 判断类是否是 Collection 或 Map
     */
    private static boolean isCollectionOrMap(PsiClass psiClass) {
        if (psiClass == null) {
            return false;
        }

        Project project = psiClass.getProject();
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        PsiClass collectionClass = facade.findClass("java.util.Collection", GlobalSearchScope.allScope(project));
        PsiClass mapClass = facade.findClass("java.util.Map", GlobalSearchScope.allScope(project));

        if ((collectionClass != null && psiClass.isInheritor(collectionClass, true))
                || (mapClass != null && psiClass.isInheritor(mapClass, true))) {
            return true;
        }

        String qualifiedName = psiClass.getQualifiedName();
        if ("java.util.Map".equals(qualifiedName)) {
            return true;
        }

        return false;
    }
}
