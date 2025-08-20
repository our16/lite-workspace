package org.example.liteworkspace.bean.engine;

import com.intellij.lang.jvm.types.JvmReferenceType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;

import java.util.*;

public class SpringDependencyCollector {

    /**
     * 收集 Spring 管理的依赖包
     *
     * @param roots 入口类集合（Spring Bean 类）
     */
    public static Set<String> collectSpringDependencyPackages(Collection<PsiClass> roots) {
        Set<String> visited = new HashSet<>();
        Set<String> packages = new LinkedHashSet<>();

        for (PsiClass root : roots) {
            if (!isSpringBean(root)) continue;
            collectDependencies(root, visited, packages);
        }

        return maximizeParentPackages(packages);
    }

    /**
     * 迭代收集依赖
     */
    private static void collectDependencies(PsiClass clazz, Set<String> visited, Set<String> packages) {
        if (clazz == null) return;

        Deque<PsiClass> stack = new ArrayDeque<>();
        stack.push(clazz);

        while (!stack.isEmpty()) {
            PsiClass current = stack.pop();
            String qName = current.getQualifiedName();
            if (qName == null || visited.contains(qName) || isJdkClass(current)) continue;
            visited.add(qName);

            // ==== 添加当前包名 ====
            String pkg = getPackageName(current);
            if (pkg != null) packages.add(pkg);

            // ==== 字段依赖 ====
            for (PsiField field : current.getFields()) {
                if (!isSpringInjectedField(field)) continue;
                collectTypeRecursive(field.getType(), stack);
            }

            // ==== 构造器注入 ====
            for (PsiMethod ctor : current.getConstructors()) {
                if (!isSpringInjectedConstructor(ctor)) continue;
                for (PsiParameter param : ctor.getParameterList().getParameters()) {
                    collectTypeRecursive(param.getType(), stack);
                }
            }

            // ==== Setter 注入 ====
            for (PsiMethod method : current.getMethods()) {
                if (!isSpringInjectedSetter(method)) continue;
                for (PsiParameter param : method.getParameterList().getParameters()) {
                    collectTypeRecursive(param.getType(), stack);
                }
            }

            // ==== @Configuration Bean 方法 ====
            if (current.hasAnnotation("org.springframework.context.annotation.Configuration")) {
                for (PsiMethod method : current.getMethods()) {
                    if (!method.hasAnnotation("org.springframework.context.annotation.Bean")) continue;
                    PsiType returnType = method.getReturnType();
                    if (returnType != null) collectTypeRecursive(returnType, stack);
                }
            }

            // ==== 父类 ====
            JvmReferenceType superType = current.getSuperClassType();
            PsiClass superClass = resolveToPsiClass(superType);
            if (superClass != null) stack.push(superClass);

            // ==== 接口 ====
            for (PsiClassType ifaceType : current.getImplementsListTypes()) {
                PsiClass iface = ifaceType.resolve();
                if (iface != null) stack.push(iface);
            }

            // ==== 接口/抽象类实现查找 ====
            if (current.isInterface() || current.hasModifierProperty(PsiModifier.ABSTRACT)) {
                Project project = current.getProject();
                for (PsiClass impl : ClassInheritorsSearch.search(current, GlobalSearchScope.allScope(project), true)) {
                    if (impl != null && !visited.contains(impl.getQualifiedName())) stack.push(impl);
                }
            }
        }
    }

    // ===================== 辅助方法 =====================

    private static void collectTypeRecursive(PsiType type, Deque<PsiClass> stack) {
        if (type == null) return;

        if (type instanceof PsiArrayType arrayType) {
            collectTypeRecursive(arrayType.getComponentType(), stack);
        } else if (type instanceof PsiClassType classType) {
            PsiClass resolved = classType.resolve();
            if (resolved != null) stack.push(resolved);
            for (PsiType param : classType.getParameters()) collectTypeRecursive(param, stack);
        } else if (type instanceof PsiWildcardType wildcardType) {
            PsiType bound = wildcardType.getBound();
            if (bound != null) collectTypeRecursive(bound, stack);
        }
    }

    /**
     * 返回包名：
     * - 项目源码类 -> 返回项目根包（前两层）
     * - JAR 类 -> 返回 JAR 文件名
     */
    private static String getPackageName(PsiClass clazz) {
        if (clazz == null) return null;

        PsiFile file = clazz.getContainingFile();
        if (!(file instanceof PsiJavaFile javaFile)) return null;

        VirtualFile vFile = javaFile.getVirtualFile();
        if (vFile == null) {
            // fallback：通过类全限定名提取
            String qName = clazz.getQualifiedName();
            if (qName != null && qName.contains(".")) {
                return extractRootPackage(qName.substring(0, qName.lastIndexOf('.')));
            }
            return null;
        }

        // 检查类是否在 JAR 中
        if (vFile.getFileSystem() instanceof JarFileSystem) {
            VirtualFile jarRoot = JarFileSystem.getInstance().getVirtualFileForJar(vFile);
            if (jarRoot != null) {
                return jarRoot.getName(); // 返回 jar 文件名
            }
            // fallback
            return vFile.getName();
        }

        // 项目源码类
        String packageName = javaFile.getPackageName();
        return extractRootPackage(packageName);
    }


    /**
     * 提取包名的根包，例如 com.example.service -> com.example
     * 如果包名为空，返回 null
     */
    private static String extractRootPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) return null;
        String[] parts = packageName.split("\\.");
        if (parts.length <= 2) return packageName; // 包名只有一两层，直接返回
        return parts[0] + "." + parts[1]; // 返回前两层作为根包
    }

    private static boolean isSpringBean(PsiClass clazz) {
        return clazz.hasAnnotation("org.springframework.stereotype.Component")
                || clazz.hasAnnotation("org.springframework.stereotype.Service")
                || clazz.hasAnnotation("org.springframework.stereotype.Repository")
                || clazz.hasAnnotation("org.springframework.stereotype.Controller")
                || clazz.hasAnnotation("org.springframework.context.annotation.Configuration");
    }

    private static boolean isSpringInjectedField(PsiField field) {
        return field.hasAnnotation("org.springframework.beans.factory.annotation.Autowired")
                || field.hasAnnotation("javax.annotation.Resource")
                || field.hasAnnotation("jakarta.annotation.Resource")
                || field.hasAnnotation("javax.inject.Inject");
    }

    private static boolean isSpringInjectedConstructor(PsiMethod ctor) {
        return ctor.hasAnnotation("org.springframework.beans.factory.annotation.Autowired");
    }

    private static boolean isSpringInjectedSetter(PsiMethod method) {
        if (!method.getName().startsWith("set")) return false;
        return method.hasAnnotation("org.springframework.beans.factory.annotation.Autowired")
                || method.hasAnnotation("javax.annotation.Resource")
                || method.hasAnnotation("jakarta.annotation.Resource")
                || method.hasAnnotation("javax.inject.Inject");
    }

    private static PsiClass resolveToPsiClass(JvmReferenceType refType) {
        if (refType == null) return null;
        PsiClassType psiType = refType instanceof PsiClassType ? (PsiClassType) refType : null;
        return psiType != null ? psiType.resolve() : null;
    }

    private static boolean isJdkClass(PsiClass psiClass) {
        String qName = psiClass.getQualifiedName();
        if (qName == null) return false;
        return qName.startsWith("java.") || qName.startsWith("javax.");
    }

    /**
     * 归约包名集合，保留公共最小前缀
     */
    private static Set<String> maximizeParentPackages(Set<String> packages) {
        if (packages.isEmpty()) return Collections.emptySet();

        // 构建前缀树
        TrieNode root = new TrieNode();
        for (String pkg : packages) {
            String[] parts = pkg.split("\\.");
            TrieNode node = root;
            for (String part : parts) {
                node = node.children.computeIfAbsent(part, k -> new TrieNode());
            }
            node.isEnd = true;
        }

        Set<String> result = new LinkedHashSet<>();
        for (Map.Entry<String, TrieNode> entry : root.children.entrySet()) {
            List<String> path = new ArrayList<>();
            path.add(entry.getKey());
            collectMaxParent(entry.getValue(), path, result);
        }

        return result;
    }

    private static void collectMaxParent(TrieNode node, List<String> path, Set<String> result) {
        if (node.children.isEmpty()) {
            result.add(String.join(".", path));
            return;
        }

        if (node.children.size() > 1) {
            result.add(String.join(".", path));
            return;
        }

        Map.Entry<String, TrieNode> entry = node.children.entrySet().iterator().next();
        path.add(entry.getKey());
        collectMaxParent(entry.getValue(), path, result);
        path.remove(path.size() - 1);
    }

    private static class TrieNode {
        Map<String, TrieNode> children = new HashMap<>();
        boolean isEnd = false;
    }
}
