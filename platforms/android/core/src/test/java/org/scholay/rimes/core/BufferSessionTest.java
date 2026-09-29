package org.scholay.rimes.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class BufferSessionTest {
    private BufferSession editing() {
        BufferSession buffer = new BufferSession();
        buffer.beginTarget(true);
        assertTrue(buffer.setEnabled(true));
        return buffer;
    }

    @Test public void nextThenAllPreservesEveryCharacter() {
        BufferSession buffer = editing();
        buffer.appendLiteral("Hello, world! 👋");
        assertEquals(2, buffer.blockCount());
        BufferSession.Delivery first = buffer.prepare(false);
        assertEquals("Hello, ", first.text);
        assertTrue(buffer.acknowledge(first));
        assertEquals("world! 👋", buffer.prepare(true).text);
        assertTrue(buffer.acknowledge(buffer.prepare(true)));
        assertEquals("", buffer.text());
    }

    @Test public void failedHostInsertionDoesNotConsumeText() {
        BufferSession buffer = editing();
        buffer.appendLiteral("keep me");
        buffer.prepare(true); // The host rejected commitText: do not acknowledge.
        assertEquals("keep me", buffer.text());
    }

    @Test public void switchingFieldRevokesOldDeliveryAndClearsDraft() {
        BufferSession buffer = editing();
        buffer.appendLiteral("old field");
        BufferSession.Delivery old = buffer.prepare(true);
        buffer.beginTarget(true);
        buffer.setEnabled(true);
        buffer.appendLiteral("new field");
        assertFalse(buffer.acknowledge(old));
        assertEquals("new field", buffer.text());
    }

    @Test public void editAndPauseRevokePreparedDelivery() {
        BufferSession buffer = editing();
        buffer.appendLiteral("a");
        BufferSession.Delivery old = buffer.prepare(true);
        buffer.appendLiteral("b");
        assertFalse(buffer.acknowledge(old));
        old = buffer.prepare(true);
        buffer.setEnabled(false);
        buffer.setEnabled(true);
        assertFalse(buffer.acknowledge(old));
        assertEquals("ab", buffer.text());
    }

    @Test public void passwordFieldCannotCaptureOrDeliver() {
        BufferSession buffer = editing();
        buffer.appendLiteral("draft");
        BufferSession.Delivery old = buffer.prepare(true);
        buffer.beginTarget(false);
        assertFalse(buffer.setEnabled(true));
        assertFalse(buffer.appendLiteral("secret"));
        assertNull(buffer.prepare(true));
        assertFalse(buffer.acknowledge(old));
        assertEquals("", buffer.text());
    }

    @Test public void hideClearsDraftAndRevokesDelivery() {
        BufferSession buffer = editing();
        buffer.appendLiteral("private draft");
        BufferSession.Delivery old = buffer.prepare(true);
        buffer.finishTarget();
        assertFalse(buffer.acknowledge(old));
        assertEquals("", buffer.text());
        assertFalse(buffer.isEnabled());
    }

    @Test public void deliveredBlockCannotBeConsumedTwiceOrByAnotherSession() {
        BufferSession buffer = editing();
        buffer.appendLiteral("one two");
        BufferSession.Delivery first = buffer.prepare(false);
        BufferSession other = editing();
        other.appendLiteral("different");
        assertFalse(other.acknowledge(first));
        assertTrue(buffer.acknowledge(first));
        assertFalse(buffer.acknowledge(first));
        assertEquals("two", buffer.text());
    }

    @Test public void overLimitAppendIsAtomic() {
        BufferSession buffer = editing();
        buffer.appendLiteral("keep");
        assertFalse(buffer.appendLiteral("x".repeat(BufferSession.MAX_CHARACTERS)));
        assertEquals("keep", buffer.text());
    }

    @Test public void deleteRemovesTheWholeLastBlock() {
        BufferSession buffer = editing();
        buffer.appendLiteral("Hello world 👋");
        buffer.deleteLastBlock();
        assertEquals("Hello ", buffer.text());
    }
}

