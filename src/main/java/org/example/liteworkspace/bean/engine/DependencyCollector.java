package org.example.liteworkspace.bean.engine;

import com.intellij.lang.jvm.types.JvmReferenceType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

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

        return minimizePackages(packages);
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
    private static Set<String> minimizePackages(Set<String> packages) {
        if (packages.isEmpty()) return Collections.emptySet();
        if (packages.size() == 1) return packages;

        // 拆分为二维数组
        List<String[]> splitPkgs = new ArrayList<>();
        for (String pkg : packages) {
            splitPkgs.add(pkg.split("\\."));
        }

        // 找公共前缀长度
        int minLen = splitPkgs.stream().mapToInt(arr -> arr.length).min().orElse(0);
        int prefixLen = 0;
        outer:
        for (int i = 0; i < minLen; i++) {
            String token = splitPkgs.get(0)[i];
            for (int j = 1; j < splitPkgs.size(); j++) {
                if (!token.equals(splitPkgs.get(j)[i])) {
                    break outer;
                }
            }
            prefixLen++;
        }

        // 拼接回包名
        if (prefixLen == 0) {
            // 没有公共前缀，返回原集合
            return packages;
        } else {
            String prefix = String.join(".", Arrays.copyOf(splitPkgs.get(0), prefixLen));
            return Collections.singleton(prefix);
        }
    }

}
