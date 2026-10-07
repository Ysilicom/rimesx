package org.scholay.rimes.core;

import org.junit.Test;
import java.util.List;

import static org.junit.Assert.*;

public class SmartCorrectorTest {

    @Test
    public void testAllLettersHaveNeighbors() {
        for (char c = 'a'; c <= 'z'; c++) {
            List<Character> neighbors = SmartCorrector.getPrioritizedNeighbors(c, 0f, 0f);
            assertNotNull(neighbors);
            assertFalse("Key " + c + " should have neighbors", neighbors.isEmpty());
        }
    }

    @Test
    public void testTouchBiasLeftOnS_RanksATop() {
        // Tapping on 's' but leaning left towards 'a'
        List<Character> neighbors = SmartCorrector.getPrioritizedNeighbors('s', -0.6f, 0f);
        assertEquals(Character.valueOf('a'), neighbors.get(0));
    }

    @Test
    public void testTouchBiasRightOnS_RanksDTop() {
        // Tapping on 's' but leaning right towards 'd'
        List<Character> neighbors = SmartCorrector.getPrioritizedNeighbors('s', 0.6f, 0f);
        assertEquals(Character.valueOf('d'), neighbors.get(0));
    }

    @Test
    public void testTouchBiasUpOnS_RanksTopRowAboveBottomRow() {
        // Tapping on 's' leaning upwards towards 'w'
        List<Character> neighbors = SmartCorrector.getPrioritizedNeighbors('s', -0.2f, -0.6f);
        assertEquals(Character.valueOf('w'), neighbors.get(0));
    }

    @Test
    public void testTouchBiasRightOnX_RanksCTop() {
        // Tapping on 'x' leaning right towards 'c' (Double pinyin Flypy 'hc' vs 'hx' typo)
        List<Character> neighbors = SmartCorrector.getPrioritizedNeighbors('x', 0.5f, 0f);
        assertEquals(Character.valueOf('c'), neighbors.get(0));
    }

    @Test
    public void testDoublePinyinInitialMistypeFLeaningRight_RanksHTop() {
        // Double pinyin user wanted 'h' but hit 'g' leaning right towards 'h'
        List<Character> neighbors = SmartCorrector.getPrioritizedNeighbors('g', 0.6f, 0f);
        assertEquals(Character.valueOf('h'), neighbors.get(0));
    }

    @Test
    public void testIsSupportedLetter() {
        assertTrue(SmartCorrector.isSupportedLetter('a'));
        assertTrue(SmartCorrector.isSupportedLetter('Z'));
        assertFalse(SmartCorrector.isSupportedLetter('1'));
        assertFalse(SmartCorrector.isSupportedLetter(' '));
        assertFalse(SmartCorrector.isSupportedLetter(','));
    }
}
