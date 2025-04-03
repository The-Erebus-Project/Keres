package io.github.vizanarkonin.keres.core.clients.actions;

import java.util.function.Consumer;

import io.github.vizanarkonin.keres.core.clients.http.KeresHttpClient;

/**
 * Main entry point for initializing actions
 */
public class ActionController {

    public static SequentialAction sequentialAction(String name, Runnable task) {
        return new SequentialAction(name, task);
    }

    public static SequentialAction sequentialAction(String name, Consumer<KeresHttpClient> task) {
        return new SequentialAction(
            name, 
            () -> task.accept(KeresHttpClient.getClientForThread()));
    }

    public static ParallelAction parallelAction(String name) {
        return new ParallelAction(name);
    }
}