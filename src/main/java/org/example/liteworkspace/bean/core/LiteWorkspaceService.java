package org.example.liteworkspace.bean.core;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import org.example.liteworkspace.bean.core.context.LiteProjectContext;
import org.example.liteworkspace.bean.engine.LiteFileWriter;
import org.example.liteworkspace.bean.engine.SpringXmlBuilder;
import org.example.liteworkspace.dto.ClassSignatureDTO;
import org.example.liteworkspace.dto.MethodSignatureDTO;
import org.example.liteworkspace.exception.BeanScanningException;
import org.example.liteworkspace.exception.ExceptionHandler;
import org.example.liteworkspace.service.BeanAnalysisService;
import org.example.liteworkspace.service.CacheService;
import org.example.liteworkspace.service.ConfigurationService;
import org.example.liteworkspace.service.ServiceContainer;
import org.example.liteworkspace.util.CostUtil;
import org.example.liteworkspace.util.LogUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 重构后的Service层：负责协调各个服务完成完整的扫描、生成、写入和缓存流程
 */
public class LiteWorkspaceService {

    private final Project project;
    private final BeanAnalysisService beanAnalysisService;
    private final CacheService cacheService;
    private final ConfigurationService configurationService;

    public LiteWorkspaceService(Project project) {
        this.project = project;
        
        // 初始化服务容器
        ServiceContainer.initialize(project);
        
        // 获取服务实例
        this.beanAnalysisService = ServiceContainer.getBeanAnalysisService(project);
        this.cacheService = ServiceContainer.getCacheService(project);
        this.configurationService = ServiceContainer.getConfigurationService(project);
    }

    /**
     * 核心流程：扫描Bean依赖、生成Spring XML、写入文件、保存缓存（带进度指示器）
     */
    public void scanAndGenerateWithDto(ClassSignatureDTO targetClassDto, MethodSignatureDTO targetMethodDto, ProgressIndicator indicator) throws BeanScanningException {
        Objects.requireNonNull(targetClassDto, "targetClassDto不能为空");
        
        String qualifiedName = targetClassDto.getQualifiedName();
        CostUtil.start(qualifiedName);
        LogUtil.info("开始扫描和生成流程: {}", qualifiedName);
        
        try {
            // 检查缓存
            String cacheKey = generateCacheKey(targetClassDto, targetMethodDto);
            BeanAnalysisService.BeanAnalysisResult cachedResult = cacheService.getCachedAnalysisResult(
                project, cacheKey, BeanAnalysisService.BeanAnalysisResult.class);
            
            if (cachedResult != null) {
                LogUtil.info("使用缓存的分析结果: {}", qualifiedName);
                generateFilesFromCachedResult(cachedResult, targetClassDto, indicator);
                return;
            }
            
            // 执行分析
            BeanAnalysisService.BeanAnalysisResult analysisResult = beanAnalysisService.analyzeClassDependencies(
                project, targetClassDto, targetMethodDto, indicator);
            
            // 缓存结果
            cacheService.cacheAnalysisResult(project, cacheKey, analysisResult);
            
            // 生成文件
            generateFiles(analysisResult, targetClassDto, indicator);
            
            indicator.setText2("完成");
            indicator.setFraction(1.0);
            
            LogUtil.info("完成扫描和生成流程，耗时: {} ms", CostUtil.end(qualifiedName));
            
        } catch (ProcessCanceledException e) {
            LogUtil.warn("扫描和生成流程被用户取消: {}", qualifiedName);
            // 对于ProcessCanceledException，直接重新抛出，不包装成BeanScanningException
            throw e;
        } catch (Exception e) {
            LogUtil.error("扫描和生成流程失败: " + qualifiedName, e);
            // 使用新的异常处理机制
            ExceptionHandler.handle(project, e, "扫描和生成流程失败: " + qualifiedName);
            throw BeanScanningException.scanFailed(qualifiedName, e);
        }
    }
    
    /**
     * 从缓存结果生成文件
     */
    private void generateFilesFromCachedResult(BeanAnalysisService.BeanAnalysisResult cachedResult, 
                                              ClassSignatureDTO targetClassDto, 
                                              ProgressIndicator indicator) {
        LogUtil.info("从缓存生成文件: {}", targetClassDto.getQualifiedName());
        
        // 生成Spring XML
        indicator.setText2("从缓存生成Spring XML配置...");
        indicator.setFraction(0.6);
        
        SpringXmlBuilder xmlBuilder = new SpringXmlBuilder(cachedResult.getProjectContext());
        Map<String, String> beanMap = xmlBuilder.buildXmlMap(cachedResult.getBeans());
        
        // 写入文件
        indicator.setText2("写入文件...");
        indicator.setFraction(0.8);
        
        PsiClass targetClass = cachedResult.getProjectContext().findTargetClass();
        writeFiles(cachedResult.getProjectContext(), targetClass, beanMap, cachedResult.getBeans(), indicator);
    }
    
    /**
     * 生成文件
     */
    private void generateFiles(BeanAnalysisService.BeanAnalysisResult analysisResult, 
                              ClassSignatureDTO targetClassDto, 
                              ProgressIndicator indicator) {
        LogUtil.info("生成文件: {}", targetClassDto.getQualifiedName());
        
        // 生成Spring XML
        indicator.setText2("生成Spring XML配置...");
        indicator.setFraction(0.6);
        
        SpringXmlBuilder xmlBuilder = new SpringXmlBuilder(analysisResult.getProjectContext());
        Map<String, String> beanMap = xmlBuilder.buildXmlMap(analysisResult.getBeans());
        LogUtil.info("生成Spring XML配置完成，数量: {}", beanMap.size());
        
        // 写入文件
        indicator.setText2("写入文件...");
        indicator.setFraction(0.8);
        
        PsiClass targetClass = analysisResult.getProjectContext().findTargetClass();
        writeFiles(analysisResult.getProjectContext(), 
                  targetClass, 
                  beanMap, 
                  analysisResult.getBeans(), 
                  indicator);
    }
    
    /**
     * 写入文件
     */
    private void writeFiles(LiteProjectContext projectContext,
                            PsiClass targetClass,
                            Map<String, String> beanMap,
                            Collection<BeanDefinition> beans,
                            ProgressIndicator indicator) {

        // 使用 invokeLater + WriteCommandAction 在主线程中执行写操作，避免死锁
        ApplicationManager.getApplication().invokeLater(() -> {
            WriteCommandAction.runWriteCommandAction(project, () -> {
                try {
                    if (indicator != null) {
                        indicator.setText2("写入Spring XML文件...");
                    }
                    
                    // 1️⃣ 写Spring XML文件
                    new LiteFileWriter(projectContext).write(project, targetClass, beanMap);
                    
                    if (indicator != null) {
                        indicator.setText2("写入bean-classes.txt文件...");
                    }
                    
                    // 2️⃣ 写 bean-classes.txt
                    try {
                        writeBeanClassesFile(beans);
                    } catch (IOException e) {
                        throw new RuntimeException("写入 bean-classes.txt 失败", e);
                    }
                    
                    // 3️⃣ 缓存Bean定义
                    if (targetClass != null) {
                        String cacheKey = "beans_" + targetClass.getQualifiedName();
                        cacheService.cacheBeanDefinitions(project, cacheKey, beans);
                    }
                    
                } catch (Exception e) {
                    LogUtil.error("写入文件失败", e);
                    // 使用新的异常处理机制
                    ExceptionHandler.handle(project, e, "写入文件失败");
                    throw new RuntimeException("写入文件失败", e);
                }
            });
        });
    }
    
    /**
     * 写入bean-classes.txt文件
     */
    private void writeBeanClassesFile(Collection<BeanDefinition> beans) throws IOException {
        Path file = Paths.get(project.getBasePath(), "build/lite/bean-classes.txt");
        Files.createDirectories(file.getParent());
        
        Set<String> classNames = beans.stream()
                .map(BeanDefinition::getClassName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        
        Files.write(file, classNames, StandardCharsets.UTF_8);
        LogUtil.info("写入bean-classes.txt文件完成，数量: {}", classNames.size());
    }
    
    /**
     * 生成缓存键
     */
    private String generateCacheKey(ClassSignatureDTO targetClassDto, MethodSignatureDTO targetMethodDto) {
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append("analysis_");
        keyBuilder.append(targetClassDto.getQualifiedName());
        
        if (targetMethodDto != null) {
            keyBuilder.append("_").append(targetMethodDto.getMethodName());
            keyBuilder.append("_").append(targetMethodDto.getParameterTypes());
        }
        
        // 添加配置相关的哈希，确保配置变更时缓存失效
        String configHash = generateConfigHash();
        keyBuilder.append("_").append(configHash);
        
        return keyBuilder.toString();
    }
    
    /**
     * 生成配置哈希
     */
    private String generateConfigHash() {
        // 简单的配置哈希实现，实际项目中可以使用更复杂的哈希算法
        return String.valueOf(Objects.hash(
            configurationService.getSettings(project).getApiKey(),
            configurationService.getSettings(project).getApiUrl(),
            configurationService.getSettings(project).getModelName()
        ));
    }
    
    /**
     * 清理缓存
     */
    public void clearCache() {
        cacheService.invalidateAllCache(project);
        LogUtil.info("清理所有缓存完成");
    }
    
    /**
     * 获取缓存统计信息
     */
    public CacheService.CacheStatistics getCacheStatistics() {
        return cacheService.getCacheStatistics(project);
    }
    
    /**
     * 获取服务统计信息
     */
    public ServiceContainer.ServiceStatistics getServiceStatistics() {
        return ServiceContainer.getServiceStatistics();
    }
    
    /**
     * 关闭服务
     */
    public void shutdown() {
        try {
            // 持久化缓存
            cacheService.persistCache(project);
            
            // 清理服务容器
            ServiceContainer.cleanup();
            
            LogUtil.info("LiteWorkspaceService关闭完成");
        } catch (Exception e) {
            LogUtil.error("LiteWorkspaceService关闭失败", e);
            // 使用新的异常处理机制（静默处理，因为关闭时的错误通常不需要显示给用户）
            ExceptionHandler.handleSilently(e, "LiteWorkspaceService关闭失败");
        }
    }
}
