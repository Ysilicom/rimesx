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
}
