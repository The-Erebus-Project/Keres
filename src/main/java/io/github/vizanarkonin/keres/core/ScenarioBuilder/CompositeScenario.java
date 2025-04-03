package io.github.vizanarkonin.keres.core.ScenarioBuilder;

import java.util.LinkedList;
import java.util.function.Consumer;

import io.github.vizanarkonin.keres.core.config.KeresStaticConfigProvider;
import io.github.vizanarkonin.keres.core.executors.ScenarioExecutor;
import io.github.vizanarkonin.keres.core.interfaces.KeresScenario;
import io.github.vizanarkonin.keres.core.interfaces.KeresScenarioMetaData;

@KeresScenarioMetaData(
    description = "TCS-1", 
    scenarioId = "Composite scenario - created from user's input"
)
public class CompositeScenario extends KeresScenario {
    private final LinkedList<Consumer<ScenarioExecutor>> tasks;

    public CompositeScenario(String scenarioString, Class<? extends KeresStaticConfigProvider> configType) {
        try {
            ScenarioTranslator scenarioTranslator = ScenarioTranslator.init(scenarioString);
            scenarioTranslator.process();
            this.tasks = scenarioTranslator.getTasks();
        } catch (SecurityException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void execute() {
        tasks.forEach(task -> task.accept(scenarioExecutor));
    }
}
