package org.example.liteworkspace.service.impl;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.search.GlobalSearchScope;
import org.example.liteworkspace.bean.core.BeanDefinition;
import org.example.liteworkspace.bean.core.context.LiteProjectContext;
import org.example.liteworkspace.bean.engine.LiteBeanScanner;
import org.example.liteworkspace.dto.ClassSignatureDTO;
import org.example.liteworkspace.dto.MethodSignatureDTO;
import org.example.liteworkspace.service.BeanAnalysisService;
import org.example.liteworkspace.util.CostUtil;
import org.example.liteworkspace.util.LogUtil;
import org.example.liteworkspace.util.ReadActionUtil;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Bean分析服务实现
 */
public class BeanAnalysisServiceImpl implements BeanAnalysisService {
    
    private final Project project;
    
    public BeanAnalysisServiceImpl(Project project) {
        this.project = Objects.requireNonNull(project, "Project cannot be null");
    }
    
    @Override
    public Collection<BeanDefinition> scanBeanDependencies(LiteProjectContext projectContext, PsiClass targetClass) {
        Objects.requireNonNull(projectContext, "ProjectContext cannot be null");
        Objects.requireNonNull(targetClass, "TargetClass cannot be null");
        
        LogUtil.info("开始扫描Bean依赖关系: {}", targetClass.getQualifiedName());
        
        LiteBeanScanner beanScanner = new LiteBeanScanner(projectContext);
        
        return ReadActionUtil.computeAsync(project, () -> {
            return beanScanner.scanAndCollectBeanList(targetClass, project);
        }).join();
    }
    
    @Override
    public CompletableFuture<Collection<BeanDefinition>> scanBeanDependenciesAsync(LiteProjectContext projectContext, PsiClass targetClass) {
        Objects.requireNonNull(projectContext, "ProjectContext cannot be null");
        Objects.requireNonNull(targetClass, "TargetClass cannot be null");
        
        return CompletableFuture.supplyAsync(() -> {
            return scanBeanDependencies(projectContext, targetClass);
        });
    }
    
    @Override
    public BeanAnalysisResult analyzeClassDependencies(Project project, 
                                                      ClassSignatureDTO targetClassDto, 
                                                      MethodSignatureDTO targetMethodDto,
                                                      ProgressIndicator indicator) {
        Objects.requireNonNull(project, "Project cannot be null");
        Objects.requireNonNull(targetClassDto, "TargetClassDto cannot be null");
        Objects.requireNonNull(indicator, "ProgressIndicator cannot be null");
        
        String qualifiedName = targetClassDto.getQualifiedName();
        CostUtil.start(qualifiedName);
        LogUtil.info("开始分析类依赖关系: {}", qualifiedName);
        
        try {
            // 查找目标类
            indicator.setText2("查找目标类: " + qualifiedName);
            indicator.setFraction(0.1);
            
            PsiClass targetClass = findPsiClassByDto(targetClassDto);
            if (targetClass == null) {
                String errorMsg = "无法找到目标类: " + qualifiedName;
                LogUtil.error(errorMsg, new RuntimeException(errorMsg));
                throw new RuntimeException(errorMsg);
            }
            
            LogUtil.info("成功找到目标类: {}", targetClass.getQualifiedName());
            
            // 查找目标方法（如果指定）
            PsiMethod targetMethod = null;
            if (targetMethodDto != null) {
                indicator.setText2("查找目标方法: " + targetMethodDto.getMethodName());
                indicator.setFraction(0.15);
                
                targetMethod = findPsiMethodByDto(targetMethodDto);
                if (targetMethod != null) {
                    LogUtil.info("成功找到目标方法: {}", targetMethod.getName());
                } else {
                    LogUtil.warn("无法找到目标方法: {}，将分析整个类", targetMethodDto.getMethodName());
                }
            }
            
            // 创建项目上下文
            indicator.setText2("初始化项目上下文...");
            indicator.setFraction(0.2);
            
            LiteProjectContext projectContext = createProjectContext(project, targetClass, targetMethod, indicator);
            LogUtil.info("完成项目上下文初始化");
            
            // 扫描Bean依赖
            indicator.setText2("扫描目标类依赖Bean...");
            indicator.setFraction(0.4);
            
            Collection<BeanDefinition> beans = scanBeanDependencies(projectContext, targetClass);
            LogUtil.info("完成Bean依赖扫描，数量: {}", beans.size());
            
            long analysisTime = CostUtil.end(qualifiedName);
            LogUtil.info("完成类依赖分析，耗时: {} ms", analysisTime);
            
            return new BeanAnalysisResult(projectContext, beans, analysisTime);
            
        } catch (ProcessCanceledException e) {
            LogUtil.warn("类依赖分析被用户取消: {}", qualifiedName);
            // 对于ProcessCanceledException，重新抛出而不是包装成RuntimeException
            throw e;
        } catch (Exception e) {
            LogUtil.error("分析类依赖关系失败: " + qualifiedName, e);
            // 打印详细的堆栈信息
            LogUtil.error("详细错误堆栈: {}", e);
            
            // 重新抛出带有详细信息的异常
            String errorMsg = "分析类依赖关系失败: " + qualifiedName + " - " + e.getMessage();
            RuntimeException re = new RuntimeException(errorMsg, e);
            LogUtil.error("抛出异常: {}", re, errorMsg);
            throw re;
        }
    }
    
    @Override
    public LiteProjectContext createProjectContext(Project project, 
                                                  PsiClass targetClass, 
                                                  PsiMethod targetMethod,
                                                  ProgressIndicator indicator) {
        Objects.requireNonNull(project, "Project cannot be null");
        Objects.requireNonNull(targetClass, "TargetClass cannot be null");
        Objects.requireNonNull(indicator, "ProgressIndicator cannot be null");
        
        return ReadActionUtil.computeAsync(project, () -> {
            return new LiteProjectContext(project, targetClass, targetMethod, indicator);
        }).join();
    }
    
    /**
     * 根据ClassSignatureDTO查找对应的PsiClass对象
     * 增强版本：支持多种搜索策略和模糊匹配
     */
    private PsiClass findPsiClassByDto(ClassSignatureDTO dto) {
        if (dto == null || dto.getQualifiedName() == null) {
            return null;
        }
        
        String qualifiedName = dto.getQualifiedName();
        LogUtil.debug("开始查找类: {}", qualifiedName);
        
        return ReadActionUtil.computeAsync(project, () -> {
            JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
            PsiClass foundClass = null;
            
            LogUtil.debug("开始执行类查找");
            
            // 策略1: 使用全局搜索范围查找
            LogUtil.debug("策略1: 全局搜索范围查找类: {}", qualifiedName);
            GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
            LogUtil.debug("全局搜索范围包含库: {}", allScope.isSearchInLibraries());
            foundClass = facade.findClass(qualifiedName, allScope);
            if (foundClass != null) {
                LogUtil.debug("策略1成功找到类: {}", foundClass.getQualifiedName());
                return foundClass;
            } else {
                LogUtil.debug("策略1未找到类，继续尝试其他策略");
            }
            
            // 策略2: 使用项目范围查找（排除库）
            LogUtil.debug("策略2: 项目范围查找类: {}", qualifiedName);
            GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);
            LogUtil.debug("项目搜索范围包含库: {}", projectScope.isSearchInLibraries());
            foundClass = facade.findClass(qualifiedName, projectScope);
            if (foundClass != null) {
                LogUtil.debug("策略2成功找到类: {}", foundClass.getQualifiedName());
                return foundClass;
            } else {
                LogUtil.debug("策略2未找到类，继续尝试其他策略");
            }
            
            // 策略3: 尝试简化类名查找（处理内部类情况）
            String simpleName = dto.getSimpleName();
            LogUtil.debug("策略3: 简化类名查找: {}", simpleName);
            PsiClass[] classesByName = facade.findClasses(simpleName, GlobalSearchScope.allScope(project));
            LogUtil.debug("策略3找到{}个同名类", classesByName.length);
            if (classesByName.length > 0) {
                // 优先选择完全匹配的类
                for (PsiClass psiClass : classesByName) {
                    LogUtil.debug("找到同名类: {}", psiClass.getQualifiedName());
                    if (qualifiedName.equals(psiClass.getQualifiedName())) {
                        LogUtil.debug("策略3成功找到完全匹配的类: {}", psiClass.getQualifiedName());
                        return psiClass;
                    }
                }
                
                // 如果没有完全匹配，返回第一个找到的类
                PsiClass firstMatch = classesByName[0];
                LogUtil.debug("策略3找到部分匹配的类: {} (目标: {})", firstMatch.getQualifiedName(), qualifiedName);
                return firstMatch;
            }
            
            // 策略4: 尝试处理可能的包名变化或别名
            String packageName = dto.getPackageName();
            if (packageName != null && !packageName.isEmpty()) {
                LogUtil.debug("策略4: 包名内查找: 包={}, 类={}", packageName, simpleName);
                // 在指定包内查找
                PsiPackage psiPackage = JavaPsiFacade.getInstance(project).findPackage(packageName);
                if (psiPackage != null) {
                    LogUtil.debug("找到包: {}", psiPackage.getQualifiedName());
                    PsiClass[] packageClasses = psiPackage.getClasses();
                    LogUtil.debug("包内有{}个类", packageClasses.length);
                    for (PsiClass pkgClass : packageClasses) {
                        LogUtil.debug("包内类: {}", pkgClass.getQualifiedName());
                        if (simpleName.equals(pkgClass.getName())) {
                            LogUtil.debug("策略4在包内找到类: {}", pkgClass.getQualifiedName());
                            return pkgClass;
                        }
                    }
                } else {
                    LogUtil.debug("未找到包: {}", packageName);
                }
            }
            
            // 策略5: 模糊匹配 - 查找包含简单类名的所有类
            LogUtil.debug("策略5: 模糊匹配查找类名包含: {}", simpleName);
            try {
                PsiClass[] allClasses = facade.findClasses("*", GlobalSearchScope.allScope(project));
                LogUtil.debug("策略5找到{}个类，开始模糊匹配", allClasses.length);
                int matchCount = 0;
                for (PsiClass psiClass : allClasses) {
                    String className = psiClass.getName();
                    if (simpleName.equals(className)) {
                        LogUtil.debug("策略5模糊匹配找到类: {}", psiClass.getQualifiedName());
                        return psiClass;
                    }
                    if (className != null && className.contains(simpleName)) {
                        matchCount++;
                        if (matchCount <= 5) { // 只记录前5个匹配，避免日志过多
                            LogUtil.debug("策略5部分匹配: {}", psiClass.getQualifiedName());
                        }
                    }
                }
                LogUtil.debug("策略5模糊匹配完成，共{}个部分匹配", matchCount);
            } catch (Exception e) {
                LogUtil.warn("策略5模糊匹配失败: {}", e.getMessage());
            }
            
            // 策略6: 尝试处理可能的编译器生成的类名
            if (qualifiedName.contains("$")) {
                String outerClassName = qualifiedName.substring(0, qualifiedName.lastIndexOf("$"));
                String innerClassName = qualifiedName.substring(qualifiedName.lastIndexOf("$") + 1);
                LogUtil.debug("策略6: 尝试查找外部类: {}, 内部类: {}", outerClassName, innerClassName);
                
                PsiClass outerClass = facade.findClass(outerClassName, GlobalSearchScope.allScope(project));
                if (outerClass != null) {
                    LogUtil.debug("策略6找到外部类: {}", outerClass.getQualifiedName());
                    // 在外部类中查找内部类
                    for (PsiClass innerClass : outerClass.getAllInnerClasses()) {
                        LogUtil.debug("外部类内部类: {}", innerClass.getQualifiedName());
                        if (innerClassName.equals(innerClass.getName())) {
                            LogUtil.debug("策略6找到内部类: {}", innerClass.getQualifiedName());
                            return innerClass;
                        }
                    }
                } else {
                    LogUtil.debug("策略6未找到外部类: {}", outerClassName);
                }
            }
            
            // 策略7: 尝试使用ShortNamesCache查找
            LogUtil.debug("策略7: 使用ShortNamesCache查找: {}", simpleName);
            try {
                // 使用反射调用ShortNamesCache，避免直接依赖
                Object shortNamesCache = Class.forName("com.intellij.psi.search.ShortNamesCache")
                    .getMethod("getInstance", Project.class)
                    .invoke(null, project);
                
                PsiClass[] shortNameClasses = (PsiClass[]) Class.forName("com.intellij.psi.search.ShortNamesCache")
                    .getMethod("getClassesByName", String.class, GlobalSearchScope.class)
                    .invoke(shortNamesCache, simpleName, GlobalSearchScope.allScope(project));
                
                LogUtil.debug("策略7找到{}个类", shortNameClasses.length);
                for (PsiClass psiClass : shortNameClasses) {
                    LogUtil.debug("ShortNamesCache找到类: {}", psiClass.getQualifiedName());
                    if (qualifiedName.equals(psiClass.getQualifiedName())) {
                        LogUtil.debug("策略7成功找到完全匹配的类: {}", psiClass.getQualifiedName());
                        return psiClass;
                    }
                }
            } catch (Exception e) {
                LogUtil.warn("策略7查找失败: {}", e.getMessage());
            }
            
            // 策略8: 尝试分步查找 - 先找包，再找类
            LogUtil.debug("策略8: 分步查找 - 先找包: {}", packageName);
            if (packageName != null && !packageName.isEmpty()) {
                try {
                    String[] packageParts = packageName.split("\\.");
                    StringBuilder currentPackage = new StringBuilder();
                    
                    for (String part : packageParts) {
                        if (currentPackage.length() > 0) {
                            currentPackage.append(".");
                        }
                        currentPackage.append(part);
                        
                        PsiPackage currentPkg = JavaPsiFacade.getInstance(project).findPackage(currentPackage.toString());
                        if (currentPkg != null) {
                            LogUtil.debug("找到中间包: {}", currentPkg.getQualifiedName());
                            PsiClass[] pkgClasses = currentPkg.getClasses();
                            for (PsiClass pkgClass : pkgClasses) {
                                if (simpleName.equals(pkgClass.getName())) {
                                    LogUtil.debug("策略8在中间包找到类: {}", pkgClass.getQualifiedName());
                                    return pkgClass;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    LogUtil.warn("策略8分步查找失败: {}", e.getMessage());
                }
            }
            
            // 策略9: 最后尝试 - 使用PsiManager的findClass
            LogUtil.debug("策略9: 使用PsiManager查找: {}", qualifiedName);
            try {
                com.intellij.psi.PsiManager psiManager = com.intellij.psi.PsiManager.getInstance(project);
                // PsiManager没有findClass方法，这里直接返回null
                LogUtil.debug("策略9: PsiManager不支持直接查找类");
            } catch (Exception e) {
                LogUtil.warn("策略9查找失败: {}", e.getMessage());
            }
            
            // 策略10: Maven多模块项目特殊处理
            LogUtil.debug("策略10: Maven多模块项目查找: {}", qualifiedName);
            try {
                PsiClass mavenResult = findClassInMavenMultiModule(project, qualifiedName, simpleName, packageName);
                if (mavenResult != null) {
                    LogUtil.debug("策略10成功找到类: {}", mavenResult.getQualifiedName());
                    return mavenResult;
                }
            } catch (Exception e) {
                LogUtil.warn("策略10查找失败: {}", e.getMessage());
            }
            
            // 策略11: 遍历所有模块查找
            LogUtil.debug("策略11: 遍历所有模块查找: {}", qualifiedName);
            try {
                PsiClass moduleResult = findClassInAllModules(project, qualifiedName, simpleName);
                if (moduleResult != null) {
                    LogUtil.debug("策略11成功找到类: {}", moduleResult.getQualifiedName());
                    return moduleResult;
                }
            } catch (Exception e) {
                LogUtil.warn("策略11查找失败: {}", e.getMessage());
            }
            
            LogUtil.warn("所有11个查找策略都未能找到类: {}", qualifiedName);
            LogUtil.warn("项目根目录: {}", project.getBasePath());
            LogUtil.warn("项目名称: {}", project.getName());
            
            return null;
        }).join();
    }
    
    /**
     * 根据MethodSignatureDTO查找对应的PsiMethod对象
     */
    private PsiMethod findPsiMethodByDto(MethodSignatureDTO dto) {
        if (dto == null || dto.getMethodName() == null) {
            return null;
        }
        
        return ReadActionUtil.computeAsync(project, () -> {
            // 首先找到包含该方法的类
            String classFqn = dto.getClassFqn();
            String simpleName = classFqn.substring(classFqn.lastIndexOf('.') + 1);
            String packageName = classFqn.substring(0, classFqn.lastIndexOf('.'));
            ClassSignatureDTO classDto = new ClassSignatureDTO(classFqn, simpleName,
                    List.of(), null, false, false, false, packageName);
            PsiClass containingClass = findPsiClassByDto(classDto);
            if (containingClass == null) {
                return null;
            }
            
            // 在类中查找匹配的方法
            for (PsiMethod method : containingClass.getMethods()) {
                if (dto.getMethodName().equals(method.getName())) {
                    // 检查参数类型是否匹配
                    List<String> dtoParamTypes = dto.getParameterTypes();
                    PsiParameter[] methodParams = method.getParameterList().getParameters();
                    
                    if (dtoParamTypes.size() != methodParams.length) {
                        continue;
                    }
                    
                    boolean paramsMatch = true;
                    for (int i = 0; i < dtoParamTypes.size(); i++) {
                        String dtoParamType = dtoParamTypes.get(i);
                        String methodParamType = methodParams[i].getType().getCanonicalText();
                        if (!dtoParamType.equals(methodParamType)) {
                            paramsMatch = false;
                            break;
                        }
                    }
                    
                    if (paramsMatch) {
                        return method;
                    }
                }
            }
            
            return null;
        }).join();
    }
    
    /**
     * 在Maven多模块项目中查找类
     */
    private PsiClass findClassInMavenMultiModule(Project project, String qualifiedName, String simpleName, String packageName) {
        LogUtil.debug("开始Maven多模块项目查找: {}", qualifiedName);
        
        try {
            // 检查是否是Maven项目
            boolean isMavenProject = isMavenProject(project);
            LogUtil.debug("项目是否为Maven项目: {}", isMavenProject);
            
            if (!isMavenProject) {
                LogUtil.debug("非Maven项目，跳过Maven多模块查找");
                return null;
            }
            
            // 获取所有模块
            com.intellij.openapi.module.Module[] modules = com.intellij.openapi.module.ModuleManager.getInstance(project).getModules();
            LogUtil.debug("项目包含{}个模块", modules.length);
            
            for (com.intellij.openapi.module.Module module : modules) {
                LogUtil.debug("查找模块: {}", module.getName());
                
                try {
                    // 在模块范围内查找类
                    GlobalSearchScope moduleScope = GlobalSearchScope.moduleScope(module);
                    JavaPsiFacade moduleFacade = JavaPsiFacade.getInstance(project);
                    
                    PsiClass moduleClass = moduleFacade.findClass(qualifiedName, moduleScope);
                    if (moduleClass != null) {
                        LogUtil.debug("在模块{}中找到类: {}", module.getName(), moduleClass.getQualifiedName());
                        return moduleClass;
                    }
                    
                    // 尝试在模块中查找简单类名
                    PsiClass[] moduleClasses = moduleFacade.findClasses(simpleName, moduleScope);
                    if (moduleClasses.length > 0) {
                        for (PsiClass psiClass : moduleClasses) {
                            if (qualifiedName.equals(psiClass.getQualifiedName())) {
                                LogUtil.debug("在模块{}中找到完全匹配的类: {}", module.getName(), psiClass.getQualifiedName());
                                return psiClass;
                            }
                        }
                        
                        // 如果没有完全匹配，返回第一个
                        PsiClass firstMatch = moduleClasses[0];
                        LogUtil.debug("在模块{}中找到部分匹配的类: {} (目标: {})", module.getName(), firstMatch.getQualifiedName(), qualifiedName);
                        return firstMatch;
                    }
                    
                } catch (Exception e) {
                    LogUtil.warn("在模块{}中查找类失败: {}", module.getName(), e.getMessage());
                }
            }
            
            LogUtil.debug("Maven多模块查找未找到类: {}", qualifiedName);
            return null;
            
        } catch (Exception e) {
            LogUtil.warn("Maven多模块查找失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 在所有模块中查找类
     */
    private PsiClass findClassInAllModules(Project project, String qualifiedName, String simpleName) {
        LogUtil.debug("开始遍历所有模块查找: {}", qualifiedName);
        
        // 从qualifiedName中提取packageName
        String packageName = "";
        int lastDotIndex = qualifiedName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            packageName = qualifiedName.substring(0, lastDotIndex);
        }
        
        try {
            com.intellij.openapi.module.Module[] modules = com.intellij.openapi.module.ModuleManager.getInstance(project).getModules();
            LogUtil.debug("项目包含{}个模块", modules.length);
            
            JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
            
            for (com.intellij.openapi.module.Module module : modules) {
                LogUtil.debug("搜索模块: {}", module.getName());
                
                try {
                    // 创建模块范围
                    GlobalSearchScope moduleScope = GlobalSearchScope.moduleScope(module);
                    LogUtil.debug("模块{}的搜索范围包含库: {}", module.getName(), moduleScope.isSearchInLibraries());
                    
                    // 在模块中查找完全匹配的类
                    PsiClass moduleClass = facade.findClass(qualifiedName, moduleScope);
                    if (moduleClass != null) {
                        LogUtil.debug("在模块{}中找到完全匹配的类: {}", module.getName(), moduleClass.getQualifiedName());
                        return moduleClass;
                    }
                    
                    // 在模块中查找简单类名
                    PsiClass[] moduleClasses = facade.findClasses(simpleName, moduleScope);
                    if (moduleClasses.length > 0) {
                        LogUtil.debug("在模块{}中找到{}个同名类", module.getName(), moduleClasses.length);
                        
                        for (PsiClass psiClass : moduleClasses) {
                            LogUtil.debug("模块{}中的同名类: {}", module.getName(), psiClass.getQualifiedName());
                            if (qualifiedName.equals(psiClass.getQualifiedName())) {
                                LogUtil.debug("在模块{}中找到完全匹配的类: {}", module.getName(), psiClass.getQualifiedName());
                                return psiClass;
                            }
                        }
                        
                        // 如果没有完全匹配，返回第一个
                        PsiClass firstMatch = moduleClasses[0];
                        LogUtil.debug("在模块{}中找到部分匹配的类: {} (目标: {})", module.getName(), firstMatch.getQualifiedName(), qualifiedName);
                        return firstMatch;
                    }
                    
                    // 尝试在模块的包中查找
                    if (packageName != null && !packageName.isEmpty()) {
                        PsiPackage modulePackage = facade.findPackage(packageName);
                        if (modulePackage != null) {
                            PsiClass[] packageClasses = modulePackage.getClasses();
                            for (PsiClass pkgClass : packageClasses) {
                                if (simpleName.equals(pkgClass.getName())) {
                                    LogUtil.debug("在模块{}的包{}中找到类: {}", module.getName(), packageName, pkgClass.getQualifiedName());
                                    return pkgClass;
                                }
                            }
                        }
                    }
                    
                } catch (Exception e) {
                    LogUtil.warn("在模块{}中查找类失败: {}", module.getName(), e.getMessage());
                }
            }
            
            LogUtil.debug("所有模块查找未找到类: {}", qualifiedName);
            return null;
            
        } catch (Exception e) {
            LogUtil.warn("所有模块查找失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 检查项目是否为Maven项目
     */
    private boolean isMavenProject(Project project) {
        try {
            // 检查是否有Maven相关的文件或配置
            String basePath = project.getBasePath();
            if (basePath == null) {
                return false;
            }
            
            // 检查是否有pom.xml文件
            java.io.File pomFile = new java.io.File(basePath, "pom.xml");
            if (pomFile.exists()) {
                LogUtil.debug("发现pom.xml文件，确认为Maven项目");
                return true;
            }
            
            // 检查模块中是否有pom.xml
            com.intellij.openapi.module.Module[] modules = com.intellij.openapi.module.ModuleManager.getInstance(project).getModules();
            for (com.intellij.openapi.module.Module module : modules) {
                String modulePath = module.getModuleFilePath();
                if (modulePath != null) {
                    java.io.File moduleDir = new java.io.File(modulePath).getParentFile();
                    if (moduleDir != null) {
                        java.io.File modulePomFile = new java.io.File(moduleDir, "pom.xml");
                        if (modulePomFile.exists()) {
                            LogUtil.debug("在模块{}中发现pom.xml文件，确认为Maven多模块项目", module.getName());
                            return true;
                        }
                    }
                }
            }
            
            LogUtil.debug("未发现Maven项目特征");
            return false;
            
        } catch (Exception e) {
            LogUtil.warn("检查Maven项目时发生错误: {}", e.getMessage());
            return false;
        }
    }
}
