package io.github.vizanarkonin.keres.core.processing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

@Getter
public class ResultLog {
    private String name;
    private long requestsCount;
    private transient List<LogEntry> responseTimesLog;
    private transient List<LogEntry> requestsPerSecondLog;
    private long failuresCount;
    private transient List<LogEntry> failuresLog;

    public ResultLog(String name) {
        this.name = name;

        responseTimesLog = new ArrayList<>();
        requestsPerSecondLog = new ArrayList<>();
        failuresLog = new ArrayList<>();
    }

    public ResultLog logRequest(int requestsCount, long averageResponseTime, long currentRPS, long currentFailures, long timeStamp) {
        requestsCount += requestsCount;
        failuresCount += currentFailures;
        responseTimesLog.add(new LogEntry(timeStamp, averageResponseTime));
        requestsPerSecondLog.add(new LogEntry(timeStamp, currentRPS));
        failuresLog.add(new LogEntry(timeStamp, currentFailures));

        return this;
    }

    public List<LogEntry> getResponseTimesLog() {
        return responseTimesLog;
    }

    public List<LogEntry> getRequestsPerSecondLog() {
        return requestsPerSecondLog;
    }

    public List<LogEntry> getFailuresLog() {
        return failuresLog;
    }

    @Getter
    public static class LogEntry {
        private long timeStamp;
        private long logValue;

        public LogEntry(int value) {
            this.timeStamp = Instant.now().toEpochMilli();
            this.logValue = value;
        }

        public LogEntry(long timeStamp, long value) {
            this.timeStamp = timeStamp;
            this.logValue = value;
        }
    }
}
