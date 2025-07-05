package org.example.liteworkspace.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class MyBatisMapperBuilder implements BeanDefinitionBuilder {

    private final Set<String> knownDataSourceClasses;
    private final Set<String> knownSqlSessionFactoryClasses;
    private static final String CUSTOM_XML_CACHE = System.getProperty("user.home") + "/.lite-workspace/custom-xml-path.cache";

    public MyBatisMapperBuilder() {
        Properties props = loadProperties();
        this.knownDataSourceClasses = parseClasses(props.getProperty("datasource.classes"));
        this.knownSqlSessionFactoryClasses = parseClasses(props.getProperty("sqlsessionfactory.classes"));
    }

    @Override
    public boolean supports(PsiClass clazz) {
        return clazz.isInterface() && clazz.getName() != null && clazz.getName().endsWith("Mapper");
    }

    @Override
    public void buildBeanXml(PsiClass clazz, Set<String> visited, Map<String, String> beanMap, XmlBeanAssembler assembler) {
        if (!supports(clazz) || visited.contains(clazz.getQualifiedName())) return;
        visited.add(clazz.getQualifiedName());

        String xmlPath = promptAndCacheXmlPath();
        XmlBeanParser parser = xmlPath != null ? new XmlBeanParser(new File(xmlPath)) : null;

        boolean hasDataSource = (parser != null && parser.containsClass(knownDataSourceClasses)) ||
                beanMap.values().stream().anyMatch(xml -> knownDataSourceClasses.stream().anyMatch(xml::contains));

        boolean hasSessionFactory = (parser != null && parser.containsClass(knownSqlSessionFactoryClasses)) ||
                beanMap.values().stream().anyMatch(xml -> knownSqlSessionFactoryClasses.stream().anyMatch(xml::contains));

        if (!hasDataSource) {
            String dsXml = loadDataSourceFromSpringConfig();
            assembler.putBeanXml("dataSource", dsXml != null ? dsXml : getDefaultDataSourceBean());
        }

        if (!hasSessionFactory) {
            assembler.putBeanXml("sqlSessionFactory", getDefaultSqlSessionFactoryBean());
        }

        String id = decapitalize(clazz.getName());
        String className = clazz.getQualifiedName();

        String mapperBean =
                "    <bean id=\"" + id + "\" class=\"org.mybatis.spring.mapper.MapperFactoryBean\">\n" +
                        "        <property name=\"mapperInterface\" value=\"" + className + "\"/>\n" +
                        "        <property name=\"sqlSessionFactory\" ref=\"sqlSessionFactory\"/>\n" +
                        "    </bean>";

        assembler.putBeanXml(id, mapperBean);
    }

    private String loadDataSourceFromSpringConfig() {
        Project project = ProjectManager.getInstance().getOpenProjects()[0];
        String basePath = project.getBasePath();
        if (basePath == null) return null;
        File resourceDir = new File(basePath, "src/main/resources");
        File yml = new File(resourceDir, "application.yml");
        File props = new File(resourceDir, "application.properties");

        try {
            if (yml.exists()) {
                return parseYamlForDataSource(yml);
            } else if (props.exists()) {
                return parsePropertiesForDataSource(props);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String parseYamlForDataSource(File file) throws IOException {
        List<String> lines = java.nio.file.Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        Map<String, String> map = new HashMap<>();
        for (String line : lines) {
            line = line.trim();
            if (line.contains(":")) {
                String[] kv = line.split(":", 2);
                map.put(kv[0].trim(), kv[1].trim().replaceAll("\"", ""));
            }
        }
        if (map.containsKey("url") && map.containsKey("username")) {
            return buildDataSourceBean(map.get("driver-class-name"), map.get("url"), map.get("username"), map.get("password"));
        }
        return null;
    }

    private String parsePropertiesForDataSource(File file) throws IOException {
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(file)) {
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        String url = props.getProperty("spring.datasource.url");
        String username = props.getProperty("spring.datasource.username");
        String password = props.getProperty("spring.datasource.password");
        String driver = props.getProperty("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");

        if (url != null && username != null) {
            return buildDataSourceBean(driver, url, username, password);
        }
        return null;
    }

    private String buildDataSourceBean(String driver, String url, String username, String password) {
        return "    <bean id=\"dataSource\" class=\"org.apache.commons.dbcp2.BasicDataSource\">\n" +
                "        <property name=\"driverClassName\" value=\"" + driver + "\"/>\n" +
                "        <property name=\"url\" value=\"" + url + "\"/>\n" +
                "        <property name=\"username\" value=\"" + username + "\"/>\n" +
                (password != null ? "        <property name=\"password\" value=\"" + password + "\"/>\n" : "") +
                "    </bean>";
    }

    private String promptAndCacheXmlPath() {
        String cached = loadCachedXmlPath();
        String input = Messages.showInputDialog(
                "请输入自定义 Spring XML 配置文件路径：",
                "MyBatis 配置文件路径",
                Messages.getQuestionIcon(),
                cached,
                null
        );
        if (input != null && !input.trim().isEmpty()) {
            cacheXmlPath(input.trim());
            return input.trim();
        }
        return cached;
    }

    private String loadCachedXmlPath() {
        File file = new File(CUSTOM_XML_CACHE);
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                return reader.readLine();
            } catch (IOException ignored) {
            }
        }
        return "";
    }

    private void cacheXmlPath(String path) {
        try {
            File dir = new File(System.getProperty("user.home"), ".lite-workspace");
            if (!dir.exists()) dir.mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(CUSTOM_XML_CACHE), StandardCharsets.UTF_8)) {
                writer.write(path);
            }
        } catch (IOException ignored) {
        }
    }

    private Properties loadProperties() {
        Properties props = new Properties();

        try (InputStream in = getClass().getClassLoader().getResourceAsStream("datasource.properties")) {
            if (in != null) {
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }

        try {
            File custom = new File(System.getProperty("user.home"), ".lite-workspace/datasource.properties");
            if (custom.exists()) {
                try (InputStream in = new FileInputStream(custom)) {
                    props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                }
            }
        } catch (Exception ignored) {
        }

        return props;
    }

    private Set<String> parseClasses(String raw) {
        if (raw == null || raw.isEmpty()) return Collections.emptySet();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private String getDefaultDataSourceBean() {
        return "    <bean id=\"dataSource\" class=\"org.apache.commons.dbcp2.BasicDataSource\">\n" +
                "        <property name=\"driverClassName\" value=\"com.mysql.cj.jdbc.Driver\"/>\n" +
                "        <property name=\"url\" value=\"jdbc:mysql://localhost:3306/test\"/>\n" +
                "        <property name=\"username\" value=\"root\"/>\n" +
                "        <property name=\"password\" value=\"123456\"/>\n" +
                "    </bean>";
    }

    private String getDefaultSqlSessionFactoryBean() {
        return "    <bean id=\"sqlSessionFactory\" class=\"org.mybatis.spring.SqlSessionFactoryBean\">\n" +
                "        <property name=\"dataSource\" ref=\"dataSource\"/>\n" +
                "    </bean>";
    }

    private String decapitalize(String name) {
        return (name == null || name.isEmpty())
                ? name
                : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
