package io.github.vizanarkonin.keres.core.clients.http.builders;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.vizanarkonin.keres.core.clients.http.KeresHttpClient;
import io.github.vizanarkonin.keres.core.clients.http.builders.KeresHttpRequest.PostPutPatchDeleteBuilder;

/**
 * A builder for simple URL-encoded form data.
 * Only accepts string data.
 */
public class HttpRequestURLEncodedFormBody {
    private static final Logger                    logger = LogManager.getLogger("HttpRequestURLEncodedFormBody");
    // Used as a return anchor in case this builder is called from there
    private              PostPutPatchDeleteBuilder parentBuilder;
    private              String                    encodedPayload;

    private HttpRequestURLEncodedFormBody(PostPutPatchDeleteBuilder parentBuilder) {
        this.parentBuilder = parentBuilder;
    }

    public static HttpRequestURLEncodedFormBody.Builder init(PostPutPatchDeleteBuilder parentBuilder) {
        HttpRequestURLEncodedFormBody instance = new HttpRequestURLEncodedFormBody(parentBuilder);

        return new HttpRequestURLEncodedFormBody.Builder(instance);
    }

    public String getContentType() {
        return "application/x-www-form-urlencoded;";
    }

    public String getBody() {
        return this.encodedPayload;
    }

    public PostPutPatchDeleteBuilder build() {
        if (parentBuilder != null) {
            parentBuilder.setStringBody(encodedPayload);
            
            parentBuilder.getParentBuilder().header("Content-Type", getContentType());
            parentBuilder.stringBody(encodedPayload);
        }

        return parentBuilder;
    }

    public static class Builder {
        private HttpRequestURLEncodedFormBody   parent;
        private HashMap<String, String>         formData = new HashMap<>();

        public Builder(HttpRequestURLEncodedFormBody parent) {
            this.parent = parent;
        }

        public Builder addValue(String key, String value) {
            String val = KeresHttpClient.getClientForThread().processStringExpression(value);
            logger.trace("Adding form value:{" + key + " : " + val + "}");
            formData.put(key, val);

            return this;
        }

        public Builder addRawValue(String key, String value) {
            logger.trace("Adding raw (unprocessed) form value:{" + key + " : " + value + "}");
            formData.put(key, value);

            return this;
        }

        public PostPutPatchDeleteBuilder backToRequestBuilder() {
            parent.encodedPayload = formData.entrySet()
                .stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
            logger.trace("Request form body is built - " + parent.encodedPayload);

            return parent.build();
        }
    }
}
