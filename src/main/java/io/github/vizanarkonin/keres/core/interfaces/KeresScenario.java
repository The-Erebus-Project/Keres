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

    /**
     * Service method. Used to initialize scenario instance and start internal Keres components
     */
    public final void initKeres() {
        metaData = this.getClass().getAnnotation(KeresScenarioMetaData.class);
        DataCollector.get().start(metaData.scenarioId(), metaData.description());
    }

    // User-defined methods. execute() implementation is mandatory, setUp and tearDown are optional
    public void setUp() {};
    public abstract void execute();
    public void tearDown() {};

    /**
     * Service method - used to stop internal components and halt the execution.
     */
    public final void shutDown() {
        DataCollector.get().stop();
    }
}
