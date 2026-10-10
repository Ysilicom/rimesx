package org.scholay.rimes.core;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class CorrectionRankTest {
    @Test
    public void higherWeightFromTheRightRanksFirst() {
        CorrectionRank.Spelling left=new CorrectionRank.Spelling("niga",
                Arrays.asList("你嘎","泥"), Arrays.asList("",""), new double[]{1.0,0.2});
        CorrectionRank.Spelling right=new CorrectionRank.Spelling("niha",
                Collections.singletonList("你好"), Collections.singletonList(""), new double[]{3.0});
        List<CorrectionRank.Offer> offers=CorrectionRank.merge(Arrays.asList(left,right));
        assertEquals("你好", offers.get(0).text);
        assertEquals("niha", offers.get(0).raw);
        assertEquals(0, offers.get(0).index);
        assertEquals("你嘎", offers.get(1).text);
        assertEquals("泥", offers.get(2).text);
    }

    @Test
    public void sameWordKeepsTheHeavierSpelling() {
        CorrectionRank.Spelling left=new CorrectionRank.Spelling("niga",
                Collections.singletonList("你好"), Collections.singletonList("左"), new double[]{1.0});
        CorrectionRank.Spelling right=new CorrectionRank.Spelling("niha",
                Collections.singletonList("你好"), Collections.singletonList("右"), new double[]{4.0});
        List<CorrectionRank.Offer> offers=CorrectionRank.merge(Arrays.asList(left,right));
        assertEquals(1, offers.size());
        assertEquals("niha", offers.get(0).raw);
        assertEquals("右", offers.get(0).comment);
    }

    @Test
    public void typedSpellingStaysAheadOfAHeavierNeighbor() {
        CorrectionRank.Spelling typed=new CorrectionRank.Spelling("agy",
                Collections.singletonList("轻"), Collections.singletonList(""), new double[]{1.0});
        CorrectionRank.Spelling neighbor=new CorrectionRank.Spelling("agu",
                Arrays.asList("重","轻"), Arrays.asList("",""), new double[]{9.0,8.0});
        List<CorrectionRank.Offer> offers=CorrectionRank.mergePrefer(Arrays.asList(neighbor,typed), "agy");
        assertEquals("轻", offers.get(0).text);
        assertEquals("agy", offers.get(0).raw);
        assertEquals("重", offers.get(1).text);
        assertEquals("agu", offers.get(1).raw);
        assertEquals(2, offers.size());
    }

    @Test
    public void continueStemsKeepsTheTypedSpellingThenHeavierOnes() {
        CorrectionRank.Offer heavy=new CorrectionRank.Offer("甲","", "right", 0, 5);
        CorrectionRank.Offer mid=new CorrectionRank.Offer("乙","", "side", 0, 2);
        CorrectionRank.Offer typed=new CorrectionRank.Offer("丙","", "typed", 0, 1);
        assertEquals(Arrays.asList("typed","right","side"),
                CorrectionRank.continueStems("typed","typed", Arrays.asList(heavy,mid,typed), 8));
    }

    @Test
    public void shortenKeepsTheSlippedInitial() {
        assertEquals(Arrays.asList("bi","ni","bu"),
                CorrectionRank.shorten(Arrays.asList("bih","nih","buh"), "bi"));
    }

    @Test
    public void shortenDropsABeamThatDoesNotMatch() {
        assertNull(CorrectionRank.shorten(Arrays.asList("abcd"), "x"));
        assertNull(CorrectionRank.shorten(Arrays.asList("bi"), ""));
        assertNull(CorrectionRank.shorten(null, "b"));
    }
}
