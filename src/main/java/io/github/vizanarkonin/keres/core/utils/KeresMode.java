package io.github.vizanarkonin.keres.core.utils;

public enum KeresMode {
    STANDALONE("standalone"),
    NODE("node");

    final String value;

    KeresMode(String value) {
        this.value = value;
    }

    public String toString() {
        return value;
    }
}
