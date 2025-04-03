package io.github.vizanarkonin.keres.core.feeders;

import java.util.ArrayList;
import java.util.Random;

import io.github.vizanarkonin.keres.core.utils.Tuple;

/**
 * Feeders are a separate class of objects, intended to be used as data providers.
 * They extract/generate sets of data, which are later injected by individual clients into the session data.
 * This is a base class that contains core elements and methods, that each individual feeder type should implement.
 */
public abstract class KeresFeeder {
    protected ArrayList<String>     headers     = new ArrayList<>();
    protected ArrayList<String[]>   values      = new ArrayList<>();
    protected int                   lastIndex   = 0;    // Used by circular mode to determine which entry number to take next
    protected FeedMode              mode        = FeedMode.CIRCULAR;

    /**
     * Main getter method.
     * Creates a tuple with headers and next row string array
     * @return Tuple - first element is a list with headers, second element is a string array with values from next row
     */
    public synchronized Tuple<ArrayList<String>, String[]> getNextRow() {
        if (mode == FeedMode.CIRCULAR) {
            if (lastIndex >= values.size()) {
                lastIndex = 0;
            }

            Tuple<ArrayList<String>,String[]> tuple = new Tuple<ArrayList<String>,String[]>(headers, values.get(lastIndex));
            lastIndex++;

            return tuple;
        } else {
            int index = new Random().nextInt(values.size() + 1);

            return new Tuple<ArrayList<String>,String[]>(headers, values.get(index));
        }
    }

    public static enum FeedMode {
        CIRCULAR,
        RANDOM
    }
}
