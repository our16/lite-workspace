package org.example.liteworkspace.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class IdeaDependencyUtil {

    /**
     * 获取当前项目（或当前选中模块）的 所有依赖库的 jar 路径列表（包括 Maven/Gradle 依赖）
     * @return List<String> jar 文件的绝对路径列表
     */
    public static List<String> getCurrentModuleDependencyJarPaths() {
        List<String> jarPaths = new ArrayList<>();

        // 获取当前项目
        com.intellij.openapi.project.Project project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
        if (project == null) return jarPaths;

        // 获取所有模块
        Module[] modules = ModuleManager.getInstance(project).getModules();
        if (modules.length == 0) return jarPaths;

        // 遍历第一个模块（可扩展为让用户选择模块）
        Module module = modules[0];

        // 获取模块的依赖配置
        ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        OrderEntry[] orderEntries = rootManager.getOrderEntries();

        for (OrderEntry entry : orderEntries) {
            if (entry instanceof LibraryOrderEntry) {
                LibraryOrderEntry libEntry = (LibraryOrderEntry) entry;
                Library library = libEntry.getLibrary();
                if (library != null) {
                    for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
                        String path = file.getPath();
                        if (path.endsWith(".jar")) {
                            jarPaths.add(path);
                            System.out.println("[INFO] 添加依赖 jar: " + path);
                        }
                    }
                }
            } else if (entry instanceof ModuleSourceOrderEntry) {
                // 源码模块，忽略
            }
            // 可以处理 SDK 等其它类型
        }

        return jarPaths;
    }
}
