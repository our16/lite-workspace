package org.example.liteworkspace.bean.engine;

import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.example.liteworkspace.bean.core.BeanDefinition;
import org.example.liteworkspace.bean.core.BeanRegistry;
import org.example.liteworkspace.bean.core.enums.BeanType;
import org.example.liteworkspace.bean.core.context.LiteProjectContext;
import org.example.liteworkspace.dto.ClassSignatureDTO;
import org.example.liteworkspace.dto.PsiToDtoConverter;
import org.example.liteworkspace.util.LogUtil;
import org.example.liteworkspace.util.MyPsiClassUtil;
import org.example.liteworkspace.util.ReadActionUtil;

import java.util.*;

public class BeanScannerTask implements Runnable  {
    private final ClassSignatureDTO clazzDto;
    private final BeanRegistry registry;
    private final LiteProjectContext context;
    private final Set<String> visited;
    private final Set<String> normalDependencies;
    private boolean isConfigBean = false;

    public BeanScannerTask(PsiClass clazz, BeanRegistry registry, LiteProjectContext context,
                           Set<String> visited, Set<String> normalDependencies) {
        // 将PSI对象转换为DTO，不长期保存PSI对象
        this.clazzDto = PsiToDtoConverter.convertToClassSignature(clazz);
        this.registry = registry;
        this.context = context;
        this.visited = visited;
        this.normalDependencies = normalDependencies;
    }

    public BeanScannerTask(PsiClass clazz, BeanRegistry registry, LiteProjectContext context,
                           Set<String> visited, Set<String> normalDependencies, boolean isConfigBean) {
        // 将PSI对象转换为DTO，不长期保存PSI对象
        this.clazzDto = PsiToDtoConverter.convertToClassSignature(clazz);
        this.registry = registry;
        this.context = context;
        this.visited = visited;
        this.normalDependencies = normalDependencies;
        this.isConfigBean = isConfigBean;
    }
    
    public BeanScannerTask(ClassSignatureDTO clazzDto, BeanRegistry registry, LiteProjectContext context,
                           Set<String> visited, Set<String> normalDependencies) {
        this.clazzDto = clazzDto;
        this.registry = registry;
        this.context = context;
        this.visited = visited;
        this.normalDependencies = normalDependencies;
    }

    public BeanScannerTask(ClassSignatureDTO clazzDto, BeanRegistry registry, LiteProjectContext context,
                           Set<String> visited, Set<String> normalDependencies, boolean isConfigBean) {
        this.clazzDto = clazzDto;
        this.registry = registry;
        this.context = context;
        this.visited = visited;
        this.normalDependencies = normalDependencies;
        this.isConfigBean = isConfigBean;
    }

    @Override
    public void run() {
        ReadActionUtil.runSync(context.getProject(), ()->{
            LogUtil.info("BeanScannerTask started: {}", clazzDto.getQualifiedName());
            executeTask();
            // 比如 registry.addBean(...);
            LogUtil.info("BeanScannerTask finished");
        });
    }

    private void executeTask() {
        try {
            String qName = clazzDto.getQualifiedName();
            if (qName == null || !visited.add(qName)) {
                return;
            }
            LogUtil.info("开始扫描类: {}", qName);
            
            // 从DTO转换回PSI对象以进行进一步处理
            PsiClass clazz = findPsiClassByDto(clazzDto);
            if (clazz == null) {
                LogUtil.warn("无法找到类: {}", qName);
                return;
            }
            
            // 使用ReadAction包装所有PSI访问
            ReadActionUtil.runSync(context.getProject(), () -> {
                // 1. 解析当前类的 Bean 类型
                BeanType type = resolveBeanType(clazz);
                if (type != BeanType.PLAIN) {
                    String beanId = generateBeanId(clazz);
                    LogUtil.info("发现Bean: {}, 类型: {}, ID: {}", qName, type, beanId);
                    if (BeanType.MAPPER_STRUCT == type) {
                        // mapstruct 生成的类名是原类名加Impl结尾的
                        registry.register(new BeanDefinition(beanId + "Impl", qName + "Impl", type, clazzDto));
                    } else {
                        registry.register(new BeanDefinition(beanId, qName, type, clazzDto));
                    }
                } else if (this.isConfigBean) {
                    LogUtil.info("配置Bean: {}, 需要扫描依赖项", qName);
                } else {
                    // 不是spring或mybatis管理的直接return
                    LogUtil.info("类 {} 不是Spring/MyBatis管理的Bean，跳过扫描", qName);
                    return;
                }

                // 2. 提取当前类引用的所有依赖类
                Set<PsiClass> dependencies = extractDependencies(clazz);
                LogUtil.info("类 {} 发现 {} 个依赖", qName, dependencies.size());
                if (dependencies.isEmpty()) {
                    return;
                }

                // 3. 针对每个依赖创建子任务
                List<BeanScannerTask> subTasks = new ArrayList<>();
                Map<String, ClassSignatureDTO> bean2ConfigurationDtos = context.getSpringContext().getBean2configurationDtos();

                for (PsiClass dependency : dependencies) {
                    String depQName = dependency.getQualifiedName();
                    if (depQName == null) {
                        continue;
                    }
                    LogUtil.info("处理依赖: {}", depQName);
                    // 将依赖类转换为DTO，但不长期保存PSI对象
                    ClassSignatureDTO dependencyDto = PsiToDtoConverter.convertToClassSignature(dependency);
                    LogUtil.debug("依赖类转换为DTO: {}", dependencyDto);
                    
                    BeanType depType = resolveBeanType(dependency);
                    if (depType != BeanType.PLAIN) {
                        // 如果依赖本身也是一个 Bean，则递归扫描
                        LogUtil.info("依赖 {} 是Bean，类型: {}", depQName, depType);
                        // 将依赖类转换为DTO，但不长期保存PSI对象
                        ClassSignatureDTO depDependencyDto = PsiToDtoConverter.convertToClassSignature(dependency);
                        subTasks.add(new BeanScannerTask(depDependencyDto, registry, context, visited, normalDependencies));
                    } else if (dependency.isInterface()) {
                        // 查找接口的所有实现类
                        LogUtil.info("依赖 {} 是接口，查找实现类", depQName);
                        List<PsiClass> implementations = findImplementations(dependency);
                        LogUtil.info("接口 {} 找到 {} 个实现类", depQName, implementations.size());
                        for (PsiClass subClass : implementations) {
                            String subClassQualifiedName = subClass.getQualifiedName();
                            // 将实现类转换为DTO，但不长期保存PSI对象
                            ClassSignatureDTO subClassDto = PsiToDtoConverter.convertToClassSignature(subClass);
                            LogUtil.debug("实现类转换为DTO: {}", subClassDto);
                            
                            if (bean2ConfigurationDtos.containsKey(subClassQualifiedName)) {
                                // 扫描对应的 configuration 类 - 需要从DTO转换回PSI对象进行进一步处理
                                ClassSignatureDTO configDto = bean2ConfigurationDtos.get(subClassQualifiedName);
                                PsiClass relateConfiguration = findPsiClassByDto(configDto);
                                if (relateConfiguration != null) {
                                    LogUtil.info("实现类 {} 有对应的配置类 {}", subClassQualifiedName, relateConfiguration.getQualifiedName());
                                    subTasks.add(new BeanScannerTask(relateConfiguration, registry, context, visited, normalDependencies));
                                }
                                // 自己也加进去是为了找依赖的类
                                subTasks.add(new BeanScannerTask(subClassDto, registry, context, visited, normalDependencies, true));
                            } else {
                                subTasks.add(new BeanScannerTask(subClassDto, registry, context, visited, normalDependencies));
                            }
                        }
                    } else if (bean2ConfigurationDtos.containsKey(depQName)) {
                        // 扫描对应的 configuration 类 - 需要从DTO转换回PSI对象进行进一步处理
                        ClassSignatureDTO configDto = bean2ConfigurationDtos.get(depQName);
                        PsiClass relateConfiguration = findPsiClassByDto(configDto);
                        if (relateConfiguration != null) {
                            LogUtil.info("普通依赖 {} 有对应的配置类 {}", depQName, relateConfiguration.getQualifiedName());
                            subTasks.add(new BeanScannerTask(relateConfiguration, registry, context, visited, normalDependencies));
                        }
                        // 自己也加进去是为了找依赖的类
                        // 将依赖类转换为DTO，但不长期保存PSI对象
                        ClassSignatureDTO configDependencyDto = PsiToDtoConverter.convertToClassSignature(dependency);
                        subTasks.add(new BeanScannerTask(configDependencyDto, registry, context, visited, normalDependencies, true));
                        normalDependencies.add(depQName);
                    } else {
                        // 普通依赖
                        LogUtil.info("添加普通依赖: {}", depQName);
                        normalDependencies.add(depQName);
                    }
                }

                // 4. 使用队列单线程执行子任务，避免死锁
                LogUtil.info("类 {} 扫描完成，创建 {} 个子任务", qName, subTasks.size());
                executeSubTasksWithQueue(subTasks);
            });
        } catch (Exception e) {
            LogUtil.error("扫描类 {} 时发生错误: {}", e, clazzDto.getQualifiedName(), e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * 使用队列单线程执行子任务，避免死锁
     */
    private void executeSubTasksWithQueue(List<BeanScannerTask> subTasks) {
        if (subTasks.isEmpty()) {
            return;
        }
        // 使用队列来管理子任务
        Queue<BeanScannerTask> taskQueue = new LinkedList<>(subTasks);
        while (!taskQueue.isEmpty()) {
            BeanScannerTask task = taskQueue.poll();
            if (task == null) {
                continue;
            }
            try {
                // 确保子任务也在ReadAction中执行
                ReadActionUtil.runSync(context.getProject(), () -> {
                    task.run();
                });
            } catch (Exception e) {
                LogUtil.error("执行子任务时发生错误: {}", e, e.getMessage());
                throw e;
            }
        }
    }

    /**
     * 判断一个类是否为需要注册的 Bean 类型
     */
    private BeanType resolveBeanType(PsiClass clazz) {
        // Spring Boot 主配置类
        if (clazz.hasAnnotation("org.springframework.boot.autoconfigure.SpringBootApplication") ||
            clazz.hasAnnotation("org.springframework.boot.SpringBootConfiguration") ||
            clazz.hasAnnotation("org.springframework.boot.autoconfigure.EnableAutoConfiguration")) {
            return BeanType.JAVA_CONFIG;
        }

        // Spring 配置类
        if (clazz.hasAnnotation("org.springframework.context.annotation.Configuration")) {
            return BeanType.JAVA_CONFIG;
        }

        if (isMapStructSpringModel(clazz)) {
            return BeanType.MAPPER_STRUCT;
        }

        // Spring 核心注解
        if (clazz.hasAnnotation("org.springframework.stereotype.Component") ||
                clazz.hasAnnotation("org.springframework.stereotype.Service") ||
                clazz.hasAnnotation("org.springframework.stereotype.Repository") ||
                clazz.hasAnnotation("org.springframework.stereotype.Controller") ||
                clazz.hasAnnotation("org.springframework.stereotype.RestController") ||
                clazz.hasAnnotation("org.springframework.web.bind.annotation.RestController")) {
            return BeanType.ANNOTATION;
        }

        // Spring Boot 特有注解
        if (clazz.hasAnnotation("org.springframework.web.bind.annotation.RestControllerAdvice") ||
                clazz.hasAnnotation("org.springframework.web.bind.annotation.ControllerAdvice") ||
                clazz.hasAnnotation("org.springframework.web.bind.annotation.Controller") ||
                clazz.hasAnnotation("org.springframework.boot.autoconfigure.condition.ConditionalOnClass") ||
                clazz.hasAnnotation("org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean") ||
                clazz.hasAnnotation("org.springframework.boot.autoconfigure.condition.ConditionalOnProperty") ||
                clazz.hasAnnotation("org.springframework.boot.context.properties.ConfigurationProperties") ||
                clazz.hasAnnotation("org.springframework.boot.context.properties.EnableConfigurationProperties") ||
                clazz.hasAnnotation("org.springframework.context.annotation.Import") ||
                clazz.hasAnnotation("org.springframework.context.annotation.ComponentScan") ||
                clazz.hasAnnotation("org.springframework.context.annotation.EnableAspectJAutoProxy")) {
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
            // 将当前类转换为DTO，但不长期保存PSI对象
            ClassSignatureDTO currentDto = PsiToDtoConverter.convertToClassSignature(current);
            LogUtil.debug("处理类 {} 的依赖，转换为DTO: {}", current.getQualifiedName(), currentDto);
            
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
                            // 将元素类转换为DTO，但不长期保存PSI对象
                            ClassSignatureDTO elementDto = PsiToDtoConverter.convertToClassSignature(elementClass);
                            LogUtil.debug("集合元素类 {} 转换为DTO: {}", elementClass.getQualifiedName(), elementDto);
                        }
                    }
                } else {
                    dependencies.add(dependency);
                    // 将依赖类转换为DTO，但不长期保存PSI对象
                    ClassSignatureDTO dependencyDto = PsiToDtoConverter.convertToClassSignature(dependency);
                    LogUtil.debug("依赖类 {} 转换为DTO: {}", dependency.getQualifiedName(), dependencyDto);
                }
            }
            // ------------------- 2️⃣ 解析 @Configuration + @Bean -------------------
            // Configuration 有@bean的方法参数也要注入进来
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
                            // 将参数类转换为DTO，但不长期保存PSI对象
                            ClassSignatureDTO paramDto = PsiToDtoConverter.convertToClassSignature(paramClass);
                            LogUtil.debug("方法参数类 {} 转换为DTO: {}", paramClass.getQualifiedName(), paramDto);
                        }
                    }
                    // 方法返回类型（可选，表示容器中暴露的Bean）
                    PsiClass returnClass = resolvePsiClassFromType(method.getReturnType());
                    if (returnClass != null && !isJavaLangOrPrimitive(returnClass)) {
                        dependencies.add(returnClass);
                        // 将返回类转换为DTO，但不长期保存PSI对象
                        ClassSignatureDTO returnDto = PsiToDtoConverter.convertToClassSignature(returnClass);
                        LogUtil.debug("方法返回类 {} 转换为DTO: {}", returnClass.getQualifiedName(), returnDto);
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
        // 检查是否有 Lombok @AllArgsConstructor 注解,有的话所有field都可以交给spring管理
        if (hasLombokAllArgsConstructor(clazz)) {
            return true;
        }

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
        return isVoid(returnType);
    }

    private boolean isVoid(PsiType returnType) {
       return returnType != null && PsiType.VOID.equals(returnType);
    }

    /**
     * 通用注解判断
     */
    private boolean hasAnnotation(PsiModifierListOwner element, String annotationFqn) {
        PsiModifierList modifierList = element.getModifierList();
        return modifierList != null && modifierList.findAnnotation(annotationFqn) != null;
    }

    /**
     * 判断类是否有 Lombok @AllArgsConstructor 注解
     */
    private boolean hasLombokAllArgsConstructor(PsiClass clazz) {
        return hasAnnotation(clazz, "lombok.AllArgsConstructor");
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
        LogUtil.info("查找接口 {} 的实现类", interfaceQName);
        
        // 将接口类转换为DTO，但不长期保存PSI对象
        ClassSignatureDTO interfaceDto = PsiToDtoConverter.convertToClassSignature(interfaceClass);
        LogUtil.debug("接口类转换为DTO: {}", interfaceDto);
        
        Project project = this.context.getProject();

        // 获取Spring上下文中配置的组件扫描包
        Set<String> scanPackages = context.getSpringContext().getComponentScanPackages();
        LogUtil.info("组件扫描包: {}", scanPackages);
        
        Set<PsiClass> allImplementations = new LinkedHashSet<>();
        
        if (scanPackages == null || scanPackages.isEmpty()) {
            // 如果没有配置扫描包，默认扫描所有类
            LogUtil.info("未配置组件扫描包，使用全局搜索范围");
            GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            // 使用全局搜索范围查找实现类
            MyPsiClassUtil.findImplementationsInScope(interfaceClass, scope, allImplementations);
        } else {
            // 针对每个包前缀单独搜索
            for (String scanPackage : scanPackages) {
                LogUtil.info("在包 {} 中搜索实现类", scanPackage);
                GlobalSearchScope packageScope = MyPsiClassUtil.createSearchScopeForPackage(project, scanPackage, true);
                // 在当前包范围内查找实现类
                MyPsiClassUtil.findImplementationsInScope(interfaceClass, packageScope, allImplementations);
            }
        }

        // 过滤出真正的实现类
        List<PsiClass> filteredImplementations = new ArrayList<>();
        for (PsiClass implClass : allImplementations) {
            // 检查是否是接口或注解类型
            if (implClass.isInterface() || implClass.isAnnotationType()) {
                continue;
            }
            
            // 检查是否实现了目标接口
            for (PsiClassType type : implClass.getImplementsListTypes()) {
                PsiClass resolved = type.resolve();
                if (resolved != null && interfaceQName.equals(resolved.getQualifiedName())) {
                    filteredImplementations.add(implClass);
                    // 将实现类转换为DTO，但不长期保存PSI对象
                    ClassSignatureDTO implDto = PsiToDtoConverter.convertToClassSignature(implClass);
                    LogUtil.info("找到实现类: {}, 转换为DTO: {}", implClass.getQualifiedName(), implDto);
                    break;
                }
            }
        }

        LogUtil.info("接口 {} 最终找到 {} 个实现类", interfaceQName, filteredImplementations.size());
        return filteredImplementations;
    }

    /**
     * 根据ClassSignatureDTO查找对应的PsiClass对象
     */
    private PsiClass findPsiClassByDto(ClassSignatureDTO dto) {
        if (dto == null || dto.getQualifiedName() == null) {
            return null;
        }
        
        Project project = context.getProject();
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        return facade.findClass(dto.getQualifiedName(), GlobalSearchScope.allScope(project));
    }
}
