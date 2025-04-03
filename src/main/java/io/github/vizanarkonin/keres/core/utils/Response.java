package io.github.vizanarkonin.keres.core.utils;

import lombok.Getter;

/**
 * This class represents unified response object - due to different types of client supported, it is necessary to keep
 * type uniformity in objects core.processing,
 */
@Getter
public class Response {
    private String requestMethod = "";
    private String requestName = "";
    private long startTime = 0;
    private long finishTime = 0;
    private boolean isFinished = false;
    private long responseTime = 0;
    private int responseCode = 0;
    private String responseContent = "";
    private long responseSize = 0;
    private boolean isFailed = false;
    private boolean isSystemFailure = false;
    private String failureCause = "";

    public Response setResponseCode(int responseCode) {
        this.responseCode = responseCode;
        return this;
    }

    public Response setResponseContent(String responseContent) {
        this.responseContent = responseContent;
        return this;
    }

    public Response setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
        return this;
    }

    public Response setRequestName(String requestName) {
        this.requestName = requestName;
        return this;
    }

    public Response setStartTime(long value) {
        this.startTime = value;

        return this;
    }

    public Response setFinishTime(long value) {
        this.finishTime = value;

        return this;
    }

    public Response setFinished(boolean value) {
        this.isFinished = value;

        return this;
    }

    public Response setResponseSize(long responseSize) {
        this.responseSize = responseSize;
        return this;
    }

    public Response setResponseTime(long responseTime) {
        this.responseTime = responseTime;
        return this;
    }

    public Response setFailed(boolean value) {
        this.isFailed = value;
        return this;
    }

    public Response setSystemFailure(boolean value) {
        this.isSystemFailure = value;
        return this;
    }

    public Response setFailureCause(String value) {
        this.failureCause = value;

        return this;
    }
}
