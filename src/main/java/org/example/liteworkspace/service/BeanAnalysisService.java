package org.example.liteworkspace.service;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.example.liteworkspace.bean.core.BeanDefinition;
import org.example.liteworkspace.bean.core.context.LiteProjectContext;
import org.example.liteworkspace.dto.ClassSignatureDTO;
import org.example.liteworkspace.dto.MethodSignatureDTO;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Bean分析服务接口
 * 负责Bean扫描、依赖分析和相关操作
 */
public interface BeanAnalysisService {
    
    /**
     * 扫描并收集Bean依赖关系
     * 
     * @param projectContext 项目上下文
     * @param targetClass 目标类
     * @return Bean定义集合
     */
    Collection<BeanDefinition> scanBeanDependencies(LiteProjectContext projectContext, PsiClass targetClass);
    
    /**
     * 异步扫描并收集Bean依赖关系
     * 
     * @param projectContext 项目上下文
     * @param targetClass 目标类
     * @return CompletableFuture包含Bean定义集合
     */
    CompletableFuture<Collection<BeanDefinition>> scanBeanDependenciesAsync(LiteProjectContext projectContext, PsiClass targetClass);
    
    /**
     * 分析类的依赖关系
     * 
     * @param project 项目
     * @param targetClassDto 目标类DTO
     * @param targetMethodDto 目标方法DTO（可选）
     * @param indicator 进度指示器
     * @return 分析结果
     */
    BeanAnalysisResult analyzeClassDependencies(Project project, 
                                               ClassSignatureDTO targetClassDto, 
                                               MethodSignatureDTO targetMethodDto,
                                               ProgressIndicator indicator);
    
    /**
     * 创建项目上下文
     * 
     * @param project 项目
     * @param targetClass 目标类
     * @param targetMethod 目标方法（可选）
     * @param indicator 进度指示器
     * @return 项目上下文
     */
    LiteProjectContext createProjectContext(Project project, 
                                          PsiClass targetClass, 
                                          PsiMethod targetMethod,
                                          ProgressIndicator indicator);
    
    /**
     * Bean分析结果
     */
    class BeanAnalysisResult {
        private final LiteProjectContext projectContext;
        private final Collection<BeanDefinition> beans;
        private final long analysisTime;
        
        public BeanAnalysisResult(LiteProjectContext projectContext, Collection<BeanDefinition> beans, long analysisTime) {
            this.projectContext = projectContext;
            this.beans = beans;
            this.analysisTime = analysisTime;
        }
        
        public LiteProjectContext getProjectContext() {
            return projectContext;
        }
        
        public Collection<BeanDefinition> getBeans() {
            return beans;
        }
        
        public long getAnalysisTime() {
            return analysisTime;
        }
    }
}
