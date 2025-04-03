package io.github.vizanarkonin.keres.core.utils;

import lombok.Getter;

/**
 * Local implementation of a two-element tuple. Quick and dirty
 */
@Getter
public class Tuple<K, V> {
    private final K val1;
    private final V val2;

    public Tuple(K val1, V val2) {
        this.val1 = val1;
        this.val2 = val2;
    }
}
