package io.github.vizanarkonin.keres.core.ScenarioBuilder;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.vizanarkonin.keres.core.executors.ScenarioExecutor;

/**
 * Static processors storage.
 * Provides command-to-Runnable processors for every supported command.
 * NOTE: Functions from this class are accessed via reflection, and each function name is lower-cased,
 * so when making new functions, make sure to follow these rules:
 * - Function name should repeat the lower-cased command name
 * - Function must be static
 * - Function must take 2 arguments - command string and parent ScenarioTranslator instance
 * - Function must return a Consumer instance with accepting type of ScenarioExecutor
 */
public class Commands {
    private static final String AMOUNT_REGEX = "amount:(\\d+)";
    private static final String HOURS_REGEX = "hours:(\\d+)";
    private static final String MINUTES_REGEX = "minutes:(\\d+)";
    private static final String SECONDS_REGEX = "seconds:(\\d+)";
    private static final String TIMES_REGEX = "times:(\\d+)";
    private static final String CYCLES_TO_RUN_REGEX = "cyclesToRun:(\\d+)";
    private static final String CURLY_BRACES_CONTENT = "\\{([^}]*)\\}";

    /**
     * CreateUsers command processor.
     * Command example:
     *      CreateUsers(amount:20);
     * @param command       - command string to process
     * @param translator    - ScenarioTranslator instance
     * @return              - Consumer instance
     */
    public static Consumer<ScenarioExecutor> createusers(String command, ScenarioTranslator translator) {
        try {
            int amount = extractAmountFrom(command);

            return (executor) -> {
                executor.createUsersAtOnce(amount);
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to process command " + command + "\nReason: " + e);
        }
    }

    /**
     * CreateUsersOverTime command processor.
     * Command example:
     *      CreateUsersOverTime(amount:500,hours:1,minutes:30,seconds:30);
     * @param command       - command string to process
     * @param translator    - ScenarioTranslator instance
     * @return              - Consumer instance
     */
    public static Consumer<ScenarioExecutor> createusersovertime (String command, ScenarioTranslator translator) {
        int amount = extractAmountFrom(command);
        long period = extractPeriodFrom(command);

        return (executor) -> {
            executor.createUsersOverPeriodOfTime(amount, Duration.ofMillis(period));
        };
    }

    /**
     * CreateCycledUsers command processor.
     * Command example:
     *      CreateCycledUsers(amount:20,cyclesToRun:50);
     * @param command       - command string to process
     * @param translator    - ScenarioTranslator instance
     * @return              - Consumer instance
     */
    public static Consumer<ScenarioExecutor> createcycledusers(String command, ScenarioTranslator translator) {
        int amount = extractAmountFrom(command);
        int cyclesToRun = extractCyclesCountFrom(command);

        return (executor) -> {
            executor.createCycledUsersAtOnce(amount, cyclesToRun);
        };
    }

    /**
     * CreateCycledUsersOverTime command processor.
     * Command example:
     *      CreateCycledUsersOverTime(amount:500,cyclesToRun:50,hours:1,minutes:30,seconds:30);
     * @param command       - command string to process
     * @param translator    - ScenarioTranslator instance
     * @return              - Consumer instance
     */
    public static Consumer<ScenarioExecutor> createcycledusersovertime(String command, ScenarioTranslator translator) {
        int amount = extractAmountFrom(command);
        int cyclesToRun = extractCyclesCountFrom(command);
        long period = extractPeriodFrom(command);

        return (executor) -> {
            executor.createCycledUsersOverPeriodOfTime(amount, cyclesToRun, Duration.ofMillis(period));
        };
    }

    /**
     * RemoveCycledUsers command processor.
     * Command example:
     *      RemoveCycledUsers(amount:20);
     * @param command       - command string to process
     * @param translator    - ScenarioTranslator instance
     * @return              - Consumer instance
     */
    public static Consumer<ScenarioExecutor> removecycledusers(String command, ScenarioTranslator translator) {
        int amount = extractAmountFrom(command);

        return (executor) -> {
            executor.removeCycledUsers(amount);
        };
    }

    /**
     * RemoveCycledUsersOverTime command processor.
     * Command example:
     *      RemoveCycledUsersOverTime(amount:500,hours:1,minutes:30,seconds:30);
     * @param command       - command string to process
     * @param translator    - ScenarioTranslator instance
     * @return              - Consumer instance
     */
    public static Consumer<ScenarioExecutor> removecycledusersovertime(String command, ScenarioTranslator translator) {
        int amount = extractAmountFrom(command);
        long period = extractPeriodFrom(command);

        return (executor) -> {
            executor.removeCycledUsersOverTime(amount, Duration.ofMillis(period));
        };
    }
    
    /**
     * CreateLoopedUsers command processor.
     * Command example:
     *      CreateLoopedUsers(amount:20);
     * @param command       - command string to process
     * @param translator    - ScenarioTranslator instance
     * @return              - Consumer instance
     */
    public static Consumer<ScenarioExecutor> createloopedusers(String command, ScenarioTranslator translator) {
        try {
            int amount = extractAmountFrom(command);

            return (executor) -> {
                executor.createLoopedUsersAtOnce(amount);
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to process command " + command + "\nReason: " + e);
        }
    }

    /**
     * CreateLoopedUsersOverTime command processor.
     * Command example:
     *      CreateLoopedUsersOverTime(amount:500,hours:1,minutes:30,seconds:30);
     * @param command       - command string to process
     * @param translator    - ScenarioTranslator instance
     * @return              - Consumer instance
     */
    public static Consumer<ScenarioExecutor> createloopedusersovertime(String command, ScenarioTranslator translator) {
        try {
            int amount = extractAmountFrom(command);
            long period = extractPeriodFrom(command);

            return (executor) -> {
                executor.createLoopedUsersOverPeriodOfTime(amount, Duration.ofMillis(period));
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to process command " + command + "\nReason: " + e);
        }
    }

    /**
     * RemoveLoopedUsers command processor.
     * Command example:
     *      RemoveLoopedUsers(amount:20);
     * @param command       - command string to process
     * @param translator    - ScenarioTranslator instance
     * @return              - Consumer instance
     */
    public static Consumer<ScenarioExecutor> removeloopedusers(String command, ScenarioTranslator translator) {
        int amount = extractAmountFrom(command);

        return (executor) -> {
            executor.removeLoopedUsers(amount);
        };
    }

    /**
     * RemoveLoopedUsersOverTime command processor.
     * Command example:
     *      RemoveLoopedUsersOverTime(amount:500,hours:1,minutes:30,seconds:30);
     * @param command       - command string to process
     * @param translator    - ScenarioTranslator instance
     * @return              - Consumer instance
     */
    public static Consumer<ScenarioExecutor> removeloopedusersovertime(String command, ScenarioTranslator translator) {
        int amount = extractAmountFrom(command);
        long period = extractPeriodFrom(command);

        return (executor) -> {
            executor.removeLoopedUsersOverTime(amount, Duration.ofMillis(period));
        };
    }
    
    /**
     * Delay command processor.
     * Command example:
     *      Delay(hours:1,minutes:30,seconds:30);
     * @param command       - command string to process
     * @param translator    - ScenarioTranslator instance
     * @return              - Consumer instance
     */
    public static Consumer<ScenarioExecutor> delay(String command, ScenarioTranslator translator) {
        try {
            long delayTime = extractPeriodFrom(command);

            return (executor) -> {
                executor.waitFor(delayTime);
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to process command " + command + "\nReason: " + e);
        }
    }

    /**
     * Repeat command processor.
     * Command example:
     *      Repeat(times:5) {
     *          CreateLoopedUsers(amount:20);
     *          Delay(minutes:1);
     *      }
     * @param command       - command string to process
     * @param translator    - ScenarioTranslator instance
     * @return              - Consumer instance
     */
    public static Consumer<ScenarioExecutor> repeat(String command, ScenarioTranslator translator) {
        try {
            int times = extractTimesFrom(command);
            String innerCommands = "";

            Matcher matcher = Pattern.compile(CURLY_BRACES_CONTENT).matcher(command);
            if (matcher.find()) {
                innerCommands = matcher.group(1);
            } else {
                throw new RuntimeException("Failed to find contents of curly braces section");
            }

            for (int index = 0; index < times; index++) {
                translator.process(innerCommands);
            }

            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to process command " + command + "\nReason: " + e);
        }
    }

    /**
     * WaitForAllClientsToFinish command processor.
     * Command example:
     *      WaitForAllClientsToFinish();
     * @param command       - command string to process
     * @param translator    - ScenarioTranslator instance
     * @return              - Consumer instance
     */
    public static Consumer<ScenarioExecutor> waitforallclientstofinish(String command, ScenarioTranslator translator) {
        return (executor) -> {
            executor.waitForAllRunnersToFinish();
        };
    }

    // ############################################################################################
    // Service methods
    // ############################################################################################

    /**
     * Processes the parameters set like 'hours:1,minutes:30,seconds:30', adds up given time and
     * returns their total millisecond value
     * @param command   - command string to process
     * @return
     */
    private static long extractPeriodFrom(String command) {
        try {
            int hours = 0;
            int minutes = 0;
            int seconds = 0;

            Matcher matcher = Pattern.compile(HOURS_REGEX).matcher(command);
            if (matcher.find()) {
                hours = Integer.parseInt(matcher.group(1));
            }

            matcher = Pattern.compile(MINUTES_REGEX).matcher(command);
            if (matcher.find()) {
                minutes = Integer.parseInt(matcher.group(1));
            }

            matcher = Pattern.compile(SECONDS_REGEX).matcher(command);
            if (matcher.find()) {
                seconds = Integer.parseInt(matcher.group(1));
            }

            long delayTime = 0;
            if (hours > 0)
                delayTime += hours * 3600000;
            if (minutes > 0)
                delayTime += minutes * 60000;
            if (seconds > 0)
                delayTime += seconds * 1000;
            
            return delayTime;
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract delay time from command '" + command + "'\nReason: " + e);
        }
    }

    /**
     * Processes the amount: parameter and returns it's value
     * @param command   - command string to process
     * @return
     */
    private static int extractAmountFrom(String command) {
        Matcher matcher = Pattern.compile(AMOUNT_REGEX).matcher(command);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        } else {
            throw new RuntimeException("Failed to find value for amount parameter");
        }
    }

    /**
     * Processes the times: parameter and returns it's value
     * @param command   - command string to process
     * @return
     */
    private static int extractTimesFrom(String command) {
        Matcher matcher = Pattern.compile(TIMES_REGEX).matcher(command);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        } else {
            throw new RuntimeException("Failed to find value for times parameter");
        }
    }

    /**
     * Processes the cyclesToRun: parameter and returns it's value
     * @param command   - command string to process
     * @return
     */
    private static int extractCyclesCountFrom(String command) {
        Matcher matcher = Pattern.compile(CYCLES_TO_RUN_REGEX).matcher(command);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        } else {
            throw new RuntimeException("Failed to find value for cyclesToRun parameter");
        }
    }
}
