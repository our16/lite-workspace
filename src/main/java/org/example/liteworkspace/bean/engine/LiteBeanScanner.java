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
        ForkJoinPool pool = new ForkJoinPool(10);
        Set<String> visited = ConcurrentHashMap.newKeySet();
        Set<String> normalDependencies = ConcurrentHashMap.newKeySet();
        BeanRegistry registry = new BeanRegistry();

        BeanScannerTask rootTask = new BeanScannerTask(rootClass, registry, context, visited, normalDependencies);

        // 用 Future 接口执行，设置超时
        ForkJoinTask<?> future = pool.submit(rootTask);

        try {
            // 等待最多3分钟
            future.get(3, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            // 超时，可能死锁，取消任务
            future.cancel(true);
            System.err.println("Bean scanning task timed out and was cancelled due to possible deadlock.");
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            pool.shutdownNow();
        }

        return registry.getAllBeans();
    }
}

