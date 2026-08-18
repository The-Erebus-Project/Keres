package io.github.vizanarkonin.keres.fixtures;

import io.github.vizanarkonin.keres.core.interfaces.KeresScenarioMetaData;
import io.github.vizanarkonin.keres.core.interfaces.KeresScenario;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.time.Duration;

@KeresScenarioMetaData(
    scenarioId = "SmokeTestScenario",
    description = "Simple smoke scenario for testing"
)
public class SmokeScenario extends KeresScenario {
    private static final Logger logger = LogManager.getLogger("SmokeScenario");

    @Override
    public void execute() {
        scenarioExecutor
            .createUsersOverPeriodOfTime(5, Duration.ofSeconds(5))
            .waitForAllRunnersToFinish();
    }
}
