package io.github.vizanarkonin.keres.core.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utils type, used to handle Keres report files copying.
 * It is used instead of plain files copy due to files being bundled in a jar file
 */
public class KeresFileUtils {
    private static final Logger log = LogManager.getLogger("KeresFileUtils");
    public static final String[] reporterResourcesList = new String[] {
        "index.html",
        "res/all.min.css",
        "res/bootstrap.bundle.min.js",
        "res/bootstrap.min.css",
        "res/chart.js",
        "res/chartjs-adapter-moment.js",
        "res/chartjs-plugin-annotation.min.js",
        "res/chartjs-plugin-datalabels.js",
        "res/chartjs-plugin-zoom.min.js",
        "res/hammerjs.js",
        "res/jquery-3.7.1.min.js",
        "res/jquery.tablesorter.min.js",
        "res/jquery.tablesorter.widgets.min.js",
        "res/loading.gif",
        "res/logo.png",
        "res/main.css",
        "res/main.js",
        "res/moment.js",
        "res/table_sort.js",
    };

    /**
     * Copies file from source location to destination.
     * For now primarily used to copy reporter resources to target report folder
     *
     * @param source - Source file input stream
     * @param destination - File destination
     */
    public static void copyResource(InputStream source , String destination) {
        try {
            Files.copy(source, Paths.get(destination), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error(e);
            log.error(ExceptionUtils.getStackTrace(e));
            log.error("Source: " + source + ", Destination: " + destination);
            throw new RuntimeException(e);
        }
    }
}
