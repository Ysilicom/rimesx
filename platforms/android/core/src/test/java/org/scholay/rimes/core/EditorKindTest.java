package org.scholay.rimes.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class EditorKindTest {
    @Test public void unspecifiedAndVisibleTextCanCompose() {
        assertFalse(EditorKind.directOnly(EditorKind.TYPE_NULL));
        assertFalse(EditorKind.directOnly(EditorKind.CLASS_TEXT));
        assertFalse(EditorKind.directOnly(EditorKind.CLASS_TEXT|EditorKind.TEXT_VISIBLE_PASSWORD));
        assertFalse(EditorKind.password(EditorKind.CLASS_TEXT|EditorKind.TEXT_VISIBLE_PASSWORD));
    }
    @Test public void passwordPhoneAndDateStayDirect() {
        assertTrue(EditorKind.directOnly(EditorKind.CLASS_TEXT|EditorKind.TEXT_PASSWORD));
        assertTrue(EditorKind.directOnly(EditorKind.CLASS_TEXT|EditorKind.TEXT_WEB_PASSWORD));
        assertTrue(EditorKind.directOnly(EditorKind.CLASS_NUMBER|EditorKind.NUMBER_PASSWORD));
        assertTrue(EditorKind.password(EditorKind.CLASS_TEXT|EditorKind.TEXT_PASSWORD));
        assertTrue(EditorKind.directOnly(EditorKind.CLASS_NUMBER));
        assertTrue(EditorKind.directOnly(EditorKind.CLASS_PHONE));
        assertTrue(EditorKind.directOnly(EditorKind.CLASS_DATETIME));
        assertTrue(EditorKind.directOnly(5));
    }
    @Test public void typelessRestartKeepsTheBoundField() {
        assertTrue(EditorKind.keepTypelessRestart(true,EditorKind.TYPE_NULL,true,false));
        assertTrue(EditorKind.keepTypelessRestart(true,EditorKind.TYPE_NULL,false,true));
        assertFalse(EditorKind.keepTypelessRestart(false,EditorKind.TYPE_NULL,false,true));
        assertFalse(EditorKind.keepTypelessRestart(true,EditorKind.CLASS_TEXT,true,false));
        assertFalse(EditorKind.keepTypelessRestart(true,EditorKind.TYPE_NULL,false,false));
    }
    @Test public void unspecifiedFieldKeepsClipboardAndAi() {
        assertTrue(EditorKind.showsTools(EditorKind.TYPE_NULL,0));
        assertTrue(EditorKind.showsTools(EditorKind.TYPE_NULL,EditorKind.NO_PERSONALIZED_LEARNING));
        assertTrue(EditorKind.showsTools(EditorKind.CLASS_TEXT,0));
        assertTrue(EditorKind.showsTools(EditorKind.CLASS_TEXT,EditorKind.NO_PERSONALIZED_LEARNING));
        assertFalse(EditorKind.showsTools(EditorKind.CLASS_TEXT|EditorKind.TEXT_PASSWORD,0));
        assertFalse(EditorKind.showsTools(EditorKind.CLASS_TEXT|EditorKind.TEXT_WEB_PASSWORD,0));
        assertFalse(EditorKind.showsTools(EditorKind.CLASS_NUMBER|EditorKind.NUMBER_PASSWORD,0));
        assertTrue(EditorKind.showsTools(EditorKind.CLASS_TEXT|EditorKind.TEXT_VISIBLE_PASSWORD,0));
        assertTrue(EditorKind.showsTools(EditorKind.CLASS_PHONE,0));
        assertTrue(EditorKind.showsTools(EditorKind.CLASS_DATETIME,0));
    }
    @Test public void asciiRequestStartsEnglishOnce() {
        assertTrue(EditorKind.enterAscii(false,EditorKind.FORCE_ASCII));
        assertFalse(EditorKind.enterAscii(true,EditorKind.FORCE_ASCII));
        assertFalse(EditorKind.enterAscii(false,0));
        assertTrue(EditorKind.asciiRequested(EditorKind.FORCE_ASCII));
        assertFalse(EditorKind.asciiRequested(0));
    }
}
