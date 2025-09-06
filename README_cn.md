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
