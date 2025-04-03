package io.github.vizanarkonin.keres.core.clients.http.builders;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.vizanarkonin.keres.core.clients.http.KeresHttpClient;
import io.github.vizanarkonin.keres.core.clients.http.builders.KeresHttpRequest.PostPutPatchDeleteBuilder;

/**
 * Multipart-body constructor - used to provide chained builder to populate the form data in multiform format.
 * Accepts both string and binary data (file attachments).
 */
public class HttpRequestMultipartBody {
    private static final Logger         logger = LogManager.getLogger("HttpRequestMultipartBody");

    // Used as a return anchor in case this builder is called from there
    private PostPutPatchDeleteBuilder   parentBuilder;
    private byte[]                      bytes;
    @Getter @Setter
    private String                      boundary;

    public static HttpRequestMultipartBody.Builder init(PostPutPatchDeleteBuilder parentBuilder) {
        HttpRequestMultipartBody instance = new HttpRequestMultipartBody();
        instance.parentBuilder = parentBuilder;

        return new HttpRequestMultipartBody.Builder(instance);
    }

    public String getContentType() {
        return "multipart/form-data; boundary=" + this.getBoundary();
    }

    public byte[] getBody() {
        return this.bytes;
    }

    public PostPutPatchDeleteBuilder build() {
        if (parentBuilder != null) {
            parentBuilder.setStringBody(new String(bytes));
            
            parentBuilder.getParentBuilder().header("Content-Type", getContentType());
            parentBuilder.byteBody(bytes);
        }

        return parentBuilder;
    }

    public static class Builder {
        private final String                DEFAULT_MIMETYPE = "text/plain";
        private HttpRequestMultipartBody    parent;

        @Getter @Setter
        public static class MultiPartRecord {
            private String fieldName;
            private String filename;
            private String contentType;
            private Object content;
        }

        List<MultiPartRecord> parts;

        public Builder(HttpRequestMultipartBody parent) {
            this.parent = parent;
            this.parts = new ArrayList<>();
        }

        public Builder addPart(String fieldName, String fieldValue) {
            String value = KeresHttpClient.getClientForThread().processStringExpression(fieldValue);
            logger.trace("Adding string body - {" + fieldName + " : " + value + "}");
            MultiPartRecord part = new MultiPartRecord();
            part.setFieldName(fieldName);
            part.setContent(value);
            part.setContentType(DEFAULT_MIMETYPE);
            this.parts.add(part);
            return this;
        }

        public Builder addPart(String fieldName, String fieldValue, String contentType) {
            String value = KeresHttpClient.getClientForThread().processStringExpression(fieldValue);
            logger.trace("Adding string body - {" + fieldName + " : " + value + "(" + contentType + ")}");
            MultiPartRecord part = new MultiPartRecord();
            part.setFieldName(fieldName);
            part.setContent(value);
            part.setContentType(contentType);
            this.parts.add(part);
            return this;
        }

        public Builder addPart(String fieldName, Object fieldValue, String contentType, String fileName) {
            logger.trace("Adding string body - {" + fieldName + " : " + fieldValue + "(" + contentType + ", file=" + fileName + ")}");
            MultiPartRecord part = new MultiPartRecord();
            part.setFieldName(fieldName);
            part.setContent(fieldValue);
            part.setContentType(contentType);
            part.setFilename(fileName);
            this.parts.add(part);
            return this;
        }

        public PostPutPatchDeleteBuilder backToRequestBuilder() {
            try {
                String boundary = new BigInteger(256, new SecureRandom()).toString();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                for (MultiPartRecord record : parts) {
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("--" + boundary + "\r\n" + "Content-Disposition: form-data; name=\"" + record.getFieldName());
                    if (record.getFilename() != null) {
                        stringBuilder.append("\"; filename=\"" + record.getFilename());
                    }
                    out.write(stringBuilder.toString().getBytes(StandardCharsets.UTF_8));
                    out.write(("\"\r\n").getBytes(StandardCharsets.UTF_8));
                    Object content = record.getContent();
                    if (content instanceof String) {
                        out.write(("\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                        out.write(((String) content).getBytes(StandardCharsets.UTF_8));
                    }
                    else if (content instanceof byte[]) {
                        out.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                        out.write((byte[]) content);
                    } else if (content instanceof File) {
                        out.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                        Files.copy(((File) content).toPath(), out);
                    } else {
                        out.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                        ObjectOutputStream objectOutputStream = new ObjectOutputStream(out);
                        objectOutputStream.writeObject(content);
                        objectOutputStream.flush();
                    }
                    out.write("\r\n".getBytes(StandardCharsets.UTF_8));
                }
                out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

                parent.bytes = out.toByteArray();
                parent.boundary = boundary;

                logger.trace("Multipart body is built - " + parent.bytes);

                return parent.parentBuilder;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}