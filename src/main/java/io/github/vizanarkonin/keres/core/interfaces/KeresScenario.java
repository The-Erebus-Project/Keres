package io.github.vizanarkonin.keres.core.interfaces;

import java.lang.reflect.InvocationTargetException;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.vizanarkonin.keres.KeresController;
import io.github.vizanarkonin.keres.core.executors.ScenarioExecutor;
import io.github.vizanarkonin.keres.core.processing.DataCollector;

/*
 * Base type for scenario classes.
 * NOTE: If derived classes need to override setUp and.or tearDown methods - 
 * make sure to include super-class call in the override.
 */
public abstract class KeresScenario {
    protected static final Logger log = LogManager.getLogger("KeresScenario");
    protected KeresScenarioMetaData metaData;
    protected ScenarioExecutor scenarioExecutor;

    public KeresScenario() {
        try {
            String userDefName = (String) KeresController.getConfigType().getMethod("getUserDefinitionName").invoke(null, null);
            scenarioExecutor = ScenarioExecutor.init(userDefName);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            log.error("Failed to initialize KeresScenario instance. Error was:");
            log.error(ExceptionUtils.getStackTrace(e));
        }
        
    }

    public void setUp() {
        metaData = this.getClass().getAnnotation(KeresScenarioMetaData.class);
        DataCollector.get().start(metaData.scenarioId(), metaData.description());
    };

    public abstract void execute();
    
    public void tearDown() {
        DataCollector.get().stop();
    };
}
