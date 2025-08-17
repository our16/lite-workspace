package org.example.liteworkspace.bean.core;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import org.apache.commons.lang3.time.StopWatch;
import org.example.liteworkspace.bean.core.context.LiteProjectContext;
import org.example.liteworkspace.bean.engine.*;
import org.example.liteworkspace.cache.LiteCacheStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

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
    public void scanAndGenerate(PsiClass targetClass) {
        Objects.requireNonNull(targetClass, "targetClass不能为空");
        // -------------------- Step 1: 收集依赖包 --------------------
        Set<String> miniPackageNames = DependencyCollector.collectAllDependencyPackages(List.of(targetClass));
        // -------------------- Step 2: 初始化项目上下文 --------------------
        LiteProjectContext projectContext = new LiteProjectContext(project, miniPackageNames);
        // -------------------- Step 3: 扫描目标类依赖Bean --------------------
        LiteBeanScanner beanScanner = new LiteBeanScanner(projectContext);
        Collection<BeanDefinition> beans = beanScanner.scanAndCollectBeanList(targetClass, project);
        // -------------------- Step 4: 生成Spring XML --------------------
        SpringXmlBuilder xmlBuilder = new SpringXmlBuilder(projectContext);
        Map<String, String> beanMap = xmlBuilder.buildXmlMap(beans);
        // -------------------- Step 5: 写入文件（Psi / 本地文件） --------------------
        writeFiles(projectContext, targetClass, beanMap, beans);
    }

    /**
     * 内部方法：封装写文件和缓存保存逻辑
     */
    private void writeFiles(LiteProjectContext projectContext,
                            PsiClass targetClass,
                            Map<String, String> beanMap,
                            Collection<BeanDefinition> beans) {

        // 使用IDEA WriteCommandAction保证写入安全
        ApplicationManager.getApplication().invokeLater(() ->
                WriteCommandAction.runWriteCommandAction(project, () -> {

                    // 1️⃣ 写Spring XML文件
                    new LiteFileWriter(projectContext).write(project, targetClass, beanMap);

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

                    // 3️⃣ 保存缓存
                    LiteCacheStorage cacheStorage = new LiteCacheStorage(project);
                    cacheStorage.saveConfigurationClasses(projectContext.getSpringContext().getBean2configuration());
                    cacheStorage.saveMapperXmlPaths(projectContext.getMyBatisContext().getNamespaceMap());
                    cacheStorage.saveDatasourceConfig(projectContext.getSpringContext().getDatasourceConfig());
                    cacheStorage.saveSpringScanPackages(projectContext.getSpringContext().getComponentScanPackages());
                    cacheStorage.saveBeanList(beans);
                })
        );
    }
}

