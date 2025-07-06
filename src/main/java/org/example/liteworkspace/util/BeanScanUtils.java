package org.example.liteworkspace.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.example.liteworkspace.index.BeanReturnTypeIndex;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class BeanScanUtils {

    private static final Map<Project, Map<String, PsiClass>> configBeanCache = new WeakHashMap<>();

    public static boolean isBeanProvidedBySpring(Project project, PsiClass targetClass) {
        String fqcn = targetClass.getQualifiedName();
        if (fqcn == null) {
            return false;
        }

        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Collection<PsiMethod> candidates = findAllMethodsWithReturnType(project, fqcn, scope);

        for (PsiMethod method : candidates) {
            if (method.hasAnnotation("org.springframework.context.annotation.Bean")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 扫描resource 目录下的xml bean 定义
     * @param project
     * @param targetClass
     * @return
     */
    public static boolean isXmlDefinedBean(Project project, PsiClass targetClass) {
        String fqcn = targetClass.getQualifiedName();
        if (fqcn == null) return false;

        PsiFile[] xmlFiles = FilenameIndex.getFilesByName(project, null, GlobalSearchScope.allScope(project));

        for (PsiFile file : xmlFiles) {
            if (!(file instanceof XmlFile)) continue;

            XmlFile xmlFile = (XmlFile) file;
            XmlTag rootTag = xmlFile.getRootTag();
            if (rootTag == null || !"beans".equals(rootTag.getName())) continue;

            for (XmlTag beanTag : rootTag.findSubTags("bean")) {
                String classAttr = beanTag.getAttributeValue("class");
                if (fqcn.equals(classAttr)) {
                    return true;
                }
            }
        }

        return false;
    }


    private static Collection<PsiMethod> findAllMethodsWithReturnType(Project project, String className, GlobalSearchScope scope) {
        List<PsiMethod> result = new ArrayList<>();
        PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);

        for (String methodName : cache.getAllMethodNames()) {
            for (PsiMethod method : cache.getMethodsByName(methodName, scope)) {
                PsiType returnType = method.getReturnType();
                if (returnType instanceof PsiClassType) {
                    PsiClass returnClass = ((PsiClassType) returnType).resolve();
                    if (returnClass != null && className.equals(returnClass.getQualifiedName())) {
                        result.add(method);
                    }
                }
            }
        }
        return result;
    }

    public static PsiClass getBeanProvidingConfiguration(Project project, PsiClass beanClass) {
        String beanFqcn = beanClass.getQualifiedName();
        if (beanFqcn == null) return null;

        Collection<PsiClass> configClasses = BeanReturnTypeIndex.getBeanProviders(beanFqcn, project);
        if (configClasses.isEmpty()) return null;

        return configClasses.iterator().next(); // 通常只有一个配置类
    }

    public static @Nullable PsiFile findXmlFileDefiningBean(Project project, PsiClass clazz) {
        String fqcn = clazz.getQualifiedName();
        if (fqcn == null) return null;

        Collection<VirtualFile> virtualFiles = FilenameIndex.getAllFilesByExt(project, "xml", GlobalSearchScope.allScope(project));
        List<PsiFile> xmlFiles = new ArrayList<>();
        PsiManager psiManager = PsiManager.getInstance(project);

        for (VirtualFile vf : virtualFiles) {
            PsiFile psiFile = psiManager.findFile(vf);
            if (psiFile != null) {
                xmlFiles.add(psiFile);
            }
        }
        for (PsiFile file : xmlFiles) {
            if (!(file instanceof XmlFile)) continue;
            XmlTag root = ((XmlFile) file).getRootTag();
            if (root == null || !"beans".equals(root.getName())) continue;

            for (XmlTag beanTag : root.findSubTags("bean")) {
                String cls = beanTag.getAttributeValue("class");
                if (fqcn.equals(cls)) {
                    return file;
                }
            }
        }

        return null;
    }


}
