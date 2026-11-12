package com.genosha.vitral.client.ui;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Runs slow work, such as calls to the server, away from the JavaFX thread and hands the
 * result back to it. Virtual threads are cheap and never keep the application alive.
 */
public final class Background {

    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private Background() {
    }

    /** Runs {@code work} in the background, then {@code onSuccess} or {@code onFailure} on the JavaFX thread. */
    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        EXECUTOR.execute(() -> {
            try {
                T result = work.call();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Exception e) {
                Platform.runLater(() -> onFailure.accept(e));
            }
        });
    }
}
