package io.github.vizanarkonin.keres.core.clients.actions;

import java.util.ArrayList;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.vizanarkonin.keres.core.clients.http.KeresHttpClient;
import io.github.vizanarkonin.keres.core.processing.DataCollector;
import io.github.vizanarkonin.keres.core.utils.Response;

/**
 * Parallel action builder and executor.
 * Used in cases when we need to simulate concurrent execution of several tasks/requests. In these cases,
 * we're interested to see how long it took the longest request to finish.
 */
public class ParallelAction {
    private static final Logger log         = LogManager.getLogger("ParallelAction");
    private final String actionName;
    private ArrayList<Runnable> runnables; 
    private ArrayList<Thread> tasks;
    private long startTime = 0;

    public ParallelAction(String name) {
        this.actionName = name;
    }

    public ParallelAction addAndStart(Runnable task) {
        if (tasks == null) {
            tasks = new ArrayList<>();
        }
        if (startTime == 0) {
            startTime = System.currentTimeMillis();
        }
        tasks.add(Thread.startVirtualThread(task));

        return this;
    }

    public ParallelAction addAndStart(Consumer<KeresHttpClient> task) {
        if (tasks == null) {
            tasks = new ArrayList<>();
        }
        if (startTime == 0) {
            startTime = System.currentTimeMillis();
        }
        tasks.add(Thread.startVirtualThread(() -> task.accept(KeresHttpClient.getClientForThread())));

        return this;
    }

    public ParallelAction addTask(Runnable task) {
        if (runnables == null) {
            runnables = new ArrayList<>();
        }
        runnables.add(task);

        return this;
    }

    public ParallelAction addTask(Consumer<KeresHttpClient> task) {
        if (runnables == null) {
            runnables = new ArrayList<>();
        }
        runnables.add(() -> task.accept(KeresHttpClient.getClientForThread()));

        return this;
    }

    public ParallelAction start() {
        if (runnables == null) {
            return this;
        }

        runnables.forEach(runnable -> {
            addAndStart(runnable);
        });

        return this;
    }

    public void waitForRequestsToFinish() {
        log.trace("waitForRequestsToFinish");
        tasks.forEach(runnerThread ->  {
            if (runnerThread.isAlive()) {
                try {
                    runnerThread.join();
                } catch (InterruptedException ignored) {}
            }
        });
        long finishTime = System.currentTimeMillis();
        long timeElapsed = finishTime - startTime;

        Response overallStats = new Response()
            .setRequestMethod("ACTION")
            .setRequestName(actionName)
            .setStartTime(startTime)
            .setFinishTime(finishTime)
            .setResponseTime(timeElapsed);
        DataCollector.get().logResponse(overallStats);
    }
}
