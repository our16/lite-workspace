# Lite-Workspace 项目图数据库模型

## 项目概述
Lite-Workspace 是一个 IntelliJ IDEA 插件，主要用于 Spring 和 MyBatis 框架的 Bean 扫描、依赖分析和代码生成。该图数据库模型旨在可视化项目中的核心组件及其关系，便于后续扩展和优化功能。

## 图数据库节点类型 (Node Types)

### 1. Project (项目节点)
**属性:**
- `name`: 项目名称
- `basePath`: 项目基础路径
- `buildTool`: 构建工具类型 (MAVEN, GRADLE, UNKNOWN)
- `multiModule`: 是否多模块项目 (boolean)
- `createdTime`: 创建时间

**标签:** `:Project`

### 2. Module (模块节点)
**属性:**
- `name`: 模块名称
- `type`: 模块类型
- `sourceRoots`: 源代码根路径列表

**标签:** `:Module`

### 3. Bean (Bean节点)
**属性:**
- `beanName`: Bean名称
- `className`: 完整类名
- `type`: Bean类型 (ANNOTATION, XML, MYBATIS, JAVA_CONFIG, MAPPER, PLAIN, MAPPER_STRUCT)
- `sourceFile`: 源文件路径
- `isSingleton`: 是否单例 (boolean)

**标签:** `:Bean`

### 4. Class (类节点)
**属性:**
- `qualifiedName`: 类全限定名
- `simpleName`: 类简单名称
- `isInterface`: 是否接口 (boolean)
- `isAbstract`: 是否抽象类 (boolean)
- `packageName`: 包名
- `sourceFile`: 源文件路径

**标签:** `:Class`

### 5. Method (方法节点)
**属性:**
- `name`: 方法名
- `returnType`: 返回类型
- `parameters`: 参数类型列表
- `modifiers`: 修饰符列表
- `annotations`: 注解列表

**标签:** `:Method`

### 6. Field (字段节点)
**属性:**
- `name`: 字段名
- `type`: 字段类型
- `modifiers`: 修饰符列表
- `annotations`: 注解列表

**标签:** `:Field`

### 7. Configuration (配置节点)
**属性:**
- `className`: 配置类名
- `scanPackages`: 扫描包列表
- `dataSourceRef`: 引用的数据源

**标签:** `:Configuration`

### 8. DataSource (数据源节点)
**属性:**
- `name`: 数据源名称
- `url`: JDBC URL
- `username`: 用户名
- `driverClassName`: 驱动类名
- `type`: 数据源类型

**标签:** `:DataSource`

### 9. Mapper (MyBatis Mapper节点)
**属性:**
- `namespace`: 命名空间
- `interfaceName`: 接口名
- `xmlPath`: XML文件路径
- `sqlSessionRef`: 引用的SqlSession

**标签:** `:Mapper`

### 10. XmlFile (XML文件节点)
**属性:**
- `filePath`: 文件路径
- `type`: 文件类型 (SPRING_CONFIG, MYBATIS_MAPPER, DATASOURCE)
- `namespace`: 命名空间 (针对Mapper XML)

**标签:** `:XmlFile`

### 11. Dependency (依赖节点)
**属性:**
- `groupId`: Group ID
- `artifactId`: Artifact ID
- `version`: 版本号
- `scope`: 作用域 (COMPILE, TEST, PROVIDED, RUNTIME)

**标签:** `:Dependency`

## 图数据库关系类型 (Relationship Types)

### 1. 项目相关关系
- `(Project)-[:HAS_MODULE]->(Module)`: 项目包含模块
- `(Project)-[:HAS_BUILD_TOOL]->(Dependency)`: 项目使用构建工具
- `(Project)-[:HAS_TARGET_CLASS]->(Class)`: 项目有目标类

### 2. 模块相关关系
- `(Module)-[:CONTAINS_CLASS]->(Class)`: 模块包含类
- `(Module)-[:HAS_DEPENDENCY]->(Dependency)`: 模块依赖其他模块或库

### 3. Bean相关关系
- `(Bean)-[:BASED_ON_CLASS]->(Class)`: Bean基于某个类
- `(Bean)-[:DEPENDS_ON]->(Bean)`: Bean依赖其他Bean
- `(Bean)-[:DEFINED_IN]->(Configuration)`: Bean在配置类中定义
- `(Bean)-[:DEFINED_IN_XML]->(XmlFile)`: Bean在XML文件中定义

### 4. 类相关关系
- `(Class)-[:EXTENDS]->(Class)`: 类继承关系
- `(Class)-[:IMPLEMENTS]->(Class)`: 类实现接口
- `(Class)-[:HAS_METHOD]->(Method)`: 类包含方法
- `(Class)-[:HAS_FIELD]->(Field)`: 类包含字段
- `(Class)-[:ANNOTATED_WITH]->(Annotation)`: 类被注解标记
- `(Class)-[:IS_CONFIGURATION]->(Configuration)`: 类是配置类

### 5. 方法相关关系
- `(Method)-[:RETURNS]->(Class)`: 方法返回类型
- `(Method)-[:HAS_PARAMETER]->(Class)`: 方法有参数类型
- `(Method)-[:ANNOTATED_WITH]->(Annotation)`: 方法被注解标记
- `(Method)-[:DEFINES_BEAN]->(Bean)`: 方法定义Bean (@Bean方法)

### 6. 字段相关关系
- `(Field)-[:OF_TYPE]->(Class)`: 字段类型
- `(Field)-[:ANNOTATED_WITH]->(Annotation)`: 字段被注解标记
- `(Field)-[:INJECTS_BEAN]->(Bean)`: 字段注入Bean

### 7. 配置相关关系
- `(Configuration)-[:SCANS_PACKAGE]->(Package)`: 配置扫描包
- `(Configuration)-[:USES_DATASOURCE]->(DataSource)`: 配置使用数据源
- `(Configuration)-[:IMPORTS]->(Configuration)`: 配置导入其他配置

### 8. 数据源相关关系
- `(DataSource)-[:USED_BY]->(Configuration)`: 数据源被配置使用
- `(DataSource)-[:USED_BY_MAPPER]->(Mapper)`: 数据源被Mapper使用

### 9. Mapper相关关系
- `(Mapper)-[:BASED_ON_INTERFACE]->(Class)`: Mapper基于接口
- `(Mapper)-[:HAS_XML_MAPPING]->(XmlFile)`: Mapper有XML映射文件
- `(Mapper)-[:USES_SQL_SESSION]->(SqlSessionConfig)`: Mapper使用SqlSession

### 10. 依赖注入关系
- `(Class)-[:INJECTS]->(Class)`: 类注入其他类 (依赖关系)
- `(Method)-[:INJECTS_PARAMETER]->(Class)`: 方法参数注入
- `(Constructor)-[:INJECTS_PARAMETER]->(Class)`: 构造器参数注入

### 11. 扫描关系
- `(BeanScanner)-[:SCANS]->(Class)`: 扫描器扫描类
- `(BeanScanner)-[:DISCOVERS]->(Bean)`: 扫描器发现Bean
- `(BeanScanner)-[:FINDS_DEPENDENCY]->(Class)`: 扫描器找到依赖

## 图数据库查询示例

### 1. 查询项目中的所有Bean
```cypher
MATCH (p:Project)-[:HAS_MODULE]->(m:Module)-[:CONTAINS_CLASS]->(c:Class)-[]->(b:Bean)
RETURN p.name, b.beanName, b.type, c.qualifiedName
```

### 2. 查询Bean的依赖关系
```cypher
MATCH (b1:Bean)-[:DEPENDS_ON]->(b2:Bean)
WHERE b1.beanName = 'serviceBean'
RETURN b1.beanName, b2.beanName, b2.type
```

### 3. 查询配置类及其定义的Bean
```cypher
MATCH (c:Configuration)-[:HAS_METHOD]->(m:Method)-[:DEFINES_BEAN]->(b:Bean)
RETURN c.className, m.name, b.beanName, b.type
```

### 4. 查询MyBatis Mapper及其XML映射
```cypher
MATCH (m:Mapper)-[:HAS_XML_MAPPING]->(x:XmlFile)
RETURN m.namespace, m.interfaceName, x.filePath
```

### 5. 查询类的继承层次
```cypher
MATCH (c1:Class)-[:EXTENDS*]->(c2:Class)
WHERE c1.qualifiedName = 'com.example.ChildClass'
RETURN c1.qualifiedName, c2.qualifiedName
```

### 6. 查询依赖注入路径
```cypher
MATCH path = (c1:Class)-[:INJECTS*]->(c2:Class)
WHERE c1.qualifiedName = 'com.example.ServiceA'
RETURN nodes(path) as dependencyPath
```

## 数据导入策略

### 1. 项目初始化
- 扫描项目结构，创建Project和Module节点
- 分析构建工具配置，创建Dependency节点
- 建立项目-模块关系

### 2. 类和Bean扫描
- 使用LiteBeanScanner扫描所有类
- 创建Class节点和Bean节点
- 建立类-Bean关系

### 3. 依赖关系分析
- 分析字段注入、方法注入、构造器注入
- 创建依赖关系边
- 建立Bean之间的依赖图

### 4. 配置分析
- 扫描@Configuration类
- 分析@ComponentScan注解
- 建立配置-包扫描关系

### 5. MyBatis分析
- 扫描Mapper接口和XML文件
- 建立Mapper-XML关系
- 分析数据源配置

## 性能优化建议

### 1. 索引策略
```cypher
CREATE INDEX ON :Bean(beanName)
CREATE INDEX ON :Class(qualifiedName)
CREATE INDEX ON :Configuration(className)
CREATE INDEX ON :Mapper(namespace)
CREATE INDEX ON :DataSource(name)
```

### 2. 分区策略
- 按项目分区
- 按模块分组
- 按Bean类型分类

### 3. 缓存策略
- 缓存频繁查询的Bean关系
- 缓存配置类信息
- 缓存依赖关系图

## 扩展功能

### 1. 代码生成优化
- 基于图数据库分析Bean依赖，优化生成顺序
- 识别循环依赖，提供解决方案
- 分析Bean作用域，优化内存使用

### 2. 性能分析
- 分析Bean创建链路
- 识别性能瓶颈
- 提供优化建议

### 3. 代码质量检查
- 检查依赖注入规范
- 分析代码复杂度
- 提供重构建议

### 4. 文档生成
- 自动生成架构图
- 生成依赖关系报告
- 生成API文档

## 监控和维护

### 1. 数据一致性
- 定期验证图数据完整性
- 检查孤立节点
- 更新过期的依赖关系

### 2. 性能监控
- 监控查询性能
- 跟踪图增长情况
- 优化慢查询

### 3. 版本管理
- 支持多版本图数据
- 提供数据迁移工具
- 支持增量更新

这个图数据库模型为Lite-Workspace项目提供了全面的关系可视化，有助于理解项目结构、优化功能扩展和提升代码质量。
