package io.github.vizanarkonin.keres.core.executors;

import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.vizanarkonin.keres.KeresController;
import io.github.vizanarkonin.keres.core.interfaces.KeresUserDefinition;
import io.github.vizanarkonin.keres.core.utils.TimeUtils;

/**
 * Main workhorse of load generation - encapsulates worker thread and defines the behavior of the client.
 * Regular implementation uses a "one-shot" approach - virtual user performs a task and then immideately finishes. Used for open load profile.
 * Cycled implementation used limited amount of cycles - virtual user performs given amount of tasks and then finishes. This type is of particular use for volume-testing.
 * Looped implementation uses a loop task-set approach - virtual user performs pre/post steps once and keeps running user actions until told to stop/ Used for closed load profile.
 */
public class KeresUser {
    private static final Logger log = LogManager.getLogger("KeresUser");
    // Static storage for all currently existing keresUser instances.
    @Getter
    private static final ConcurrentHashMap<Long, KeresUser> allRunners  = new ConcurrentHashMap<>();
    @Getter
    private long runnerId;
    private Thread runnerThread;
    private boolean isActive = true;
    @Getter
    private final Mode mode;

    private KeresUser(Mode mode, Thread thread) {
        setThread(thread);
        this.mode = mode;
    }

    private KeresUser(Mode mode) {
        this.mode = mode;
    }

    private void setThread(Thread thread) {
        runnerThread = thread;
    }

    /**
     * Creates a regular-type user - it runs before-task, does 1 pass of the task itself, and then finishes up with after task.
     * @param task  - Task to execute
     * @return      - KeresUser instance
     */
    public static KeresUser initRegularUser(Class<? extends KeresUserDefinition> task) {
        KeresUser runner = new KeresUser(Mode.DEFAULT);
        runner.setThread(Thread.startVirtualThread(() -> {
            try {
                KeresUserDefinition runnerTask = task.getConstructor().newInstance();

                runner.registerRunner();
                runnerTask.setUp();
                runnerTask.beforeTask();
                // NOTE: Regular runner should watch for shouldStop() state on it's own, since we do not pass it down
                runnerTask.task();
                runnerTask.afterTask();
                runnerTask.tearDown();
            } catch (Exception e) {
                log.error(ExceptionUtils.getStackTrace(e));
            } finally {
                runner.unregisterRunner();
            }
        }));

        return runner;
    }

    /**
     * Creates a looped task executor - it runs a before task and then keeps on running the user tasks until it is told to stop.
     * Used for closed-type load scenarios to control the amount of concurrent users
     * @param task  - Task to execute
     * @return      - KeresUser instance
     */
    public static KeresUser initLoopedUser(Class<? extends KeresUserDefinition> task) {
        KeresUser runner = new KeresUser(Mode.LOOPED);
        runner.setThread(Thread.startVirtualThread(() -> {
            try {
                KeresUserDefinition runnerTask = task.getConstructor().newInstance();

                runner.registerRunner();
                runnerTask.setUp();

                while (runner.isActive && !KeresController.shouldStop()) {
                    runnerTask.beforeTask();
                    runnerTask.task();
                    runnerTask.afterTask();
                }

                runnerTask.tearDown();
            } catch (Exception e) {
                log.error(ExceptionUtils.getStackTrace(e));
            } finally {
                runner.unregisterRunner();
            }
        }));

        return runner;
    }

    /**
     * Creates cycled task executor - it runs a before method, then it executes given amount of tasks, then executes after method and finishes.
     * Used for controlled iteration scenarios when we need to limit the amount of activity per user.
     * @param task              - Task to execute
     * @param cyclesToPerform   - amount of cycles to perform before finishing.
     * @return                  - KeresUser instance
     */
    public static KeresUser initCycledUser(Class<? extends KeresUserDefinition> task, int cyclesToPerform) {
        KeresUser runner = new KeresUser(Mode.CYCLED);
        runner.setThread(Thread.startVirtualThread(() -> {
            try {
                KeresUserDefinition runnerTask = task.getConstructor().newInstance();

                runner.registerRunner();
                runnerTask.setUp();

                for (int cycle = 0; cycle < cyclesToPerform; cycle++) {
                    if (runner.isActive && !KeresController.shouldStop()) {
                        runnerTask.beforeTask();
                        runnerTask.task();
                        runnerTask.afterTask();
                    } else {
                        break;
                    }
                }

                runnerTask.tearDown();
            } catch (Exception e) {
                log.error(ExceptionUtils.getStackTrace(e));
            } finally {
                runner.unregisterRunner();
            }
        }));

        return runner;
    }

    public KeresUser start() {
        if (!runnerThread.isAlive()) {
            runnerThread.start();
            while (!runnerThread.isAlive()) {
                TimeUtils.waitFor(TimeUtils.ONE_MS);
            }
        }

        return this;
    }

    /**
     * Softly requests runner to initiate shut-down.
     * NOTE: Only works for looped and cycled runners, have no effect on regular ones
     */
    public void requestStop() {
        isActive = false;
    }

    /**
     * Sets the activity trigger to false and wait for thread to finish.
     * NOTE: Only works for looped and cycled runners, have no effect on regular ones
     */
    public void stop() {
        isActive = false;
        waitToFinish();
    }

    /**
     * Clues client to stop execution and interrupts the runner thread.
     * This is used to cause controlled user shutdown.
     */
    public void abortExecution() {
        isActive = false;
        runnerThread.interrupt();
    }

    /**
     * Waits until runner thread finished execution.
     * @return - this instance.
     */
    public KeresUser waitToFinish() {
        log.trace("Waiting to finish");
        if (runnerThread.isAlive()) {
            try {
                runnerThread.join();
            } catch (InterruptedException ignored) {}
        }

        return this;
    }

    /**
     * Adds KeresUser to allRunners list.
     * IMPORTANT: This method MUST be called from inside the virtual user thread - it relies on thread ID for registration.
     */
    private void registerRunner() {
        runnerId = Thread.currentThread().threadId();
        log.trace("Registering runner " + runnerId + " in the runners map");
        allRunners.put(runnerId, this);
    }

    /**
     * Removes KeresUser from allRunners list.
     */
    private void unregisterRunner() {
        log.trace("Removing runner " + runnerId + " from the runners map");
        allRunners.remove(runnerId);
    }

    public static enum Mode {
        DEFAULT,    // Default one-shot mode - do 1 task and stop
        LOOPED,     // Keep running until explicitly told to stop
        CYCLED      // Run a number of cycles and then stop. Can also be stopped manually
    }
}
