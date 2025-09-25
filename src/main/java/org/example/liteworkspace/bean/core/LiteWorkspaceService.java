package org.example.liteworkspace.bean.core;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.example.liteworkspace.bean.core.context.LiteProjectContext;
import org.example.liteworkspace.bean.engine.*;
import org.example.liteworkspace.util.CostUtil;
import org.example.liteworkspace.util.LogUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;

/**
 * Service层：负责完整的扫描、生成、写入和缓存流程
 */
public class LiteWorkspaceService {

    private final Project project;

    public LiteWorkspaceService(Project project) {
        this.project = project;
    }

    /**
     * 核心流程：扫描Bean依赖、生成Spring XML、写入文件、保存缓存
     */
    public void scanAndGenerate(PsiClass targetClass, PsiMethod targetMethod) {
        scanAndGenerate(targetClass, targetMethod, null);
    }

    /**
     * 核心流程：扫描Bean依赖、生成Spring XML、写入文件、保存缓存（带进度指示器）
     */
    public void scanAndGenerate(PsiClass targetClass, PsiMethod targetMethod, ProgressIndicator indicator) {
        Objects.requireNonNull(targetClass, "targetClass不能为空");
        CostUtil.start(targetClass.getQualifiedName());
        LogUtil.info("start scanAndGenerate java bean an xml file");
        
        try {
            // -------------------- Step 1: 初始化项目上下文 --------------------
            indicator.setText2("初始化项目上下文...");
            indicator.setFraction(0.2);
            
            // 使用 runReadAction 确保在正确的线程上下文中创建项目上下文
            final LiteProjectContext[] projectContextHolder = new LiteProjectContext[1];
            ApplicationManager.getApplication().runReadAction(() -> {
                projectContextHolder[0] = new LiteProjectContext(project, targetClass, targetMethod, null);
            });
            LiteProjectContext projectContext = projectContextHolder[0];
            LogUtil.info("complete project context init ");
            
            // -------------------- Step 2: 扫描目标类依赖Bean --------------------
            indicator.setText2("扫描目标类依赖Bean...");
            indicator.setFraction(0.4);
            LiteBeanScanner beanScanner = new LiteBeanScanner(projectContext);
            LogUtil.info("start scanner relation bean list");
            
            // 使用 runReadAction 在后台线程中读取 PSI
            final Collection<BeanDefinition>[] beans = new Collection[]{null};
            ApplicationManager.getApplication().runReadAction(() -> {
                beans[0] = beanScanner.scanAndCollectBeanList(targetClass, project);
            });
            
            Collection<BeanDefinition> beansCollection = beans[0];
            LogUtil.info("end scanner relation bean list,size:{}", beansCollection.size());
            
            // -------------------- Step 3: 生成Spring XML --------------------
            indicator.setText2("生成Spring XML配置...");
            indicator.setFraction(0.6);
            SpringXmlBuilder xmlBuilder = new SpringXmlBuilder(projectContext);
            LogUtil.info("start build spring xml config");
            Map<String, String> beanMap = xmlBuilder.buildXmlMap(beansCollection);
            LogUtil.info("end build spring xml config,size:{}", beanMap.size());
            
            // -------------------- Step 4: 写入文件（Psi / 本地文件） --------------------
            indicator.setText2("写入文件...");
            indicator.setFraction(0.8);
            writeFiles(projectContext, targetClass, beanMap, beansCollection, indicator);
            
            indicator.setText2("完成");
            indicator.setFraction(1.0);
            LogUtil.info("end write xml file,cost:{} s", CostUtil.end(targetClass.getQualifiedName()) / 1000);
        } catch (Exception e) {
            LogUtil.error("scanAndGenerate error", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 内部方法：封装写文件和缓存保存逻辑
     */
    private void writeFiles(LiteProjectContext projectContext,
                            PsiClass targetClass,
                            Map<String, String> beanMap,
                            Collection<BeanDefinition> beans) {
        writeFiles(projectContext, targetClass, beanMap, beans, null);
    }

    /**
     * 内部方法：封装写文件和缓存保存逻辑（带进度指示器）
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
                    Path file = Paths.get(project.getBasePath(), "build/lite/bean-classes.txt");
                    try {
                        Files.createDirectories(file.getParent());
                        Set<String> classNames = beans.stream()
                                .map(BeanDefinition::getClassName)
                                .collect(Collectors.toCollection(LinkedHashSet::new));
                        Files.write(file, classNames, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new RuntimeException("写入 bean-classes.txt 失败", e);
                    }
                    
                    // 3️⃣ 保存缓存（如果需要）
                    // LiteCacheStorage cacheStorage = new LiteCacheStorage(project);
                    // cacheStorage.saveConfigurationClasses(projectContext.getSpringContext().getBean2configuration());
                    // cacheStorage.saveMapperXmlPaths(projectContext.getMyBatisContext().getNamespace2XmlFileMap());
                    // cacheStorage.saveDatasourceConfig(projectContext.getSpringContext().getDatasourceConfig());
                    // cacheStorage.saveSpringScanPackages(projectContext.getSpringContext().getComponentScanPackages());
                    // cacheStorage.saveBeanList(beans);
                    
                } catch (Exception e) {
                    LogUtil.error("writeFiles error", e);
                    throw e;
                }
            });
        });
    }
}

