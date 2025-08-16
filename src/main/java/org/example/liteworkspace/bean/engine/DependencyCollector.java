package org.example.liteworkspace.bean.engine;

import com.intellij.lang.jvm.types.JvmReferenceType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;

import java.util.*;

public class DependencyCollector {

    /**
     * 收集所有依赖的包名（入口：文件路径 -> 包集合）
     */
    public static Set<String> collectAllDependencyPackages(Project project, Collection<PsiClass> roots) {
        Set<String> visited = new HashSet<>();
        Set<String> packages = new LinkedHashSet<>();

        for (PsiClass root : roots) {
            collectDependenciesIterative(root, visited, packages);
        }

        return maximizeParentPackages(packages);
    }

    /**
     * 迭代收集依赖
     */
    private static void collectDependenciesIterative(PsiClass root,
                                                     Set<String> visited,
                                                     Set<String> packages) {
        if (root == null) {
            return;
        }

        Deque<PsiClass> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            PsiClass currentClazz = stack.pop();

            String qName = currentClazz.getQualifiedName();
            if (qName == null || visited.contains(qName) || isJdkClass(currentClazz)) {
                continue;
            }
            visited.add(qName);

            // === 添加自身包名（支持源码和jar里的ClsFile） ===
            PsiFile file = currentClazz.getContainingFile();
            String pkgName = null;
            if (file instanceof PsiJavaFile) {
                pkgName = ((PsiJavaFile) file).getPackageName();
            } else {
                // jar 里的 class 没有 PsiJavaFile，但 PsiClass#getQualifiedName 有全限定名
                if (qName.contains(".")) {
                    int lastDot = qName.lastIndexOf('.');
                    pkgName = qName.substring(0, lastDot);
                }
            }
            if (pkgName != null && !pkgName.isEmpty()) {
                packages.add(pkgName);
            }

            // ===== 字段依赖 =====
            for (PsiField field : currentClazz.getFields()) {
                collectTypeRecursive(field.getType(), stack);
            }

            // ===== 父类 =====
            JvmReferenceType superType = currentClazz.getSuperClassType();
            if (superType != null) {
                PsiClass superClass = resolveToPsiClass(superType);
                if (superClass != null) {
                    stack.push(superClass);
                }
            }

            // ===== 接口 =====
            for (PsiClassType ifaceType : currentClazz.getImplementsListTypes()) {
                PsiClass iface = ifaceType.resolve();
                if (iface != null) {
                    stack.push(iface);
                }
            }
            // ===== 如果是接口或抽象类，查找所有实现类/子类 =====
            if (currentClazz.isInterface() || currentClazz.hasModifierProperty(PsiModifier.ABSTRACT)) {
                Project project = currentClazz.getProject();
                GlobalSearchScope scope = GlobalSearchScope.allScope(project);

                PsiClass[] impls = ClassInheritorsSearch.search(currentClazz, scope, true).toArray(PsiClass.EMPTY_ARRAY);
                for (PsiClass impl : impls) {
                    if (impl != null && !visited.contains(impl.getQualifiedName())) {
                        stack.push(impl);
                    }
                }
            }
        }
    }

    /**
     * 递归解析类型（含泛型）
     */
    private static void collectTypeRecursive(PsiType type, Deque<PsiClass> stack) {
        if (type == null) return;

        if (type instanceof PsiArrayType) {
            // 数组：取组件类型
            collectTypeRecursive(((PsiArrayType) type).getComponentType(), stack);
        } else if (type instanceof PsiClassType) {
            PsiClassType classType = (PsiClassType) type;
            PsiClass resolved = classType.resolve();
            if (resolved != null) {
                stack.push(resolved);
            }
            // 递归处理泛型参数
            for (PsiType paramType : classType.getParameters()) {
                collectTypeRecursive(paramType, stack);
            }
        } else if (type instanceof PsiWildcardType) {
            PsiType bound = ((PsiWildcardType) type).getBound();
            if (bound != null) {
                collectTypeRecursive(bound, stack);
            }
        }
    }

    /**
     * JvmReferenceType 转 PsiClass
     */
    private static PsiClass resolveToPsiClass(JvmReferenceType refType) {
        if (refType == null) {
            return null;
        }
        PsiClassType psiType = refType instanceof PsiClassType ? (PsiClassType) refType : null;
        return psiType != null ? psiType.resolve() : null;
    }

    /**
     * 判断是否是 JDK 类
     */
    private static boolean isJdkClass(PsiClass psiClass) {
        String qName = psiClass.getQualifiedName();
        if (qName == null) return false;
        return qName.startsWith("java.") || qName.startsWith("javax.");
    }

    /**
     * 归约包名集合，保留最短前缀
     */
    /**
     * 归约包名集合，保留公共最小前缀
     * 例如：
     *   ["com.alibaba.fastjson.parser", "com.alibaba.fastjson.serializer"]
     *   => ["com.alibaba.fastjson"]
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
            // 叶子节点
            result.add(String.join(".", path));
            return;
        }

        if (node.children.size() > 1) {
            // 多分支，当前路径就是最大公共父节点
            result.add(String.join(".", path));
            return;
        }

        // 只有一条子节点，继续下钻
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
