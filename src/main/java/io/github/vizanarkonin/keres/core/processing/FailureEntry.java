package io.github.vizanarkonin.keres.core.processing;

import lombok.Getter;

@Getter
public class FailureEntry {
    private String name;
    private int responseCode;
    private String response;
    private long occurrencesCount;

    public FailureEntry(String name, int responseCode, String responseContent) {
        this.name           = name;
        this.responseCode   = responseCode;
        this.response       = responseContent;
        occurrencesCount    = 0;
    }

    public void logFailure() {
        occurrencesCount += 1;
    }

    public void logFailures(long qty) {
        occurrencesCount += qty;
    }

    public void reset() {
        occurrencesCount = 0;
    }
}
