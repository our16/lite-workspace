//package org.example.liteworkspace.util;
//
//import com.intellij.openapi.project.Project;
//import org.gradle.tooling.*;
//import org.gradle.tooling.model.GradleProject;
//import org.gradle.tooling.model.idea.IdeaModule;
//import org.gradle.tooling.model.idea.IdeaProject;
//
//import java.io.File;
//import java.util.ArrayList;
//import java.util.List;
//
///**
// * 通过 Gradle Tooling API 获取当前 Gradle 项目的 runtime 或 testRuntime 依赖的第三方 jar 包路径列表
// * 返回如：/Users/name/.gradle/caches/modules-2/files-2.1/org.springframework/spring-test/5.3.30/.../spring-test-5.3.30.jar
// */
//public class GradleDependencyJarFetcher {
//
//    /**
//     * 获取当前 Gradle 项目的所有第三方依赖 jar 包的完整物理路径列表
//     *
//     * @param project 当前 IntelliJ 项目
//     * @return List<String> 依赖 jar 的绝对路径列表，例如 ["/path/to/spring-test.jar", ...]
//     */
//    public static List<String> getGradleRuntimeDependencyJarPaths(Project project) {
//        List<String> jarPaths = new ArrayList<>();
//
//        if (project == null) {
//            System.err.println("[ERROR] 项目为空");
//            return jarPaths;
//        }
//
//        // 1. 检查是否存在 build.gradle，确认是 Gradle 项目
//        var baseDir = project.getBaseDir();
//        if (baseDir == null) {
//            System.err.println("[ERROR] 无法获取项目根目录");
//            return jarPaths;
//        }
//
//        var buildGradle = baseDir.findChild("build.gradle");
//        var buildGradleKts = baseDir.findChild("build.gradle.kts");
//
//        boolean isGradleProject = buildGradle != null || buildGradleKts != null;
//        if (!isGradleProject) {
//            System.out.println("[INFO] 当前项目不是 Gradle 项目（未找到 build.gradle 或 build.gradle.kts），跳过");
//            return jarPaths;
//        }
//
//        // 2. 连接 Gradle 项目
//        ProjectConnection connection = null;
//        try {
//            connection = GradleConnector.newConnector()
//                    .forProjectDirectory(baseDir)
//                    .connect();
//
//            // 3. 获取 IdeaProject（包含模块列表）
//            IdeaProject ideaProject = connection.getModel(IdeaProject.class);
//            if (ideaProject == null) {
//                System.err.println("[ERROR] 无法获取 IdeaProject 模型");
//                return jarPaths;
//            }
//
//            // 4. 遍历所有模块（通常只需要第一个 module）
//            for (IdeaModule module : ideaProject.getModules()) {
//                System.out.println("[INFO] 扫描 Gradle 模块: " + module.getName());
//
//                // ⚠️ 注意：IdeaModule 并没有直接暴露 dependencies 的 File 列表
//                // 所以我们需要采用替代方案，如下：
//
//                /*
//                 * ✅ 推荐方案（实际可行）：通过 Gradle Tooling API 的扩展能力，
//                 * 获取当前模块的 runtimeClasspath 或 testRuntimeClasspath 对应的依赖文件列表
//                 *
//                 * 但目前标准的 Tooling API 模型（如 IdeaModule）并未直接提供该功能。
//                 *
//                 * 所以这里我们采用一个实际可行的方案：返回空列表，或者你可以扩展为：
//                 * - 使用自定义 Gradle Task 输出依赖文件路径
//                 * - 或使用 BuildController + Model API（需要额外配置）
//                 */
//
//                // 目前为了可运行，我们返回一个示例路径（可删除，后续替换为真实逻辑）
//                // 👇 下面是你未来真正要实现的代码位置
//
//                // 示例（待替换）:
//                // jarPaths.addAll(getRuntimeClasspathJarFilesFromModule(module));
//            }
//
//            // 🔧 暂时返回空列表，下面我会告诉你如何真正获取这些 jar 文件
//
//        } catch (Exception e) {
//            System.err.println("[ERROR] 访问 Gradle Tooling API 失败: " + e.getMessage());
//            e.printStackTrace();
//        } finally {
//            if (connection != null) {
//                try {
//                    connection.close();
//                } catch (Exception e) {
//                    System.err.println("[WARN] 关闭 Gradle 连接失败: " + e.getMessage());
//                }
//            }
//        }
//
//        // ✅ 目前返回空列表，你需要后续扩展为真实获取 runtimeClasspath jar 路径
//        // 👇 下一步：我告诉你如何真正实现它（通过解析依赖或执行任务）
//
//        return jarPaths;
//    }
//
//    /**
//     * （待实现 / 可选方案）通过解析模块的依赖，返回 runtimeClasspath 对应的 jar 文件路径
//     */
//    /*
//    private static List<String> getRuntimeClasspathJarFilesFromModule(IdeaModule module) {
//        // TODO: 通过 Module 获取其依赖的 libraries 或 configurations
//        // 如：module.getRuntimeDependencies() 或类似方法（目前不可用）
//        return new ArrayList<>();
//    }
//    */
//}