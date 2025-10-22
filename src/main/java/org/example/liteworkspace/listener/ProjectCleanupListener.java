package org.example.liteworkspace.listener;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import org.example.liteworkspace.dto.OptimizedPsiToDtoConverter;
import org.example.liteworkspace.util.OptimizedLogUtil;
import org.jetbrains.annotations.NotNull;

/**
 * 项目清理监听器
 * 
 * 负责在项目关闭时清理相关的静态缓存，防止项目间数据污染
 */
public class ProjectCleanupListener implements ProjectManagerListener {
    
    @Override
    public void projectClosing(@NotNull Project project) {
        String projectId = project.getLocationHash();
        
        // 清理 PSI 转换器缓存
        OptimizedPsiToDtoConverter.clearInstance(projectId);
        
        // 清理日志条件缓存
        clearLogConditions(projectId);
        
        // 清理其他可能的静态缓存
        clearOtherCaches(projectId);
        
        OptimizedLogUtil.info("项目 {} 的缓存已清理", projectId);
    }
    
    /**
     * 清理日志条件缓存
     */
    private void clearLogConditions(String projectId) {
        // 由于 OptimizedLogUtil 的日志条件是私有的，我们需要通过反射来清理
        // 或者修改 OptimizedLogUtil 来提供清理方法
        try {
            // 这里可以添加清理逻辑
            // 目前 OptimizedLogUtil 使用弱引用，应该会自动清理
        } catch (Exception e) {
            // 忽略清理异常
        }
    }
    
    /**
     * 清理其他静态缓存
     */
    private void clearOtherCaches(String projectId) {
        // 清理 CostUtil 中的计时器
        try {
            Class<?> costUtilClass = Class.forName("org.example.liteworkspace.util.CostUtil");
            java.lang.reflect.Method clearAllMethod = costUtilClass.getMethod("clearAll");
            clearAllMethod.invoke(null);
        } catch (Exception e) {
            // 忽略清理异常
        }
        
        // 清理 ConsoleService 中的控制台缓存
        try {
            Class<?> consoleServiceClass = Class.forName("org.example.liteworkspace.util.ConsoleService");
            java.lang.reflect.Field consoleMapField = consoleServiceClass.getDeclaredField("consoleMap");
            consoleMapField.setAccessible(true);
            java.util.Map<?, ?> consoleMap = (java.util.Map<?, ?>) consoleMapField.get(null);
            
            // 移除与该项目相关的控制台
            consoleMap.entrySet().removeIf(entry -> {
                Object consoleProject = entry.getKey();
                return consoleProject instanceof Project && 
                       ((Project) consoleProject).getLocationHash().equals(projectId);
            });
        } catch (Exception e) {
            // 忽略清理异常
        }
    }
}
