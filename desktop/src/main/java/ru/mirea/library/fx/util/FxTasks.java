package ru.mirea.library.fx.util;

import javafx.concurrent.Task;
import javafx.scene.Node;

import ru.mirea.library.Main;
import ru.mirea.library.client.ApiException;

import java.util.concurrent.*;
import java.util.function.Consumer;

public final class FxTasks {
    private static final ExecutorService WORKERS =
            Executors.newFixedThreadPool(
                    4,
                    r -> {
                        Thread t = new Thread(r, "api-request");
                        t.setDaemon(true);
                        return t;
                    });

    private FxTasks() {}

    public static <T> void run(Node busy, Callable<T> work, Consumer<T> success) {
        run(busy, work, success, FxTasks::showError);
    }

    public static <T> void run(
            Node busy, Callable<T> work, Consumer<T> success, Consumer<Throwable> failure) {
        if (busy != null && busy.isDisable()) return;
        if (busy != null) busy.setDisable(true);
        Task<T> task =
                new Task<>() {
                    protected T call() throws Exception {
                        return work.call();
                    }
                };
        task.setOnSucceeded(
                e -> {
                    if (busy != null) busy.setDisable(false);
                    success.accept(task.getValue());
                });
        task.setOnFailed(
                e -> {
                    if (busy != null) busy.setDisable(false);
                    failure.accept(task.getException());
                });
        WORKERS.execute(task);
    }

    public static void showError(Throwable error) {
        if (error instanceof ApiException api && "SESSION_CHANGED".equals(api.code())) return;
        if (error instanceof ApiException api && api.status() == 401) {
            Main.showLogin("Сессия истекла. Войдите снова");
            return;
        }
        if (error instanceof ApiException api && "PASSWORD_CHANGE_REQUIRED".equals(api.code())) {
            Main.showPasswordChange();
            return;
        }
        AlertHelper.showError(
                error.getMessage() == null ? "Не удалось выполнить операцию" : error.getMessage());
    }
}
