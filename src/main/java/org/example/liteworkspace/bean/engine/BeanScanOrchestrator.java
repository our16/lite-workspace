package org.example.liteworkspace.bean.engine;

import com.intellij.psi.*;
import org.example.liteworkspace.bean.core.BeanDefinition;
import org.example.liteworkspace.bean.core.BeanRegistry;
import org.example.liteworkspace.bean.core.BeanType;
import org.example.liteworkspace.bean.core.LiteProjectContext;
import org.example.liteworkspace.bean.scanner.BeanScanner;
import org.example.liteworkspace.util.MyBatisXmlFinder;

import java.util.*;

public class BeanScanOrchestrator {

    private final List<BeanScanner> scanners;
    private final Set<String> visited = new HashSet<>();
    private final MyBatisXmlFinder mybatisContext;
    private final Map<BeanType, BeanScanner> scannerMap = new HashMap<>();

    public BeanScanOrchestrator(List<BeanScanner> scanners, LiteProjectContext context) {
        this.scanners = scanners;
        this.mybatisContext = context.getMybatisContext();
        for (BeanScanner scanner : scanners) {
            for (BeanType beanType : scanner.supportedType()) {
                scannerMap.put(beanType, scanner);
            }
        }
    }

    public void scan(PsiClass clazz, BeanRegistry registry) {
        String qName = clazz.getQualifiedName();
        if (qName == null || visited.contains(qName)) {
            return;
        }
        visited.add(qName);

        BeanType type = resolveBeanType(clazz);
        if (type != null) {
            String id = decapitalize(clazz.getName());
            registry.register(new BeanDefinition(id, qName, type, clazz));
        }

        // 2. 分发给各个扫描器提取“依赖类”
        for (BeanScanner scanner : scanners) {
            Set<PsiClass> dependencies = scanner.collectDependencies(clazz);
            for (PsiClass dep : dependencies) {
                scan(dep, registry); // 递归扫描依赖类
            }
        }
    }

    private BeanType resolveBeanType(PsiClass clazz) {
        // 1. 显式 @Configuration 配置类
        if (clazz.hasAnnotation("org.springframework.context.annotation.Configuration")) {
            return BeanType.JAVA_CONFIG;
        }

        // 2. @Component/@Service/@Repository 等注解
        if (clazz.hasAnnotation("org.springframework.stereotype.Component") ||
                clazz.hasAnnotation("org.springframework.stereotype.Service") ||
                clazz.hasAnnotation("org.springframework.stereotype.Repository")) {
            return BeanType.ANNOTATION;
        }

        // 4. Mapper 接口（XML 形式）,优先xml，支持混合模式
        if (clazz.isInterface() && mybatisContext.hasMatchingMapperXml(clazz)) {
            return BeanType.MYBATIS;
        }

        // 3. MyBatis @Mapper 判定
        if (clazz.hasAnnotation("org.apache.ibatis.annotations.Mapper") ||
                clazz.getName() != null && clazz.getName().endsWith("Mapper") ||
                clazz.getName() != null && clazz.getName().endsWith("Dao")) {
            boolean hasMethodLevelSqlAnnotation = false;
            for (PsiMethod method : clazz.getMethods()) {
                if (hasMyBatisSqlAnnotation(method)) {
                    hasMethodLevelSqlAnnotation = true;
                    break;
                }
            }

            return hasMethodLevelSqlAnnotation ? BeanType.MAPPER : BeanType.MYBATIS;
        }
        return null;
    }

    private boolean hasMyBatisSqlAnnotation(PsiMethod method) {
        return method.hasAnnotation("org.apache.ibatis.annotations.Select") ||
                method.hasAnnotation("org.apache.ibatis.annotations.Insert") ||
                method.hasAnnotation("org.apache.ibatis.annotations.Update") ||
                method.hasAnnotation("org.apache.ibatis.annotations.Delete");
    }

    private String decapitalize(String name) {
        return name == null || name.isEmpty() ? name :
                Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
