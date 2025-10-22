package org.example.liteworkspace.util;

import com.intellij.openapi.diagnostic.Logger;
import org.example.liteworkspace.bean.core.LiteWorkspaceService;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

public class LogUtil {

    private final static Logger log = Logger.getInstance(LiteWorkspaceService.class);

    private final static BlockingQueue<Runnable> logQueue = new LinkedBlockingQueue<>();
    private final static Thread logThread;

    static {
        logThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Runnable task = logQueue.take();
                    task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Log thread exception", e);
                }
            }
        }, "LiteWorkspace-Log");
        logThread.setDaemon(true);
        logThread.start();
    }

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
            } else if (param instanceof Collection<?> col) {
                value = col.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(", ", "[", "]"));
            } else if (param instanceof Map<?, ?> map) {
                value = map.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining(", ", "{", "}"));
            } else {
                value = JSONUtil.toJsonStr(param);
            }
            try {
                assert value != null;
                result = result.replaceFirst("\\{\\}", value);
            } catch (Exception e) {
                e.printStackTrace();
                result = null;
            }
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

    private static void submit(Runnable task) {
        logQueue.offer(task); // 非阻塞方式入队
    }

    public static void info(String str, Object... params) {
        submit(() -> log.info(format(str, params)));
    }

    public static void warn(String str, Object... params) {
        submit(() -> log.warn(format(str, params)));
    }

    public static void error(String str, Throwable t, Object... params) {
        submit(() -> log.error(format(str, params), t));
    }

    public static void debug(String str, Object... params) {
        submit(() -> log.info(format(str, params)));
    }

    public static void shutdown() {
        logThread.interrupt();
    }
}
