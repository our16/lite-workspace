package org.example.liteworkspace.index;

import com.intellij.psi.*;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class BeanReturnTypeIndexer implements DataIndexer<String, String, FileContent> {
    @Override
    public @NotNull Map<String, String> map(@NotNull FileContent inputData) {
        Map<String, String> map = new HashMap<>();
        PsiFile file = inputData.getPsiFile();

        file.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitMethod(PsiMethod method) {
                if (!method.hasAnnotation("org.springframework.context.annotation.Bean")) return;

                PsiType returnType = method.getReturnType();
                PsiClass returnClass = returnType instanceof PsiClassType ? ((PsiClassType) returnType).resolve() : null;
                PsiClass configClass = method.getContainingClass();

                if (returnClass != null && returnClass.getQualifiedName() != null
                        && configClass != null && configClass.hasAnnotation("org.springframework.context.annotation.Configuration")) {
                    map.put(returnClass.getQualifiedName(), configClass.getQualifiedName());
                }
            }
        });

        return map;
    }
}

