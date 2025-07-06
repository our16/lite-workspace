package org.example.liteworkspace.bean.resolver;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.example.liteworkspace.bean.BeanDefinitionResolver;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;



public class XmlBeanResolver implements BeanDefinitionResolver {
    private final Project project;

    public XmlBeanResolver(Project project) {
        this.project = project;
    }

    @Override
    public boolean isBean(PsiClass clazz) {
        return false;
    }

    @Override
    public boolean isProvidedByBeanMethod(PsiClass clazz) {
        return false;
    }

    @Override
    public boolean isXmlDefined(PsiClass clazz) {
        return findXmlFile(clazz) != null;
    }

    public XmlFile findXmlFile(PsiClass clazz) {
        String fqcn = clazz.getQualifiedName();
        if (fqcn == null) return null;

        for (VirtualFile vf : FilenameIndex.getAllFilesByExt(project, "xml", GlobalSearchScope.projectScope(project))) {
            PsiFile file = PsiManager.getInstance(project).findFile(vf);
            if (!(file instanceof XmlFile xmlFile)) continue;

            XmlTag root = xmlFile.getRootTag();
            if (root == null || !"beans".equals(root.getName())) continue;

            for (XmlTag bean : root.findSubTags("bean")) {
                String cls = bean.getAttributeValue("class");
                if (fqcn.equals(cls)) {
                    return xmlFile;
                }
            }
        }
        return null;
    }

}