package org.example.liteworkspace.util;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import org.example.liteworkspace.dto.ClassSignatureDTO;
import org.example.liteworkspace.util.LogUtil;

import java.util.*;

/**
 * 简化的搜索范围管理器
 * 完全移除缓存机制，避免项目间数据污染
 */
public class OptimizedSearchScopeManager {
    
    private final Project project;
    
    public OptimizedSearchScopeManager(Project project) {
        this.project = project;
    }
    
    /**
     * 获取搜索范围（无缓存版本）
     */
    public GlobalSearchScope getOptimizedSearchScope(PsiClass targetClass) {
        return ReadAction.compute(() -> {
            LogUtil.debug("获取搜索范围: {}", targetClass.getQualifiedName());
            
            // 简化实现：直接返回项目范围
            return GlobalSearchScope.projectScope(project);
        });
    }
    
    /**
     * 获取相关类的搜索范围（无缓存版本）
     */
    public GlobalSearchScope getRelatedClassesScope(Collection<PsiClass> relatedClasses) {
        LogUtil.debug("获取相关类搜索范围，数量: {}", relatedClasses.size());
        
        // 简化实现：直接返回项目范围
        return GlobalSearchScope.projectScope(project);
    }
    
    /**
     * 基于DTO获取搜索范围（无缓存版本）
     */
    public GlobalSearchScope getSearchScopeForDto(ClassSignatureDTO classDto) {
        LogUtil.debug("基于DTO获取搜索范围: {}", classDto.getQualifiedName());
        
        // 简化实现：直接返回项目范围
        return GlobalSearchScope.projectScope(project);
    }
    
    /**
     * 获取包名
     */
    private String getPackageName(PsiClass psiClass) {
        return ReadAction.compute(() -> {
            String qualifiedName = psiClass.getQualifiedName();
            if (qualifiedName != null) {
                int lastDot = qualifiedName.lastIndexOf('.');
                if (lastDot > 0) {
                    return qualifiedName.substring(0, lastDot);
                }
            }
            return "";
        });
    }
    
    /**
     * 获取统计信息（无缓存版本）
     */
    public ScopeStatistics getStatistics() {
        return new ScopeStatistics(
            0, // 无缓存
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
                "ScopeStatistics{cachedScopes=%d, description=%s, noCache=true}",
                cachedScopes, description
            );
        }
    }
    
    /**
     * 清理资源（无缓存版本）
     */
    public void dispose() {
        LogUtil.debug("OptimizedSearchScopeManager已清理");
    }
}
