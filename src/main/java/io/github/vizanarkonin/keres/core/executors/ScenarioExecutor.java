package io.github.vizanarkonin.keres.core.executors;

import java.time.Duration;
import java.util.Map.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.vizanarkonin.keres.KeresController;
import io.github.vizanarkonin.keres.core.executors.KeresUser.Mode;
import io.github.vizanarkonin.keres.core.interfaces.KeresUserDefinition;
import io.github.vizanarkonin.keres.core.utils.TimeUtils;

/**
 * Base scenario executor. Execution flow depends on selected mode, which in turn will use different task runner threads.
 */
public class ScenarioExecutor {
    private static final Logger                             log             = LogManager.getLogger("TaskExecutor");
    protected final String                                  executorUUID;
    protected final Class<? extends KeresUserDefinition>    task;
    protected ConcurrentHashMap<String, KeresUser>          runners         = new ConcurrentHashMap<>();
    private Thread                                          watcherThread;

    private ScenarioExecutor(Class<? extends KeresUserDefinition> userDef) {
        this.task = userDef;
        executorUUID = UUID.randomUUID().toString();

        watcherThread = new Thread() {
            @Override
            public void run() {
                while (true) {
                    if (KeresController.shouldStop()) {
                        abortExecution();
                        break;
                    }
                    TimeUtils.waitFor(TimeUtils.ONE_MS);
                }
            }
        };

        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    /**
     * Primary initializer - takes in user definition class name as parameter.
     * @param userDefinitionClassName   - User definition class name. Should be a full name with package names included (e.g. users.LandingPageUser).
     * @return                          - ScenarioExecutor instance.
     */
    public static ScenarioExecutor init(String userDefinitionClassName) {
        if (KeresController.getAllUserDefinitionTypes().containsKey(userDefinitionClassName)) {
            Class<? extends KeresUserDefinition> type = KeresController.getAllUserDefinitionTypes().get(userDefinitionClassName);

            return init(type);
        }

        throw new RuntimeException("No KeresUserDefinition class with name " + userDefinitionClassName + " was found in classpath. Make sure you entered correct class name (including packages)");
    }

    /**
     * Secondary initializer - takes KeresUserDefinition class and initializes the executor.
     * @param userDefinition    - KeresUserDefinition class (type)
     * @return                  - ScenarioExecutor instance.
     */
    public static ScenarioExecutor init(Class<? extends KeresUserDefinition> userDefinition) {
        return new ScenarioExecutor(userDefinition);
    }

    // #########################################################################
    // User creation calls
    // #########################################################################

    /**
     * Creates specified number of regular users - they will do 1 single task iteration and then stop.
     * @param number    - number of users to create
     * @return          - this instance for chaining
     */
    public ScenarioExecutor createUsersAtOnce(int number) {
        createUsersAtOnce(number, Mode.DEFAULT, 0);

        return this;
    }

    /**
     * Creates specified number of looped users - they will keep running tasks until stopped by command.
     * @param number    - number of users to create
     * @return          - this instance for chaining
     */
    public ScenarioExecutor createLoopedUsersAtOnce(int number) {
        createUsersAtOnce(number, Mode.LOOPED, 0);

        return this;
    }

    /**
     * Creates specified number of cycled users - they will execute specified amount of tasks and then stop.
     * @param number            - number of users to create
     * @param cyclesToExecute   - number of cycles to run
     * @return                  - this instance for chaining
     */
    public ScenarioExecutor createCycledUsersAtOnce(int number, int cyclesToExecute) {
        createUsersAtOnce(number, Mode.CYCLED, cyclesToExecute);

        return this;
    }

    /**
     * Creates specified number of regular users over specified period of time.
     * @param number    - number of users to create
     * @param period    - period of time to create them over
     * @return          - this instance for chaining
     */
    public ScenarioExecutor createUsersOverPeriodOfTime(int number, Duration period) {
        createUsersOverTime(number, period, Mode.DEFAULT, 0);

        return this;
    }

    /**
     * Creates specified number of looped users over specified period of time.
     * @param number    - number of users to create
     * @param period    - period of time to create them over
     * @return          - this instance for chaining
     */
    public ScenarioExecutor createLoopedUsersOverPeriodOfTime(int number, Duration period) {
        createUsersOverTime(number, period, Mode.LOOPED, 0);

        return this;
    }

    /**
     * Creates specified number of cycled users over specified period of time.
     * @param number            - number of users to create
     * @param cyclesToExecute   - number of cycles to run
     * @param period            - period of time to create them over
     * @return                  - this instance for chaining
     */
    public ScenarioExecutor createCycledUsersOverPeriodOfTime(int number, int cyclesToExecute, Duration period) {
        createUsersOverTime(number, period, Mode.CYCLED, cyclesToExecute);

        return this;
    }

    // #########################################################################
    // User removal calls
    // #########################################################################

    /**
     * Removes given amount of looped users from the pool. 
     * If the amount of users is greater than their actual value - all of them will be removed.
     * @param number    - number of users to remove
     * @return          - this instance for chaining
     */
    public ScenarioExecutor removeLoopedUsers(int number) {
        removeUsers(number, Mode.LOOPED);

        return this;
    }

    /**
     * Removes given amount of cycled users from the pool. 
     * If the amount of users is greater than their actual value - all of them will be removed.
     * @param number    - number of users to remove
     * @return          - this instance for chaining
     */
    public ScenarioExecutor removeCycledUsers(int number) {
        removeUsers(number, Mode.CYCLED);

        return this;
    }

    /**
     * Removes given amount of looped users over specified period of time.
     * @param number    - number of users to remove
     * @param period    - period of time to create them over
     * @return          - this instance for chaining
     */
    public ScenarioExecutor removeLoopedUsersOverTime(int number, Duration period) {
        removeUsersOverTime(number, Mode.LOOPED, period);

        return this;
    }

    /**
     * Removes given amount of cycled users over specified period of time.
     * @param number    - number of users to remove
     * @param period    - period of time to create them over
     * @return          - this instance for chaining
     */
    public ScenarioExecutor removeCycledUsersOverTime(int number, Duration period) {
        removeUsersOverTime(number, Mode.CYCLED, period);

        return this;
    }

    // #########################################################################
    // Utility calls
    // #########################################################################

    /**
     * Halts the execution for given period of time.
     * @param duration  - delay duration.
     * @return          - this instance for chaining
     */
    public ScenarioExecutor waitFor(Duration duration) {
        if (KeresController.shouldStop()) {
            log.trace("waitFor - shouldStop is true");
            return this;
        }

        TimeUtils.waitFor(duration);

        return this;
    }

    /**
     * Halts the execution for given amount of milliseconds
     * @param milliseconds  - delay in milliseconds.
     * @return              - this instance for chaining
     */
    public ScenarioExecutor waitFor(long milliseconds) {
        if (KeresController.shouldStop()) {
            log.trace("waitFor - shouldStop is true");
            return this;
        }

        TimeUtils.waitFor(milliseconds);

        return this;
    }

    /**
     * Block further execution untill all existing runners finish their execution.
     * NOTE: It will wait for any type of runner, so make sure you don't create a deadlock using looped users and this method.
     */
    public void waitForAllRunnersToFinish() {
        runners.values().forEach(runner -> runner.waitToFinish());
    }

    /**
     * Sends stop signal to every active runner, effectively triggering execution stop.
     */
    public void abortExecution() {
        runners.values().forEach(runner -> runner.sendStopSignal());
    }

    // #########################################################################
    // Service methods
    // #########################################################################

    /**
     * Base user creation method - creates given amount of specified users.
     * @param number            - number of users to create.
     * @param userMode          - desired user mode.
     * @param cyclesToExecute   - (For cycled user mode) amount of cycles to execute. Only used for Mode=Cycled
     */
    private void createUsersAtOnce(int number, Mode userMode, int cyclesToExecute) {
        for (int index = 0; index < number; index++) {
            if (KeresController.shouldStop()) {
                log.trace("createUsersAtOnce - shouldStop is true");
                break;
            }

            KeresUser runner;
            switch (userMode) {
                case DEFAULT: 
                    runner = KeresUser.initRegularUser(task);
                    break;
                case LOOPED:
                    runner = KeresUser.initLoopedUser(task);
                    break;
                case CYCLED:
                    runner = KeresUser.initCycledUser(task, cyclesToExecute);
                    break;
                default:
                    throw new RuntimeException("Unknown KeresUser mode - " + userMode);
            }

            synchronized(runners) {
                runners.put(runner.getRunnerUUID(), runner);
            }
        }
    }

    /**
     * Base user creation method - creates given amount of specified users over specified period of time.
     * @param number            - number of users to create.
     * @param period            - period of time to create them over
     * @param userMode          - desired user mode.
     * @param cyclesToExecute   - (For cycled user mode) amount of cycles to execute. Only used for Mode=Cycled
     */
    private void createUsersOverTime(int number, Duration period, Mode userMode, int cyclesToExecute) {
        long delayBetweenCycles = period.toMillis() / number;
        for (int index = 0; index < number; index++) {
            if (KeresController.shouldStop()) {
                log.trace("createUsersOverTime - shouldStop is true");
                break;
            }

            createUsersAtOnce(1, userMode, cyclesToExecute);

            TimeUtils.waitFor(delayBetweenCycles);
        }
    }

    /**
     * Base user removal method - stops and removes given amount of specific users from the pool.
     * @param number    - number of users to remove
     * @param userMode  - user mode to remove
     */
    private void removeUsers(int number, Mode userMode) {
        if (KeresController.shouldStop()) {
            log.trace("removeLoopedUsers - shouldStop is true");
            return;
        }

        List<Entry<String, KeresUser>> runnersToTurnOff = new ArrayList<>(
            runners
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().getMode() == userMode)
                .collect(Collectors.toList()));
        
        if (runnersToTurnOff.size() <= 0) {
            log.info("Requested to remove " + number + " of " + userMode + " users but there are none running. Ignoring");
            return;
        }

        if (number > runnersToTurnOff.size()) {
            log.info("Requested to remove " + number + " of " + userMode + " users but only " + runnersToTurnOff.size() + " were found. Removing " + number + " users");
            runnersToTurnOff = runnersToTurnOff.subList(0, number - 1);
        }

        for (int index = 0; index < number; index++) {
            Entry<String, KeresUser> entry = runnersToTurnOff.get(index);
            entry.getValue().sendStopSignal();
            runners.remove(entry.getKey());
        }
    }

    /**
     * Base user removal method - stops and removes given amount of specific users from the pool over given period of time.
     * @param number    - number of users to remove
     * @param userMode  - user mode to remove
     * @param period    - removal period
     */
    private void removeUsersOverTime(int number, Mode userMode, Duration period) {
        long delayBetweenCycles = period.toMillis() / number;
        for (int index = 0; index < number; index++) {
            if (KeresController.shouldStop()) {
                log.trace("removeUsersOverTime - shouldStop is true");
                break;
            }

            removeUsers(1, userMode);

            TimeUtils.waitFor(delayBetweenCycles);
        }
    }
}
