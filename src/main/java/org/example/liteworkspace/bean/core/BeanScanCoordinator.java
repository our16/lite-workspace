package org.example.liteworkspace.bean.core;

import com.intellij.psi.PsiClass;
import org.example.liteworkspace.bean.BeanDefinitionBuilder;
import org.example.liteworkspace.bean.BeanDependencyResolver;
import org.example.liteworkspace.bean.BeanDefinitionResolver;
import org.example.liteworkspace.bean.resolver.BeanMethodResolver;

import java.util.List;

public class BeanScanCoordinator {
    private final List<BeanDefinitionResolver> resolvers;
    private final List<BeanDependencyResolver> dependencyResolvers;
    private final List<BeanDefinitionBuilder> builders;

    public BeanScanCoordinator(List<BeanDefinitionResolver> resolvers,
                               List<BeanDependencyResolver> dependencyResolvers,
                               List<BeanDefinitionBuilder> builders) {
        this.resolvers = resolvers;
        this.dependencyResolvers = dependencyResolvers;
        this.builders = builders;
    }

    public void scanAndBuild(PsiClass root, BeanRegistry registry) {
        if (root == null || root.getQualifiedName() == null) return;
        String fqcn = root.getQualifiedName();
        if (registry.isVisited(fqcn)) return;

        registry.markVisited(fqcn);

        for (BeanDefinitionResolver resolver : resolvers) {
            if (resolver.isProvidedByBeanMethod(root)) {
                buildProviderClass(root, registry);
                return;
            }
            if (resolver.isXmlDefined(root)) {
                invokeBuilder(root, BeanOrigin.XML, registry);
                break;
            }
            if (resolver.isBean(root)) {
                invokeBuilder(root, BeanOrigin.ANNOTATION, registry);
                break;
            }
        }

        for (BeanDependencyResolver depResolver : dependencyResolvers) {
            for (PsiClass dep : depResolver.resolveDependencies(root)) {
                scanAndBuild(dep, registry);
            }
        }
    }

    private void buildProviderClass(PsiClass beanClass, BeanRegistry registry) {
        for (BeanDefinitionResolver resolver : resolvers) {
            if (resolver instanceof BeanMethodResolver) {
                PsiClass config = ((BeanMethodResolver) resolver).getProvidingConfiguration(beanClass);
                if (config != null) {
                    scanAndBuild(config, registry);
                }
            }
        }
    }

    private void invokeBuilder(PsiClass clazz, BeanOrigin origin, BeanRegistry registry) {
        for (BeanDefinitionBuilder builder : builders) {
            builder.buildBean(clazz, registry);
        }
    }
}