package io.github.vizanarkonin.keres.core.grpc;

import lombok.Getter;

public enum KeresGrpcConnectionMode {
    DEFAULT("default"),     // Default - uses transport security
    PLAINTEXT("plaintext"), // Uses plaintext
    TLS("tls");             // Uses TLS auth. Requires keystore

    @Getter
    private final String value;

    private KeresGrpcConnectionMode(String value) {
        this.value = value;
    }

    public static KeresGrpcConnectionMode fromString(String value) {
        switch (value.toLowerCase()) {
            case "default":     return KeresGrpcConnectionMode.DEFAULT;
            case "plaintext":   return KeresGrpcConnectionMode.PLAINTEXT;
            case "tls":         return KeresGrpcConnectionMode.TLS;
            default:            throw new RuntimeException("Unknown connection mode value - " + value);
        }
    }
}
