package org.example.liteworkspace.util;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public class ReadActionUtil {

    private static final Executor EXECUTOR = ForkJoinPool.commonPool();

    /**
     * 异步执行只读任务，返回 CompletableFuture
     */
    public static <T> CompletableFuture<T> computeAsync(@NotNull Project project,
                                                        @NotNull ThrowableComputable<T, Exception> task) {
        CompletableFuture<T> future = new CompletableFuture<>();

        ReadAction.nonBlocking(() -> {
                    try {
                        return task.compute();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .inSmartMode(project)         // 等待索引就绪
                .submit(EXECUTOR)             // 提交到后台线程池
                .onSuccess(future::complete)  // 成功时完成 future
                .onError(future::completeExceptionally);

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

