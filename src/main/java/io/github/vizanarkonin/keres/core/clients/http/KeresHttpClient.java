package io.github.vizanarkonin.keres.core.clients.http;

import org.assertj.core.api.SoftAssertions;

import io.github.vizanarkonin.keres.KeresController;
import io.github.vizanarkonin.keres.core.clients.KeresClientBase;
import io.github.vizanarkonin.keres.core.clients.actions.ActionController;
import io.github.vizanarkonin.keres.core.clients.actions.ParallelAction;
import io.github.vizanarkonin.keres.core.clients.http.builders.KeresHttpRequest;
import io.github.vizanarkonin.keres.core.processing.DataCollector;
import io.github.vizanarkonin.keres.core.utils.Response;
import io.github.vizanarkonin.keres.core.utils.Tuple;

import java.io.IOException;
import java.net.CookieManager;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * HTTP client implementation, wired to core Keres systems like data colelctor.
 * Used to consume request builder instances, execute said requests and report the results to collector.
 */
public class KeresHttpClient extends KeresClientBase<KeresHttpClient> {
    /**
     * In order to make requests builder work without passing any dependant objects, we create a ThreadLocal pool for clients with a getter.
     * This will allow builder (or any entity, at that) to access the client and extract any required session data.
     * It's not the most elegant solution, but it'll do for the time being.
     */
    private static final    ThreadLocal<KeresHttpClient>    clients = new ThreadLocal<>();
    public final            HttpClient                      httpClient;
    public final            CookieManager                   cookieManager;
    private static final    ExecutorService                 executor = Executors.newVirtualThreadPerTaskExecutor();
    public final            HashMap<String, String>         headers = new HashMap<>();

    public static KeresHttpClient getClientForThread() {
        return clients.get();
    }

    public KeresHttpClient() {
        cookieManager = new CookieManager();
        httpClient = HttpClient
            .newBuilder()
            .executor(executor)
            .version(Version.HTTP_1_1)
            .cookieHandler(cookieManager)
            .followRedirects(Redirect.ALWAYS)
            .build();
        clients.set(this);
    }

    public KeresHttpClient setHeader(String header, String value) {
        String val = processStringExpression(value);

        log.trace("Adding header: " + header + "; " + val);
        headers.put(header, value);
        
        return this;
    }

    public KeresHttpClient removeHeader(String header) {
        if (headers.containsKey(header)) {
            log.trace("Removing header: " + header);
            headers.remove(header);
        }

        return this;
    }

    public KeresHttpClient execute(KeresHttpRequest... requests) {
        for(KeresHttpRequest request : requests) {
            execute(request);
        }

        return this;
    }

    /**
     * Main entry point - takes KeresHttpRequest builder object, executes a request and reports it's results to DataCollector
     * @param request   - KeresHttpRequest instance
     * @return          - Instance of self for chaining
     */
    public KeresHttpClient execute(KeresHttpRequest request) {
        if (KeresController.shouldStop()) {
            return this;
        }
        
        Response response = new Response()
            .setRequestMethod(request.getMethod().methodValue)
            .setRequestName(request.getName());
        long startPoint = System.currentTimeMillis();
        response.setStartTime(startPoint);
        long timeElapsed = 0;
        long finishTime = 0;
        try {
            HttpRequest req = request.build();
            log.trace("Request:\r\n" + req.toString() + "\r\n\r\nBody:\r\n" + (request.getStringBody() == null ? "" : request.getStringBody()) + "\r\n\r\nHeaders:\r\n" + req.headers().toString() + "\r\n");
            HttpResponse<String> res = httpClient.send(request.build(), BodyHandlers.ofString());
            finishTime = System.currentTimeMillis();
            timeElapsed = finishTime - startPoint;

            log.trace(request.getName());
            log.trace("Response:\r\nStatus:\r\n" + res.statusCode() + "\r\n\r\nBody:\r\n" + res.body() + "\r\n\r\nHeaders:\r\n" + res.headers().toString() + "\r\n");
            response
                .setResponseCode(res.statusCode())
                .setResponseContent(res.body())
                .setResponseSize(res.body().length())
                .setFinishTime(finishTime);
            if (res.statusCode() >= 400) {
                response
                    .setFailed(true)
                    .setFailureCause(res.body().substring(0, res.body().length() > 100 ? 100 : res.body().length()));
            }

            if (request.getPostRequestTasks() != null) {
                for (Consumer<Response> task : request.getPostRequestTasks()) {
                    try {
                        task.accept(response);
                    } catch (Exception e) {
                        log.error("Post-request task");
                        log.error(e);
                    }
                }
            }

            if (request.getCheckTasks() != null) {
                SoftAssertions softAssertions = new SoftAssertions();
                request.getCheckTasks().forEach(check -> {
                    check.accept(response, softAssertions);
                });

                try {
                    softAssertions.assertAll();
                } catch(AssertionError e) {
                    log.error("Checks");
                    log.error(e);
                    response
                        .setFailed(true)
                        .setFailureCause(e.getMessage());
                }
            }

            if (request.getSaveTasks() != null) {
                for (Tuple<String, Function<Response, String>> tuple : request.getSaveTasks()) {
                    try {
                        sessionData.storeValue(tuple.getVal1(), tuple.getVal2().apply(response));
                    } catch (Exception e) {
                        log.error("Save task");
                        log.error(e);
                    }
                }
            }

            response.setFinished(true);

        } catch (InterruptedException e) {
            log.warn("Caught interrupt during request. Aborting");
        } catch (IOException e) {
            if (KeresController.isSystemExceptionsAreFails()) {
                response
                    .setResponseCode(0)
                    .setFailed(true)
                    .setFailureCause(e.toString())
                    .setSystemFailure(true)
                    .setResponseSize(0)
                    .setFinished(true);
            }
            
            log.error("Caught IOException: " + e);
            log.error(ExceptionUtils.getStackTrace(e));
        } finally {
            if (KeresController.isPrintFailedRequests() && response.isFailed()) {
                log.info("Request failed: " + response.getRequestName());
                log.info("Request body:\n" + (request.getStringBody() == null ? "" : request.getStringBody()));
                log.info("isFinished: " + response.isFinished());
                log.info("Response body:\n" + 
                    (response.getResponseContent().isEmpty() ?
                        response.getFailureCause() == null ?
                            "" :
                            response.getFailureCause() :
                        response.getResponseContent()
                    ));
            }

            // In case request was interrupted with InterruptedException
            if (!response.isFinished())
                return this;

            if (finishTime == 0) {
                finishTime = System.currentTimeMillis();
            }
            if (timeElapsed == 0) {
                timeElapsed = finishTime - startPoint;
            }

            response
                .setResponseTime(timeElapsed)
                .setFinishTime(finishTime);
            if (response.isSystemFailure() && !KeresController.isSystemExceptionsAreFails())
                return this;
            
            DataCollector.get().logResponse(response);
        }

        return this;
    }

    public KeresHttpClient action(String name, Consumer<KeresHttpClient> task) {
        ActionController
            .sequentialAction(name, () -> { task.accept(this); })
            .execute();

        return this;
    }

    public KeresHttpClient action(String name, KeresHttpRequest builder) {
        ActionController
            .sequentialAction(name, () -> { execute(builder); })
            .execute();

        return this;
    }

    public KeresHttpClient parallelAction(String name, Consumer<KeresHttpClient>... tasks) {
        ParallelAction action = new ParallelAction(name);
        for (Consumer<KeresHttpClient> task : tasks) {
            action.addAndStart(() -> { task.accept(this); });
        }
        action.waitForRequestsToFinish();

        return this;
    }

    public KeresHttpClient parallelAction(String name, KeresHttpRequest... tasks) {
        ParallelAction action = new ParallelAction(name);
        for (KeresHttpRequest task : tasks) {
            action.addAndStart(() -> { execute(task); });
        }
        action.waitForRequestsToFinish();

        return this;
    }

    @Override
    public void start() {
        // Not used - everything is handled in constructor;
    }

    @Override
    public void close() {
        httpClient.shutdownNow();
        clients.remove();
    }
}
