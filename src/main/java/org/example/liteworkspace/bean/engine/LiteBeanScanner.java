package org.example.liteworkspace.bean.engine;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import org.example.liteworkspace.bean.core.BeanDefinition;
import org.example.liteworkspace.bean.core.BeanRegistry;
import org.example.liteworkspace.bean.core.context.LiteProjectContext;
import org.example.liteworkspace.util.LogUtil;
import org.example.liteworkspace.util.ReadActionUtil;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.*;

public class LiteBeanScanner {

    private final LiteProjectContext context;

    public LiteBeanScanner(LiteProjectContext context) {
        this.context = context;
    }

    /**
     * 扫描并收集依赖bean
     *
     * @param rootClass 基础类
     * @return 基础类依赖的bean列表
     */
    public Collection<BeanDefinition> scanAndCollectBeanList(PsiClass rootClass, Project project) {
        // 使用单线程执行器和队列，避免并发问题
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Set<String> visited = ConcurrentHashMap.newKeySet();
        Set<String> normalDependencies = ConcurrentHashMap.newKeySet();
        BeanRegistry registry = new BeanRegistry();
        try {
            LogUtil.info("scanAndCollectBeanList start");
            // 在ReadAction中执行PSI操作
            ReadActionUtil.runSync(project, () -> {
                // 创建根任务
                BeanScannerTask rootTask = new BeanScannerTask(rootClass, registry, context, visited, normalDependencies);
                rootTask.run();
            });
        } finally {
            // 关闭线程池
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        return registry.getAllBeans();
    }
}
