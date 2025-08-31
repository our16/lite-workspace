package org.example.liteworkspace.util;

import com.intellij.openapi.diagnostic.Logger;
import org.example.liteworkspace.bean.core.LiteWorkspaceService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LogUtil {

    private final static Logger log = Logger.getInstance(LiteWorkspaceService.class);

    private final static ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LiteWorkspace-Log");
        t.setDaemon(true);
        return t;
    });

    private static String format(String str, Object... params) {
        if (params == null || params.length == 0) {
            return "LiteWorkspace:" + str;
        }
        String result = "LiteWorkspace:" + str;
        for (Object param : params) {
            String value;
            if (param == null) {
                value = "null";
            } else if (isPrimitiveOrString(param)) {
                value = String.valueOf(param);
            } else {
                value = JSONUtil.toJsonStr(param);
            }
            result = result.replaceFirst("\\{\\}", value);
        }
        return result;
    }

    private static boolean isPrimitiveOrString(Object obj) {
        Class<?> clazz = obj.getClass();
        return clazz.isPrimitive()
                || clazz == String.class
                || Number.class.isAssignableFrom(clazz)
                || clazz == Boolean.class
                || clazz == Character.class;
    }

    public static void info(String str, Object... params) {
        executor.submit(() -> log.info(format(str, params)));
    }

    public static void warn(String str, Object... params) {
        executor.submit(() -> log.warn(format(str, params)));
    }

    public static void error(String str, Throwable t, Object... params) {
        executor.submit(() -> log.error(format(str, params), t));
    }

    public static void debug(String str, Object... params) {
        if (log.isDebugEnabled()) {
            executor.submit(() -> log.debug(format(str, params)));
        }
    }

    public static void shutdown() {
        executor.shutdown();
    }
}
