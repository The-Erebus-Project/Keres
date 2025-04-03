package io.github.vizanarkonin.keres.core.feeders;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implementation of file contents feeder.
 * It reads the contents of given files and stores them in memory with given header name.
 * If File instance provided is a folder - we will recursively iterate over the files inside the folder and load them.
 * NOTE: Resulting values are Strings.
 */
public class KeresFileFeeder extends KeresFeeder {
    private static final Logger log = LogManager.getLogger("KeresFileFeeder");
    
    private KeresFileFeeder(String paramName, File... files) {
        headers.add(paramName);
        
        for (File file : files) {
            loadFile(file);
        }
    }

    private KeresFileFeeder(String paramName, String... filesLocations) {
        headers.add(paramName);
        
        for (String fileLocation : filesLocations) {
            File file = new File(fileLocation);
            loadFile(file);
        }
    }

    public static KeresFileFeeder load(String paramName, String... filesLocations) {
        return new KeresFileFeeder(paramName, filesLocations);
    }

    public static KeresFileFeeder load(String paramName, File... files) {
        return new KeresFileFeeder(paramName, files);
    }

    public KeresFileFeeder circular() {
        mode = FeedMode.CIRCULAR;

        return this;
    }

    public KeresFileFeeder random() {
        mode = FeedMode.RANDOM;

        return this;
    }

    private void loadFile(File file) {
        if (file.isDirectory()) {
            for (File innerFile : file.listFiles()) {
                loadFile(innerFile);
            }
        } else {
            try {
                values.add(new String[] { new String(Files.readAllBytes(file.toPath())) });
            } catch (IOException e) {
                log.error(e);
                log.error(ExceptionUtils.getStackTrace(e));
            }
        }
    }
}
