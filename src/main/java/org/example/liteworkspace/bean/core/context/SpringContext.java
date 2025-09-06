package org.example.liteworkspace.bean.core.context;

import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.example.liteworkspace.bean.engine.SpringConfigurationScanner;
import org.example.liteworkspace.util.LogUtil;
import org.jetbrains.annotations.Nullable;

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
        // 默认：全量搜索
        GlobalSearchScope baseScope = GlobalSearchScope.allScope(project);

        if (packagePrefixes == null || packagePrefixes.isEmpty()) {
            classesToScan.addAll(AllClassesSearch.search(baseScope, project).findAll());
        } else {
            for (String pkgOrJar : packagePrefixes) {
                // 1️⃣ 先尝试当作包名前缀
                PsiPackage psiPackage = JavaPsiFacade.getInstance(project).findPackage(pkgOrJar);
                if (psiPackage != null) {
                    for (PsiDirectory dir : psiPackage.getDirectories(baseScope)) {
                        classesToScan.addAll(List.of(JavaDirectoryService.getInstance().getClasses(dir)));
                        addSubPackageClasses(dir, classesToScan);
                    }
                    continue;
                }

                // 2️⃣ 如果不是包，再尝试匹配 JAR
                VirtualFile jarFile = findJarByName(project, pkgOrJar);
                if (jarFile != null) {
                    GlobalSearchScope jarScope = GlobalSearchScope.filesScope(project, Set.of(jarFile));
                    classesToScan.addAll(AllClassesSearch.search(jarScope, project).findAll());
                }
            }
        }

        // 遍历类，找到 @Configuration + @Bean 方法
        for (PsiClass clazz : classesToScan) {
            if (!hasAnnotation(clazz, "org.springframework.context.annotation.Configuration")) {
                continue;
            }

            for (PsiMethod method : clazz.getMethods()) {
                if (!hasAnnotation(method, "org.springframework.context.annotation.Bean")) {
                    continue;
                }

                PsiType returnType = method.getReturnType();
                if (returnType == null) continue;

                String beanName = getBeanName(method);
                String returnTypeName = returnType.getCanonicalText();

                beanToConfiguration.put(method.getName(), clazz);
                beanToConfiguration.put(returnTypeName, clazz);
                if (!beanName.equals(method.getName())) {
                    beanToConfiguration.put(beanName, clazz);
                }
            }
        }

        return beanToConfiguration;
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

