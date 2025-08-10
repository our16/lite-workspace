package org.example.liteworkspace.bean.engine;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import org.example.liteworkspace.bean.core.BeanDefinition;
import org.example.liteworkspace.bean.core.BeanRegistry;
import org.example.liteworkspace.bean.core.LiteProjectContext;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

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
        // 全局只创建一次 ForkJoinPool，最大并行度 10
        ForkJoinPool pool = new ForkJoinPool(1);
        // 用线程安全集合
        Set<String> visited = ConcurrentHashMap.newKeySet();
        Set<String> normalDependencies = ConcurrentHashMap.newKeySet();
        BeanRegistry registry = new BeanRegistry();
        // 启动任务
        pool.invoke(new BeanScannerTask(rootClass, registry, context, visited, normalDependencies));
        pool.shutdown();
        return registry.getAllBeans();
    }
}

