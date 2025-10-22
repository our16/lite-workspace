package org.example.liteworkspace.util;

import java.util.HashMap;
import java.util.Map;

public class CostUtil {
    private static final Map<String, Long> timerMap = new HashMap<>();
    
    /**
     * 开始计时
     * @param timerName 计时器名称
     */
    public static void start(String timerName) {
        timerMap.put(timerName, System.currentTimeMillis());
    }
    
    /**
     * 结束计时并打印耗时
     * @param timerName 计时器名称
     * @return 耗时（毫秒）
     */
    public static long end(String timerName) {
        Long startTime = timerMap.get(timerName);
        if (startTime == null) {
            LogUtil.info("计时器 [" + timerName + "] 未启动");
            return -1;
        }
        
        long endTime = System.currentTimeMillis();
        long cost = endTime - startTime;
        LogUtil.info("计时器 [" + timerName + "] 耗时: " + cost + " 毫秒");
        
        // 移除计时器
        timerMap.remove(timerName);
        
        return cost;
    }
    
    /**
     * 结束计时并打印耗时（带描述信息）
     * @param timerName 计时器名称
     * @param description 描述信息
     * @return 耗时（毫秒）
     */
    public static long end(String timerName, String description) {
        Long startTime = timerMap.get(timerName);
        if (startTime == null) {
            LogUtil.info("计时器 [" + timerName + "] 未启动");
            return -1;
        }
        
        long endTime = System.currentTimeMillis();
        long cost = endTime - startTime;
        LogUtil.info("计时器 [" + timerName + "] " + description + " 耗时: " + cost + " 毫秒");
        
        // 移除计时器
        timerMap.remove(timerName);
        
        return cost;
    }
    
    /**
     * 获取当前计时器状态（不结束计时）
     * @param timerName 计时器名称
     * @return 当前耗时（毫秒），如果计时器未启动则返回-1
     */
    public static long getCurrentTime(String timerName) {
        Long startTime = timerMap.get(timerName);
        if (startTime == null) {
            return -1;
        }
        
        return System.currentTimeMillis() - startTime;
    }
    
    /**
     * 清除所有计时器
     */
    public static void clearAll() {
        timerMap.clear();
    }
    
    /**
     * 清除指定计时器
     * @param timerName 计时器名称
     */
    public static void clear(String timerName) {
        timerMap.remove(timerName);
    }
}
