package org.example.liteworkspace.bean.engine;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import org.example.liteworkspace.bean.core.BeanDefinition;
import org.example.liteworkspace.bean.core.BeanRegistry;
import org.example.liteworkspace.bean.core.LiteProjectContext;
import org.example.liteworkspace.bean.scanner.impl.AnnotationBeanScanner;
import org.example.liteworkspace.bean.scanner.impl.JavaConfigBeanScanner;
import org.example.liteworkspace.bean.scanner.impl.MyBatisMapperScanner;

import java.util.Collection;
import java.util.List;

public class LiteBeanScanner {

    private final LiteProjectContext context;

    public LiteBeanScanner(LiteProjectContext context) {
        this.context = context;
    }

    /**
     * 扫描并收集依赖bean
     * @param rootClass  基础类
     * @return 基础类依赖的bean列表
     */
    public Collection<BeanDefinition> scanAndCollectBeanList(PsiClass rootClass, Project project) {
        BeanRegistry registry = new BeanRegistry();
        BeanScanOrchestrator orchestrator = new BeanScanOrchestrator(context);
        orchestrator.scan(rootClass, registry);

        return registry.getAllBeans();
    }
}

