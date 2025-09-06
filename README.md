**Read this in other languages: [English](README.md), [中文](README_zh.md).**

# LiteWorkspace - IntelliJ IDEA Plugin

LiteWorkspace is an IntelliJ IDEA plugin that helps developers **optimize Spring application startup** by scanning and analyzing bean definitions, reducing unnecessary Spring context loading, and improving development efficiency.

[![JetBrains Plugins](https://img.shields.io/badge/JetBrains-Plugin-blue.svg)](https://plugins.jetbrains.com/)  
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](LICENSE)

---

## ✨ Features

- 🔍 **Smart Bean Scanning**  
  Automatically detects `@ComponentScan` annotations and XML `component-scan` configurations.

- 📦 **Dependency JAR Support**  
  Scans external JAR packages for `spring.factories` and auto-configuration classes.

- 🏗 **Multi-Module Project Support**  
  Works seamlessly in large, modular Spring Boot projects.

- ⚡ **Startup Optimization**  
  Identifies the minimum required Spring context to load, accelerating startup time.

- 📊 **Dependency Visualization** *(coming soon)*  
  Provides a graphical view of bean dependencies to help refine context loading.

---

## 🎯 Use Cases

- Large **Spring Boot** projects with long startup times.  
- Multi-module projects needing **precise dependency analysis**.  
- Debugging sessions where developers want to **limit Spring context scope**.  
- Analyzing **third-party JAR auto-configurations** in the project classpath.

---

## 📥 Installation

1. Download from [JetBrains Marketplace](https://plugins.jetbrains.com/) (coming soon).  
2. Or build from source:
   ```bash
   ./gradlew buildPlugin
---
<img width="1888" height="757" alt="企业微信截图_17571522179138" src="https://github.com/user-attachments/assets/92b70d2f-dba7-4563-9325-25529a62a1db" />
<img width="1386" height="696" alt="企业微信截图_17571522948750" src="https://github.com/user-attachments/assets/f59e43c2-de81-463a-9809-2dc1664875db" />

