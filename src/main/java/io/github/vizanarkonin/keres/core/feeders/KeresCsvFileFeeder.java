package io.github.vizanarkonin.keres.core.feeders;

import java.io.FileReader;
import com.opencsv.CSVReader;

/**
 * An implementation of feeder class, designed to work with CSV files.
 * First row of loaded CSV file is considered to be a header row - value defines the name of the entire column
 * All following rows are considered to be data sets, which are extracted and imported in groups - 1 row per data array.
 */
public class KeresCsvFileFeeder extends KeresFeeder {
    
    private KeresCsvFileFeeder(String fileLocation) {
        try {
            try (CSVReader reader = new CSVReader(new FileReader(fileLocation))) {
                String[] lineInArray;
                while ((lineInArray = reader.readNext()) != null) {
                    // First iteration - populating headers list from 1'st row
                    if (headers.size() == 0) {
                        for (String header : lineInArray) {
                            headers.add(header);
                        }
                    } else {
                        values.add(lineInArray);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
    }

    public static KeresCsvFileFeeder open(String fileLocation) {
        return new KeresCsvFileFeeder(fileLocation);
    }

    public KeresCsvFileFeeder circular() {
        mode = FeedMode.CIRCULAR;

        return this;
    }

    public KeresCsvFileFeeder random() {
        mode = FeedMode.RANDOM;

        return this;
    }
}
