package org.example.liteworkspace.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiTreeUtil;
import org.example.liteworkspace.dto.ClassSignatureDTO;
import org.example.liteworkspace.util.LogUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简化的搜索范围管理器
 * 移除了缓存依赖和复杂的API调用，避免项目间数据污染
 */
public class OptimizedSearchScopeManager {
    
    private final Project project;
    private final Map<String, GlobalSearchScope> scopeCache = new ConcurrentHashMap<>();
    
    public OptimizedSearchScopeManager(Project project) {
        this.project = project;
    }
    
    /**
     * 获取优化的搜索范围
     */
    public GlobalSearchScope getOptimizedSearchScope(PsiClass targetClass) {
        String className = targetClass.getQualifiedName();
        if (className == null) {
            return GlobalSearchScope.projectScope(project);
        }
        
        // 简单的内存缓存，仅限于当前实例
        return scopeCache.computeIfAbsent(className, k -> createOptimizedScope(targetClass));
    }
    
    /**
     * 创建优化的搜索范围
     */
    private GlobalSearchScope createOptimizedScope(PsiClass targetClass) {
        LogUtil.debug("创建优化的搜索范围: {}", targetClass.getQualifiedName());
        
        // 简化实现：直接返回项目范围
        // 移除复杂的UseScopeOptimizer调用，避免API兼容性问题
        return GlobalSearchScope.projectScope(project);
    }
    
    /**
     * 基于包结构创建搜索范围
     */
    private GlobalSearchScope createPackageBasedScope(PsiClass targetClass, GlobalSearchScope projectScope) {
        String packageName = getPackageName(targetClass);
        if (packageName == null || packageName.isEmpty()) {
            return projectScope;
        }
        
        // 查找同包的所有文件
        List<PsiFile> samePackageFiles = findFilesInPackage(packageName);
        if (samePackageFiles.isEmpty()) {
            return projectScope;
        }
        
        // 简化实现：直接返回项目范围
        // 移除复杂的范围构建逻辑，避免API兼容性问题
        return projectScope;
    }
    
    /**
     * 获取包名
     */
    private String getPackageName(PsiClass psiClass) {
        PsiFile file = psiClass.getContainingFile();
        if (file == null) {
            return null;
        }
        
        // 简化包名获取逻辑
        String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName != null) {
            int lastDot = qualifiedName.lastIndexOf('.');
            if (lastDot > 0) {
                return qualifiedName.substring(0, lastDot);
            }
        }
        
        return "";
    }
    
    /**
     * 查找包中的文件
     */
    private List<PsiFile> findFilesInPackage(String packageName) {
        List<PsiFile> result = new ArrayList<>();
        
        try {
            PsiManager psiManager = PsiManager.getInstance(project);
            
            // 简化实现：使用PsiShortNamesCache查找类
            String packagePrefix = packageName + ".";
            String[] classNames = PsiShortNamesCache.getInstance(project).getAllClassNames();
            
            for (String className : classNames) {
                if (className.startsWith(packagePrefix)) {
                    PsiClass[] foundClasses = PsiShortNamesCache.getInstance(project)
                        .getClassesByName(className, GlobalSearchScope.projectScope(project));
                    
                    for (PsiClass cls : foundClasses) {
                        String qualifiedName = cls.getQualifiedName();
                        if (qualifiedName != null && qualifiedName.startsWith(packagePrefix)) {
                            PsiFile file = cls.getContainingFile();
                            if (file != null && file.getVirtualFile() != null) {
                                result.add(file);
                            }
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            LogUtil.warn("查找包文件失败: {} - {}", packageName, e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 清理范围缓存
     */
    public void clearScopeCache() {
        scopeCache.clear();
        LogUtil.debug("清理搜索范围缓存");
    }
    
    /**
     * 获取相关类的搜索范围
     */
    public GlobalSearchScope getRelatedClassesScope(Collection<PsiClass> relatedClasses) {
        if (relatedClasses.isEmpty()) {
            return GlobalSearchScope.projectScope(project);
        }
        
        // 简化实现：直接返回项目范围
        // 移除复杂的范围合并逻辑，避免API兼容性问题
        return GlobalSearchScope.projectScope(project);
    }
    
    /**
     * 基于DTO获取搜索范围
     */
    public GlobalSearchScope getSearchScopeForDto(ClassSignatureDTO classDto) {
        try {
            // 使用PsiShortNamesCache查找类
            String className = classDto.getSimpleName();
            PsiClass[] foundClasses = PsiShortNamesCache.getInstance(project)
                .getClassesByName(className, GlobalSearchScope.projectScope(project));
            
            for (PsiClass psiClass : foundClasses) {
                if (classDto.getQualifiedName().equals(psiClass.getQualifiedName())) {
                    return getOptimizedSearchScope(psiClass);
                }
            }
        } catch (Exception e) {
            LogUtil.warn("基于DTO获取搜索范围失败: {} - {}", classDto.getQualifiedName(), e.getMessage());
        }
        
        return GlobalSearchScope.projectScope(project);
    }
    
    /**
     * 获取统计信息
     */
    public ScopeStatistics getStatistics() {
        return new ScopeStatistics(
            scopeCache.size(),
            "OptimizedSearchScopeManager"
        );
    }
    
    /**
     * 范围统计信息
     */
    public static class ScopeStatistics {
        private final int cachedScopes;
        private final String description;
        
        public ScopeStatistics(int cachedScopes, String description) {
            this.cachedScopes = cachedScopes;
            this.description = description;
        }
        
        public int getCachedScopes() {
            return cachedScopes;
        }
        
        public String getDescription() {
            return description;
        }
        
        @Override
        public String toString() {
            return String.format(
                "ScopeStatistics{cachedScopes=%d, description=%s}",
                cachedScopes, description
            );
        }
    }
    
    /**
     * 清理资源
     */
    public void dispose() {
        clearScopeCache();
        LogUtil.debug("OptimizedSearchScopeManager已清理");
    }
}
