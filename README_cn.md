**其他语言版本: [English](README.md), [中文](README_zh.md).**

# LiteWorkspace - IntelliJ IDEA 插件

LiteWorkspace 是一个 IntelliJ IDEA 插件，旨在 **优化 Spring 应用启动速度**。  
它通过扫描和分析 Bean 定义，减少不必要的 Spring 上下文加载，帮助开发者提升开发效率。  

[![JetBrains Plugins](https://img.shields.io/badge/JetBrains-Plugin-blue.svg)](https://plugins.jetbrains.com/)  
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](LICENSE)

---

## ✨ 功能特性 (Features)

- 🔍 **智能 Bean 扫描**  
  自动识别 `@ComponentScan` 注解和 XML 中的 `component-scan` 配置。  

- 📦 **依赖 JAR 支持**  
  扫描外部 JAR 包中的 `spring.factories` 和自动配置类。  

- 🏗 **多模块项目支持**  
  在大型分模块 Spring Boot 项目中同样可用。  

- ⚡ **启动优化**  
  找出最小化的上下文加载范围，加快应用启动速度。  

- 📊 **依赖可视化（规划中）**  
  提供图形化视图，展示 Bean 依赖关系，帮助优化上下文。  

---

## 🎯 适用场景 (Use Cases)

- 启动耗时较长的大型 **Spring Boot** 项目  
- 多模块工程，需要精确分析依赖的场景  
- 调试时仅希望加载最小化的 **Spring 上下文**  
- 分析项目中引入的第三方 JAR 自动配置内容  

---

## 📥 安装 (Installation)

1. 从 [JetBrains Marketplace](https://plugins.jetbrains.com/) 下载（即将上线）。  
2. 或者源码构建：
   ```bash
   ./gradlew buildPlugin

---
## 📥 使用方法 (Usage)
1. 安装插件 / Install Plugin
下载本项目打包的 LiteWorkspace 插件（liteworkspace-<version>.jar）。

在 IntelliJ IDEA 中依次选择：
Settings/Preferences → Plugins → ⚙️ → Install Plugin from Disk... → 选择下载的 jar 文件 → 重启 IDEA
2. 启用插件 / Enable Plugin
打开一个 Spring / Spring Boot / MyBatis 项目。

在 IDEA 右键测试类或测试方法 中可以看到 LiteWorkspace 提供的入口 FastRunTest。

3. 运行依赖扫描 / FastRunTest-扫描spring bean xml

在目标类（例如 @UserService 主类或测试类）上 右键 → FastRunTest-扫描spring bean xml

插件会执行以下步骤：

扫描 源码 中的注解 (@ComponentScan, @Configuration, @Bean 等)；

扫描 XML 配置 中的 <context:component-scan> 与 <bean> 定义；

扫描 依赖 JAR 中的 META-INF/spring.factories、spring/...AutoConfiguration.imports；

分析依赖关系，推断接口实现类、泛型 Bean、@Configuration 生成的 Bean；

自动生成一个 精简版上下文加载清单 和对应的 测试类

4. 查看结果 / View Results

扫描结果会显示在 IDEA 工具窗口（LiteWorkspace 面板）。

你可以直接基于该配置 启动应用，大幅减少启动时间。

<img width="1888" height="757" alt="企业微信截图_17571522179138" src="https://github.com/user-attachments/assets/92b70d2f-dba7-4563-9325-25529a62a1db" />
<img width="1386" height="696" alt="企业微信截图_17571522948750" src="https://github.com/user-attachments/assets/f59e43c2-de81-463a-9809-2dc1664875db" />
