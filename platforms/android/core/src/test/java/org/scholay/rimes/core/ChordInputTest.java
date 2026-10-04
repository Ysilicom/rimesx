package org.scholay.rimes.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChordInputTest {
    private final ChordProfile profile=ChordProfile.builtIn();
    @Test public void allBundledMappingsResolveOnceOnFinalReleaseInEitherOrder() {
        assertEquals(427,profile.entries.size());
        for(ChordProfile.Entry entry:profile.entries) {
            ChordProfile.Resolution expected=profile.resolve(entry.keys);
            assertNotNull(entry.keys,expected); assertEquals(entry.output,expected.preview); assertEquals(entry.input,expected.input);
            for(boolean reverse:new boolean[]{false,true}) {
                ChordGesture gesture=new ChordGesture(profile); List<Integer> ids=new ArrayList<>();
                for(int hand=0;hand<2;hand++) {
                    String keys=profile.canonical(entry.mask&profile.handMask(hand));
                    if(!keys.isEmpty()) { gesture.begin(hand,keys.charAt(0)); gesture.move(hand,keys.charAt(keys.length()-1)); ids.add(hand); }
                }
                assertEquals(entry.keys,entry.mask,gesture.keys());
                if(reverse) java.util.Collections.reverse(ids);
                for(int i=0;i<ids.size();i++) {
                    ChordProfile.Resolution actual=gesture.end(ids.get(i),null);
                    if(i==ids.size()-1) assertEquals(entry.keys,expected,actual); else assertNull(entry.keys,actual);
                }
                assertNull(gesture.end(0,'a')); assertFalse(gesture.active());
            }
        }
    }
    @Test public void defaultMappingsProduceTheSameNaturalCodeAsIos() {
        assertEquals("ni",profile.resolve("dvi").input);
        assertEquals("hk",profile.resolve("xck").input);
        assertEquals("nihk",profile.resolve("dvi").input+profile.resolve("xck").input);
        assertEquals("u",profile.resolve("ef").input);
        assertEquals("uh",profile.resolve("efh").input);
        assertEquals("v",profile.resolve("v").input);
        assertEquals("aa",ChordProfile.syllableCode("a"));
        assertEquals("ah",ChordProfile.syllableCode("ang"));
        assertEquals("lv",ChordProfile.syllableCode("lv"));
        assertEquals("jt",ChordProfile.syllableCode("jue"));
        assertNull(profile.resolve("qjk")); assertNull(profile.resolve("!")); assertNull(profile.resolve(""));
        assertEquals("niang",profile.resolve("dvuo").preview);
    }
    @Test public void everyEncodedMappingMatchesTheFrozenIosImplementation() throws Exception {
        // Fixture was produced by running current iOS ChordEncoding.swift, independent of Java rules.
        List<String> rows=java.nio.file.Files.readAllLines(java.nio.file.Path.of("../resources/chord-natural-code.tsv"));
        assertEquals(427,rows.size());
        for(String row:rows) {
            String[] fields=row.split("\t");
            ChordProfile.Resolution result=profile.resolve(fields[0]);
            assertNotNull(fields[0],result); assertEquals(fields[1],result.preview); assertEquals(fields[0],fields[3],result.input);
        }
    }
    @Test public void immutableStartsAndFrozenReleasedHandsSurviveGapsAndCrossings() {
        for(boolean reverse:new boolean[]{false,true}) {
            ChordGesture gesture=new ChordGesture(profile);
            gesture.begin(1,'d'); gesture.move(1,'v'); gesture.begin(2,'i');
            assertEquals("ni",gesture.preview().combined); assertEquals("dv",gesture.preview().left.keys);
            gesture.move(1,null); gesture.move(1,'k'); assertEquals("ni",gesture.preview().combined);
            assertNull(gesture.end(reverse?2:1,null));
            gesture.move(reverse?2:1,'a'); // A released hand may not be rewritten.
            assertEquals("ni",gesture.preview().combined);
            assertEquals("ni",gesture.end(reverse?1:2,null).input);
            assertNull(gesture.preview());
        }
    }
    @Test public void invalidExtraFingerAndCancelledOrRetiredGesturesNeverSubmit() {
        ChordGesture gesture=new ChordGesture(profile);
        gesture.begin(1,'d'); gesture.move(1,'v'); gesture.begin(2,'i'); gesture.begin(3,'a');
        assertTrue(gesture.cancelled()); assertNull(gesture.preview());
        assertNull(gesture.end(1,'v')); assertNull(gesture.end(2,'i'));
        gesture.begin(4,'q'); assertNull(gesture.end(3,'a')); assertNull(gesture.end(4,'q'));
        gesture.begin(5,'a'); assertEquals("a",gesture.end(5,'a').input);
        gesture.begin(6,'d'); gesture.cancel(); assertNull(gesture.end(6,'d'));
        gesture.begin(7,'d'); gesture.reset(); assertNull(gesture.end(7,'d'));
        gesture.begin(8,null); assertNull(gesture.end(8,'a'));
        gesture.begin(9,'d'); gesture.move(9,'v'); gesture.begin(10,'i'); gesture.begin(11,null);
        assertTrue(gesture.cancelled()); assertNull(gesture.end(9,'v')); assertNull(gesture.end(10,'i'));
        assertTrue(gesture.active()); assertNull(gesture.end(11,null)); assertFalse(gesture.active());
    }
    @Test public void unmappedEndpointDoesNotEraseValidSameHandPairAndReturningCollapsesIt() {
        ChordGesture gesture=new ChordGesture(profile);
        gesture.begin(1,'e'); gesture.move(1,'f');
        for(char end:new char[]{'g','v','q'}) { gesture.move(1,end); assertEquals("u",gesture.resolution().input); }
        gesture.move(1,'e'); assertEquals("e",gesture.resolution().input);
        assertEquals("e",gesture.end(1,'e').input);
    }
    @Test public void directionalSlidesMatchIosIncludingFastSkippedTouchSamples() {
        String[][] routes={{"ty","ting"},{"tyu","tu"},{"gh","gang"},{"ghj","gan"},
                {"bn","bin"},{"bnm","bian"},{"bh","bang"},{"th","tang"},{"gy","guai"}};
        for(String[] route:routes) {
            ChordGesture gesture=new ChordGesture(profile);
            String path=route[0]; gesture.begin(1,path.charAt(0)); gesture.move(1,path.charAt(path.length()-1));
            ChordProfile.Resolution result=gesture.end(1,null);
            assertEquals(route[1],result.preview); assertEquals(ChordProfile.syllableCode(route[1]),result.input);
            assertNull(gesture.end(1,null));
        }
    }
    @Test public void reachabilityRetainsEveryLegalMappingWhileAHandIsHeld() {
        for(ChordProfile.Entry entry:profile.entries) {
            ChordGesture gesture=new ChordGesture(profile);
            for(int hand=0;hand<2;hand++) {
                String keys=profile.canonical(entry.mask&profile.handMask(hand));
                if(!keys.isEmpty()) {
                    gesture.begin(hand,keys.charAt(0));
                    assertEquals(entry.keys,entry.mask,entry.mask&gesture.availableKeys());
                    gesture.move(hand,keys.charAt(keys.length()-1));
                }
            }
        }
    }
    @Test public void stationaryMovesReuseReadoutsButReleaseAndOriginsInvalidateReachability() {
        ChordGesture gesture=new ChordGesture(profile);
        gesture.begin(1,'d'); gesture.move(1,'v'); gesture.begin(2,'i');
        long held=gesture.revision(); ChordGesture.Preview preview=gesture.preview();
        ChordProfile.Resolution resolution=gesture.resolution(); int reachable=gesture.availableKeys();
        for(int i=0;i<10000;i++) {
            gesture.move(1,'v'); gesture.move(2,'i');
            assertEquals(held,gesture.revision()); assertSame(preview,gesture.preview());
            assertSame(resolution,gesture.resolution()); assertEquals(reachable,gesture.availableKeys());
        }
        assertNull(gesture.end(1,null)); assertTrue(gesture.revision()>held);
        assertNotSame(preview,gesture.preview());
        int frozen=gesture.availableKeys(); gesture.move(1,'a'); assertEquals(frozen,gesture.availableKeys());
        assertTrue((reachable&ChordProfile.mask('f'))!=0); assertEquals(0,frozen&ChordProfile.mask('f'));
        assertEquals("ni",gesture.end(2,null).input);
        // The same final keys have different immutable origins after reset: origins must not share a cache key.
        gesture.begin(3,'v'); gesture.move(3,'d'); gesture.begin(4,'i');
        assertEquals(ChordProfile.mask("dvi"),gesture.keys());
        assertEquals("ni",gesture.preview().combined);
        assertNotSame(preview,gesture.preview()); assertEquals(gesture.keys(),gesture.keys()&gesture.availableKeys());
        assertNotEquals(reachable,gesture.availableKeys());
        long next=gesture.revision(); gesture.cancel(); assertTrue(gesture.revision()>next);
        assertEquals(0,gesture.availableKeys()); assertNull(gesture.preview());
        gesture.reset(); assertEquals(profile.allMask,gesture.availableKeys()); assertNull(gesture.resolution());
    }
    @Test public void orthogonalAndSplitGeometryKeepEqualSquareCapsAndBalancedHands() {
        for(int width:new int[]{280,320,360,390,411,600,840,1200}) for(boolean split:new boolean[]{false,true}) {
            List<ChordLayout.Key> keys=ChordLayout.keys(width,split); assertEquals(30,keys.size());
            float h=ChordLayout.height(width,split);
            for(int i=0;i<keys.size();i++) {
                ChordLayout.Key a=keys.get(i);
                assertTrue(a.x>=0 && a.y>=0 && a.x+a.width<=width && a.y+a.height<=h+0.01);
                assertEquals(a.width,a.height,0.01);
                for(int j=i+1;j<keys.size();j++) {
                    ChordLayout.Key b=keys.get(j);
                    assertFalse(Math.min(a.x+a.width,b.x+b.width)-Math.max(a.x,b.x)>0.01
                            && Math.min(a.y+a.height,b.y+b.height)-Math.max(a.y,b.y)>0.01);
                }
            }
            ChordLayout.Key q=keys.get(0),y=keys.get(15);
            assertEquals(q.width,y.width,0.01); assertEquals(split?14:2,y.x-(q.x+5*(q.width+2)-2),0.01);
        }
    }
}
