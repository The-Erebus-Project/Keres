package io.github.vizanarkonin.keres.core.utils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TimeUtils {
    private static final Logger log                         = LogManager.getLogger("TimeUtils");

    private static final String TRIMMED_DATE_TIME_FORMAT    = "yyyy.MM.dd : HH.mm.ss";
    private static final String PATH_DATE_TIME_FORMAT       = "yyyy-MM-dd--HH-mm-ss";
    private static DateFormat ISO_DATE_FORMATTER            = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    public static final Duration ONE_MS                     = Duration.ofMillis(1);
    public static final Duration ONE_SECOND                 = Duration.ofSeconds(1);
    public static final Duration TWO_SECONDS                = Duration.ofSeconds(2);
    
    public static String getCurrentDateTimeString() {
        return DateTimeFormatter
            .ofPattern(PATH_DATE_TIME_FORMAT)
            .withZone(ZoneOffset.UTC)
            .format(Instant.now()); 
    }

    public static String getISODateString() {
        return ISO_DATE_FORMATTER.format(new Date());
    }

    public static void waitFor(Duration period) {
        waitFor(period.toMillis());
    }

    public static void waitFor(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException ignored) {}
    }
}
