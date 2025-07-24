//package org.example.liteworkspace.util;
//
//import com.intellij.openapi.project.Project;
//import com.intellij.openapi.roots.ProjectFileIndex;
//import com.intellij.openapi.vfs.VirtualFile;
//import org.gradle.tooling.*;
//import org.gradle.tooling.model.GradleProject;
//import org.gradle.tooling.model.idea.IdeaModule;
//import org.gradle.tooling.model.idea.IdeaProject;
//
//import java.io.File;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Objects;
//
///**
// * 自动识别当前项目是 Gradle 还是 Maven，并获取其所有依赖的第三方 jar 包路径列表
// * 目前优先实现：通过 Gradle Tooling API 获取 runtime / testRuntimeClasspath 的 jar 文件
// */
//public class DependencyJarResolver {
//
//    /**
//     * 自动获取当前项目的所有第三方依赖 jar 包路径（如 spring-test.jar, junit.jar）
//     *
//     * @param project 当前 IntelliJ 项目
//     * @return List<String> 依赖 jar 的完整物理路径列表，如 ["/path/to/spring-test-5.3.30.jar", ...]
//     */
//    public static List<String> resolveThirdPartyDependencyJarPaths(Project project) {
//        List<String> jarPaths = new ArrayList<>();
//
//        if (project == null) {
//            System.err.println("[ERROR] 项目为空，无法解析依赖");
//            return jarPaths;
//        }
//
//        // 1. 检查是否为 Gradle 项目（通过是否存在 build.gradle 或使用 Project 文件判断）
//        VirtualFile baseDir = project.getBaseDir();
//        if (baseDir == null) {
//            System.err.println("[ERROR] 无法获取项目根目录");
//            return jarPaths;
//        }
//
//        VirtualFile buildGradle = baseDir.findChild("build.gradle");
//        VirtualFile buildGradleKts = baseDir.findChild("build.gradle.kts");
//
//        boolean isGradleProject = buildGradle != null || buildGradleKts != null;
//
//        if (isGradleProject) {
//            System.out.println("[INFO] 检测到 Gradle 项目，尝试通过 Gradle Tooling API 获取依赖 jar");
//            jarPaths.addAll(getGradleDependencyJarPaths(project));
//        } else {
//            // TODO: 后续扩展支持 Maven 项目（解析 pom.xml，查找本地 ~/.m2/...jar）
//            System.out.println("[WARN] 当前仅为 Gradle 项目实现，Maven 支持待扩展");
//        }
//
//        return jarPaths;
//    }
//
//    /**
//     * 通过 Gradle Tooling API 获取当前模块的 testRuntimeClasspath 或 runtimeClasspath 对应的 jar 文件路径
//     */
//    private static List<String> getGradleDependencyJarPaths(Project project) {
//        List<String> jarPaths = new ArrayList<>();
//
//        try {
//            // 1. 连接到当前 Gradle 项目
//            ProjectConnection connection = GradleConnector.newConnector()
//                    .forProjectDirectory(project.getBaseDir())
//                    .connect();
//
//            try {
//                // 2. 获取 IdeaProject（包含模块信息）
//                IdeaProject ideaProject = connection.getModel(IdeaProject.class);
//
//                for (IdeaModule module : ideaProject.getModules()) {
//                    System.out.println("[INFO] 扫描 Gradle 模块: " + module.getName());
//
//                    // 注意：Gradle Tooling API 并没有直接暴露 runtimeClasspath 的 File 列表
//                    // 所以这里采用一种替代方案：通过依赖声明解析或提示用户配置范围
//                    // 但更实用的方式是：让开发者手动指定范围，或者我们扩展获取依赖配置
//
//                    // 目前为了快速实现，我们返回一个空列表，或者你可以扩展如下：
//
//                    /*
//                     * 方案（推荐扩展）：执行一个 Gradle 任务，输出 runtimeClasspath 文件列表
//                     * 或使用自定义 Tooling API 模型解析
//                     * 或使用第三方库如 gradle-tooling-api-builder
//                     */
//
//                    // 暂时返回空，你可以后续扩展为：
//                    // - 执行 `dependencies` 任务并解析输出
//                    // - 或使用 build.gradle 中配置的依赖，解析为本地文件
//                }
//            } finally {
//                connection.close();
//            }
//        } catch (Exception ex) {
//            System.err.println("[ERROR] 访问 Gradle Tooling API 失败: " + ex.getMessage());
//            ex.printStackTrace();
//        }
//
//        // 🔧 目前返回一个空列表，你需要后续扩展为真实获取 runtimeClasspath jar 文件路径
//        // 你可以暂时手动返回一些 jar 路径用于测试，或者让我下一步为你实现真正自动获取逻辑
//
//        return jarPaths;
//    }
//}