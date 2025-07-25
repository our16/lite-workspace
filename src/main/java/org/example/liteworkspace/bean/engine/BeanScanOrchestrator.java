package org.example.liteworkspace.bean.engine;

import com.intellij.psi.*;
import org.example.liteworkspace.bean.core.BeanDefinition;
import org.example.liteworkspace.bean.core.BeanRegistry;
import org.example.liteworkspace.bean.core.BeanType;
import org.example.liteworkspace.bean.core.LiteProjectContext;

import java.util.*;

public class BeanScanOrchestrator {

    private final LiteProjectContext context;

    // 普通依赖类（非 @Component 等注解的类，但被引用到的类）
    private final Set<String> normalDependencies = new HashSet<>();

    public BeanScanOrchestrator(LiteProjectContext context) {
        this.context = context;
    }

    /**
     * 扫描一个类，如果是 Spring/MyBatis Bean，则注册；并提取它的依赖
     */
    public void scan(PsiClass clazz, BeanRegistry registry) {
        String qName = clazz.getQualifiedName();
        if (qName == null || isVisited(qName)) {
            return;
        }
        markVisited(qName);

        // 1. 解析当前类的 Bean 类型
        BeanType type = resolveBeanType(clazz);
        if (type != BeanType.PLAIN) {
            // 是需要由 Spring/MyBatis 管理的 Bean
            String beanId = generateBeanId(clazz);
            registry.register(new BeanDefinition(beanId, qName, type, clazz));
        }

        // 2. 提取当前类引用的所有依赖类，并分类处理
        Set<PsiClass> dependencies = extractDependencies(clazz);

        // 3. 递归扫描那些需要被注册的依赖（比如它们也可能是 @Service 等）
        for (PsiClass dependency : dependencies) {
            String depQName = dependency.getQualifiedName();
            if (depQName == null) {
                continue;
            }

            BeanType depType = resolveBeanType(dependency);
            if (depType != BeanType.PLAIN) {
                // 如果依赖本身也是一个 Bean，则递归扫描并注册
                scan(dependency, registry);
            } else {
                // 否则认为是普通依赖，只记录 FQCN
                normalDependencies.add(depQName);
            }
        }
    }

    /**
     * 提取当前类中引用到的其他类（字段、未来可扩展构造器 / 方法等）
     */
    private Set<PsiClass> extractDependencies(PsiClass clazz) {
        Set<PsiClass> dependencies = new HashSet<>();

        // 当前只分析字段类型依赖
        for (PsiField field : clazz.getFields()) {
            PsiType fieldType = field.getType();
            PsiClass dependency = resolvePsiClassFromType(fieldType);
            if (dependency != null) {
                dependencies.add(dependency);
            }
        }

        // TODO: 后续可扩展构造器参数、方法参数、返回值等
        return dependencies;
    }

    /**
     * 从 PsiType 解析实际的 PsiClass
     */
    private PsiClass resolvePsiClassFromType(PsiType type) {
        if (type instanceof PsiClassType classType) {
            PsiClass resolved = classType.resolve();
            if (resolved != null && !isJavaLangOrPrimitive(resolved)) {
                return resolved;
            }
        }
        return null;
    }

    /**
     * 判断是否为基础类型或 java.lang 包下的类
     */
    private boolean isJavaLangOrPrimitive(PsiClass psiClass) {
        if (psiClass == null) return true;
        String qualifiedName = psiClass.getQualifiedName();
        return qualifiedName == null ||
                qualifiedName.startsWith("java.lang.") ||
                qualifiedName.equals("int") || qualifiedName.equals("long") ||
                qualifiedName.equals("String") || qualifiedName.equals("boolean") ||
                qualifiedName.equals("void");
    }

    /**
     * 判断一个类是否为需要注册的 Bean 类型
     */
    private BeanType resolveBeanType(PsiClass clazz) {
        if (clazz.hasAnnotation("org.springframework.context.annotation.Configuration")) {
            return BeanType.JAVA_CONFIG;
        }

        if (clazz.hasAnnotation("org.springframework.stereotype.Component") ||
                clazz.hasAnnotation("org.springframework.stereotype.Service") ||
                clazz.hasAnnotation("org.springframework.stereotype.Repository") ||
                clazz.hasAnnotation("org.springframework.stereotype.Controller") ||
                clazz.hasAnnotation("org.springframework.stereotype.RestController")) {
            return BeanType.ANNOTATION;
        }

        //先判断是不是xml mapper, 如果是混用则要返回这个类型
        if (clazz.isInterface() && context.getMybatisContext().hasMatchingMapperXml(clazz)) {
            return BeanType.MYBATIS;
        }

        if (clazz.hasAnnotation("org.apache.ibatis.annotations.Mapper")) {
            return BeanType.MAPPER;
        }

        if (clazz.isInterface() &&
                (clazz.getName() != null &&
                        (clazz.getName().endsWith("Mapper") || clazz.getName().endsWith("Dao")))) {
            return BeanType.MYBATIS;
        }

        return BeanType.PLAIN; // 不是 Spring / MyBatis 管理的 Bean
    }

    private String generateBeanId(PsiClass clazz) {
        String name = clazz.getName();
        return name == null ? "" : decapitalize(name);
    }

    private String decapitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    // 避免重复扫描
    private final Set<String> visited = new HashSet<>();
    private boolean isVisited(String qName) {
        return visited.contains(qName);
    }
    private void markVisited(String qName) {
        visited.add(qName);
    }

    /**
     * 获取所有普通依赖的类（非 Bean，但被引用）
     */
    public Set<String> getNormalDependencies() {
        return normalDependencies;
    }
}