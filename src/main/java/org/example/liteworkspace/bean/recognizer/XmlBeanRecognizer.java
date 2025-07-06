package org.example.liteworkspace.bean.recognizer;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.example.liteworkspace.bean.BeanRecognizer;
import org.example.liteworkspace.bean.core.BeanOrigin;

public class XmlBeanRecognizer implements BeanRecognizer {
    private final Project project;

    public XmlBeanRecognizer(Project project) {
        this.project = project;
    }

    @Override
    public boolean isBean(PsiClass clazz) {
        return getOrigin(clazz) != null;
    }

    @Override
    public BeanOrigin getOrigin(PsiClass clazz) {
        String fqcn = clazz.getQualifiedName();
        if (fqcn == null) return null;

        for (VirtualFile vf : FilenameIndex.getAllFilesByExt(project, "xml", GlobalSearchScope.projectScope(project))) {
            PsiFile file = PsiManager.getInstance(project).findFile(vf);
            if (!(file instanceof XmlFile xmlFile)) continue;

            XmlTag root = xmlFile.getRootTag();
            if (root == null || !"beans".equals(root.getName())) continue;

            for (XmlTag bean : root.findSubTags("bean")) {
                if (fqcn.equals(bean.getAttributeValue("class"))) {
                    return BeanOrigin.XML;
                }
            }
        }
        return null;
    }
}

