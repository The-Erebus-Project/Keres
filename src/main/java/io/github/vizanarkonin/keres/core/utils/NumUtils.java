package io.github.vizanarkonin.keres.core.utils;

import java.util.Random;

public class NumUtils {
    public static int getRandomIntInRange(int min, int max) {
        return new Random().nextInt(min, max);
    }
}
