package org.scholay.rimes.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Same-row touch correction for a 26-key keyboard.
 * A slip on this layout is the key to the left or right. Keys on the row
 * above or below are not neighbors. Horizontal touch bias still decides
 * which side is tried first.
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

        NEIGHBORS.put('q', new char[]{'w'});
        NEIGHBORS.put('w', new char[]{'q', 'e'});
        NEIGHBORS.put('e', new char[]{'w', 'r'});
        NEIGHBORS.put('r', new char[]{'e', 't'});
        NEIGHBORS.put('t', new char[]{'r', 'y'});
        NEIGHBORS.put('y', new char[]{'t', 'u'});
        NEIGHBORS.put('u', new char[]{'y', 'i'});
        NEIGHBORS.put('i', new char[]{'u', 'o'});
        NEIGHBORS.put('o', new char[]{'i', 'p'});
        NEIGHBORS.put('p', new char[]{'o'});

        NEIGHBORS.put('a', new char[]{'s'});
        NEIGHBORS.put('s', new char[]{'a', 'd'});
        NEIGHBORS.put('d', new char[]{'s', 'f'});
        NEIGHBORS.put('f', new char[]{'d', 'g'});
        NEIGHBORS.put('g', new char[]{'f', 'h'});
        NEIGHBORS.put('h', new char[]{'g', 'j'});
        NEIGHBORS.put('j', new char[]{'h', 'k'});
        NEIGHBORS.put('k', new char[]{'j', 'l'});
        NEIGHBORS.put('l', new char[]{'k'});

        NEIGHBORS.put('z', new char[]{'x'});
        NEIGHBORS.put('x', new char[]{'z', 'c'});
        NEIGHBORS.put('c', new char[]{'x', 'v'});
        NEIGHBORS.put('v', new char[]{'c', 'b'});
        NEIGHBORS.put('b', new char[]{'v', 'n'});
        NEIGHBORS.put('n', new char[]{'b', 'm'});
        NEIGHBORS.put('m', new char[]{'n'});
    }

    private SmartCorrector() {}

    /**
     * Returns the same-row neighbors, nearer side first.
     * biasX ranges from -1.0 (left edge) to +1.0 (right edge).
     * biasY is ignored for choosing a key: the row above and below are not neighbors.
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

    /** The same-row neighbor on one side. Zero when this key is at that edge. */
    public static char sideNeighbor(char key, boolean left) {
        char lower=Character.toLowerCase(key);
        char[] list=NEIGHBORS.get(lower);
        float[] origin=KEY_CENTERS.get(lower);
        if(list==null || origin==null) return 0;
        char found=0;
        for(char neighbor:list) {
            float[] center=KEY_CENTERS.get(neighbor);
            if(center==null) continue;
            if(left?center[0]<origin[0]:center[0]>origin[0]) found=neighbor;
        }
        return found;
    }

    /**
     * Checks if a character is a supported 26-key QWERTY letter.
     */
    public static boolean isSupportedLetter(char c) {
        char lower = Character.toLowerCase(c);
        return lower >= 'a' && lower <= 'z';
    }
}
