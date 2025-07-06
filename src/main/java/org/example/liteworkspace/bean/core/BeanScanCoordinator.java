package org.example.liteworkspace.bean.core;

import com.intellij.psi.PsiClass;
import org.example.liteworkspace.bean.*;

import java.util.List;

public class BeanScanCoordinator {
    private final List<BeanRecognizer> recognizers;
    private final List<BeanDependencyResolver> dependencyResolvers;
    private final List<BeanDefinitionBuilder> builders;

    public BeanScanCoordinator(List<BeanRecognizer> recognizers,
                               List<BeanDependencyResolver> dependencyResolvers,
                               List<BeanDefinitionBuilder> builders) {
        this.recognizers = recognizers;
        this.dependencyResolvers = dependencyResolvers;
        this.builders = builders;
    }

    public void scanAndBuild(PsiClass root, BeanRegistry registry) {
        if (root == null || root.getQualifiedName() == null) return;
        String fqcn = root.getQualifiedName();
        if (registry.isVisited(fqcn)) {
            return;
        }
        registry.markVisited(fqcn);
        // 识别 Bean 并根据来源调用 Builder 构建
        for (BeanRecognizer recognizer : recognizers) {
            if (recognizer.isBean(root)) {
                BeanOrigin origin = recognizer.getOrigin(root);
                PsiClass provider = recognizer.getProviderClass(root);
                if (provider != null) {
                    scanAndBuild(provider, registry); // 先处理提供者类
                }
                invokeBuilder(root, origin, registry);
                break;
            }
        }

        // 如果尚未注册，尝试通过支持类型的 builder 构建（如 MyBatis）
        if (!registry.contains(fqcn)) {
            for (BeanDefinitionBuilder builder : builders) {
                if (builder instanceof SupportAware supportAware && supportAware.supports(root)) {
                    builder.buildBean(root, registry);
                    break;
                }
            }
        }

        // 递归依赖
        for (BeanDependencyResolver depResolver : dependencyResolvers) {
            for (PsiClass dep : depResolver.resolveDependencies(root)) {
                scanAndBuild(dep, registry);
            }
        }
    }

    private void invokeBuilder(PsiClass clazz, BeanOrigin origin, BeanRegistry registry) {
        for (BeanDefinitionBuilder builder : builders) {
            builder.buildBean(clazz, registry);
        }
    }
}
