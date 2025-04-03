package io.github.vizanarkonin.keres.core.ScenarioBuilder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.lang3.exception.ExceptionUtils;

import io.github.vizanarkonin.keres.core.executors.ScenarioExecutor;
import lombok.Getter;

/**
 * Entry point for processing text-based load profile scenario into CustomScenario instance.
 * Consumes provided scenario string, processes it and dissects it into individual executable tasks.
 * Expected flow:
 * - Translator is initialized and fed the scenario string and task executor that will be performing actions
 * - Translator uses process() functions to diffect the scenario string and generate a list of Runnable tasks
 * - Target object retrieves the tasks using getTasks() method and uses them
 */
public class ScenarioTranslator {

    // This list contains names of functions that have an extended body - curly braces block.
    // Used to determine which separator to use.
    private static final List<String> EXTENDED_FUNCTIONS = Arrays.asList("repeat");
    private static final String SEMICOLON_SEPARATOR = ";";
    private static final String CURLY_SEPARATOR = "}";

    // Containers for initial and processed values. Mostly for debugging purposes.
    private final String providedScenario;
    private final String processedScenario;

    @Getter
    private LinkedList<Consumer<ScenarioExecutor>> tasks = new LinkedList<>();

    private ScenarioTranslator(String providedScenario) {
        this.providedScenario = providedScenario;

        this.processedScenario = providedScenario
            // First we trim the string of all spaces, tabs, line breaks and newlines.
            .replaceAll("\\s+", "")
            // Then we get rid of all comment blocks
            .replaceAll("##(?s:.)[^##]*##", "");
    }

    public static ScenarioTranslator init(String scenarioString) {
        return new ScenarioTranslator(scenarioString);
    }

    /**
     * Top-level entry point - starts dissecting scenario string into individual runnable tasks
     */
    public void process() {
        String processingStream = processedScenario.toLowerCase();
        while(processingStream.contains(SEMICOLON_SEPARATOR) || processingStream.contains(CURLY_SEPARATOR)) {
            String commandName = processingStream.substring(0, processingStream.indexOf("("));
            String command;
            int separatorIndex;
            if (EXTENDED_FUNCTIONS.contains(commandName)) {
                command = processingStream.substring(0, processingStream.indexOf(CURLY_SEPARATOR) + 1);
                separatorIndex = processingStream.indexOf(CURLY_SEPARATOR);
            } else {
                command = processingStream.substring(0, processingStream.indexOf(SEMICOLON_SEPARATOR) + 1);
                separatorIndex = processingStream.indexOf(SEMICOLON_SEPARATOR);
            }

            try {
                Method processingMethod = Commands.class.getMethod(commandName, new Class[] {String.class, ScenarioTranslator.class});
                Consumer<ScenarioExecutor> task = (Consumer<ScenarioExecutor>)processingMethod.invoke(null, command, this);
                if (task != null)
                    tasks.add(task);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Failed to process command '" + command + "'\nCause: " + ExceptionUtils.getStackTrace(e));
            }

            processingStream = processingStream.substring(separatorIndex + 1);
        }
    }

    /**
     * Overload entry point - used for processing nested commands (the ones in curly braces)
     * @param commands - Commands string (contents of curly-braced expression)
     */
    public void process(String commands) {
        String processingStream = commands.toLowerCase();
        while(processingStream.contains(SEMICOLON_SEPARATOR) || processingStream.contains(CURLY_SEPARATOR)) {
            String commandName = processingStream.substring(0, processingStream.indexOf("("));
            String command;
            int separatorIndex;
            if (EXTENDED_FUNCTIONS.contains(commandName)) {
                command = processingStream.substring(0, processingStream.indexOf(CURLY_SEPARATOR) + 1);
                separatorIndex = processingStream.indexOf(CURLY_SEPARATOR);
            } else {
                command = processingStream.substring(0, processingStream.indexOf(SEMICOLON_SEPARATOR) + 1);
                separatorIndex = processingStream.indexOf(SEMICOLON_SEPARATOR);
            }

            try {
                Method processingMethod = Commands.class.getMethod(commandName, new Class[] {String.class, ScenarioTranslator.class});
                Consumer<ScenarioExecutor> task = (Consumer<ScenarioExecutor>)processingMethod.invoke(null, command, this);
                if (task != null)
                    tasks.add(task);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Failed to process command '" + command + "'\nCause: " + ExceptionUtils.getStackTrace(e));
            }

            processingStream = processingStream.substring(separatorIndex + 1);
        }
    }
}
