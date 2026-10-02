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
                    assertTrue(a.x>=0 && a.y>=0 && a.width>0 && a.height>=(landscape?36:56));
                    assertTrue(a.x+a.width<=width+0.01 && a.y+a.height<=KeyboardLayout.height(landscape));
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
            if(key.action==KeyboardLayout.Action.SPACE) assertTrue(key.width>=390*0.45);
        }
        assertEquals(26,letters);
        assertEquals(19.5,keys.get(10).x,0.01);
        assertEquals(58.5,keys.get(19).x,0.01);
    }
    @Test public void nineKeysHaveTelephoneOrderAndEqualTouchArea() {
        List<KeyboardLayout.Key> keys=KeyboardLayout.keys(400,false,KeyboardLayout.Mode.NINE_KEY);
        int digits=0;
        for(KeyboardLayout.Key key:keys) if(key.action==KeyboardLayout.Action.TEXT) {
            digits++; assertEquals(80,key.width,0.01); assertEquals(56,key.height,0.01);
            if(key.text.equals("2")) { assertEquals(160,key.x,0.01); assertEquals(0,key.y,0.01); }
            if(key.text.equals("9")) { assertEquals(240,key.x,0.01); assertEquals(112,key.y,0.01); }
        }
        assertEquals(8,digits);
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
