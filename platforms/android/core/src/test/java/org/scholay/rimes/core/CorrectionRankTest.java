package org.scholay.rimes.core;

import org.junit.Test;
import java.util.ArrayList;
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
    public void middleTapKeepsThePressedWordAheadOfAHeavierNeighbor() {
        CorrectionRank.Spelling pressed=new CorrectionRank.Spelling("bihc",
                Arrays.asList("比","毕","币","闭","壁"), Arrays.asList("","","","",""), new double[]{5,4,3,2,1}, true);
        CorrectionRank.Spelling neighbor=new CorrectionRank.Spelling("nihc",
                Collections.singletonList("你好"), Collections.singletonList(""), new double[]{9}, false);
        List<CorrectionRank.Offer> offers=CorrectionRank.mergeByTouch(Arrays.asList(pressed,neighbor), "bihc");
        assertEquals("比", offers.get(0).text);
        assertTrue(offers.get(0).favored);
        assertEquals("你好", offers.get(CorrectionRank.CENTER_HEAD).text);
        assertFalse(offers.get(CorrectionRank.CENTER_HEAD).favored);
        assertEquals("壁", offers.get(offers.size()-1).text);
    }

    @Test
    public void seamTapLetsTheHeavierNeighborRankFirst() {
        CorrectionRank.Spelling pressed=new CorrectionRank.Spelling("bihc",
                Collections.singletonList("比好"), Collections.singletonList(""), new double[]{1}, true);
        CorrectionRank.Spelling neighbor=new CorrectionRank.Spelling("nihc",
                Collections.singletonList("你好"), Collections.singletonList(""), new double[]{9}, true);
        List<CorrectionRank.Offer> offers=CorrectionRank.mergeByTouch(Arrays.asList(pressed,neighbor), "bihc");
        assertEquals("你好", offers.get(0).text);
        assertEquals("nihc", offers.get(0).raw);
        assertTrue(offers.get(0).favored);
        assertEquals("比好", offers.get(1).text);
        assertTrue(offers.get(1).favored);
    }

    @Test
    public void centerTapWithNoPressedWordDoesNotFavorTheNeighbor() {
        CorrectionRank.Spelling neighbor=new CorrectionRank.Spelling("side",
                Collections.singletonList("旁"), Collections.singletonList(""), new double[]{9}, false);
        List<CorrectionRank.Offer> offers=CorrectionRank.mergeByTouch(Collections.singletonList(neighbor), "pressed");
        assertEquals(1, offers.size());
        assertFalse(offers.get(0).favored);
    }

    @Test
    public void fullPressedListStillLeavesRoomForTheNeighbor() {
        List<String> texts=new ArrayList<>();
        double[] qualities=new double[60];
        for(int i=0;i<60;i++) { texts.add("词"+i); qualities[i]=60-i; }
        CorrectionRank.Spelling pressed=new CorrectionRank.Spelling("b", texts, Collections.emptyList(), qualities, true);
        CorrectionRank.Spelling neighbor=new CorrectionRank.Spelling("n",
                Collections.singletonList("你"), Collections.singletonList(""), new double[]{1}, false);
        List<CorrectionRank.Offer> offers=CorrectionRank.mergeByTouch(Arrays.asList(pressed,neighbor), "b");
        assertEquals("你", offers.get(CorrectionRank.CENTER_HEAD).text);
        assertEquals(60, offers.size());
    }

    @Test
    public void selectStemsKeepsAHeavyNeighborWhenThePressedSpellingIsLong() {
        List<CorrectionRank.Probe> probes=new ArrayList<>();
        probes.add(new CorrectionRank.Probe("pressed", true, true, 1));
        probes.add(new CorrectionRank.Probe("empty-favored", true, false, Double.NaN));
        for(int i=0;i<10;i++) probes.add(new CorrectionRank.Probe("weak"+i, false, true, i));
        probes.add(new CorrectionRank.Probe("heavy", false, true, 100));
        List<CorrectionRank.Stem> crowded=CorrectionRank.selectStems("pressed", probes, 4);
        assertEquals(new CorrectionRank.Stem("pressed", true), crowded.get(0));
        assertTrue(crowded.contains(new CorrectionRank.Stem("heavy", false)));
        assertFalse(crowded.contains(new CorrectionRank.Stem("weak0", false)));
        List<CorrectionRank.Stem> room=CorrectionRank.selectStems("pressed", Arrays.asList(
                new CorrectionRank.Probe("pressed", true, true, 1),
                new CorrectionRank.Probe("empty-favored", true, false, Double.NaN),
                new CorrectionRank.Probe("weak", false, true, 1)), 8);
        assertTrue(room.contains(new CorrectionRank.Stem("empty-favored", true)));
    }

    @Test
    public void zoneUsesTheOuterThirdOfTheKey() {
        assertEquals(CorrectionRank.Side.CENTER, CorrectionRank.zone(0));
        assertEquals(CorrectionRank.Side.CENTER, CorrectionRank.zone(-0.3f));
        assertEquals(CorrectionRank.Side.CENTER, CorrectionRank.zone(0.3f));
        assertEquals(CorrectionRank.Side.LEFT, CorrectionRank.zone(-0.34f));
        assertEquals(CorrectionRank.Side.RIGHT, CorrectionRank.zone(0.34f));
        assertEquals('v', SmartCorrector.sideNeighbor('b', true));
        assertEquals('n', SmartCorrector.sideNeighbor('b', false));
        assertEquals(0, SmartCorrector.sideNeighbor('q', true));
        assertEquals(0, SmartCorrector.sideNeighbor('p', false));
    }

    @Test
    public void shortenKeepsTheSlippedInitial() {
        assertEquals(Arrays.asList(
                new CorrectionRank.Stem("bi", true),
                new CorrectionRank.Stem("ni", true),
                new CorrectionRank.Stem("bu", false)),
                CorrectionRank.shorten(Arrays.asList(
                        new CorrectionRank.Stem("bih", true),
                        new CorrectionRank.Stem("nih", true),
                        new CorrectionRank.Stem("buh", false)), "bi"));
    }

    @Test
    public void shortenDropsABeamThatDoesNotMatch() {
        assertNull(CorrectionRank.shorten(Arrays.asList(new CorrectionRank.Stem("abcd", true)), "x"));
        assertNull(CorrectionRank.shorten(Arrays.asList(new CorrectionRank.Stem("bi", true)), ""));
        assertNull(CorrectionRank.shorten(null, "b"));
    }
}
