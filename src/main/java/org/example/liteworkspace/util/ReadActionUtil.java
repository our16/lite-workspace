package org.example.liteworkspace.util;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

public class ReadActionUtil {

    private static final Executor EXECUTOR = ForkJoinPool.commonPool();

    /**
     * 异步执行只读任务，返回 CompletableFuture
     * 增强版本：支持ProcessCanceledException处理和超时控制
     */
    public static <T> CompletableFuture<T> computeAsync(@NotNull Project project,
                                                        @NotNull ThrowableComputable<T, Exception> task) {
        return computeAsync(project, task, 30, TimeUnit.SECONDS);
    }

    /**
     * 异步执行只读任务，返回 CompletableFuture，支持超时控制
     */
    public static <T> CompletableFuture<T> computeAsync(@NotNull Project project,
                                                        @NotNull ThrowableComputable<T, Exception> task,
                                                        long timeout, TimeUnit unit) {
        CompletableFuture<T> future = new CompletableFuture<>();

        ReadAction.nonBlocking(() -> {
                    try {
                        // 在ReadAction内部进行日志记录
                        System.out.println("[DEBUG] 开始执行异步ReadAction任务");
                        T result = task.compute();
                        System.out.println("[DEBUG] 异步ReadAction任务执行成功");
                        return result;
                    } catch (ProcessCanceledException e) {
                        System.out.println("[WARN] 异步ReadAction任务被取消: " + e.getMessage());
                        // 重新抛出ProcessCanceledException，让上层处理
                        throw e;
                    } catch (Exception e) {
                        System.err.println("[ERROR] 异步ReadAction任务执行失败");
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                })
                .inSmartMode(project)         // 等待索引就绪
                .submit(EXECUTOR)             // 提交到后台线程池
                .onSuccess(result -> {
                    System.out.println("[DEBUG] 异步ReadAction任务成功完成");
                    future.complete(result);
                })
                .onError(ex -> {
                    if (ex instanceof ProcessCanceledException) {
                        System.out.println("[WARN] 异步ReadAction任务被取消，不标记为失败");
                        // 对于ProcessCanceledException，不标记future为异常状态
                        // 这允许上层代码继续执行或优雅地处理取消
                        future.cancel(true);
                    } else {
                        System.err.println("[ERROR] 异步ReadAction任务失败");
                        ex.printStackTrace();
                        future.completeExceptionally(ex);
                    }
                });

        // 添加超时控制
        if (timeout > 0) {
            future.orTimeout(timeout, unit).handle((result, ex) -> {
                if (ex != null) {
                    if (ex instanceof java.util.concurrent.TimeoutException) {
                        System.out.println("[WARN] 异步ReadAction任务超时: " + timeout + " " + unit);
                        future.cancel(true);
                    } else if (ex instanceof ProcessCanceledException) {
                        System.out.println("[DEBUG] 异步ReadAction任务被取消");
                    }
                }
                return null;
            });
        }

        return future;
    }

    /**
     * 异步执行只读任务，不关心返回值
     */
    public static void runAsync(@NotNull Project project,
                                @NotNull ThrowableRunnable<Exception> task) {
        ReadAction.nonBlocking(() -> {
                    try {
                        task.run();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .inSmartMode(project)
                .submit(EXECUTOR);
    }

    public static void runSync(@NotNull Project project,
                                @NotNull ThrowableRunnable<Exception> task) {
        ReadAction.nonBlocking(() -> {
                    try {
                        task.run();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .inSmartMode(project)
                .executeSynchronously();
    }

    @FunctionalInterface
    public interface ThrowableComputable<T, E extends Exception> {
        T compute() throws E;
    }

    @FunctionalInterface
    public interface ThrowableRunnable<E extends Exception> {
        void run() throws E;
    }
}
