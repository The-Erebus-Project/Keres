package io.github.vizanarkonin.keres.core.clients.http.builders;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.api.SoftAssertions;

import io.github.vizanarkonin.keres.core.clients.http.HttpMethod;
import io.github.vizanarkonin.keres.core.clients.http.KeresHttpClient;
import io.github.vizanarkonin.keres.core.utils.Response;
import io.github.vizanarkonin.keres.core.utils.Tuple;
import lombok.Getter;
import lombok.Setter;

/**
 * Main building block of the scenarios - allows user to build a request by chaining in all the required parameters.
 * Builder is not bound to any particular client instance and is rather used as a blueprint - it tells client what to do once it's been fed to it.
 * Since we're working on per-thread principle (1 thread has 1 instance of scenario with initialized client), we use client's getClientForThread()
 * method to get the instance - it is used in cases when we need to access current session data
 */
public class KeresHttpRequest {
    private static final Logger logger = LogManager.getLogger("KeresHttpRequestBuilder");
    @Getter
    private final String                                    name;
    @Getter
    // This value is only used for debugging purposes - it gets printed by the logger
    private String                                          stringBody;
    @Getter
    private final HttpMethod                                method;
    @Getter
    private final HttpRequest.Builder                       builder;
    @Getter
    private List<Consumer<Response>>                        postRequestTasks;
    @Getter
    // Tuple key is value name, Value is a function that takes response body as input and returns a string value to save
    private List<Tuple<String, Function<Response, String>>> saveTasks;
    @Getter
    private List<BiConsumer<Response, SoftAssertions>>      checkTasks;

    private KeresHttpRequest(String name, String url, HttpMethod method) {
        this.name = name;
        this.builder = HttpRequest.newBuilder(URI.create(KeresHttpClient.getClientForThread().processStringExpression(url)));
        this.method = method;
    }

    public static KeresHttpRequest get(String name, String url) {
        return new KeresHttpRequest(name, url, HttpMethod.GET);
    }

    public static PostPutPatchDeleteBuilder post(String name, String url) {
        KeresHttpRequest parentBuilder = new KeresHttpRequest(name, url, HttpMethod.POST);

        return new PostPutPatchDeleteBuilder(parentBuilder, HttpMethod.POST);
    }

    public static PostPutPatchDeleteBuilder patch(String name, String url) {
        KeresHttpRequest parentBuilder = new KeresHttpRequest(name, url, HttpMethod.PATCH);

        return new PostPutPatchDeleteBuilder(parentBuilder, HttpMethod.PATCH);
    }

    public static PostPutPatchDeleteBuilder delete(String name, String url) {
        KeresHttpRequest builder = new KeresHttpRequest(name, url, HttpMethod.DELETE);

        return new PostPutPatchDeleteBuilder(builder, HttpMethod.DELETE);
    }

    public KeresHttpRequest header(String header, String value) {
        String val = KeresHttpClient.getClientForThread().processStringExpression(value);

        logger.trace("Adding header: " + header + "; " + val);
        builder.header(header, val);

        return this;
    }

    public KeresHttpRequest afterRequest(Consumer<Response> task) {
        if (postRequestTasks == null) {
            postRequestTasks = new ArrayList<>();
        }
        postRequestTasks.add(task);

        return this;
    }

    public KeresHttpRequest saveValue(String valueName, Function<Response, String> task) {
        if (saveTasks == null) {
            saveTasks = new ArrayList<>();
        }
        saveTasks.add(new Tuple<String, Function<Response, String>>(valueName, task));

        return this;
    }

    public KeresHttpRequest check(BiConsumer<Response, SoftAssertions> task) {
        if (checkTasks == null) {
            checkTasks = new ArrayList<>();
        }
        checkTasks.add(task);

        return this;
    }

    public HttpRequest build() {
        return builder.build();
    }

    public static class PostPutPatchDeleteBuilder {
        @Getter
        @Setter
        private       String                   stringBody;
        @Getter
        private final HttpMethod               method;
        @Getter
        private final KeresHttpRequest parentBuilder;

        public PostPutPatchDeleteBuilder(KeresHttpRequest builder, HttpMethod method) {
            this.method = method;
            this.parentBuilder = builder;

            parentBuilder.builder.method(method.methodValue, HttpRequest.BodyPublishers.noBody());
        }

        public PostPutPatchDeleteBuilder header(String header, String value) {
            parentBuilder.header(header, value);

            return this;
        }

        public HttpRequestMultipartBody.Builder multipart() {
            return HttpRequestMultipartBody.init(this);
        }

        public HttpRequestURLEncodedFormBody.Builder urlEncodedForm() {
            return HttpRequestURLEncodedFormBody.init(this);
        }

        public PostPutPatchDeleteBuilder stringBody(String value) {
            String val = KeresHttpClient.getClientForThread().processStringExpression(value);
            return rawStringBody(val);
        }

        public PostPutPatchDeleteBuilder rawStringBody(String value) {
            logger.trace("Adding string body - " + stringBody);
            stringBody = value;
            switch (method) {
                case HttpMethod.POST: {
                    parentBuilder.builder.POST(HttpRequest.BodyPublishers.ofString(stringBody));
                    break;
                }
                case HttpMethod.PUT: {
                    parentBuilder.builder.PUT(HttpRequest.BodyPublishers.ofString(stringBody));
                    break;
                }
                case HttpMethod.PATCH: {
                    parentBuilder.builder.method(method.methodValue, HttpRequest.BodyPublishers.ofString(stringBody));
                }
                case HttpMethod.DELETE: {
                    parentBuilder.builder.method(method.methodValue, HttpRequest.BodyPublishers.ofString(stringBody));
                }
                default:
                    break;
            }

            return this;
        }

        public PostPutPatchDeleteBuilder byteBody(byte[] value) {
            logger.trace("Adding byte body - " + value.length + " bytes in total");
            stringBody = "Byte body - " + value.length + " bytes";
            switch (method) {
                case HttpMethod.POST: {
                    parentBuilder.builder.POST(HttpRequest.BodyPublishers.ofByteArray(value));
                    break;
                }
                case HttpMethod.PUT: {
                    parentBuilder.builder.PUT(HttpRequest.BodyPublishers.ofByteArray(value));
                    break;
                }
                case HttpMethod.PATCH:
                case HttpMethod.DELETE: {
                    parentBuilder.builder.method(method.methodValue, HttpRequest.BodyPublishers.ofByteArray(value));
                }
                default:
                    break;
            }

            return this;
        }

        public KeresHttpRequest build() {
            parentBuilder.stringBody = stringBody;
            return parentBuilder;
        }
    }
}
