package org.example.liteworkspace.bean.core.context;

import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.psi.util.PsiUtil;
import org.apache.commons.collections.CollectionUtils;
import org.example.liteworkspace.bean.engine.SpringConfigurationScanner;
import org.example.liteworkspace.util.LogUtil;
import org.example.liteworkspace.util.MyPsiClassUtil;

import java.util.*;

public class SpringContext {
    private final Set<String> componentScanPackages = new HashSet<>();
    private final Set<PsiClass> configurationClasses = new HashSet<>();
    private final Map<String, PsiClass> bean2configuration = new HashMap<>();
    private final Project project;

    public SpringContext(Project project) {
        this.project = project;
    }

    public void refresh(Set<String> miniPackages) {
        LogUtil.info("miniPackages：{}", miniPackages);
        SpringConfigurationScanner scanner = new SpringConfigurationScanner();
        // 收集配置的bean扫描目录
        componentScanPackages.addAll(scanner.scanEffectiveComponentScanPackages(project));
        if (CollectionUtils.isEmpty(miniPackages)) {
            miniPackages = componentScanPackages;
        } else {
            miniPackages.addAll(componentScanPackages);
        }
        LogUtil.info("componentScanPackages：{}", componentScanPackages);
        //收集 @bean 定义的bean
        Map<String, PsiClass> configs = getConfigurationClasses(miniPackages);
        bean2configuration.putAll(configs);
        configurationClasses.addAll(configs.values());
        LogUtil.info("configs：{}", configs);
    }

    public Map<String, PsiClass> getConfigurationClasses(Set<String> packagePrefixes) {
        Map<String, PsiClass> beanToConfiguration = new HashMap<>();
        Project project = this.project;

        Collection<PsiClass> classesToScan = new ArrayList<>();
        for (String pkgOrJar : packagePrefixes) {
            // 1️⃣ 先尝试当作包名前缀
            PsiPackage psiPackage = JavaPsiFacade.getInstance(project).findPackage(pkgOrJar);
            if (psiPackage != null) {
                // 使用 PackageScope 创建搜索范围，包含库文件
                GlobalSearchScope packageScope = new PackageScope(psiPackage, true, true);
                LogUtil.info("为包 {} 创建 PackageScope，包含库文件", pkgOrJar);
                // 在包范围内搜索所有类
                classesToScan.addAll(AllClassesSearch.search(packageScope, project).findAll());
                continue;
            }

            // 2️⃣ 如果不是包，再尝试匹配 JAR
            VirtualFile jarFile = findJarByName(project, pkgOrJar);
            if (jarFile != null) {
                GlobalSearchScope jarScope = GlobalSearchScope.filesScope(project, Set.of(jarFile));
                LogUtil.info("为 JAR 文件 {} 创建搜索范围", jarFile.getName());
                classesToScan.addAll(AllClassesSearch.search(jarScope, project).findAll());
            }
        }

        LogUtil.info("总共找到 {} 个类需要扫描", classesToScan.size());

        // 遍历类，找到 @Configuration + @Bean 方法
        for (PsiClass configClass : classesToScan) {
            if (!hasAnnotation(configClass, "org.springframework.context.annotation.Configuration")) {
                continue;
            }

            for (PsiMethod method : configClass.getMethods()) {
                if (!hasAnnotation(method, "org.springframework.context.annotation.Bean")) {
                    continue;
                }

                PsiType returnType = method.getReturnType();
                if (returnType == null) {
                    continue;
                }

                String beanName = getBeanName(method);
                String returnTypeName = returnType.getCanonicalText();

                beanToConfiguration.put(method.getName(), configClass);
                beanToConfiguration.put(returnTypeName, configClass);
                if (!beanName.equals(method.getName())) {
                    beanToConfiguration.put(beanName, configClass);
                }

                // 如果返回类型是接口或抽象类，查找实现类
                PsiClass returnPsiClass = PsiUtil.resolveClassInType(returnType);
                if (returnPsiClass != null && (returnPsiClass.isInterface() || returnPsiClass.hasModifierProperty(PsiModifier.ABSTRACT))) {
                    LogUtil.info("查找 {} 的实现类", returnTypeName);
                    List<PsiClass> implementations = findImplementations(returnPsiClass, configClass, project);
                    for (PsiClass implClass : implementations) {
                        String implClassName = implClass.getQualifiedName();
                        if (implClassName != null) {
                            // 检查实现类是否有 Spring Bean 定义注解
                            if (!hasSpringBeanAnnotation(implClass)) {
                                LogUtil.info("实现类 {} 没有 Spring Bean 注解，添加到映射", implClassName);
                                beanToConfiguration.put(implClassName, configClass);
                            } else {
                                LogUtil.info("实现类 {} 有 Spring Bean 注解，跳过", implClassName);
                            }
                        }
                    }
                }
            }
        }

        LogUtil.info("找到 {} 个配置类", beanToConfiguration.size());
        return beanToConfiguration;
    }

    private String resolveActualBeanType(PsiMethod method) {
        PsiType returnType = method.getReturnType();
        if (returnType == null) {
            return null;
        }

        String fallbackType = returnType.getCanonicalText();

        PsiCodeBlock body = method.getBody();
        if (body == null) {
            return fallbackType;
        }

        for (PsiStatement stmt : body.getStatements()) {
            if (stmt instanceof PsiReturnStatement returnStmt) {
                PsiExpression returnExpr = returnStmt.getReturnValue();
                String resolved = resolveExpressionType(returnExpr, new HashSet<>(), 0);
                if (resolved != null) {
                    return resolved;
                }
            }
        }

        return fallbackType;
    }

    /**
     * 递归解析表达式的实际类型
     */
    private String resolveExpressionType(PsiExpression expr, Set<PsiElement> visited, int depth) {
        if (expr == null || depth > 10) { // 避免无限递归
            LogUtil.info("递归超过10层，停止递归");
            return null;
        }
        // 访问过了
        if (!visited.add(expr)) {
            return null;
        }

        // case 1: new XxxImpl(...)
        if (expr instanceof PsiNewExpression newExpr) {
            PsiJavaCodeReferenceElement classRef = newExpr.getClassReference();
            if (classRef != null) {
                PsiElement resolved = classRef.resolve();
                if (resolved instanceof PsiClass implClass) {
                    return implClass.getQualifiedName();
                }
            }
        }

        // case 2: method call
        if (expr instanceof PsiMethodCallExpression callExpr) {
            PsiMethod calledMethod = callExpr.resolveMethod();
            if (calledMethod != null && calledMethod.getBody() != null) {
                return resolveActualBeanType(calledMethod);
            }
            // 如果解析不到方法体，退化为方法返回类型
            PsiType type = callExpr.getType();
            return type != null ? type.getCanonicalText() : null;
        }

        // case 3: reference to variable/field
        if (expr instanceof PsiReferenceExpression refExpr) {
            PsiElement resolved = refExpr.resolve();
            if (resolved instanceof PsiVariable var) {
                PsiExpression initializer = var.getInitializer();
                if (initializer != null) {
                    return resolveExpressionType(initializer, visited, depth + 1);
                }
            }
        }

        // case 4: 三元表达式 a ? b : c
        if (expr instanceof PsiConditionalExpression condExpr) {
            String thenType = resolveExpressionType(condExpr.getThenExpression(), visited, depth + 1);
            if (thenType != null) {
                return thenType;
            }
            return resolveExpressionType(condExpr.getElseExpression(), visited, depth + 1);
        }

        // case 5: 类型直接推断
        PsiType type = expr.getType();
        return type != null ? type.getCanonicalText() : null;
    }


    /**
     * 查找项目依赖中是否存在给定名字的 JAR
     */
    /**
     * 在项目依赖库中查找指定名字的 JAR
     */
    private VirtualFile findJarByName(Project project, String jarName) {
        com.intellij.openapi.module.Module[] modules = ModuleManager.getInstance(project).getModules();
        for (com.intellij.openapi.module.Module module : modules) {
            OrderEnumerator enumerator = OrderEnumerator.orderEntries(module).withoutSdk().librariesOnly();
            for (VirtualFile root : enumerator.getClassesRoots()) {
                // root 可能是 jar://...!/ 这样的路径
                String name = root.getName();
                if (name.equals(jarName) || name.startsWith(jarName)) {
                    return root;
                }
            }
        }
        return null;
    }


    // 递归扫描子包
    private void addSubPackageClasses(PsiDirectory dir, Collection<PsiClass> classes) {
        for (PsiDirectory subDir : dir.getSubdirectories()) {
            classes.addAll(List.of(JavaDirectoryService.getInstance().getClasses(subDir)));
            addSubPackageClasses(subDir, classes);
        }
    }


    /**
     * 判断类或方法是否有指定注解（支持 jar 中的类）
     */
    private boolean hasAnnotation(PsiModifierListOwner owner, String annotationFqn) {
        PsiModifierList list = owner.getModifierList();
        if (list == null) {
            return false;
        }
        return list.findAnnotation(annotationFqn) != null;
    }


    /**
     * 从@Bean注解中提取Bean名称
     */
    private String getBeanName(PsiMethod method) {
        PsiAnnotation beanAnnotation = method.getAnnotation("org.springframework.context.annotation.Bean");
        if (beanAnnotation == null) {
            return method.getName();
        }

        // 查找@Bean的value或name属性
        PsiAnnotationMemberValue valueAttr = beanAnnotation.findAttributeValue("value");
        if (valueAttr == null) {
            valueAttr = beanAnnotation.findAttributeValue("name");
        }

        if (valueAttr instanceof PsiLiteralExpression literal) {
            String value = literal.getValue() instanceof String ? (String) literal.getValue() : null;
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }

        // 默认返回方法名
        return method.getName();
    }

    /**
     * 查找接口或抽象类的所有实现类
     *
     * @param interfaceClass 接口或抽象类
     * @param project        项目对象
     * @return 实现类列表
     */
    private List<PsiClass> findImplementations(PsiClass interfaceClass, PsiClass configClass, Project project) {
        if (interfaceClass == null || !interfaceClass.isValid()) {
            return Collections.emptyList();
        }

        String interfaceQName = interfaceClass.getQualifiedName();
        LogUtil.info("查找接口 {} 的实现类", interfaceQName);
        PsiFile psiFile = interfaceClass.getContainingFile();
        String packageName = null;
        if (psiFile instanceof PsiClassOwner) {
            packageName = ((PsiClassOwner) psiFile).getPackageName();
        }
        if (packageName == null || packageName.isEmpty()) {
            LogUtil.info("无法获取接口 {} 的包名，使用全局范围", interfaceQName);
            packageName = ""; // fallback: 全局搜索
        }
        // 使用 ClassInheritorsSearch 查找所有实现类
        GlobalSearchScope allList = MyPsiClassUtil.createSearchScopeForPackage(project, packageName, true);
        Set<PsiClass> list = new HashSet<>();
        MyPsiClassUtil.findImplementationsInScope(interfaceClass, allList, list);
        // 过滤出真正的实现类（排除接口和注解）
        List<PsiClass> allImplementations = new ArrayList<>();
        for (PsiClass implClass : list) {
            if (!implClass.isInterface() && !implClass.isAnnotationType()) {
                allImplementations.add(implClass);
                LogUtil.info("找到候选实现类: {}", implClass.getQualifiedName());
            }
        }

        // 如果没有配置类或者配置类不在文件中，直接返回所有实现类
        if (interfaceClass.getContainingFile() == null) {
            LogUtil.info("接口 {} 没有包含文件，返回所有 {} 个实现类", interfaceQName, allImplementations.size());
            return allImplementations;
        }

        if (configClass == null) {
            LogUtil.info("接口 {} 没有找到配置类，返回所有 {} 个实现类", interfaceQName, allImplementations.size());
            return allImplementations;
        }

        // 1. 分析配置类的 import 范围
        Set<String> importedClasses = getImportedClasses(configClass);
        LogUtil.info("配置类 {} 导入了 {} 个类", configClass.getQualifiedName(), importedClasses.size());

        // 2. 分析配置类的同包目录
        String configPackage = getPackageName(configClass);
        LogUtil.info("配置类 {} 所在包: {}", configClass.getQualifiedName(), configPackage);

        // 3. 过滤实现类
        List<PsiClass> filteredImplementations = new ArrayList<>();
        for (PsiClass implClass : allImplementations) {
            String implClassName = implClass.getQualifiedName();
            if (implClassName == null) {
                continue;
            }

            // 检查是否在 import 范围内
            boolean isInImportScope = importedClasses.contains(implClassName);

            // 检查是否在同包目录
            boolean isInSamePackage = false;
            String implPackage = getPackageName(implClass);
            if (implPackage != null && implPackage.equals(configPackage)) {
                isInSamePackage = true;
            }

            filteredImplementations.add(implClass);
            if (isInImportScope || isInSamePackage) {
                LogUtil.info("实现类 {} 通过过滤 (import: {}, samePackage: {})",
                        implClassName, isInImportScope, isInSamePackage);
            } else {
                LogUtil.info("实现类 {} 未通过过滤 (import: {}, samePackage: {})",
                        implClassName, isInImportScope, isInSamePackage);
            }
        }

        LogUtil.info("接口 {} 过滤后找到 {} 个实现类", interfaceQName, filteredImplementations.size());
        return filteredImplementations;
    }

    /**
     * 获取类的所有导入类
     *
     * @param psiClass 要分析的类
     * @return 导入类的全限定名集合
     */
    private Set<String> getImportedClasses(PsiClass psiClass) {
        Set<String> importedClasses = new HashSet<>();

        PsiFile containingFile = psiClass.getContainingFile();
        if (containingFile == null) {
            LogUtil.info("获取类 {} 的导入列表为空", psiClass.getQualifiedName());
            return importedClasses;
        }

        // 检查是否是 JAR 文件中的类
        VirtualFile virtualFile = containingFile.getVirtualFile();
        if (!(containingFile instanceof PsiJavaFile)) {
            LogUtil.info("获取类 {} 的导入列表,不是PsiJavaFile", psiClass.getQualifiedName());
            return importedClasses;
        }
        PsiImportList importList = ((PsiJavaFile) containingFile).getImportList();
        if (importList != null) {
            for (PsiImportStatement importStatement : importList.getImportStatements()) {
                String qualifiedName = importStatement.getQualifiedName();
                if (qualifiedName != null) {
                    importedClasses.add(qualifiedName);
                    LogUtil.debug("添加导入类: {}", qualifiedName);
                }
            }
        }

        boolean isJarFile = virtualFile != null && virtualFile.getPath().contains(".jar!");
        LogUtil.info("获取类 {} 的导入列表，是否在 JAR 中: {}", psiClass.getQualifiedName(), isJarFile);

        // 对于 JAR 文件中的类，可能需要额外处理
        if (isJarFile) {
            // 获取包名，同包下的类可以不导入
            String packageName = ((PsiJavaFile) containingFile).getPackageName();
            if (!packageName.isEmpty()) {
                LogUtil.info("JAR 中的类 {} 所在包: {}", psiClass.getQualifiedName(), packageName);
                // 查找同包下的其他类，这些类可以不导入但可以被访问
                Project project = psiClass.getProject();
                PsiPackage psiPackage = JavaPsiFacade.getInstance(project).findPackage(packageName);
                if (psiPackage != null) {
                    GlobalSearchScope packageScope = new PackageScope(psiPackage, false, true);
                    Collection<PsiClass> packageClasses = AllClassesSearch.search(packageScope, project).findAll();

                    for (PsiClass packageClass : packageClasses) {
                        String packageClassName = packageClass.getQualifiedName();
                        if (packageClassName != null && !packageClassName.equals(psiClass.getQualifiedName())) {
                            // 同包下的类添加到导入列表，表示可以访问
                            importedClasses.add(packageClassName);
                            LogUtil.debug("添加同包类 (JAR): {}", packageClassName);
                        }
                    }
                }
            }
        }

        LogUtil.info("类 {} 总共导入 {} 个类", psiClass.getQualifiedName(), importedClasses.size());
        return importedClasses;
    }

    /**
     * 获取类所在的包名
     *
     * @param psiClass 要分析的类
     * @return 包名，如果无法确定则返回 null
     */
    private String getPackageName(PsiClass psiClass) {
        String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName == null) {
            return null;
        }

        int lastDotIndex = qualifiedName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return qualifiedName.substring(0, lastDotIndex);
        }

        return null; // 默认包
    }

    /**
     * 检查类是否有 Spring Bean 定义注解
     *
     * @param psiClass 要检查的类
     * @return 如果有 Spring Bean 注解则返回 true，否则返回 false
     */
    private boolean hasSpringBeanAnnotation(PsiClass psiClass) {
        if (psiClass == null) {
            return false;
        }

        // 检查常见的 Spring Bean 注解
        return hasAnnotation(psiClass, "org.springframework.stereotype.Component") ||
                hasAnnotation(psiClass, "org.springframework.stereotype.Service") ||
                hasAnnotation(psiClass, "org.springframework.stereotype.Repository") ||
                hasAnnotation(psiClass, "org.springframework.stereotype.Controller") ||
                hasAnnotation(psiClass, "org.springframework.stereotype.RestController") ||
                hasAnnotation(psiClass, "org.springframework.context.annotation.Configuration") ||
                hasAnnotation(psiClass, "org.springframework.beans.factory.annotation.Autowired") ||
                hasAnnotation(psiClass, "org.apache.ibatis.annotations.Mapper");
    }

    public Set<String> getComponentScanPackages() {
        return componentScanPackages;
    }

    public Set<PsiClass> getConfigurationClasses() {
        return configurationClasses;
    }

    public Map<String, PsiClass> getBean2configuration() {
        return bean2configuration;
    }
}

