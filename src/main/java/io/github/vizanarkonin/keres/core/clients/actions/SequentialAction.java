package io.github.vizanarkonin.keres.core.clients.actions;

import io.github.vizanarkonin.keres.core.processing.DataCollector;
import io.github.vizanarkonin.keres.core.utils.Response;

/**
 * Implementation of sequential action - a series of actions/requests that represent certain functional procedure being executed.
 * In this case, we're interested in total time it took to execute all actions from start to finish
 */
public class SequentialAction {
    private final String actionName;
    private final Runnable task;

    public SequentialAction(String name, Runnable task) {
        this.actionName = name;
        this.task = task;
    }

    public void execute() {
        long startPoint = System.currentTimeMillis();
        task.run();
        long finishPoint = System.currentTimeMillis();
        long timeElapsed = finishPoint - startPoint;

        Response overallStats = new Response()
            .setRequestMethod("ACTION")
            .setRequestName(actionName)
            .setStartTime(startPoint)
            .setFinishTime(finishPoint)
            .setResponseTime(timeElapsed);
        DataCollector.get().logResponse(overallStats);
    }
}
