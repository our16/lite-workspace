package org.example.liteworkspace.bean.engine;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import org.example.liteworkspace.bean.core.BeanDefinition;
import org.example.liteworkspace.bean.core.BeanRegistry;
import org.example.liteworkspace.bean.core.context.LiteProjectContext;

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
        // 使用固定大小的线程池，避免 ForkJoinPool 可能导致的死锁
        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
        Set<String> visited = ConcurrentHashMap.newKeySet();
        Set<String> normalDependencies = ConcurrentHashMap.newKeySet();
        BeanRegistry registry = new BeanRegistry();

        try {
            // 创建根任务
            BeanScannerTask rootTask = new BeanScannerTask(rootClass, registry, context, visited, normalDependencies, executorService);
            // 提交根任务
            Future<?> future = executorService.submit(rootTask);
            
            try {
                // 等待最多3分钟
                future.get(3, TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                // 超时，取消任务
                future.cancel(true);
                System.err.println("Bean scanning task timed out and was cancelled due to possible deadlock.");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
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

