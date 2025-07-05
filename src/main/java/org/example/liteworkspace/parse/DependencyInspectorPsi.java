package org.example.liteworkspace.parse;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;

import java.util.HashSet;
import java.util.Set;

public class DependencyInspectorPsi {

    public static String analyze(PsiClass psiClass) {
        StringBuilder md = new StringBuilder("# 依赖分析报告\n\n");
        Set<String> visited = new HashSet<>();
        inspect(psiClass, md, 0, visited);
        return md.toString();
    }

    private static void inspect(PsiClass clazz, StringBuilder md, int indent, Set<String> visited) {
        if (clazz == null || visited.contains(clazz.getQualifiedName())) return;
        String qName = clazz.getQualifiedName();
        if (qName == null || qName.startsWith("java.")) return;

        visited.add(qName);

        indent(md, indent);
        md.append("- ").append(clazz.getName()).append(" (`").append(qName).append("`)\n");

        // 构造方法参数依赖
        for (PsiMethod constructor : clazz.getConstructors()) {
            for (PsiParameter parameter : constructor.getParameterList().getParameters()) {
                PsiClass dep = resolveClass(parameter.getType());
                inspect(dep, md, indent + 1, visited);
            }
        }

        // 字段依赖
        for (PsiField field : clazz.getFields()) {
            PsiClass dep = resolveClass(field.getType());
            inspect(dep, md, indent + 1, visited);
        }
    }

    private static PsiClass resolveClass(PsiType type) {
        if (type instanceof PsiClassType) {
            return ((PsiClassType) type).resolve();
        }
        return null;
    }

    private static void indent(StringBuilder sb, int level) {
        for (int i = 0; i < level; i++) sb.append("  ");
    }
}
