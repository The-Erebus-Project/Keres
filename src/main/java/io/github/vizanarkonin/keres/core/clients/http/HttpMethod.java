package io.github.vizanarkonin.keres.core.clients.http;

public enum HttpMethod {
    GET("GET"),
    POST("POST"),
    PUT("PUT"),
    PATCH("PATCH"),
    DELETE("DELETE");
    
    public String methodValue;

    HttpMethod(String value) {
        this.methodValue = value;
    }
}
