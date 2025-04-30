package io.github.vizanarkonin.keres;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.github.vizanarkonin.keres.core.grpc.ClientStatusMessage;
import io.github.vizanarkonin.keres.core.grpc.NodeStatus;
import io.github.vizanarkonin.keres.core.ScenarioBuilder.CompositeScenario;
import io.github.vizanarkonin.keres.core.config.KeresStaticConfigProvider;
import io.github.vizanarkonin.keres.core.grpc.KeresGrpcClient;
import io.github.vizanarkonin.keres.core.grpc.KeresGrpcConnectionMode;
import io.github.vizanarkonin.keres.core.interfaces.KeresScenario;
import io.github.vizanarkonin.keres.core.interfaces.KeresScenarioMetaData;
import io.github.vizanarkonin.keres.core.interfaces.KeresUserDefinition;
import io.github.vizanarkonin.keres.core.interfaces.KeresUserDefinitionMetaData;
import io.github.vizanarkonin.keres.core.processing.DataCollector;
import io.github.vizanarkonin.keres.core.utils.ClassUtils;
import io.github.vizanarkonin.keres.core.utils.KeresMode;
import io.github.vizanarkonin.keres.core.utils.TimeUtils;
import lombok.Getter;
import lombok.Setter;

/**
 * Main entry point for interaction with Gatling framework.
 * Used to initiate test runs, process reports and handle parameters preparation
 */
public class KeresController {
    private static final Logger                                             log                         = LogManager.getLogger("KeresController");

    private static boolean                                                  stopExecution               = false;
    // This flag allows us to print failed results data to logs without lowering logging level to trace
    // Only prints failed requests/responses
    @Getter @Setter
    private static boolean                                                  printFailedRequests         = false;
    // This flag tells us how to treat client exceptions - should they register in report as failures or not
    @Getter @Setter
    private static boolean                                                  systemExceptionsAreFails    = true;
    // Key is scenario class name, value is a tuple-like list (1 element - Simulation ID, 2 element - description, 3 element - checksum String)
    @Getter
    private static HashMap<String, List<String>>                            allSimulationsMetaData      = new HashMap<>();
    private static HashMap<String, Class<? extends KeresScenario>>          allSimulationTypes          = new HashMap<>();
    // Key is user definition class name, value is a tuple-like list (1 element - User def ID, 2 element - description, 3 element - checksum String)
    @Getter
    private static HashMap<String, List<String>>                            allUserDefinitionsMetaData  = new HashMap<>();
    @Getter
    private static HashMap<String, Class<? extends KeresUserDefinition>>    allUserDefinitionTypes      = new HashMap<>();
    @Getter @Setter
    private static KeresMode                                                mode                        = KeresMode.STANDALONE;
    @Getter
    private static Class<? extends KeresStaticConfigProvider>               configType                  = null;
    private static Thread                                                   runnerThread                = null;
    private static Thread                                                   inputReaderThread           = null;

    static {
        /*
         * In order to be able to select scenarios and user definitions from Thanatos's project page - we need to scan the classpath and find
         * suitable classes - the ones with KeresScenarioMetaData and KeresUserDefinitionMetaData annotations.
         * Then we process both target types AND annotation values, preparing the entire package
         */
        List<List<Object>> scenarioClasses = ClassUtils.getClassesByAnnotation("", KeresScenarioMetaData.class);

        for (List<Object> simulationClass : scenarioClasses) {
            Class<? extends KeresScenario> type = (Class<? extends KeresScenario>) simulationClass.get(0);
            String checksum = (String) simulationClass.get(1);
            KeresScenarioMetaData metaData = type.getAnnotation(KeresScenarioMetaData.class);
            
            allSimulationTypes.put(type.getName(), type);
            allSimulationsMetaData.put(type.getName(), Arrays.asList(metaData.scenarioId(), metaData.description(), checksum));
        }

        List<List<Object>> userDefClasses = ClassUtils.getClassesByAnnotation("", KeresUserDefinitionMetaData.class);

        for (List<Object> userDefClass : userDefClasses) {
            Class<? extends KeresUserDefinition> type = (Class<? extends KeresUserDefinition>) userDefClass.get(0);
            String checksum = (String) userDefClass.get(1);
            KeresUserDefinitionMetaData metaData = type.getAnnotation(KeresUserDefinitionMetaData.class);
            
            allUserDefinitionTypes.put(type.getName(), type);
            allUserDefinitionsMetaData.put(type.getName(), Arrays.asList(metaData.userDefId(), metaData.description(), checksum));
        }
    }

    public static void runScenario(String scenarioClassName, String runUUID) {
        if (configType == null) {
            log.error("Config type was not injected. Make sure to call injectConfigProvider() prior to start");
            return;
        }

        if (!allSimulationTypes.containsKey(scenarioClassName)) {
            log.error("Class " + scenarioClassName + " was not found in the classpath. Aborting");
            return;
        }

        if (runnerThread != null) {
            if (runnerThread.isAlive()) {
                log.error("Attempting to run another scenario while previous is still active. Aborting");
                return;
            }
        }

        KeresScenario scenario;
        try {
            // First we check if we have custom scenario set
            String customScenario = (String)KeresController
                .getConfigType()
                .getMethod("getScenario", new Class[]{})
                .invoke(null);
            if (customScenario.isEmpty())
                // If not - proceed with regular, class based scenario
                scenario = allSimulationTypes.get(scenarioClassName).getDeclaredConstructor().newInstance();
            else
                // If yes = use it instead.
                scenario = new CompositeScenario(customScenario, configType);
        } catch(Exception e) {
            log.error("Failed to initialize scenario.\nCause: " + ExceptionUtils.getStackTrace(e));
            return;
        }

        runnerThread = new Thread(() -> {
            try {
                String uuid;
                if (runUUID == null) {
                    uuid = UUID.randomUUID().toString();
                } else {
                    uuid = runUUID;
                }
    
                DataCollector.get().setRunUUID(uuid);
                stopExecution = false;
    
                scenario.setUp();
                scenario.execute();
                scenario.tearDown();
            } catch (Exception e) {
                log.fatal(e);
                log.fatal(ExceptionUtils.getStackTrace(e));
            } finally {
                if (KeresController.getMode() == KeresMode.NODE) {
                    ClientStatusMessage statusMessage = ClientStatusMessage.newBuilder()
                        .setNodeId(KeresGrpcClient.get().getNodeId())
                        .setProjectId(KeresGrpcClient.get().getProjectId())
                        .setStatus(NodeStatus.FINISHED)
                        .build();
                    KeresGrpcClient.get().statusStub.sendStatusUpdate(statusMessage);
                }
            }
        });
        runnerThread.start();

        inputReaderThread = new Thread() {
            @Override
            public void run() {
                BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
                String line = "";

                log.info("Started input reader");
                while (!stopExecution) {
                    try {
                        line = in.readLine();
                        if (line.equalsIgnoreCase("stop")) {
                            KeresController.stopExecution();
                            break;
                        }
                    } catch (IOException e) {
                        log.error(e);
                        log.error(ExceptionUtils.getStackTrace(e));
                    }
                    TimeUtils.waitFor(TimeUtils.ONE_MS);
                }

                log.info("Closed input reader");

                try {
                    in.close();
                } catch (IOException e) {
                    log.error(e);
                    log.error(ExceptionUtils.getStackTrace(e));
                }
            }
        };
        inputReaderThread.setDaemon(true);
        inputReaderThread.start();
    }

    public static void runScenario() {
        try {
            String simulationName = (String) configType.getMethod("getScenarioName").invoke(null, null);
            runScenario(simulationName, null);
        } catch (Exception e) {
            log.error(e);
            log.error(ExceptionUtils.getStackTrace(e));
        }
    }

    public static void waitForScenarioToFinish() {
        if (runnerThread != null) {
            if (runnerThread.isAlive()) {
                try {
                    runnerThread.join();
                } catch (Exception ignored) {}
            }
        }
    }

    public static void initNode(String hubAddress, int hubPort, int projectId, String nodeId, KeresGrpcConnectionMode grpcMode) {
        if (configType == null) {
            throw new RuntimeException("Config type was not injected. Make sure to call injectConfigProvider() prior to start");
        }

        mode = KeresMode.NODE;
        KeresGrpcClient
            .init(
                hubAddress,
                hubPort,
                projectId,
                nodeId,
                grpcMode)
            .connect();
    }

    public static void injectConfigProvider(Class<? extends KeresStaticConfigProvider> configType) {
        try {
            // In case there are no static accessors called before we inject and start using the config type -
            // we trigger the static initialization block manually by initializing an instance of it.
            Class.forName(configType.getName());
        } catch (ClassNotFoundException ignored) {}

        KeresController.configType = configType;
    }

    public static void stopExecution() {
        log.warn("---------------------");
        log.warn("Stop Execution called");
        log.warn("---------------------");
        stopExecution = true;
        runnerThread.interrupt();
    }

    public static boolean shouldStop() {
        return stopExecution;
    }

    public static void setResultsFolderRoot(String location) {
        DataCollector.get().setResultsFolder(location);
    }
}
