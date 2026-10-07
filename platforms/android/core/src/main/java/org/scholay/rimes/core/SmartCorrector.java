package org.scholay.rimes.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gboard-level spatial touch error correction model for QWERTY keyboards.
 * Evaluates geometric key proximity and touch offset bias (dx, dy) to rescue
 * invalid syllables, fat-finger typos, and dead-end candidate states.
 */
public final class SmartCorrector {
    private static final Map<Character, float[]> KEY_CENTERS = new HashMap<>();
    private static final Map<Character, char[]> NEIGHBORS = new HashMap<>();

    static {
        // Row 0: y = 0.5
        char[] row0 = "qwertyuiop".toCharArray();
        for (int i = 0; i < row0.length; i++) {
            KEY_CENTERS.put(row0[i], new float[]{0.5f + i, 0.5f});
        }
        // Row 1: y = 1.5, offset = 0.5
        char[] row1 = "asdfghjkl".toCharArray();
        for (int i = 0; i < row1.length; i++) {
            KEY_CENTERS.put(row1[i], new float[]{1.0f + i, 1.5f});
        }
        // Row 2: y = 2.5, offset = 1.5
        char[] row2 = "zxcvbnm".toCharArray();
        for (int i = 0; i < row2.length; i++) {
            KEY_CENTERS.put(row2[i], new float[]{2.0f + i, 2.5f});
        }

        NEIGHBORS.put('q', new char[]{'w', 'a', 's'});
        NEIGHBORS.put('w', new char[]{'q', 'e', 'a', 's', 'd'});
        NEIGHBORS.put('e', new char[]{'w', 'r', 's', 'd', 'f'});
        NEIGHBORS.put('r', new char[]{'e', 't', 'd', 'f', 'g'});
        NEIGHBORS.put('t', new char[]{'r', 'y', 'f', 'g', 'h'});
        NEIGHBORS.put('y', new char[]{'t', 'u', 'g', 'h', 'j'});
        NEIGHBORS.put('u', new char[]{'y', 'i', 'h', 'j', 'k'});
        NEIGHBORS.put('i', new char[]{'u', 'o', 'j', 'k', 'l'});
        NEIGHBORS.put('o', new char[]{'i', 'p', 'k', 'l'});
        NEIGHBORS.put('p', new char[]{'o', 'l'});

        NEIGHBORS.put('a', new char[]{'s', 'q', 'w', 'z'});
        NEIGHBORS.put('s', new char[]{'a', 'd', 'w', 'e', 'z', 'x'});
        NEIGHBORS.put('d', new char[]{'s', 'f', 'e', 'r', 'x', 'c'});
        NEIGHBORS.put('f', new char[]{'d', 'g', 'r', 't', 'c', 'v'});
        NEIGHBORS.put('g', new char[]{'f', 'h', 't', 'y', 'v', 'b'});
        NEIGHBORS.put('h', new char[]{'g', 'j', 'y', 'u', 'b', 'n'});
        NEIGHBORS.put('j', new char[]{'h', 'k', 'u', 'i', 'n', 'm'});
        NEIGHBORS.put('k', new char[]{'j', 'l', 'i', 'o', 'm'});
        NEIGHBORS.put('l', new char[]{'k', 'o', 'p'});

        NEIGHBORS.put('z', new char[]{'x', 'a', 's'});
        NEIGHBORS.put('x', new char[]{'z', 'c', 's', 'd'});
        NEIGHBORS.put('c', new char[]{'x', 'v', 'd', 'f'});
        NEIGHBORS.put('v', new char[]{'c', 'b', 'f', 'g'});
        NEIGHBORS.put('b', new char[]{'v', 'n', 'g', 'h'});
        NEIGHBORS.put('n', new char[]{'b', 'm', 'h', 'j'});
        NEIGHBORS.put('m', new char[]{'n', 'j', 'k'});
    }

    private SmartCorrector() {}

    /**
     * Returns a prioritized list of adjacent keys based on touch offset bias.
     * biasX ranges from -1.0 (left edge) to +1.0 (right edge).
     * biasY ranges from -1.0 (top edge) to +1.0 (bottom edge).
     * Keys closest to the calculated physical touch coordinate appear first.
     */
    public static List<Character> getPrioritizedNeighbors(char key, float biasX, float biasY) {
        char lower = Character.toLowerCase(key);
        char[] list = NEIGHBORS.get(lower);
        float[] origin = KEY_CENTERS.get(lower);
        if (list == null || list.length == 0 || origin == null) return Collections.emptyList();

        // Convert normalized [-1.0, 1.0] bias to key-coordinate space (unit width/height = 1.0)
        final float touchX = origin[0] + biasX * 0.5f;
        final float touchY = origin[1] + biasY * 0.5f;

        List<Character> result = new ArrayList<>(list.length);
        for (char c : list) {
            result.add(c);
        }

        Collections.sort(result, (a, b) -> {
            float[] ca = KEY_CENTERS.get(a);
            float[] cb = KEY_CENTERS.get(b);
            if (ca == null || cb == null) return 0;
            float da = (ca[0] - touchX) * (ca[0] - touchX) + (ca[1] - touchY) * (ca[1] - touchY);
            float db = (cb[0] - touchX) * (cb[0] - touchX) + (cb[1] - touchY) * (cb[1] - touchY);
            return Float.compare(da, db);
        });

        return result;
    }

    /**
     * Checks if a character is a supported 26-key QWERTY letter.
     */
    public static boolean isSupportedLetter(char c) {
        char lower = Character.toLowerCase(c);
        return lower >= 'a' && lower <= 'z';
    }
}
