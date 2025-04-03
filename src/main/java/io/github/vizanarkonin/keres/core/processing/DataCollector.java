package io.github.vizanarkonin.keres.core.processing;

import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.vizanarkonin.keres.core.grpc.RunLogRequest;
import io.github.vizanarkonin.keres.KeresController;
import io.github.vizanarkonin.keres.core.executors.KeresUser;
import io.github.vizanarkonin.keres.core.grpc.KeresGrpcClient;
import io.github.vizanarkonin.keres.core.processing.ResultLog.LogEntry;
import io.github.vizanarkonin.keres.core.utils.KeresFileUtils;
import io.github.vizanarkonin.keres.core.utils.KeresMode;
import io.github.vizanarkonin.keres.core.utils.Response;
import io.github.vizanarkonin.keres.core.utils.TimeUtils;

/**
 * Main entry point for metrics collection and analysis.
 * Serves as a receiver of response data, calculating averages on tick, composing a results file and generating an HTML report to view the results
 */
public class DataCollector {
    private static final Logger                                     log                         = LogManager.getLogger("DataCollector");
    // Used for standalone mode
    private static DataCollector                                    staticInstance;
    private static HashMap<String, DataCollector>                   instances                   = new HashMap<>();                 
    @Setter
    private String                                                  resultsFolder               = "KeresResults";
    @Setter
    private String                                                  testId                      = "";
    @Setter
    private String                                                  testDescription             = "";
    @Getter @Setter
    private String                                                  runUUID                     = "";
    private Thread                                                  statusMonitor;
    private boolean                                                 monitorIsRunning;
    private ConcurrentLinkedQueue<Response>                         resultsCollector            = new ConcurrentLinkedQueue<>();
    private Thread                                                  resultsCollectionThread;
    private boolean                                                 collectionThreadIsRunning;
    private int                                                     runTimeInSeconds            = 0;

    private ArrayList<ResultLog.LogEntry>                           usersOverTimeStatistics     = new ArrayList<>();
    /**
     * Main data storage.
     * Key is a request name in format ({methodName}){name}
     * Value is an array of objects (we're using it instead of custom type to truncate resulting JSON)
     * 0 - request start timestamp
     * 1 - request finish timestamp
     * 2 - total response time
     * 3 - failure status (true - failed, false - passed)
     * 4 - response code
     * 5 - if failed - response body
     */
    private ConcurrentHashMap<String, ArrayList<Object[]>>          requestsLog                 = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, ArrayList<Object[]>>          accumulatedRequestsLog      = new ConcurrentHashMap<>();

    static Runtime rt       = Runtime.getRuntime();
    static long prevTotal   = 0;
    static long prevFree    = rt.freeMemory();

    public static DataCollector get() {
        if (staticInstance == null) {
            staticInstance = new DataCollector();
        }

        return staticInstance;
    }

    public static DataCollector get(String id) {
        if (!instances.containsKey(id)) {
            instances.put(id, new DataCollector());
        }

        return instances.get(id);
    }

    public void dropLogResults() {
        requestsLog.clear();
        accumulatedRequestsLog.clear();
        runTimeInSeconds = 0;
    }

    public void logResponse(Response response) {
        resultsCollector.add(response);
    }

    public void start(String testId, String testDescription) {
        setTestId(testId);
        setTestDescription(testDescription);
        dropLogResults();
        monitorIsRunning = true;
        statusMonitor = new Thread() {
            @Override
            public void run() {
                while (monitorIsRunning || resultsCollector.size() > 0) {
                    tickResults();
                }

                collectionThreadIsRunning = false;
            }
        };
        statusMonitor.start();

        collectionThreadIsRunning = true;
        resultsCollectionThread = new Thread() {
            @Override
            public void run() {
                while (collectionThreadIsRunning) {
                    if (resultsCollector.size() > 0) {
                        try {
                            Response response = resultsCollector.remove();
                            String key = String.format("(%s)%s", response.getRequestMethod(), response.getRequestName());

                            if(!requestsLog.containsKey(key)) {
                                requestsLog.put(key, new ArrayList<>());
                            }
                            if(!accumulatedRequestsLog.containsKey(key)) {
                                accumulatedRequestsLog.put(key, new ArrayList<>());
                            }

                            Object[] entry = new Object[] {
                                response.getStartTime(),
                                response.getFinishTime(),
                                response.getResponseTime(),
                                response.isFailed() ? true : false,
                                response.getResponseCode(),
                                response.isFailed() ? 
                                    response.getResponseContent().isEmpty() ?
                                        response.getFailureCause() == null ?
                                            "" :
                                            response.getFailureCause() :
                                        response.getResponseContent()
                                    : ""
                            };

                            requestsLog
                                .get(key)
                                    .add(entry);
                            accumulatedRequestsLog
                                .get(key)
                                    .add(entry);
                        } catch (Exception e) {
                            System.out.println(e);
                            log.info(e);
                        }
                    }
                    // Thread goes into busy-wait loop if we don't put a delay in here, hence why we got 1ms thread sleep
                    TimeUtils.waitFor(TimeUtils.ONE_MS);
                }
            }
        };
        resultsCollectionThread.start();
    }

    public void startListening(String testId, String testDescription) {
        setRunUUID(UUID.randomUUID().toString());
        setTestId(testId);
        setTestDescription(testDescription);
    }

    private void tickResults() {
        TimeUtils.waitFor(TimeUtils.ONE_SECOND);
        printStatistics();
        
        if (KeresController.getMode() == KeresMode.NODE)
            submitResultsToHub();
    }

    /**
     * Stops the execution, generates report and re-sets the logs storage.
     * Used by runner side - it acts differently depending on specified KeresMode state
     */
    public void stop() {
        System.out.println("Stopping listener");
        monitorIsRunning = false;
        try { statusMonitor.join(); } catch (InterruptedException ignored) {}
        if (KeresController.getMode() == KeresMode.STANDALONE) {
            generateReport();
        } else if (KeresController.getMode() == KeresMode.NODE) {
            submitResultsToHub();
        }
        
        dropLogResults();
    }

    /**
     * Stops results listener on hub side and generates report.
     * @param resultsFolder - folder to unload results to
     */
    public void stopListening(Path resultsFolder) {
        System.out.println("Stopping listener");
        generateReport(resultsFolder, false);
        dropLogResults();
    }

    public void generateReport() {
        generateReport(Paths.get(resultsFolder), true);
    }

    public void generateReport(Path resultsFolderRootPath, boolean createSubFolder) {
        List<Long> timestamps = generateTimestampsList();
        HashMap<String, ResultLog> averageResultsLog = generateAverageResultsMap(timestamps);
        HashMap<String, FailureEntry> failuresLog = generateFailuresMap(timestamps);
        ArrayList<ResultLog.LogEntry> currentUsersLog = generateActiveUsersGraph(timestamps);

        try {
            Files.createDirectories(Paths.get(resultsFolder));
            Path targetPath;
            if (createSubFolder) {
                String resultsFolderPath = (resultsFolder + "/" + TimeUtils.getCurrentDateTimeString() + "-" + testId).replaceAll(" ", "_");
                targetPath = Paths.get(resultsFolderPath);
                Files.createDirectory(targetPath);
            } else {
                targetPath = resultsFolderRootPath;
            }
            
            Files.createDirectory(Paths.get(targetPath + "/res"));
            // Copying over the reporter template and resources
            for (String reporterFile : KeresFileUtils.reporterResourcesList) {
                KeresFileUtils.copyResource(ClassLoader.getSystemClassLoader().getResourceAsStream("report-viewer/" + reporterFile), targetPath + "/" + reporterFile);
            }
            PrintWriter resultsWriter = new PrintWriter(targetPath + "/results.js", "UTF-8");
            resultsWriter.println("const test_id = '" + testId + "';");
            resultsWriter.println("const test_description = '" + testDescription + "';");
            resultsWriter.println("const timestamps = " + new JSONArray(timestamps).toString() + ";");
            resultsWriter.println("const users_timeline = " + new JSONArray(currentUsersLog).toString() + ";");
            resultsWriter.println("const requests_averages_data = " + new JSONObject(averageResultsLog).toString() + ";");
            resultsWriter.println("const failures = " + new JSONObject(failuresLog).toString() + ";");
            resultsWriter.println("const requests_log = " + new JSONObject(requestsLog).toString() + ";");
            resultsWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void submitResultsToHub() {
        synchronized (requestsLog) {
            JSONObject request = new JSONObject();
            request.put("requests_log", requestsLog);
            request.put("users_timeline", Arrays.asList(new ResultLog.LogEntry(Instant.now().toEpochMilli(), KeresUser.getAllRunners().size())));

            RunLogRequest runLogRequest = RunLogRequest.newBuilder()
                .setProjectId(KeresGrpcClient.get().getProjectId())
                .setNodeId(KeresGrpcClient.get().getNodeId())
                .setRunUUID(runUUID)
                .setLogContents(request.toString())
                .build();

            try {
                KeresGrpcClient.get().controlsBlockingStub.submitResults(runLogRequest);
            } catch (Exception e) {
                log.error(e);
                log.error(ExceptionUtils.getStackTrace(e));
                log.error("Message was: " + request.toString());
            }

            requestsLog.values().forEach(value -> value.clear());
        }
    }

    public synchronized void processNodeResults(JSONObject input) {
        JSONObject reqLog = input.getJSONObject("requests_log");
        JSONArray usersLog = input.getJSONArray("users_timeline");

        reqLog
            .keySet()
            .forEach(key -> {
                if (reqLog.isNull(key)) {
                    log.warn("(processNodeResults) Value with key " + key + " was null. Ignoring");
                    return;
                }
                // Layer one - map entry.
                reqLog
                    .getJSONArray(key)
                    .forEach(keyEntry -> {
                        // Layer two - individual log entries
                        if (keyEntry instanceof JSONArray) {
                            JSONArray arr = (JSONArray) keyEntry;
                            Object[] entry = new Object[] {
                                arr.getLong(0),
                                arr.getLong(1),
                                arr.getLong(2),
                                arr.getBoolean(3),
                                arr.getInt(4),
                                arr.getString(5)
                            };

                            if (!requestsLog.containsKey(key)) {
                                requestsLog.put(key, new ArrayList<Object[]>());
                            }
                            requestsLog.get(key).add(entry);
                        }
                    });
            });
        
        usersLog
            .forEach(entry -> {
                if (entry instanceof JSONObject) {
                    JSONObject obj = (JSONObject) entry;
                    usersOverTimeStatistics.add(new LogEntry(obj.getLong("timeStamp"), obj.getInt("logValue")));
                }
            });
    }

    public void printStatistics() {
        int activeUsersCount = KeresUser.getAllRunners().size();
        runTimeInSeconds += 1;
        System.out.println("------------------------------------------------------------------------------------------");
        System.out.println(TimeUtils.getISODateString());
        System.out.println("Runnning for: " + secondsToTimeString(runTimeInSeconds));
        System.out.println("Active virtual users - " + activeUsersCount);
        System.out.println("------------------------------------------------------------------------------------------");
        int totalRequests = 0;
        
        for (String key : accumulatedRequestsLog.keySet()) {
            ArrayList<Object[]> entry = accumulatedRequestsLog.get(key);
            totalRequests += entry.size();
                try {
                    System.out.println(
                    String.format("%s - %d requests - %d failed", 
                        key, entry.size(), entry.stream().filter(object -> (boolean) object[3] == true).count()));
                } catch (Exception e) {
                    log.error(e);
                    log.error(ExceptionUtils.getStackTrace(e));
                }
        }
        
        synchronized(usersOverTimeStatistics) {
            usersOverTimeStatistics.add(new LogEntry(Instant.now().toEpochMilli(), activeUsersCount));
        }
        System.out.println("------------------------------------------------------------------------------------------");
        System.out.println(String.format("TOTAL - %s requests", totalRequests));
        System.out.println("------------------------------------------------------------------------------------------");
        long total = rt.totalMemory();
        long free = rt.freeMemory();
        if (total != prevTotal || free != prevFree) {
            long used = total - free;
            long prevUsed = (prevTotal - prevFree);
            System.out.println(
                    "Memory statistics: " +
                            " Total: " + (total / 1024) / 1024 +
                            " Mb, Used: " + (used / 1024) / 1024 +
                            " Mb, ∆Used: " + ((used - prevUsed) / 1024) / 1024 +
                            " Mb, Free: " + (free / 1024) / 1024 +
                            " Mb, ∆Free: " + ((free - prevFree) / 1024) / 1024 + " Mb");
            prevTotal = total;
            prevFree = free;
        }
    }

    // ##########################################################################################
    // Service methods
    // ##########################################################################################

    /**
     * Iterates over requests in the run, extracts the earliest and the latest start timestamps and creates a timestamp frame with 
     * 1 second step
     * @return List of Unix timestamps
     */
    private List<Long> generateTimestampsList() {
        List<Long> timestamps = new ArrayList<>();
        // TODO: It should be possible to be done with streams. Revisit it later
        Long firstStamp = 0L, lastStamp = 0L;
        for (Entry<String, ArrayList<Object[]>> entry : requestsLog.entrySet()) {
            Optional lowestEntry = entry.getValue().stream().min((obj1, obj2) -> Long.compare((long) obj1[0], (long) obj2[0]));
            Optional highestEntry = entry.getValue().stream().max((obj1, obj2) -> Long.compare((long) obj1[0], (long) obj2[0]));

            if (!lowestEntry.isPresent() && !highestEntry.isPresent())
                continue;
            
            long lowestEntryValue = (long)((Object[])lowestEntry.get())[0];
            long highestEntryValue =(long)((Object[])highestEntry.get())[0];
            if (firstStamp == 0L) {
                firstStamp = lowestEntryValue;
            } else {
                if (firstStamp > lowestEntryValue) {
                    firstStamp = lowestEntryValue;
                }
            }

            if (lastStamp == 0L) {
                lastStamp = highestEntryValue;
            } else {
                if (lastStamp < highestEntryValue) {
                    lastStamp = highestEntryValue;
                }
            }
        }

        // Adding 1 second prior and after the corner values to make sure we don't miss any data entries
        for (long entry = firstStamp - 1000; entry < lastStamp + 1000; entry += 1000) {
            timestamps.add(entry);
        }

        return timestamps;
    }

    private HashMap<String, ResultLog> generateAverageResultsMap(List<Long> timestamps) {
        HashMap<String, ResultLog> results = new HashMap<>();

        long previousStamp = 0;
        for (int index = 0; index < timestamps.size(); index++) {
            if (index == 0) {
                previousStamp = timestamps.get(index);
                continue;
            } else {
                previousStamp = timestamps.get(index - 1);
            }

            Long currentStamp = timestamps.get(index);
            final long prevStamp = previousStamp;
            for (Entry<String, ArrayList<Object[]>> entry : requestsLog.entrySet()) {
                List<Object[]> requests = entry
                    .getValue()
                    .stream()
                        .filter(object -> (Long) object[0] > prevStamp && (Long) object[0] <= currentStamp).collect(Collectors.toList());

                long averageResponseTime = 0;
                long requestsPerSecond = 0;
                long failures = 0;
                if (requests.size() > 0) {
                    try {
                        averageResponseTime = requests.stream().mapToLong(object -> (Long) object[2]).sum() / requests.size();
                        requestsPerSecond = requests.size();
                        failures = requests.stream().filter(object -> (Boolean) object[3]).count();
                    } catch (Exception e) {
                        log.error(e);
                        log.error(ExceptionUtils.getStackTrace(e));
                    }
                }

                if (!results.containsKey(entry.getKey())) {
                    results.put(entry.getKey(), new ResultLog(entry.getKey()));
                }

                results.get(entry.getKey()).logRequest(requests.size(), averageResponseTime, requestsPerSecond, failures, currentStamp);
            }
        }

        return results;
    }

    private HashMap<String, FailureEntry> generateFailuresMap(List<Long> timestamps) {
        HashMap<String, FailureEntry> failures = new HashMap<>();

        for (Entry<String, ArrayList<Object[]>> entry : requestsLog.entrySet()) {
            List<Object[]> failedRequests = entry
                .getValue()
                .stream()
                    .filter(object -> (boolean) object[3] == true)
                    .collect(Collectors.toList());

            for (Object[] object : failedRequests) {
                int responseCode = (int) object[4];
                String message = (String) object[5];
                if (message != null)
                    message = message.length() > 200 ? message.substring(0, 200) + "..." : message;
                else
                    message = "null";
                String key = String.format("%s -- Code %d -- Cause: '%s'", entry.getKey(), responseCode, message);

                if (!failures.containsKey(key)) {
                    failures.put(key, new FailureEntry(key, responseCode, (String) object[5]));
                }

                failures.get(key).logFailure();
            }
        }

        return failures;
    }

    private ArrayList<ResultLog.LogEntry> generateActiveUsersGraph(List<Long> timestamps) {
        ArrayList<ResultLog.LogEntry> users = new ArrayList<>();

        long previousStamp = 0;
        long previousUsersCount = 0;
        for (int index = 0; index < timestamps.size(); index++) {
            if (index == 0) {
                previousStamp = timestamps.get(index);
                continue;
            } else {
                previousStamp = timestamps.get(index - 1);
            }

            Long currentStamp = timestamps.get(index);
            final long prevStamp = previousStamp;
            List<ResultLog.LogEntry> userRecords = usersOverTimeStatistics
                .stream()
                    .filter(record -> record.getTimeStamp() > prevStamp && (Long) record.getTimeStamp() <= currentStamp)
                    .collect(Collectors.toList());

            long usersCount = 0;
            if (userRecords.size() > 0) {
                try {
                    // Can't go for-each route since we'd need final value - which we don't. Using regular for-loop
                    for (ResultLog.LogEntry record : userRecords) {
                        usersCount += record.getLogValue();
                    }
                } catch (Exception e) {
                    log.error(e);
                    log.error(ExceptionUtils.getStackTrace(e));
                }
            }
            // Sometimes we don't hit the correct timing with stream filter, so we end up with a 0-value
            // In this case we will assume that the amount of users was the same as the one in the previous tick
            if (usersCount == 0 && previousUsersCount != 0)
                usersCount = previousUsersCount;
            else
                previousUsersCount = usersCount;

            users.add(new ResultLog.LogEntry(currentStamp, usersCount));
        }

        return users;
    }

    private static String secondsToTimeString(int timeInSeconds) {
        int seconds = 0; int minutes = 0; int hours = 0;
        while (timeInSeconds > 60) {
            minutes += 1;
            timeInSeconds -= 60;
            if (minutes == 60) {
                hours += 1;
                minutes = 0;
            }
        }
        seconds = (int)timeInSeconds;

        return String.format("%s:%s:%s",
                             hours >= 10 ? String.valueOf(hours) : "0" + String.valueOf(hours),
                             minutes >= 10 ? String.valueOf(minutes) : "0" + String.valueOf(minutes), 
                             seconds >= 10 ? String.valueOf(seconds) : "0" + String.valueOf(seconds));
    }
}
