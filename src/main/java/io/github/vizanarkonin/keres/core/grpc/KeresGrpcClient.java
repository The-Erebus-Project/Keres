package io.github.vizanarkonin.keres.core.grpc;

import io.github.vizanarkonin.keres.KeresController;
import io.github.vizanarkonin.keres.core.utils.ThreadHolder;
import io.github.vizanarkonin.keres.core.utils.TimeUtils;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.TlsChannelCredentials;
import io.grpc.stub.StreamObserver;
import lombok.Getter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONArray;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class KeresGrpcClient {
    private static KeresGrpcClient                          instance;
    private static final Logger                             log = LogManager.getLogger("KeresGrpcClient");
    private final ManagedChannel                            channel;

    public final HandshakeSvcGrpc.HandshakeSvcBlockingStub  handshakeStub;
    public final StatusSvcGrpc.StatusSvcBlockingStub        statusStub;
    public final ControlsSvcGrpc.ControlsSvcStub            controlsStreamStub;
    public final ControlsSvcGrpc.ControlsSvcBlockingStub    controlsBlockingStub;

    private StreamObserver<NodeControlRequest>              controlRequestObserver;
    private StreamObserver<MessageResponse>                 controlResponseObserver;

    public boolean                                          connected = false;

    @Getter
    private final String                                    hubAddress;
    @Getter
    private final int                                       hubPort;
    @Getter
    private final int                                       projectId;
    @Getter
    private final String                                    nodeId;
    @Getter
    private final KeresGrpcConnectionMode                   mode;

    private KeresGrpcClient(String hubAddress, int hubPort, int projectId, String nodeId, KeresGrpcConnectionMode mode) throws IOException {
        this.hubAddress     = hubAddress;
        this.hubPort        = hubPort;
        this.projectId      = projectId;
        this.nodeId         = nodeId;
        this.mode           = mode;

        switch (mode) {
            case KeresGrpcConnectionMode.DEFAULT:
                channel = ManagedChannelBuilder
                    .forAddress(hubAddress, hubPort)
                    .build();
                break;
            case KeresGrpcConnectionMode.PLAINTEXT:
                channel = ManagedChannelBuilder
                    .forAddress(hubAddress, hubPort)
                    .usePlaintext()
                    .build();
                break;
            case KeresGrpcConnectionMode.TLS:
                channel = Grpc.newChannelBuilderForAddress(
                    hubAddress, 
                    hubPort, 
                    TlsChannelCredentials.newBuilder()
                        .trustManager(new File("certificates/ca-cert.pem"))
                        .keyManager(
                            new File("certificates/client-cert.pem"), 
                            new File("certificates/client-key.pem"))
                        .build())
                .build();
                break;
            default:
                throw new RuntimeException("Unknown gRPC connection mode - " + mode + ". Should be default, plaintext or tls");
        }
        
        handshakeStub           = HandshakeSvcGrpc.newBlockingStub(channel);
        statusStub              = StatusSvcGrpc.newBlockingStub(channel);
        controlsStreamStub      = ControlsSvcGrpc.newStub(channel);
        controlsBlockingStub    = ControlsSvcGrpc.newBlockingStub(channel);
    }

    public static KeresGrpcClient get() {
        if (instance == null) {
            throw new RuntimeException("Failed to get the instance of KeresGrpcClient - it was not initialized. Make sure you call init() prior to any further actions");
        }

        return instance;
    }

    public static KeresGrpcClient init(String hubAddress, int hubPort, int projectId, String nodeId, KeresGrpcConnectionMode mode) {
        try {
            instance = new KeresGrpcClient(hubAddress, hubPort, projectId, nodeId, mode); 
        } catch (IOException e) {
            log.error(e.toString()); 
            throw new RuntimeException(e); 
        }

        return instance;
    }

    public void connect() {
        if (connected) {
            log.warn("gRPC client is already connected. Skipping");
            return;
        }
        
        log.info(String.format("Attempting to connect to host '%s', port %d", hubAddress, hubPort));
        // First, we execute handshake sequence to figure out whether server will accept us
        HandshakeRequest req = HandshakeRequest.newBuilder()
            .setProjectId(projectId)
            .setNodeId(nodeId)
            .build();
        
        MessageResponse res = handshakeStub.connect(req);
        if (res.getStatus() == ResponseStatus.FAILURE) {
            throw new RuntimeException("Keres gRPC connect - handshake failed. Reason - " + res.getDetails());
        }
        log.info("Handshake successful. Attempting to initiate control stream");

        // After that, we're initializing stream observers
        res = MessageResponse.newBuilder()
            .setStatus(ResponseStatus.ACKNOWLEDGED)
            .setDetails(String.format("{\"projectId\" : %d, \"nodeId\" : %s}", projectId, nodeId))
            .setTimestamp(Instant.now().toEpochMilli())
            .build();
        
        ThreadHolder.init();
        
        controlRequestObserver = new StreamObserver<NodeControlRequest>() {
            @Override
            public void onNext(NodeControlRequest req) {
                switch(req.getCommand()) {
                    case NodeControlCommand.SEND_PARAMETERS_VALUES: {
                        controlsBlockingStub.sendParameterValues(generateNodeParametersRequest());
                        break;
                    }
                    case NodeControlCommand.UPDATE_PARAMETERS_VALUES: {
                        JSONArray args = new JSONArray(req.getParameter());
                        
                        try {
                            KeresController
                                .getConfigType()
                                .getMethod("setNodeParameters", JSONArray.class)
                                .invoke(null, args);
                        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException
                                | SecurityException e) {
                            log.error(e);
                            log.error(ExceptionUtils.getStackTrace(e));
                        }

                        controlsBlockingStub.sendParameterValues(generateNodeParametersRequest());
                        break;
                    }
                    case NodeControlCommand.START_SCENARIO: {
                        ClientStatusMessage statusUpdate = ClientStatusMessage.newBuilder()
                            .setProjectId(projectId)
                            .setNodeId(nodeId)
                            .setStatus(NodeStatus.RUNNING)
                            .setDetails("Starting scenario")
                            .build();
                        statusStub.sendStatusUpdate(statusUpdate);
                        KeresController.runScenario();
                        break;
                    }
                    case NodeControlCommand.STOP_SCENARIO: {
                        log.trace("Received STOP_SCENARIO command");
                        ClientStatusMessage statusUpdate = ClientStatusMessage.newBuilder()
                            .setProjectId(projectId)
                            .setNodeId(nodeId)
                            .setStatus(NodeStatus.STOPPING)
                            .setDetails("Stopping scenario")
                            .build();
                        statusStub.sendStatusUpdate(statusUpdate);
                        log.trace("Sent client status update");

                        KeresController.stopExecution();
                        break;
                    }
                    case NodeControlCommand.SEND_SCENARIOS_LIST: {
                        ScenariosListRequest.Builder requestBuilder = ScenariosListRequest.newBuilder()
                            .setProjectId(projectId)
                            .setNodeId(nodeId);

                        for (Map.Entry<String, List<String>> entry : KeresController.getAllSimulationsMetaData().entrySet()) {
                            String className = entry.getKey();
                            List<String> values = entry.getValue();

                            ScenarioData dataEntry = ScenarioData.newBuilder()
                                .setClassName(className)
                                .setScenarioId(values.get(0))
                                .setDescription(values.get(1))
                                .setChecksum(values.get(2))
                                .build();
                            
                            requestBuilder.addScenarios(dataEntry);
                        }

                        controlsBlockingStub.sendAvailableScenariosList(requestBuilder.build());
                        break;
                    }
                    case NodeControlCommand.PING: {
                        // This command is primarily used to keep the connection alive - in case regular keepalive won't cut it.
                        log.trace("Received PING request from the server");
                        MessageResponse res = MessageResponse.newBuilder()
                            .setStatus(ResponseStatus.SUCCESS)
                            .setTimestamp(Instant.now().toEpochMilli())
                            .build();
                        controlResponseObserver.onNext(res);
                        break;
                    }
                    case NodeControlCommand.SEND_USER_DEFS_LIST: {
                        UserDefinitionsListRequest.Builder requestBuilder = UserDefinitionsListRequest.newBuilder()
                            .setProjectId(projectId)
                            .setNodeId(nodeId);

                        for (Map.Entry<String, List<String>> entry : KeresController.getAllUserDefinitionsMetaData().entrySet()) {
                            String className = entry.getKey();
                            List<String> values = entry.getValue();

                            UserDefinitionData dataEntry = UserDefinitionData.newBuilder()
                                .setClassName(className)
                                .setUserDefId(values.get(0))
                                .setDescription(values.get(1))
                                .setChecksum(values.get(2))
                                .build();
                            
                            requestBuilder.addUserDefinitions(dataEntry);
                        }

                        controlsBlockingStub.sendAvailableUserDefinitionsList(requestBuilder.build());
                        break;
                    }
                    default:
                        log.error("Unhandled NodeControlCommand - " + req.getCommand());
                        break;
                }
            }

            @Override
            public void onCompleted() {
                
            }

            @Override
            public void onError(Throwable exception) {
                log.error(exception.toString());
                log.error(ExceptionUtils.getStackTrace(exception));
                if(exception.getMessage().contains("UNAVAILABLE:")) {
                    log.warn("UNAVAILABLE detected. Attempting to re-connect");
                    onCompleted();
                    
                    KeresGrpcClient client = KeresGrpcClient.get();
                    client.connected = false;
                    int attempts = 0;
                    while (attempts < 10 || client.connected) {
                        try {
                            client.connect();
                            return;
                        } catch (Exception e) {
                            attempts++;
                            log.error(e);
                            log.error("Attempt № " + attempts + " failed. Retrying in 5 seconds");
                            TimeUtils.waitFor(Duration.ofSeconds(5));
                        }
                    }
                    
                    log.error("Exceeded 10 reconnection attempts. Shutting down");
                    ThreadHolder.shutDown();
                }
            }

            public CurrentNodeParameters generateNodeParametersRequest() {
                CurrentNodeParameters.Builder builder = CurrentNodeParameters
                    .newBuilder()
                        .setProjectId(projectId)
                        .setNodeId(nodeId);

                    try {
                        KeresController
                            .getConfigType()
                            .getMethod("populateNodeParameters", CurrentNodeParameters.Builder.class)
                            .invoke(null, builder);
                    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException
                            | SecurityException e) {
                        log.error(e);
                        log.error(ExceptionUtils.getStackTrace(e));
                    }
                    
                    
                    return builder.build();
            }
        };
        controlResponseObserver = controlsStreamStub.streamControls(controlRequestObserver);

        controlResponseObserver.onNext(res);
        connected = true;
        log.info("Control stream initiated. Node is now online");
    }

}
