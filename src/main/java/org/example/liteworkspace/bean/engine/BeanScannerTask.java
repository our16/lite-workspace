package org.example.liteworkspace.bean.engine;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.stubs.StubIndex;
import org.example.liteworkspace.bean.core.BeanDefinition;
import org.example.liteworkspace.bean.core.BeanRegistry;
import org.example.liteworkspace.bean.core.enums.BeanType;
import org.example.liteworkspace.bean.core.context.LiteProjectContext;

import java.util.*;
import java.util.concurrent.*;

public class BeanScannerTask extends RecursiveAction {
    private final PsiClass clazz;
    private final BeanRegistry registry;
    private final LiteProjectContext context;
    private final Set<String> visited;
    private final Set<String> normalDependencies;
    private boolean isConfigBean = false;

    public BeanScannerTask(PsiClass clazz, BeanRegistry registry, LiteProjectContext context,
                           Set<String> visited, Set<String> normalDependencies) {
        this.clazz = clazz;
        this.registry = registry;
        this.context = context;
        this.visited = visited;
        this.normalDependencies = normalDependencies;
    }

    public BeanScannerTask(PsiClass clazz, BeanRegistry registry, LiteProjectContext context,
                           Set<String> visited, Set<String> normalDependencies, boolean isConfigBean) {
        this.clazz = clazz;
        this.registry = registry;
        this.context = context;
        this.visited = visited;
        this.normalDependencies = normalDependencies;
        this.isConfigBean = isConfigBean;
    }

    @Override
    protected void compute() {
        ApplicationManager.getApplication().runReadAction(() -> {
            try {
                String qName = clazz.getQualifiedName();
                if (qName == null || !visited.add(qName)) {
                    return;
                }

                // 1. 解析当前类的 Bean 类型
                BeanType type = resolveBeanType(clazz);
                if (type != BeanType.PLAIN) {
                    String beanId = generateBeanId(clazz);
                    if (BeanType.MAPPER_STRUCT == type) {
                        // mapstruct 生成的类名是原类名加Impl结尾的
                        registry.register(new BeanDefinition(beanId + "Impl", qName + "Impl", type, clazz));
                    } else {
                        registry.register(new BeanDefinition(beanId, qName, type, clazz));
                    }
                } else if (this.isConfigBean) {
                    System.out.println("是isConfigBean，需要扫描依赖项");
                } else {
                    // 不是spring或mybatis管理的直接return
                    return;
                }

                // 2. 提取当前类引用的所有依赖类
                Set<PsiClass> dependencies = extractDependencies(clazz);
                if (dependencies.isEmpty()) {
                    return;
                }

                // 3. 针对每个依赖创建子任务
                List<BeanScannerTask> subTasks = new ArrayList<>();
                Map<String, PsiClass> bean2Configuration = context.getSpringContext().getBean2configuration();

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
                        for (PsiClass subClass : implementations) {
                            String subClassQualifiedName = subClass.getQualifiedName();
                            if (bean2Configuration.containsKey(subClassQualifiedName)) {
                                // 扫描对应的 configuration 类
                                PsiClass relateConfiguration = bean2Configuration.get(subClassQualifiedName);
                                subTasks.add(new BeanScannerTask(relateConfiguration, registry, context, visited, normalDependencies));
                                // 自己也加进去是为了找依赖的类
                                subTasks.add(new BeanScannerTask(subClass, registry, context, visited, normalDependencies, true));
                            } else {
                                subTasks.add(new BeanScannerTask(subClass, registry, context, visited, normalDependencies));
                            }
                        }
                    } else if (bean2Configuration.containsKey(depQName)) {
                        // 扫描对应的 configuration 类
                        PsiClass relateConfiguration = bean2Configuration.get(depQName);
                        subTasks.add(new BeanScannerTask(relateConfiguration, registry, context, visited, normalDependencies));
                        // 自己也加进去是为了找依赖的类
                        subTasks.add(new BeanScannerTask(dependency, registry, context, visited, normalDependencies, true));
                        normalDependencies.add(depQName);
                    } else {
                        // 普通依赖
                        normalDependencies.add(depQName);
                    }
                }

                // 4. 并发执行所有子任务
                invokeAll(subTasks);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 判断一个类是否为需要注册的 Bean 类型
     */
    private BeanType resolveBeanType(PsiClass clazz) {
        if (clazz.hasAnnotation("org.springframework.context.annotation.Configuration")) {
            return BeanType.JAVA_CONFIG;
        }

        if (isMapStructSpringModel(clazz)) {
            return BeanType.MAPPER_STRUCT;
        }

        if (clazz.hasAnnotation("org.springframework.stereotype.Component") ||
                clazz.hasAnnotation("org.springframework.stereotype.Service") ||
                clazz.hasAnnotation("org.springframework.stereotype.Repository") ||
                clazz.hasAnnotation("org.springframework.stereotype.Controller") ||
                clazz.hasAnnotation("org.springframework.stereotype.RestController")) {
            return BeanType.ANNOTATION;
        }

        //先判断是不是xml mapper, 如果是混用则要返回这个类型
        if (clazz.isInterface() && context.getMyBatisContext().hasMatchingMapperXml(clazz)) {
            return BeanType.MYBATIS;
        }

        if (clazz.hasAnnotation("org.apache.ibatis.annotations.Mapper")) {
            return BeanType.MAPPER;
        }

        if (clazz.isInterface() && (clazz.getName() != null &&
                (clazz.getName().endsWith("Mapper") || clazz.getName().endsWith("Dao")))) {
            return BeanType.MYBATIS;
        }

        return BeanType.PLAIN; // 不是 Spring / MyBatis 管理的 Bean
    }

    /**
     * 判断某个 Mapper 类是否是 Spring 管理的 MapStruct Mapper（componentModel = "spring"）
     */
    private boolean isMapStructSpringModel(PsiClass mapperClass) {
        if (mapperClass == null) {
            return false;
        }

        PsiAnnotation mapperAnnotation = Objects.requireNonNull(mapperClass.getModifierList())
                .findAnnotation("org.mapstruct.Mapper");
        if (mapperAnnotation == null) {
            return false;
        }

        PsiAnnotationMemberValue componentModelValue = mapperAnnotation.findDeclaredAttributeValue("componentModel");
        if (componentModelValue == null) {
            // 默认不是spring
            return false;
        }

        String text = componentModelValue.getText();
        // componentModel = "spring" 会是字符串字面量，带引号，例如 "spring"
        if ("\"spring\"".equalsIgnoreCase(text)) {
            return true;
        }

        return false;
    }


    private String generateBeanId(PsiClass clazz) {
        String name = clazz.getName();
        return name == null ? "" : decapitalize(name);
    }

    private String decapitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private Set<PsiClass> extractDependencies(PsiClass clazz) {
        Set<PsiClass> dependencies = new HashSet<>();
        PsiClass current = clazz;
        while (current != null && !"java.lang.Object".equals(current.getQualifiedName())) {
            for (PsiField field : current.getFields()) {
                PsiType type = field.getType();
                PsiClass dependency = resolvePsiClassFromType(type);
                // 不是需要检测的类型 基础类型等
                if (dependency == null || isJavaLangOrPrimitive(dependency)) {
                    continue;
                }
                // 既不是spring 注解 也不是 构造器注入等
                if (!isSpringInjectedMember(field) && !isInjectedViaConstructorOrSetter(clazz, field)) {
                    continue;
                }
                // 是否是 list map 这些构造的
                if (isCollectionOrMap(dependency) && type instanceof PsiClassType classType) {
                    for (PsiType paramType : classType.getParameters()) {
                        PsiClass elementClass = resolvePsiClassFromType(paramType);
                        if (elementClass != null && !isJavaLangOrPrimitive(elementClass)) {
                            dependencies.add(elementClass);
                        }
                    }
                } else {
                    dependencies.add(dependency);
                }
            }
            // ------------------- 2️⃣ 解析 @Configuration + @Bean -------------------
            if (isConfigurationClass(current)) {
                for (PsiMethod method : current.getMethods()) {
                    if (!isBeanMethod(method)) {
                        continue;
                    }
                    // 方法参数依赖
                    for (PsiParameter parameter : method.getParameterList().getParameters()) {
                        PsiClass paramClass = resolvePsiClassFromType(parameter.getType());
                        if (paramClass != null && !isJavaLangOrPrimitive(paramClass)) {
                            dependencies.add(paramClass);
                        }
                    }
                    // 方法返回类型（可选，表示容器中暴露的Bean）
                    PsiClass returnClass = resolvePsiClassFromType(method.getReturnType());
                    if (returnClass != null && !isJavaLangOrPrimitive(returnClass)) {
                        dependencies.add(returnClass);
                    }
                }
            }
            // 获取父类
            current = current.getSuperClass();
        }

        return dependencies;
    }

    // 是否是 @Configuration 类
    private boolean isConfigurationClass(PsiClass clazz) {
        return clazz.getModifierList() != null &&
                clazz.getModifierList().findAnnotation("org.springframework.context.annotation.Configuration") != null;
    }

    // 是否是 @Bean 方法
    private boolean isBeanMethod(PsiMethod method) {
        return method.getModifierList().findAnnotation("org.springframework.context.annotation.Bean") != null;
    }

    /**
     * 判断一个字段是否通过构造器或 Setter 注入（即使没有注解）
     */
    private boolean isInjectedViaConstructorOrSetter(PsiClass clazz, PsiField field) {
        PsiType fieldType = field.getType();
        // 构造器参数匹配
        for (PsiMethod constructor : clazz.getConstructors()) {
            for (PsiParameter param : constructor.getParameterList().getParameters()) {
                if (fieldType.isAssignableFrom(param.getType()) || param.getType().isAssignableFrom(fieldType)) {
                    return true;
                }
            }
        }

        // Setter 方法参数匹配
        for (PsiMethod method : clazz.getMethods()) {
            if (isSetterMethod(method)) {
                PsiParameter[] params = method.getParameterList().getParameters();
                if (params.length == 1 &&
                        (fieldType.isAssignableFrom(params[0].getType()) || params[0].getType().isAssignableFrom(fieldType))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断一个类成员（字段/方法/构造器）是否是 Spring 注入点
     */
    private boolean isSpringInjectedMember(PsiElement element) {
        if (element instanceof PsiField field) {
            // 字段注入：有注解，或者没有注解但按需可放行（一般字段必须有注解才注入）
            return isAnnotatedWithSpringInject(field);
        }
        if (element instanceof PsiMethod method) {
            // 构造器注入（有注解）
            if (method.isConstructor() && isAnnotatedWithSpringInject(method)) {
                return true;
            }
            // 构造器注入（无注解但全参构造器）
            if (method.isConstructor() && isAllArgsConstructor(method)) {
                return true;
            }
            // Setter 注入
            if (isSetterMethod(method) && isAnnotatedWithSpringInject(method)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为全参数构造器（无注解情况）
     */
    private boolean isAllArgsConstructor(PsiMethod constructor) {
        if (!constructor.isConstructor()) return false;
        PsiClass clazz = constructor.getContainingClass();
        if (clazz == null) return false;

        // 获取类的字段（排除 static / final 已初始化的）
        List<PsiField> instanceFields = Arrays.stream(clazz.getFields())
                .filter(f -> !f.hasModifierProperty(PsiModifier.STATIC))
                .filter(f -> !f.hasModifierProperty(PsiModifier.FINAL) || f.getInitializer() == null)
                .toList();

        // 构造器参数数量匹配字段数量，并且类型一一对应
        PsiParameter[] params = constructor.getParameterList().getParameters();
        if (params.length != instanceFields.size()) return false;

        // 判断类型匹配（简单按顺序比对）
        for (int i = 0; i < params.length; i++) {
            PsiType paramType = params[i].getType();
            PsiType fieldType = instanceFields.get(i).getType();
            if (!paramType.isAssignableFrom(fieldType)) {
                return false;
            }
        }
        return true;
    }


    /**
     * 判断字段或方法是否带有 Spring 注入相关注解
     */
    private boolean isAnnotatedWithSpringInject(PsiModifierListOwner element) {
        return hasAnnotation(element, "org.springframework.beans.factory.annotation.Autowired") ||
                hasAnnotation(element, "jakarta.annotation.Resource") ||
                hasAnnotation(element, "javax.annotation.Resource") ||
                hasAnnotation(element, "jakarta.inject.Inject") ||
                hasAnnotation(element, "javax.inject.Inject");
    }

    /**
     * 判断是否是标准 Setter 方法
     */
    private boolean isSetterMethod(PsiMethod method) {
        // 方法名必须以 "set" 开头，且长度至少4（setX）
        String name = method.getName();
        if (name == null || !name.startsWith("set") || name.length() < 4) {
            return false;
        }

        // 必须是 public 方法
        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }

        // 参数数量必须是1
        if (method.getParameterList().getParametersCount() != 1) {
            return false;
        }

        // 返回类型必须是 void
        PsiType returnType = method.getReturnType();
        return returnType != null && returnType.equals(PsiTypes.voidType());
    }

    /**
     * 通用注解判断
     */
    private boolean hasAnnotation(PsiModifierListOwner element, String annotationFqn) {
        PsiModifierList modifierList = element.getModifierList();
        return modifierList != null && modifierList.findAnnotation(annotationFqn) != null;
    }


    /**
     * 判断类是否是 Collection 或 Map
     */
    private boolean isCollectionOrMap(PsiClass psiClass) {
        if (psiClass == null) {
            return false;
        }

        Project project = psiClass.getProject();
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        PsiClass collectionClass = facade.findClass("java.util.Collection", GlobalSearchScope.allScope(project));
        PsiClass mapClass = facade.findClass("java.util.Map", GlobalSearchScope.allScope(project));

        if ((collectionClass != null && psiClass.isInheritor(collectionClass, true))
                || (mapClass != null && psiClass.isInheritor(mapClass, true))) {
            return true;
        }

        String qualifiedName = psiClass.getQualifiedName();
        if ("java.util.Map".equals(qualifiedName)) {
            return true;
        }

        return false;
    }

    /**
     * 从 PsiType 解析实际的 PsiClass
     */
    /**
     * 从 PsiType 解析 PsiClass
     */
    private PsiClass resolvePsiClassFromType(PsiType type) {
        if (type instanceof PsiClassType classType) {
            return classType.resolve();
        } else if (type instanceof PsiArrayType arrayType) {
            return resolvePsiClassFromType(arrayType.getComponentType());
        }
        return null;
    }

    /**
     * 判断是否为基础类型或 java.lang 包下的类
     */
    private boolean isJavaLangOrPrimitive(PsiClass psiClass) {
        if (psiClass == null) {
            return true;
        }
        String qualifiedName = psiClass.getQualifiedName();
        return qualifiedName == null ||
                qualifiedName.startsWith("java.lang.") ||
                qualifiedName.equals("int") || qualifiedName.equals("long") ||
                qualifiedName.equals("String") || qualifiedName.equals("boolean") ||
                qualifiedName.equals("void");
    }

    private List<PsiClass> findImplementations(PsiClass interfaceClass) {
        if (interfaceClass == null || interfaceClass.getQualifiedName() == null) {
            return Collections.emptyList();
        }

        String interfaceQName = interfaceClass.getQualifiedName();
        Project project = this.context.getProjectContext().getProject();

        // -------------------------------
        // 方法1：ClassInheritorsSearch（支持源码 + 库）
        // -------------------------------
        GlobalSearchScope scope = GlobalSearchScope.allScope(project); // 包含模块 + 库（jar）
        Set<PsiClass> implementations = new LinkedHashSet<>(ClassInheritorsSearch.search(interfaceClass, scope, true).findAll());

        // -------------------------------
        // 方法2：JavaPsiFacade查找类（备用）
        // -------------------------------
        PsiClass[] classes = JavaPsiFacade.getInstance(project).findClasses(interfaceQName, scope);
        implementations.addAll(Arrays.asList(classes));

        // -------------------------------
        // 方法3：StubIndex底层扫描（可选，性能稍低）
        // -------------------------------
        StubIndex.getInstance().processElements(
                JavaFullClassNameIndex.getInstance().getKey(),
                interfaceQName,
                project,
                scope,
                PsiClass.class,
                psiClass -> {
                    if (!psiClass.isInterface() && !psiClass.isAnnotationType()) {
                        for (PsiClassType type : psiClass.getImplementsListTypes()) {
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

        return new ArrayList<>(implementations);
    }


}
