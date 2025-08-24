package org.example.liteworkspace.bean.engine;

import org.example.liteworkspace.bean.core.BeanDefinition;
import org.example.liteworkspace.bean.core.enums.BeanType;
import org.example.liteworkspace.bean.core.context.LiteProjectContext;
import org.example.liteworkspace.datasource.SqlSessionConfig;
import org.example.liteworkspace.util.MapperMatcher;
import org.example.liteworkspace.util.MybatisBeanDto;

import java.util.*;

public class SpringXmlBuilder {

    private final LiteProjectContext context;

    public SpringXmlBuilder(LiteProjectContext context) {
        this.context = context;
    }

    public Map<String, String> buildXmlMap(Collection<BeanDefinition> beans) {
        Map<String, String> xmlMap = new LinkedHashMap<>();
        // 1. 分组
        Map<BeanType, List<BeanDefinition>> grouped = new EnumMap<>(BeanType.class);
        for (BeanDefinition bean : beans) {
            grouped.computeIfAbsent(bean.getType(), t -> new ArrayList<>()).add(bean);
        }

        // 2. 普通 Bean（Annotation / Java Config）
        buildSimpleBeans(grouped, xmlMap, BeanType.ANNOTATION);

        buildSimpleBeans(grouped, xmlMap, BeanType.MAPPER_STRUCT);
        //
        buildSimpleBeans(grouped, xmlMap, BeanType.JAVA_CONFIG);

        // 3. MAPPER 类型
        buildSimpleBeans(grouped, xmlMap, BeanType.MAPPER);

        // 4. MYBATIS 类型
        buildMyBatisBeans(grouped, xmlMap);

        // 5. 其他（可扩展）
        // ...

        return xmlMap;
    }

    private void buildSimpleBeans(Map<BeanType, List<BeanDefinition>> grouped,
                                  Map<String, String> xmlMap,
                                  BeanType type) {
        List<BeanDefinition> list = grouped.get(type);
        if (list == null) {
            return;
        }
        for (BeanDefinition bean : list) {
            xmlMap.put(bean.getBeanName(), String.format("    <bean id=\"%s\" class=\"%s\"/>",
                    bean.getBeanName(), bean.getClassName()));
        }
    }

    private void buildMyBatisBeans(Map<BeanType, List<BeanDefinition>> grouped,
                                   Map<String, String> xmlMap) {
        List<BeanDefinition> list = grouped.get(BeanType.MYBATIS);
        if (list == null || list.isEmpty()) return;

        Map<String, MybatisBeanDto> namespace2XmlFileMap = context.getMyBatisContext().getNamespace2XmlFileMap();
        if (namespace2XmlFileMap.isEmpty()) {
            return;
        }
        // 相对路径
        Set<String> allMapperXmlRelativePath = new LinkedHashSet<>();
        for (BeanDefinition bean : list) {
            String beanName = bean.getBeanName();
            String daoClassName = bean.getClassName();
            MybatisBeanDto mybatisBeanDto = namespace2XmlFileMap.get(daoClassName);
            if (mybatisBeanDto == null) {
                continue;
            }
            String classpathPath = mybatisBeanDto.getXmlFilePath()
                    .replace("\\", "/")  // 统一使用正斜杠
                    .replaceFirst(".*src/(main|test)/resources/", "")  // 去掉资源目录前缀
                    .replaceFirst("^/", "");  // 去掉可能的前导斜杠
            allMapperXmlRelativePath.add("classpath:" + classpathPath);

            String mapperBean = String.format("""
                        <bean id="%s" class="org.mybatis.spring.mapper.MapperFactoryBean">
                            <property name="mapperInterface" value="%s"/>
                            <property name="sqlSessionFactory" ref="%s"/>
                        </bean>
                    """, beanName, daoClassName, mybatisBeanDto.getSqlSessionFactory());
            xmlMap.put(beanName, mapperBean);
        }

        if (context.getSpringContext().getDatasourceConfig().isImported()) {
            // 使用标准Spring import格式
            String importPath = context.getSpringContext().getDatasourceConfig().getImportPath();
            String relativePath = importPath.replace(context.getProjectContext().getProject().getBasePath() + "/", "");
            xmlMap.put("defaultDatasource",
                    String.format("    <import resource=\"classpath:%s\"/>",
                            relativePath.replace("src/test/resources/", "")));
        } else {
            // 使用模板方式填充默认配置
            xmlMap.putAll(context.getSpringContext().getDatasourceConfig().getDefaultDatasource());
        }

        // 所有sqlSessionFactory配置列表
        List<SqlSessionConfig> sqlSessionConfigList = context.getSqlSessionConfigList();
        for (SqlSessionConfig sessionConfig : sqlSessionConfigList) {
            String beanName = sessionConfig.getName();
            String dataSourceBeanId = sessionConfig.getDataSourceBeanId();
            List<String> mapperLocations = sessionConfig.getMapperLocations();
            // 这个sqlSession 没有配置扫描路径
            if (dataSourceBeanId == null || mapperLocations == null || mapperLocations.isEmpty()) {
                continue;
            }

            List<String> matchedPaths = MapperMatcher
                    .matchMapperPaths(new ArrayList<>(allMapperXmlRelativePath), sessionConfig.getMapperLocations());
            // 这个sqlSession 匹配的mapper,即当前类不依赖这个数据源
            if (matchedPaths.isEmpty()) {
                continue;
            }

            // 构造SqlSessionFactory,把需要扫描的mapper.xml 以classpath的形式定义
            StringBuilder factory = new StringBuilder();
            factory.append(String.format("""
                        <bean id="%s" class="org.mybatis.spring.SqlSessionFactoryBean">
                            <property name="dataSource" ref="%s"/>
                            <property name="mapperLocations">
                                <list>
                    """, beanName, dataSourceBeanId));
            for (String path : matchedPaths) {
                factory.append("            <value>").append(path).append("</value>\n");
            }

            factory.append("""
                                </list>
                            </property>
                        </bean>
                    """);

            xmlMap.put(beanName, factory.toString());
        }
    }
}
