package org.scholay.rimes.core;

import org.junit.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class PunctuationTest {
    @Test public void defaultsStayInOrderUntilASymbolBeatsTheSeed() {
        assertEquals(16,Punctuation.weightRow(false,Map.of()).size());
        assertEquals(List.of(Punctuation.NUMBER_ROW[0],Punctuation.NUMBER_ROW[1],Punctuation.NUMBER_ROW[2],
                Punctuation.NUMBER_ROW[3],Punctuation.NUMBER_ROW[4],Punctuation.NUMBER_ROW[5],
                Punctuation.NUMBER_ROW[6],Punctuation.NUMBER_ROW[7],Punctuation.NUMBER_ROW[8],
                Punctuation.NUMBER_ROW[9],Punctuation.NUMBER_TAIL[0],Punctuation.NUMBER_TAIL[1],
                Punctuation.NUMBER_TAIL[2],Punctuation.NUMBER_TAIL[3],Punctuation.NUMBER_TAIL[4],
                Punctuation.NUMBER_TAIL[5]),Punctuation.weightRow(false,Map.of()));
        Map<String,Integer> once=new HashMap<>();
        once.put("【",1);
        assertFalse(Punctuation.weightRow(false,once).contains("【"));
        once.put("【",Punctuation.SEED);
        assertFalse(Punctuation.weightRow(false,once).contains("【"));
        once.put("【",Punctuation.SEED+1);
        List<String> admitted=Punctuation.weightRow(false,once);
        assertEquals("【",admitted.get(0));
        assertEquals(16,admitted.size());
        assertFalse(admitted.contains("》"));
        assertTrue(admitted.contains("《"));
    }

    @Test public void higherCountsMoveLeftAndTiesKeepDefaultOrder() {
        Map<String,Integer> counts=new HashMap<>();
        counts.put("。",40);
        counts.put("，",40);
        counts.put("？",12);
        List<String> row=Punctuation.weightRow(false,counts);
        assertEquals("，",row.get(0));
        assertEquals("。",row.get(1));
        assertEquals("？",row.get(2));
        assertEquals("、",row.get(3));
    }

    @Test public void theHigherSymbolTakesTheOnlyOpenSlot() {
        Map<String,Integer> counts=new HashMap<>();
        for(int i=0;i<Punctuation.NUMBER_ROW.length;i++) counts.put(Punctuation.NUMBER_ROW[i],100);
        for(int i=0;i<Punctuation.NUMBER_TAIL.length-1;i++) counts.put(Punctuation.NUMBER_TAIL[i],100);
        counts.put("》",5);
        counts.put("【",6);
        counts.put("】",10);
        List<String> row=Punctuation.weightRow(false,counts);
        assertTrue(row.contains("】"));
        assertFalse(row.contains("【"));
        assertFalse(row.contains("》"));
        assertEquals("】",row.get(row.size()-1));
    }

    @Test public void equalSymbolCountsKeepSymbolPageOrder() {
        Map<String,Integer> counts=new HashMap<>();
        for(String mark:Punctuation.NUMBER_ROW) counts.put(mark,100);
        for(int i=0;i<Punctuation.NUMBER_TAIL.length-1;i++) counts.put(Punctuation.NUMBER_TAIL[i],100);
        counts.put("》",5);
        counts.put("【",9);
        counts.put("】",9);
        List<String> row=Punctuation.weightRow(false,counts);
        assertTrue(row.contains("【"));
        assertFalse(row.contains("】"));
    }

    @Test public void chineseAndEnglishCountsDoNotCross() {
        Map<String,Integer> chinese=new HashMap<>();
        chinese.put("【",30);
        assertTrue(Punctuation.weightRow(false,chinese).contains("【"));
        assertFalse(Punctuation.weightRow(true,Map.of()).contains("【"));
        assertEquals(",",Punctuation.weightRow(true,Map.of()).get(0));
        assertEquals("、",Punctuation.face("、",true));
        assertEquals("…",Punctuation.face("……",true));
        assertEquals("—",Punctuation.face("——",true));
        assertEquals("\"",Punctuation.face("“",true));
        assertEquals("'",Punctuation.face("”",true));
        assertEquals("<",Punctuation.face("《",true));
        assertEquals("，",Punctuation.face("，",false));
        assertEquals("#",Punctuation.face("#",true));
    }

    @Test public void bumpStartsDefaultsAtTheSeedAndSymbolsAtZero() {
        assertEquals(Punctuation.SEED+1,Punctuation.bump("，",false,null));
        assertEquals(1,Punctuation.bump("【",false,null));
        assertEquals(Punctuation.SEED+3,Punctuation.bump(",",true,Punctuation.SEED+2));
        assertFalse(Punctuation.tracked("1",false));
        assertTrue(Punctuation.tracked("#",true));
        assertTrue(Punctuation.tracked("(",true));
    }

    @Test public void storedCountsRoundTrip() {
        Map<String,Integer> counts=new HashMap<>();
        counts.put("……",11);
        counts.put("——",8);
        counts.put("【",9);
        String raw=Punctuation.encode(counts);
        Map<String,Integer> back=new HashMap<>();
        Punctuation.decode(raw,back);
        assertEquals(counts,back);
        Punctuation.decode("bad\nno-tab\n【\tnope\n",back);
        assertEquals(Integer.valueOf(9),back.get("【"));
    }
}
