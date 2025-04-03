package io.github.vizanarkonin.keres.core.utils;

/**
 * This class is only used as an intermediary between infinite loop thread in Main class and the rest of the app - 
 * holding the main thread from finishing and killing the process.
 */
public class ThreadHolder {
    private static boolean isRunning = false;
    private static Thread holderThread;

    public static void init() {
        if (isRunning) {
            return;
        }

        isRunning = true;

        holderThread = new Thread(() -> {
            while (isRunning) {
                TimeUtils.waitFor(200);
            }
        });
        holderThread.start();
    }
    public static void shutDown() {
        isRunning = false;
    }

    public static boolean isRunning() {
        return isRunning;
    }
}
