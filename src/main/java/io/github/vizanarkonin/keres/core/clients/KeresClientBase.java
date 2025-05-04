package io.github.vizanarkonin.keres.core.clients;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.vizanarkonin.keres.KeresController;
import io.github.vizanarkonin.keres.core.clients.actions.ActionController;
import io.github.vizanarkonin.keres.core.clients.actions.ParallelAction;
import io.github.vizanarkonin.keres.core.clients.actions.SequentialAction;
import io.github.vizanarkonin.keres.core.clients.http.KeresHttpClient;
import io.github.vizanarkonin.keres.core.executors.KeresUser;
import io.github.vizanarkonin.keres.core.feeders.KeresFeeder;
import io.github.vizanarkonin.keres.core.processing.DataCollector;
import io.github.vizanarkonin.keres.core.utils.Response;
import io.github.vizanarkonin.keres.core.utils.TimeUtils;
import io.github.vizanarkonin.keres.core.utils.Tuple;

/**
 * Base class for implementing custom client protocols.
 * Provides base and common methods and attributes that are used by pretty much any implementation.
 */
public abstract class KeresClientBase<T extends KeresClientBase<T>> {
    protected final Logger log                      = LogManager.getLogger(this.getClass().getSimpleName());
    private static final String SAVED_VALUE_REGEX   = ".*?\\{\\{(.*?)\\}\\}.*?";
    protected SessionData sessionData               = new SessionData();

    public String getStoredValue(String name) {
        return sessionData.getStoredValue(name);
    }

    public abstract void start();

    public abstract void close();

    /**
     * String expressions allow user to inject desired values into a string data.
     * Parameter name is specified in double curly braces (e.g. {{username}} ), which during the processing is
     * replaced by a session data value with the same name.
     */
    public String processStringExpression(String target) {
        Matcher matcher = Pattern.compile(KeresClientBase.SAVED_VALUE_REGEX).matcher(target);
        String newVal = target;
        while (matcher.find()) {
            try {
                String paramName = matcher.group(1);
                String paramVal = KeresHttpClient.getClientForThread().getStoredValue(paramName);
                newVal = newVal.replaceAll("\\{\\{" + paramName + "\\}\\}", paramVal);
            } catch (Exception e) {
                log.error(e);
            }
        }

        return newVal;
    }

    // ####################################################################
    // Session value processors
    // ####################################################################
    public T setSessionValue(String key, String value) {
        sessionData.storeValue(key, value);

        return (T)this;
    }

    public String getSessionValue(String key) {
        if (sessionData.hasEntry(key)) {
            return sessionData.getStoredValue(key);
        } else {
            return "";
        }
    }

    public T clearSessionValue(String key) {
        sessionData.removeValue(key);

        return (T)this;
    }

    public T feed(KeresFeeder feeder) {
        Tuple<ArrayList<String>,String[]> feedValues = feeder.getNextRow();

        for (int index = 0; index < feedValues.getVal1().size(); index++) {
            String header = feedValues.getVal1().get(index);
            String value = feedValues.getVal2()[index];
            sessionData.storeValue(header, value);
        }

        return (T)this;
    }

    // ####################################################################
    // Actions
    // ####################################################################

    public T action(String name, Runnable task) {
        ActionController
            .sequentialAction(name, task)
            .execute();

        return (T)this;
    }

    public T action(SequentialAction action) {
        action.execute();

        return (T)this;
    }

    public T parallelAction(String name, Runnable... tasks) {
        ParallelAction action = new ParallelAction(name);
        for (Runnable task : tasks) {
            action.addAndStart(task);
        }
        action.waitForRequestsToFinish();

        return (T)this;
    }

    public T parallelAction(ParallelAction action) {
        action
            .start()
            .waitForRequestsToFinish();
        
        return (T)this;
    }

    // ####################################################################
    // Cycle and conditional operators
    // ####################################################################

    public T repeat(int iterations, Runnable procedure) {
        for (int iteration = 0; iteration < iterations; iteration++) {
            procedure.run();
        }
        
        return (T)this;
    }

    // ####################################################################
    // Misc.
    // ####################################################################

    public T waitFor(Duration time) {
        if (KeresController.shouldStop()) {
            return (T)this;
        }

        TimeUtils.waitFor(time);
        return (T)this;
    }

    /**
     * This method is used to "mock" the regular data flow, using dummy response objects instead of
     * real requests execution. 
     * Primarily used for demonstration and data flow debugging purposes.
     * @param method        - Method prefix
     * @param requestName   - Request name
     * @param lowerLimit    - Lower response time limit
     * @param higherLimit   - Higher response time limit
     */
    public Response mock(String method, String requestName, long lowerLimit, long higherLimit) {
        long delay = new Random().nextLong(lowerLimit, higherLimit);
        boolean failed = new Random().nextBoolean();
        long start = System.currentTimeMillis();
        TimeUtils.waitFor(delay);
        long finish = System.currentTimeMillis();
        long elapsed = finish - start;

        Response res = new Response()
            .setRequestMethod(method)
            .setRequestName(requestName)
            .setResponseCode(failed ? 401 : 200)
            .setFailed(failed)
            .setResponseContent(String.format("(%s)%s - request failed", method, requestName))
            .setResponseSize(delay)
            .setStartTime(start)
            .setFinishTime(finish)
            .setResponseTime(elapsed);

        DataCollector.get().logResponse(res);

        return res;
    }

    /**
     * Same as above, but it aborts the virtual user execution in case request has failed.
     * Primarily used for demonstration and data flow debugging purposes.
     * @param method        - Method prefix
     * @param requestName   - Request name
     * @param lowerLimit    - Lower response time limit
     * @param higherLimit   - Higher response time limit
     */
    public void mockAndStopIfFailed(String method, String requestName, long lowerLimit, long higherLimit) {
        Response res = mock(method, requestName, lowerLimit, higherLimit);
        
        if (res.isFailed()) {
            long userId = Thread.currentThread().threadId();
            KeresUser user = KeresUser.getAllRunners().get(userId);
            user.requestStop();
            // We throw an exception in order to stop current method execution - in case there are more requests down the line.
            throw new RuntimeException(String.format("Request '(%s)%s' has failed. Stopping virtual user '%d'", res.getRequestMethod(), res.getRequestName(), userId));
        }
    }

    /**
     * During the execution we might want to save some values - credentials, tokens, parsed data - that might come in handy later.
     * For that - we use SessionData storage. It keeps this data in a simple Key-Value map, with both key and value being Strings.
     * TODO: We might want to store object different from strings at some point. Once we do - implement it.
     */
    public static class SessionData {
        private final  HashMap<String, String> storedValues = new HashMap<>();

        public boolean hasEntry(String key) {
            return storedValues.containsKey(key);
        }

        public SessionData storeValue(String name, String value) {
            storedValues.put(name, value);

            return this;
        }

        public SessionData removeValue(String name) {
            if (storedValues.containsKey(name)) {
                storedValues.remove(name);
            }

            return this;
        }

        public String getStoredValue(String name) {
            if (storedValues.containsKey(name)) {
                return storedValues.get(name);
            } else {
                throw new RuntimeException("Couldn't find value with name '" + name + "' in session storage.");
            }
        }
    }
}
