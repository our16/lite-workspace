package org.example.liteworkspace.bean.recognizer;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifierList;
import org.example.liteworkspace.bean.BeanRecognizer;
import org.example.liteworkspace.bean.core.BeanOrigin;

public class AnnotationBeanRecognizer implements BeanRecognizer {
    @Override
    public boolean isBean(PsiClass clazz) {
        return getOrigin(clazz) != null;
    }

    @Override
    public BeanOrigin getOrigin(PsiClass clazz) {
        PsiModifierList list = clazz.getModifierList();
        if (list == null) return null;
        for (PsiAnnotation ann : list.getAnnotations()) {
            String qName = ann.getQualifiedName();
            if (qName != null && (
                    qName.startsWith("org.springframework.stereotype.") ||
                            qName.equals("org.springframework.web.bind.annotation.RestController") ||
                            qName.equals("org.springframework.context.annotation.Configuration"))) {
                return BeanOrigin.ANNOTATION;
            }
        }
        return null;
    }
}
