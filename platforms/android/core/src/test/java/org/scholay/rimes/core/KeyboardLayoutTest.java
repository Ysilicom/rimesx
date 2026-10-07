package org.scholay.rimes.core;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class KeyboardLayoutTest {
    @Test public void everyModeFitsWithoutOverlappingAtPhoneAndTabletWidths() {
        for(int width:new int[]{280,320,360,411,600,840,1200}) for(boolean landscape:new boolean[]{false,true})
            for(KeyboardLayout.Mode mode:KeyboardLayout.Mode.values()) {
                List<KeyboardLayout.Key> keys=KeyboardLayout.keys(width,landscape,mode);
                for(int i=0;i<keys.size();i++) {
                    KeyboardLayout.Key a=keys.get(i);
                    assertTrue(a.x>=0 && a.y>=0 && a.width>0 && a.height>=KeyboardLayout.height(landscape)/4f);
                    assertTrue(a.x+a.width<=width+0.01 && a.y+a.height<=KeyboardLayout.height(landscape));
                    assertTrue("cap left/top in touch cell",a.visualX>=a.x-0.01 && a.visualY>=a.y-0.01);
                    assertTrue("cap right/bottom in touch cell",a.visualX+a.visualWidth<=a.x+a.width+0.01
                            && a.visualY+a.visualHeight<=a.y+a.height+0.01);
                    assertTrue(a.visualWidth>0 && a.visualHeight>0);
                    for(int j=i+1;j<keys.size();j++) {
                        KeyboardLayout.Key b=keys.get(j);
                        assertFalse(mode+" overlap",Math.min(a.x+a.width,b.x+b.width)-Math.max(a.x,b.x)>0.01
                                && Math.min(a.y+a.height,b.y+b.height)-Math.max(a.y,b.y)>0.01);
                    }
                }
            }
    }
    @Test public void qwertyLettersHaveEqualWidthAndSpaceIsReachable() {
        List<KeyboardLayout.Key> keys=KeyboardLayout.keys(390,false,KeyboardLayout.Mode.QWERTY);
        int letters=0;
        for(KeyboardLayout.Key key:keys) {
            if(key.action==KeyboardLayout.Action.TEXT) { letters++; assertEquals(39,key.width,0.01); }
            if(key.action==KeyboardLayout.Action.SPACE) assertTrue(key.width>=390*0.34);
        }
        assertEquals(26,letters);
        assertEquals(19.5,keys.get(10).x,0.01);
        assertEquals(58.5,keys.get(19).x,0.01);
    }
    @Test public void nineKeysHaveTelephoneOrderAndEqualTouchArea() {
        List<KeyboardLayout.Key> keys=KeyboardLayout.keys(400,false,KeyboardLayout.Mode.NINE_KEY);
        int digits=0;
        for(KeyboardLayout.Key key:keys) if(key.action==KeyboardLayout.Action.TEXT) {
            digits++; assertEquals(80,key.width,0.01); assertEquals(51.5,key.height,0.01);
            if(key.text.equals("2")) { assertEquals(160,key.x,0.01); assertEquals(0,key.y,0.01); }
            if(key.text.equals("9")) { assertEquals(240,key.x,0.01); assertEquals(103,key.y,0.01); }
        }
        assertEquals(8,digits);
    }
    @Test public void capsMatchTheIosStandardReferenceWithoutShrinkingTouchCells() {
        assertEquals(206,KeyboardLayout.height(false)); assertEquals(143,KeyboardLayout.height(true));
        List<KeyboardLayout.Key> qwerty=KeyboardLayout.keys(392,false,KeyboardLayout.Mode.QWERTY);
        KeyboardLayout.Key q=qwerty.get(0),a=qwerty.get(10),z=qwerty.get(19);
        assertEquals(33.8,q.visualWidth,0.01); assertEquals(44,q.visualHeight,0.01);
        assertEquals(19.9,a.visualX,0.01); assertEquals(54,a.visualY,0.01);
        assertEquals(59.7,z.visualX,0.01); assertEquals(108,z.visualY,0.01);
        KeyboardLayout.Key shift=find(qwerty,KeyboardLayout.Action.SHIFT);
        KeyboardLayout.Key punct=find(qwerty,KeyboardLayout.Action.PUNCTUATION);
        KeyboardLayout.Key space=find(qwerty,KeyboardLayout.Action.SPACE);
        assertEquals(45.08,shift.visualWidth,0.01);
        assertEquals(104.0,punct.visualX,0.01); assertEquals(46.0,punct.visualWidth,0.01);
        assertEquals(156.0,space.visualX,0.01); assertEquals(156.4,space.visualWidth,0.01);
        assertEquals(162,space.visualY,0.01);
        List<KeyboardLayout.Key> nine=KeyboardLayout.keys(392,false,KeyboardLayout.Mode.NINE_KEY);
        KeyboardLayout.Key enter=find(nine,KeyboardLayout.Action.RETURN),nineSpace=find(nine,KeyboardLayout.Action.SPACE);
        assertEquals(318.4,enter.visualX,0.01); assertEquals(108,enter.visualY,0.01);
        assertEquals(73.6,enter.visualWidth,0.01); assertEquals(98,enter.visualHeight,0.01);
        assertEquals(159.2,nineSpace.visualX,0.01); assertEquals(153.2,nineSpace.visualWidth,0.01);
        List<KeyboardLayout.Key> wide=KeyboardLayout.keys(840,true,KeyboardLayout.Mode.NINE_KEY);
        assertEquals(32,wide.get(0).visualHeight,0.01);
        assertEquals(69,find(wide,KeyboardLayout.Action.RETURN).visualHeight,0.01);
    }
    private static KeyboardLayout.Key find(List<KeyboardLayout.Key> keys,KeyboardLayout.Action action) {
        return keys.stream().filter(key -> key.action==action).findFirst().orElseThrow();
    }
    @Test public void spellingChoicesConstrainOnlyTheFirstPendingSyllable() {
        NineKeyPinyin spelling=new NineKeyPinyin(List.of("ni","mi","hao","gao","ha","n","foo!"));
        assertEquals("64426",NineKeyPinyin.digits("nihao"));
        assertTrue(spelling.choices("64426").containsAll(List.of("ni","mi")));
        assertEquals("ni'426",spelling.select("ni","64426"));
        assertEquals("ni'hao'",spelling.select("hao","ni'426"));
        assertEquals("ni'426",spelling.select("ni","64'426"));
        assertNull(spelling.select("ni","426"));
        assertEquals("ni'426",NineKeyPinyin.backspace("ni'hao'"));
        assertEquals("6442",NineKeyPinyin.backspace("64426"));
        assertTrue(spelling.choices("ni'hao'").isEmpty());
    }
}
