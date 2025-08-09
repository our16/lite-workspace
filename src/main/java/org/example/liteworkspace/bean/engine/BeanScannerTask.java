package org.example.liteworkspace.bean.engine;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.stubs.StubIndex;
import org.example.liteworkspace.bean.core.BeanDefinition;
import org.example.liteworkspace.bean.core.BeanRegistry;
import org.example.liteworkspace.bean.core.BeanType;
import org.example.liteworkspace.bean.core.LiteProjectContext;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class BeanScannerTask extends RecursiveAction {
    private final PsiClass clazz;
    private final BeanRegistry registry;
    private final LiteProjectContext context;
    private final Set<String> visited;
    private final Set<String> normalDependencies;

    public BeanScannerTask(PsiClass clazz, BeanRegistry registry, LiteProjectContext context,
                           Set<String> visited, Set<String> normalDependencies) {
        this.clazz = clazz;
        this.registry = registry;
        this.context = context;
        this.visited = visited;
        this.normalDependencies = normalDependencies;
    }

    @Override
    protected void compute() {
        String qName = clazz.getQualifiedName();
        if (qName == null || !visited.add(qName)) {
            return;
        }

        // 1. 解析当前类的 Bean 类型
        BeanType type = resolveBeanType(clazz);
        if (type != BeanType.PLAIN) {
            String beanId = generateBeanId(clazz);
            registry.register(new BeanDefinition(beanId, qName, type, clazz));
        }

        // 2. 提取当前类引用的所有依赖类
        Set<PsiClass> dependencies = extractDependencies(clazz);
        if (dependencies.isEmpty()) {
            return;
        }

        // 3. 针对每个依赖创建子任务
        List<BeanScannerTask> subTasks = new ArrayList<>();
        Map<String, PsiClass> bean2Configuration = context.getBean2configuration();

        for (PsiClass dependency : dependencies) {
            String depQName = dependency.getQualifiedName();
            if (depQName == null) {
                continue;
            }

            BeanType depType = resolveBeanType(dependency);
            if (depType != BeanType.PLAIN) {
                // 如果依赖本身也是一个 Bean，则递归扫描
                subTasks.add(new BeanScannerTask(dependency, registry, context, visited, normalDependencies));
            } else if (dependency.isInterface()) {
                // 查找接口的所有实现类
                List<PsiClass> implementations = findImplementations(dependency);
                for (PsiClass impl : implementations) {
                    subTasks.add(new BeanScannerTask(impl, registry, context, visited, normalDependencies));
                }
            } else if (bean2Configuration.containsKey(depQName)) {
                // 扫描对应的 configuration 类
                PsiClass relateConfiguration = bean2Configuration.get(depQName);
                subTasks.add(new BeanScannerTask(relateConfiguration, registry, context, visited, normalDependencies));
                normalDependencies.add(depQName);
            } else {
                // 普通依赖
                normalDependencies.add(depQName);
            }
        }

        // 4. 并发执行所有子任务
        invokeAll(subTasks);
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
     * 查找接口的所有实现类
     */
    private List<PsiClass> findImplementations(PsiClass interfaceClass) {
        List<PsiClass> implementations = new ArrayList<>();
        String interfaceQName = interfaceClass.getQualifiedName();
        if (interfaceQName == null) {
            return implementations;
        }

        // 方法1：使用ClassInheritorsSearch（推荐）
        implementations.addAll(ClassInheritorsSearch.search(interfaceClass).findAll());
        Project project = this.context.getProject();
        // 方法2：使用JavaPsiFacade查找类（备用）
        PsiClass[] implementers = JavaPsiFacade.getInstance(project)
                .findClasses(interfaceQName, GlobalSearchScope.allScope(project));
        implementations.addAll(Arrays.asList(implementers));

        // 方法3：使用StubIndex查找（更底层的方式）
        StubIndex.getInstance().processElements(
                JavaFullClassNameIndex.getInstance().getKey(),
                interfaceQName,
                project,
                GlobalSearchScope.allScope(project),
                PsiClass.class,
                psiClass -> {
                    if (!psiClass.isInterface() && !psiClass.isAnnotationType()) {
                        PsiClassType[] implementsTypes = psiClass.getImplementsListTypes();
                        for (PsiClassType type : implementsTypes) {
                            PsiClass resolved = type.resolve();
                            if (resolved != null && interfaceQName.equals(resolved.getQualifiedName())) {
                                implementations.add(psiClass);
                                break;
                            }
                        }
                    }
                    return true;
                }
        );

        // 去重并返回
        return implementations.stream()
                .distinct()
                .collect(Collectors.toList());
    }

}
