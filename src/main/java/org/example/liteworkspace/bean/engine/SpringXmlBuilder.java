package org.example.liteworkspace.bean.engine;

import org.example.liteworkspace.bean.core.BeanDefinition;
import org.example.liteworkspace.bean.core.enums.BeanType;
import org.example.liteworkspace.bean.core.context.LiteProjectContext;

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

        Set<String> mapperXmlPaths = new LinkedHashSet<>();
        for (BeanDefinition bean : list) {
            String id = bean.getBeanName();
            String className = bean.getClassName();
            String xmlPath = context.getMyBatisContext().getNamespaceMap().get(className);
            if (xmlPath != null) {
                String classpathPath = xmlPath
                        .replace("\\", "/")  // 统一使用正斜杠
                        .replaceFirst(".*src/(main|test)/resources/", "")  // 去掉资源目录前缀
                        .replaceFirst("^/", "");  // 去掉可能的前导斜杠
                mapperXmlPaths.add("classpath:" + classpathPath);
            }

            String mapperBean = String.format("""
                        <bean id="%s" class="org.mybatis.spring.mapper.MapperFactoryBean">
                            <property name="mapperInterface" value="%s"/>
                            <property name="sqlSessionFactory" ref="sqlSessionFactory"/>
                        </bean>
                    """, id, className);
            xmlMap.put(id, mapperBean);
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

        // 添加 sqlSessionFactory，依赖 dataSource
        StringBuilder factory = new StringBuilder();
        factory.append("""
                    <bean id="sqlSessionFactory" class="org.mybatis.spring.SqlSessionFactoryBean">
                        <property name="dataSource" ref="dataSource"/>
                        <property name="mapperLocations">
                            <list>
                """);

        for (String path : mapperXmlPaths) {
            factory.append("            <value>").append(path).append("</value>\n");
        }

        factory.append("""
                            </list>
                        </property>
                    </bean>
                """);

        xmlMap.put("sqlSessionFactory", factory.toString());
    }
}
