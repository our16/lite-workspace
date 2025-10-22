package org.example.liteworkspace.bean.core.context;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.concurrency.NonUrgentExecutor;
import org.apache.commons.collections.CollectionUtils;
import org.example.liteworkspace.bean.engine.SpringConfigurationScanner;
import org.example.liteworkspace.dto.ClassSignatureDTO;
import org.example.liteworkspace.dto.PsiToDtoConverter;
import org.example.liteworkspace.util.LogUtil;
import org.example.liteworkspace.util.MyPsiClassUtil;
import org.jetbrains.concurrency.CancellablePromise;

import java.util.*;

public class SpringContext {
    private final Set<String> componentScanPackages = new HashSet<>();
    private final Set<ClassSignatureDTO> configurationClassDtos = new HashSet<>();
    private final Map<String, ClassSignatureDTO> bean2configurationDtos = new HashMap<>();
    private final Project project;

    private final ProgressIndicator indicator;

    public SpringContext(Project project, ProgressIndicator indicator) {
        this.project = project;
        this.indicator = indicator;
    }

    public void refresh(Set<String> miniPackages) {
        LogUtil.info("start refresh SpringContext");

        SpringConfigurationScanner scanner = new SpringConfigurationScanner();
        componentScanPackages.addAll(scanner.scanEffectiveComponentScanPackages(project));

        if (CollectionUtils.isEmpty(miniPackages)) {
            miniPackages = componentScanPackages;
        } else {
            miniPackages.addAll(componentScanPackages);
        }

        LogUtil.info("componentScanPackages：{}", componentScanPackages);

        CancellablePromise<Map<String, ClassSignatureDTO>> promise =
                getConfigurationClassesAsync(miniPackages, indicator);

        promise.onSuccess(configs -> {
            bean2configurationDtos.putAll(configs);
            configurationClassDtos.addAll(configs.values());
            LogUtil.info("configs：{}", configs);
        });

        promise.onError(error -> LogUtil.error("刷新SpringContext失败", error));
    }

    public CancellablePromise<Map<String, ClassSignatureDTO>> getConfigurationClassesAsync(
            Set<String> packagePrefixes, ProgressIndicator indicator) {

        return ReadAction.nonBlocking(() -> {
                    Map<String, ClassSignatureDTO> beanToConfiguration = new HashMap<>();
                    List<String> classesToScanFqns = new ArrayList<>();

                    // Step 1: 扫描包 / JAR
                    for (String pkgOrJar : packagePrefixes) {
                        indicator.checkCanceled();

                        PsiPackage psiPackage = JavaPsiFacade.getInstance(project).findPackage(pkgOrJar);
                        if (psiPackage != null) {
                            GlobalSearchScope packageScope = new PackageScope(psiPackage, true, true);
                            AllClassesSearch.search(packageScope, project).forEach(psiClass -> {
                                String fqName = psiClass.getQualifiedName();
                                if (fqName != null) classesToScanFqns.add(fqName);
                                return true;
                            });
                            continue;
                        }

                        VirtualFile jarFile = findJarByName(project, pkgOrJar);
                        if (jarFile != null) {
                            GlobalSearchScope jarScope = GlobalSearchScope.filesScope(project, Set.of(jarFile));
                            AllClassesSearch.search(jarScope, project).forEach(psiClass -> {
                                String fqName = psiClass.getQualifiedName();
                                if (fqName != null) classesToScanFqns.add(fqName);
                                return true;
                            });
                        }
                    }

                    indicator.checkCanceled();

                    // Step 2: 遍历 FQN -> 转 DTO，避免长期持有 PSI
                    for (String classFqn : classesToScanFqns) {
                        indicator.checkCanceled();
                        PsiClass psiClass = JavaPsiFacade.getInstance(project)
                                .findClass(classFqn, GlobalSearchScope.allScope(project));
                        if (psiClass == null) continue;
                        if (!hasAnnotation(psiClass, "org.springframework.context.annotation.Configuration")) continue;

                        ClassSignatureDTO configDto = PsiToDtoConverter.convertToClassSignature(psiClass);
                        beanToConfiguration.put(classFqn, configDto);

                        for (PsiMethod method : psiClass.getMethods()) {
                            if (!hasAnnotation(method, "org.springframework.context.annotation.Bean")) continue;

                            PsiType returnType = method.getReturnType();
                            if (returnType == null) continue;

                            String beanName = getBeanName(method);
                            String returnTypeName = returnType instanceof PsiClassType ?
                                    Optional.ofNullable(((PsiClassType) returnType).resolve())
                                            .map(PsiClass::getQualifiedName)
                                            .orElse(returnType.getCanonicalText())
                                    : returnType.getCanonicalText();

                            beanToConfiguration.put(beanName, configDto);
                            beanToConfiguration.put(returnTypeName, configDto);

                            // 查找接口或抽象类的实现类
                            resolveInterfaceAndAbstract(psiClass, returnType, returnTypeName, project, beanToConfiguration);
                        }
                    }

                    return beanToConfiguration;
                })
                .inSmartMode(project)
                .expireWith(project)
                .submit(NonUrgentExecutor.getInstance());
    }

    private void resolveInterfaceAndAbstract(PsiClass configClass, PsiType returnType, String returnTypeName,
                                             Project project, Map<String, ClassSignatureDTO> beanToConfiguration) {
        PsiClass returnPsiClass = PsiUtil.resolveClassInType(returnType);
        if (returnPsiClass != null && (returnPsiClass.isInterface() || returnPsiClass.hasModifierProperty(PsiModifier.ABSTRACT))) {
            LogUtil.info("查找 {} 的实现类", returnTypeName);

            List<PsiClass> implementations = findImplementations(returnPsiClass, configClass, project);
            for (PsiClass implClass : implementations) {
                String implClassName = implClass.getQualifiedName();
                if (implClassName != null) {
                    if (!hasSpringBeanAnnotation(implClass)) {
                        beanToConfiguration.put(implClassName, PsiToDtoConverter.convertToClassSignature(configClass));
                    }
                }
            }
        }
    }

    private String getBeanName(PsiMethod method) {
        PsiAnnotation beanAnnotation = method.getAnnotation("org.springframework.context.annotation.Bean");
        if (beanAnnotation == null) return method.getName();

        PsiAnnotationMemberValue valueAttr = beanAnnotation.findAttributeValue("value");
        if (valueAttr == null) valueAttr = beanAnnotation.findAttributeValue("name");

        if (valueAttr instanceof PsiLiteralExpression literal) {
            String value = literal.getValue() instanceof String ? (String) literal.getValue() : null;
            if (value != null && !value.isEmpty()) return value;
        }
        return method.getName();
    }

    private VirtualFile findJarByName(Project project, String jarName) {
        for (var module : ModuleManager.getInstance(project).getModules()) {
            var enumerator = OrderEnumerator.orderEntries(module).withoutSdk().librariesOnly();
            for (VirtualFile root : enumerator.getClassesRoots()) {
                String name = root.getName();
                if (name.equals(jarName) || name.startsWith(jarName)) return root;
            }
        }
        return null;
    }

    private boolean hasAnnotation(PsiModifierListOwner owner, String annotationFqn) {
        PsiModifierList list = owner.getModifierList();
        return list != null && list.findAnnotation(annotationFqn) != null;
    }

    private boolean hasSpringBeanAnnotation(PsiClass psiClass) {
        if (psiClass == null) return false;
        return hasAnnotation(psiClass, "org.springframework.stereotype.Component") ||
                hasAnnotation(psiClass, "org.springframework.stereotype.Service") ||
                hasAnnotation(psiClass, "org.springframework.stereotype.Repository") ||
                hasAnnotation(psiClass, "org.springframework.stereotype.Controller") ||
                hasAnnotation(psiClass, "org.springframework.stereotype.RestController") ||
                hasAnnotation(psiClass, "org.springframework.context.annotation.Configuration") ||
                hasAnnotation(psiClass, "org.apache.ibatis.annotations.Mapper");
    }

    private List<PsiClass> findImplementations(PsiClass interfaceClass, PsiClass configClass, Project project) {
        if (interfaceClass == null || !interfaceClass.isValid()) return Collections.emptyList();

        String packageName = Optional.ofNullable(interfaceClass.getContainingFile())
                .filter(f -> f instanceof PsiClassOwner)
                .map(f -> ((PsiClassOwner) f).getPackageName())
                .orElse("");

        GlobalSearchScope scope = MyPsiClassUtil.createSearchScopeForPackage(project, packageName, true);
        Set<PsiClass> resultSet = new HashSet<>();
        MyPsiClassUtil.findImplementationsInScope(interfaceClass, scope, resultSet);

        List<PsiClass> implementations = new ArrayList<>();
        for (PsiClass impl : resultSet) {
            if (!impl.isInterface() && !impl.isAnnotationType()) implementations.add(impl);
        }
        return implementations;
    }

    public Set<String> getComponentScanPackages() { return componentScanPackages; }
    public Set<ClassSignatureDTO> getConfigurationClassDtos() { return configurationClassDtos; }
    public Map<String, ClassSignatureDTO> getBean2configurationDtos() { return bean2configurationDtos; }
}
