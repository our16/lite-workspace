package org.example.liteworkspace.bean.core.context;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.example.liteworkspace.bean.core.DatasourceConfig;
import org.example.liteworkspace.bean.core.enums.BuildToolType;
import org.example.liteworkspace.cache.CacheVersionChecker;
import org.example.liteworkspace.datasource.DataSourceConfigLoader;
import org.example.liteworkspace.datasource.SqlSessionConfig;
import org.example.liteworkspace.dto.ClassSignatureDTO;
import org.example.liteworkspace.dto.MethodSignatureDTO;
import org.example.liteworkspace.dto.PsiToDtoConverter;
import org.example.liteworkspace.util.LogUtil;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class LiteProjectContext {
    private final Project project;
    private final List<Module> modules;
    private final boolean multiModule;
    private final BuildToolType buildToolType;

    /**
     * 目标类的DTO表示
     */
    private final ClassSignatureDTO targetClassDto;

    /**
     * 目标方法的DTO表示
     */
    private final MethodSignatureDTO targetMethodDto;

    /**
     * 数据源配置
     */
    private final DatasourceConfig datasourceConfig;

    /**
     * spring 上下文
     */
    private final SpringContext springContext;

    /**
     * mybatis 上下文
     */
    private final MyBatisContext myBatisContext;
    private final CacheVersionChecker versionChecker = new CacheVersionChecker();
    private final List<SqlSessionConfig> sqlSessionConfigList;

    public LiteProjectContext(Project project, PsiClass targetClass, PsiMethod targetMethod, ProgressIndicator indicator) {
        LogUtil.info("开始初始化 LiteProjectContext, 项目名称: {}", project.getName());
        
        // 将PSI对象转换为DTO，避免长期保存PSI对象
        this.targetClassDto = PsiToDtoConverter.convertToClassSignature(targetClass);
        this.targetMethodDto = PsiToDtoConverter.convertToMethodSignature(targetMethod);
        LogUtil.debug("目标类转换为DTO: {}", targetClassDto);
        LogUtil.debug("目标方法转换为DTO: {}", targetMethodDto);
        
        this.project = project;
        this.modules = Arrays.asList(ModuleManager.getInstance(project).getModules());
        LogUtil.info("检测到模块数量: {}, 是否为多模块项目: {}", modules.size(), modules.size() > 1);
        this.multiModule = modules.size() > 1;
        this.buildToolType = detect(project);
        LogUtil.info("检测到构建工具类型: {}", buildToolType);
        // 数据源配置
        this.datasourceConfig = refreshDatasourceConfig();
        LogUtil.info("datasourceConfig：{}", datasourceConfig);
        // spring 上下下文初始化
        LogUtil.info("开始初始化 Spring 上下文");
        this.springContext = new SpringContext(project, indicator);
        this.springContext.refresh(null);
        LogUtil.info("Spring 上下文初始化完成");
        // 数据源配置初始化
        LogUtil.info("开始加载数据源配置");
        this.sqlSessionConfigList = DataSourceConfigLoader.load(project);
        LogUtil.info("sqlSessionConfigList：{}", sqlSessionConfigList);
        // mybatis 上下文初始化
        LogUtil.info("开始初始化 MyBatis 上下文");
        this.myBatisContext = new MyBatisContext(project, sqlSessionConfigList);
        this.myBatisContext.refresh();
        LogUtil.info("MyBatis 上下文初始化完成, LiteProjectContext 初始化完成");
    }

    public static BuildToolType detect(Project project) {
        LogUtil.debug("开始检测项目构建工具类型, 项目名称: {}", project.getName());
        VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentRoots();
        if (roots == null || roots.length == 0) {
            LogUtil.warn("未找到项目根目录, 返回未知构建工具类型");
            return BuildToolType.UNKNOWN;
        }

        LogUtil.debug("找到 {} 个项目根目录", roots.length);
        for (VirtualFile root : roots) {
            LogUtil.debug("检查根目录: {}", root.getPath());
            if (root.findChild("pom.xml") != null) {
                LogUtil.info("检测到 Maven 项目 (pom.xml)");
                return BuildToolType.MAVEN;
            }
            if (root.findChild("build.gradle") != null) {
                LogUtil.info("检测到 Gradle 项目 (build.gradle)");
                return BuildToolType.GRADLE;
            }
            if (root.findChild("build.gradle.kts") != null) {
                LogUtil.info("检测到 Gradle Kotlin 项目 (build.gradle.kts)");
                return BuildToolType.GRADLE;
            }
        }
        LogUtil.warn("未检测到支持的构建工具, 返回未知类型");
        return BuildToolType.UNKNOWN;
    }

    /**
     * 只读取 test/resource/configs/testDatasource.xml 的数据库配置，
     * 如果文件不存在，则返回默认配置
     *
     * @return Map<String, String> 数据源配置
     */
    public DatasourceConfig refreshDatasourceConfig() {
        LogUtil.info("开始刷新数据源配置");
        // 1. 优先检查是否有指定的测试数据源文件
        String configFile = findTestDatasourceXml(project);
        if (configFile != null ) {
            LogUtil.info("找到测试数据源配置文件: {}, 使用导入配置", configFile);
            // 如果找到指定文件，返回一个特殊标识表示使用导入文件
            return DatasourceConfig.createImportedConfig(configFile);
        }
        // 2. 如果没有找到文件，返回默认配置
        LogUtil.warn("未找到测试数据源配置文件, 使用默认配置");
        return DatasourceConfig.createDefaultConfig(
                "jdbc:mysql://localhost:3306/default_db",
                "root",
                "123456",
                "com.mysql.cj.jdbc.Driver"
        );
    }


    /**
     * 查找多模块项目下的 test/resources/configs/datasource.xml 文件
     */
    private String findTestDatasourceXml(Project project) {
        LogUtil.debug("开始查找测试数据源配置文件");
        String relativePath = "configs/datasource.xml";

        // 1. 遍历所有模块 Source Roots
        LogUtil.debug("步骤1: 遍历所有模块的 Source Roots");
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            LogUtil.debug("检查模块: {}", module.getName());
            VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
            for (VirtualFile root : sourceRoots) {
                // 只关心 test/resources 目录
                if (root.getPath().contains("test")) {
                    LogUtil.debug("检查测试资源目录: {}", root.getPath());
                    VirtualFile file = root.findFileByRelativePath(relativePath);
                    if (file != null && file.exists() && file.isValid()) {
                        LogUtil.info("在模块 {} 的测试资源目录中找到配置文件: {}", module.getName(), file.getPath());
                        return relativePath;
                    }
                }
            }
        }

        // 2. 全局索引搜索兜底
        LogUtil.debug("步骤2: 使用全局索引搜索配置文件");
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName("datasource.xml", scope);
        LogUtil.debug("通过全局索引找到 {} 个 datasource.xml 文件", files.size());

        for (VirtualFile file : files) {
            if (file.getPath().contains("configs")) {
                LogUtil.info("通过全局索引找到配置文件: {}", file.getPath());
                return relativePath;
            }
        }

        // 3. 类加载器兜底（运行时资源）
        LogUtil.debug("步骤3: 使用类加载器查找配置文件");
        try {
            URL resourceUrl = getClass().getClassLoader()
                    .getResource("configs/datasource.xml");
            if (resourceUrl != null) {
                LogUtil.info("通过类加载器找到配置文件: {}", resourceUrl);
                return relativePath;
            }
        } catch (Exception ignored) {
            LogUtil.debug("使用类加载器查找配置文件时发生异常", ignored);
        }

        LogUtil.debug("未找到任何测试数据源配置文件");
        return null;
    }

    public Project getProject() {
        return project;
    }

    public List<Module> getModules() {
        return modules;
    }

    public boolean isMultiModule() {
        return multiModule;
    }

    public BuildToolType getBuildToolType() { return buildToolType; }
    public SpringContext getSpringContext() { return springContext; }
    public MyBatisContext getMyBatisContext() { return myBatisContext; }
    public CacheVersionChecker getVersionChecker() { return versionChecker; }

    public ClassSignatureDTO getTargetClassDto() {
        return targetClassDto;
    }

    public MethodSignatureDTO getTargetMethodDto() {
        return targetMethodDto;
    }

    /**
     * 根据ClassSignatureDTO查找对应的PsiClass对象
     */
    public PsiClass findTargetClass() {
        if (targetClassDto == null || targetClassDto.getQualifiedName() == null) {
            return null;
        }
        
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        return facade.findClass(targetClassDto.getQualifiedName(), GlobalSearchScope.allScope(project));
    }

    /**
     * 根据MethodSignatureDTO查找对应的PsiMethod对象
     */
    public PsiMethod findTargetMethod() {
        PsiClass targetClass = findTargetClass();
        if (targetClass == null || targetMethodDto == null) {
            return null;
        }
        
        for (PsiMethod method : targetClass.getMethods()) {
            if (targetMethodDto.getMethodName().equals(method.getName())) {
                // 检查参数类型是否匹配
                List<String> dtoParamTypes = targetMethodDto.getParameterTypes();
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
    }

    public List<SqlSessionConfig> getSqlSessionConfigList() {
        return sqlSessionConfigList;
    }

    public DatasourceConfig getDatasourceConfig() {
        return datasourceConfig;
    }
}