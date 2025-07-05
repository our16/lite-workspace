package org.example.liteworkspace.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class MyBatisMapperBuilder implements BeanDefinitionBuilder {
    private final Set<String> knownDataSourceClasses;
    private final Set<String> knownSqlSessionFactoryClasses;
    private final Map<String, String> mapperClassToXmlPath = new HashMap<>();
    private static final String CUSTOM_XML_CACHE = System.getProperty("user.home") + "/.lite-workspace/custom-xml-path.cache";

    public MyBatisMapperBuilder() {
        Properties props = loadProperties();
        this.knownDataSourceClasses = parseClasses(props.getProperty("datasource.classes"));
        this.knownSqlSessionFactoryClasses = parseClasses(props.getProperty("sqlsessionfactory.classes"));
        scanAllMapperXml(); // 自动扫描 mapper XML 文件
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

        String className = clazz.getQualifiedName();
        String id = decapitalize(clazz.getName());

        boolean isAnnotated = isAnnotatedMapper(clazz);

        StringBuilder mapperBean = new StringBuilder();
        mapperBean.append("    <bean id=\"").append(id).append("\" ");
        if (isAnnotated) {
            mapperBean.append("class=\"").append(className).append("\"/>");
        } else {
            mapperBean.append("class=\"org.mybatis.spring.mapper.MapperFactoryBean\">\n")
                    .append("        <property name=\"mapperInterface\" value=\"").append(className).append("\"/>\n")
                    .append("        <property name=\"sqlSessionFactory\" ref=\"sqlSessionFactory\"/>\n")
                    .append("    </bean>");
        }
        assembler.putBeanXml(id, mapperBean.toString());

        if (!hasSessionFactory) {
            assembler.putBeanXml("sqlSessionFactory", getDefaultSqlSessionFactoryBean());
        }
    }

    private boolean isAnnotatedMapper(PsiClass clazz) {
        if (clazz == null || !clazz.isInterface()) return false;

        // 只有当类上有 @Mapper 且方法上含有 @Select/@Insert/... 才认为是注解模式
        boolean hasMapperAnnotation = Arrays.stream(clazz.getAnnotations())
                .map(PsiAnnotation::getQualifiedName)
                .anyMatch(q -> q != null && q.endsWith(".Mapper"));

        if (!hasMapperAnnotation) return false;

        for (PsiMethod method : clazz.getMethods()) {
            for (PsiAnnotation annotation : method.getAnnotations()) {
                String qn = annotation.getQualifiedName();
                if (qn != null && (qn.endsWith(".Select") || qn.endsWith(".Insert")
                        || qn.endsWith(".Update") || qn.endsWith(".Delete"))) {
                    return true; // 至少一个方法有注解，才是真正的注解 Mapper
                }
            }
        }

        return false; // 否则仍走 XML 配置
    }



    private String getDefaultSqlSessionFactoryBean() {
        StringBuilder bean = new StringBuilder();
        bean.append("    <bean id=\"sqlSessionFactory\" class=\"org.mybatis.spring.SqlSessionFactoryBean\">\n")
                .append("        <property name=\"dataSource\" ref=\"dataSource\"/>\n");

        List<String> mapperPaths = findMapperXmlPaths();
        if (!mapperPaths.isEmpty()) {
            bean.append("        <property name=\"mapperLocations\">\n")
                    .append("            <list>\n");
            for (String path : mapperPaths) {
                bean.append("                <value>classpath:").append(path).append("</value>\n");
            }
            bean.append("            </list>\n")
                    .append("        </property>\n");
        }

        bean.append("    </bean>");
        return bean.toString();
    }

    private void scanAllMapperXml() {
        Project project = ProjectManager.getInstance().getOpenProjects()[0];
        File resourceDir = new File(project.getBasePath(), "src/main/resources");

        if (!resourceDir.exists()) return;

        List<File> xmlFiles = new ArrayList<>();
        collectXmlFiles(resourceDir, xmlFiles);

        for (File xmlFile : xmlFiles) {
            try (BufferedReader reader = new BufferedReader(new FileReader(xmlFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("<mapper") && line.contains("namespace=")) {
                        String namespace = line.replaceAll(".*namespace\\s*=\\s*\"([^\"]+)\".*", "$1");
                        String relativePath = xmlFile.getAbsolutePath()
                                .replace(resourceDir.getAbsolutePath() + File.separator, "")
                                .replace(File.separatorChar, '/');
                        mapperClassToXmlPath.put(namespace, relativePath);
                        break;
                    }
                }
            } catch (IOException ignored) {
            }
        }
    }

    private void collectXmlFiles(File dir, List<File> files) {
        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (file.isDirectory()) {
                collectXmlFiles(file, files);
            } else if (file.getName().endsWith(".xml")) {
                files.add(file);
            }
        }
    }

    private List<String> findMapperXmlPaths() {
        return new ArrayList<>(mapperClassToXmlPath.values());
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
        return "    <bean id=\"dataSource\" class=\"com.zaxxer.hikari.HikariDataSource\">\n" +
                "        <property name=\"driverClassName\" value=\"" + escapeXml(driver) + "\"/>\n" +
                "        <property name=\"jdbcUrl\" value=\"" + escapeXml(url) + "\"/>\n" +
                "        <property name=\"username\" value=\"" + escapeXml(username) + "\"/>\n" +
                (password != null ? "        <property name=\"password\" value=\"" + escapeXml(password) + "\"/>\n" : "") +
                "    </bean>";
    }

    private String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
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
        return "    <bean id=\"dataSource\" class=\"com.zaxxer.hikari.HikariDataSource\">\n" +
                "        <property name=\"driverClassName\" value=\"com.mysql.cj.jdbc.Driver\"/>\n" +
                "        <property name=\"jdbcUrl\" value=\"jdbc:mysql://localhost:3306/test\"/>\n" +
                "        <property name=\"username\" value=\"root\"/>\n" +
                "        <property name=\"password\" value=\"123456\"/>\n" +
                "    </bean>";
    }

    private String decapitalize(String name) {
        return (name == null || name.isEmpty())
                ? name
                : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

}

