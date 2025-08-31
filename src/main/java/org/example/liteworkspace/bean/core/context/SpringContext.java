package org.example.liteworkspace.bean.core.context;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import org.example.liteworkspace.bean.core.DatasourceConfig;
import org.example.liteworkspace.util.ResourceConfigAnalyzer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SpringContext {
    private final ResourceConfigAnalyzer resourceAnalyzer;
    private final Set<String> componentScanPackages = new HashSet<>();
    private DatasourceConfig datasourceConfig = new DatasourceConfig();
    private final Set<PsiClass> configurationClasses = new HashSet<>();
    private final Map<String, PsiClass> bean2configuration = new HashMap<>();
    private final Set<String> providedFqcns = new HashSet<>();

    public SpringContext(Project project) {
        this.resourceAnalyzer = new ResourceConfigAnalyzer(project, "");
    }

    public void refresh(Set<String> miniPackages) {
        componentScanPackages.addAll(resourceAnalyzer.scanComponentScanPackages());
        datasourceConfig = resourceAnalyzer.scanSpringDatasourceConfigs();
        Map<String, PsiClass> configs = resourceAnalyzer.scanConfigurationClasses(miniPackages);
        for (Map.Entry<String, PsiClass> entry : configs.entrySet()) {
            String beanName = entry.getKey();
            PsiClass configClass = entry.getValue();
            bean2configuration.put(beanName, configClass);
            configurationClasses.add(configClass);
        }
    }

    public Set<String> getComponentScanPackages() {
        return componentScanPackages;
    }

    public DatasourceConfig getDatasourceConfig() {
        return datasourceConfig;
    }

    public ResourceConfigAnalyzer getResourceAnalyzer() {
        return resourceAnalyzer;
    }

    public void setDatasourceConfig(DatasourceConfig datasourceConfig) {
        this.datasourceConfig = datasourceConfig;
    }

    public Set<PsiClass> getConfigurationClasses() {
        return configurationClasses;
    }

    public Map<String, PsiClass> getBean2configuration() {
        return bean2configuration;
    }

    public Set<String> getProvidedFqcns() {
        return providedFqcns;
    }
}

