package org.example.liteworkspace.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;

import java.util.Collection;
import java.util.Set;

public class MyPsiClassUtil {


    /**
     * 为单个包前缀创建搜索范围
     *
     * @param project 当前项目
     * @param packagePrefix 包前缀
     * @param includeLibraries 是否包含依赖的 jar
     * @return 搜索范围
     */
    public static GlobalSearchScope createSearchScopeForPackage(Project project,
                                                          String packagePrefix,
                                                          boolean includeLibraries) {
        LogUtil.info("为包前缀 {} 创建搜索范围 (includeLibraries={})", packagePrefix, includeLibraries);
        PsiPackage psiPackage = JavaPsiFacade.getInstance(project).findPackage(packagePrefix);
        if (psiPackage == null) {
            LogUtil.warn("未找到包前缀 {} 对应的 PsiPackage", packagePrefix);
            return GlobalSearchScope.EMPTY_SCOPE;
        }
        // true = 递归子包
        GlobalSearchScope scope = new PackageScope(psiPackage, true, includeLibraries);
        LogUtil.info("创建 PackageScope 成功: 包={}, 子包递归=true, 包含库={}", packagePrefix, includeLibraries);
        return scope;
    }


    /**
     * 在指定的搜索范围内查找接口的实现类
     *
     * @param interfaceClass 接口类
     * @param scope 搜索范围
     * @param allImplementations 所有实现类的集合
     */
    public static void findImplementationsInScope(PsiClass interfaceClass,
                                            GlobalSearchScope scope,
                                            Set<PsiClass> allImplementations) {
        String interfaceQName = interfaceClass.getQualifiedName();
        if (interfaceQName == null) {
            LogUtil.warn("接口 {} 没有获取到合法的 QualifiedName", interfaceClass);
            return;
        }

        // -------------------------------
        // 使用 ClassInheritorsSearch（推荐）
        // 支持：源码 + jar 依赖
        // -------------------------------
        LogUtil.info("使用 ClassInheritorsSearch 在 scope=[{}] 查找实现类", scope);
        Collection<PsiClass> implementations =
                ClassInheritorsSearch.search(interfaceClass, scope, true).findAll();

        int beforeSize = allImplementations.size();
        allImplementations.addAll(implementations);
        int added = allImplementations.size() - beforeSize;

        LogUtil.info("ClassInheritorsSearch 找到 {} 个实现类，其中新增 {} 个", implementations.size(), added);

        // -------------------------------
        // 可选：输出每个实现类的来源（源码 or jar）
        // -------------------------------
        for (PsiClass implClass : implementations) {
            PsiFile psiFile = implClass.getContainingFile();
            if (psiFile == null) {
                continue;
            }
            VirtualFile vFile = psiFile.getVirtualFile();
            if (vFile != null) {
                String origin = vFile.getPath().contains(".jar!")
                        ? "依赖JAR"
                        : "源码";
                LogUtil.info("实现类: {} 来源: {}", implClass.getQualifiedName(), origin);
            }
        }
    }
}
