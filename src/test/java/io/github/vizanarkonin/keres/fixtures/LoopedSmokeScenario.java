package io.github.vizanarkonin.keres.fixtures;

import io.github.vizanarkonin.keres.core.interfaces.KeresScenarioMetaData;
import io.github.vizanarkonin.keres.core.interfaces.KeresScenario;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.time.Duration;

@KeresScenarioMetaData(
    scenarioId = "LoopedSmokeTestScenario",
    description = "Simple smoke scenario for testing"
)
public class LoopedSmokeScenario extends KeresScenario {
    private static final Logger logger = LogManager.getLogger("LoopedSmokeScenario");

    @Override
    public void execute() {
        scenarioExecutor
            .createLoopedUsersAtOnce(5)
            .waitFor(Duration.ofSeconds(2))
            .removeLoopedUsers(5)
            .waitForAllRunnersToFinish();
    }
}
