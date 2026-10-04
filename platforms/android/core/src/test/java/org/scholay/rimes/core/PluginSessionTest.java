package org.scholay.rimes.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class PluginSessionTest {
    private BufferSession source() {
        BufferSession buffer = new BufferSession();
        buffer.beginTarget(true);
        buffer.setEnabled(true);
        buffer.appendCommittedBlock("你好😀");
        buffer.appendLiteral(" a source");
        return buffer;
    }
    private PluginSession plugin() {
        PluginSession session = new PluginSession();
        session.select("translate");
        return session;
    }

    @Test public void captureAndStreamingSnapshotsAreImmutableAndPartialOutputCannotSend() {
        BufferSession buffer = source(); PluginSession plugin = plugin();
        PluginSession.Request request = plugin.start(buffer);
        assertEquals("translate", request.plugin);
        assertEquals("你好😀 a source", request.source.text);
        assertTrue(plugin.update(request, buffer, "Hello", false));
        PluginSession.Snapshot partial = plugin.snapshot(buffer);
        assertEquals(PluginSession.Status.RUNNING, partial.status);
        assertEquals("Hello", partial.output);
        assertNull(plugin.prepare(buffer));
        assertTrue(plugin.update(request, buffer, "Hello 😀\nworld", true));
        assertEquals("Hello", partial.output);
        assertEquals(PluginSession.Status.RUNNING, partial.status);
        PluginSession.Delivery delivery = plugin.prepare(buffer);
        assertEquals("Hello 😀\nworld", delivery.text);
        assertTrue(plugin.isCurrent(delivery, buffer));
        assertEquals("你好😀 a source", buffer.text());
    }

    @Test public void rejectedHostCommitRetainsOutputAndSourceUntilWholeOutputAcceptedOnce() {
        BufferSession buffer = source(); PluginSession plugin = plugin();
        PluginSession.Request request = plugin.start(buffer);
        plugin.update(request, buffer, "First sentence.\nSecond sentence. 😀", true);
        PluginSession.Delivery rejected = plugin.prepare(buffer); // commitText returned false: no acknowledge.
        assertEquals(rejected.text, plugin.prepare(buffer).text);
        assertEquals("你好😀 a source", buffer.text());
        assertTrue(plugin.acknowledge(plugin.prepare(buffer), buffer));
        assertEquals("", buffer.text());
        assertEquals(PluginSession.Status.IDLE, plugin.snapshot(buffer).status);
        assertEquals("translate", plugin.snapshot(buffer).plugin);
        assertNull(plugin.prepare(buffer));
        assertFalse(plugin.acknowledge(rejected, buffer));
        assertFalse(plugin.update(request, buffer, "late duplicate", true));
    }

    @Test public void repeatedRunDoesNotStartDuplicateAndCompletionCannotBeOverwritten() {
        BufferSession buffer = source(); PluginSession plugin = plugin();
        PluginSession.Request request = plugin.start(buffer);
        assertNull(plugin.start(buffer));
        assertTrue(plugin.update(request, buffer, "done", true));
        assertFalse(plugin.update(request, buffer, "late partial", false));
        assertFalse(plugin.fail(request, buffer, "late network failure"));
        assertEquals("done", plugin.snapshot(buffer).output);
    }

    @Test public void sourceChangeInvalidatesPartialFinalAndPreparedDeliveryEvenIfTextRestored() {
        BufferSession buffer = source(); PluginSession plugin = plugin();
        PluginSession.Request request = plugin.start(buffer);
        plugin.update(request, buffer, "partial", false);
        buffer.appendCommittedBlock("extra");
        assertFalse(plugin.update(request, buffer, "late completion", true));
        assertEquals(PluginSession.Status.IDLE, plugin.snapshot(buffer).status);
        assertEquals("", plugin.snapshot(buffer).output);
        buffer.deleteLastBlock(); // Same visible source does not restore an old request authorization.
        assertEquals(request.source.text, buffer.text());
        assertFalse(plugin.update(request, buffer, "restored text replay", true));
        request = plugin.start(buffer); plugin.update(request, buffer, "new output", true);
        PluginSession.Delivery old = plugin.prepare(buffer);
        buffer.deleteLastBlock();
        assertFalse(plugin.isCurrent(old, buffer));
        assertFalse(plugin.acknowledge(old, buffer));
        assertEquals("你好😀 a ", buffer.text());
    }

    @Test public void pluginChangeCancelsOldRequestAndResultButKeepsSource() {
        BufferSession buffer = source(); PluginSession plugin = plugin();
        PluginSession.Request request = plugin.start(buffer);
        plugin.update(request, buffer, "old output", true);
        PluginSession.Delivery old = plugin.prepare(buffer);
        plugin.select("polish");
        assertFalse(plugin.isCurrent(old, buffer));
        assertFalse(plugin.update(request, buffer, "late translation", true));
        assertEquals("", plugin.snapshot(buffer).output);
        assertEquals("你好😀 a source", buffer.text());
        PluginSession.Request next = plugin.start(buffer);
        assertTrue(next.id > request.id);
        assertEquals("polish", next.plugin);
        plugin.select(null);
        assertFalse(plugin.update(next, buffer, "late polish", true));
        assertNull(plugin.start(buffer));
    }

    @Test public void selectingSamePluginInPanelDoesNotCancelRequestOrResult() {
        BufferSession buffer = source(); PluginSession plugin = plugin();
        PluginSession.Request request = plugin.start(buffer);
        plugin.select("translate");
        assertTrue(plugin.update(request, buffer, "ready", true));
        PluginSession.Delivery delivery = plugin.prepare(buffer);
        plugin.select("translate");
        assertTrue(plugin.isCurrent(delivery, buffer));
        assertEquals("ready", plugin.snapshot(buffer).output);
    }

    @Test public void cancellationAndRegenerationRejectOldCallbacksAndDeliveries() {
        BufferSession buffer = source(); PluginSession plugin = plugin();
        PluginSession.Request old = plugin.start(buffer);
        plugin.invalidate();
        PluginSession.Request current = plugin.start(buffer);
        assertFalse(plugin.fail(old, buffer, "cancelled"));
        assertFalse(plugin.update(old, buffer, "stale", true));
        assertTrue(plugin.update(current, buffer, "current", true));
        PluginSession.Delivery oldDelivery = plugin.prepare(buffer);
        PluginSession.Request regenerated = plugin.start(buffer);
        assertFalse(plugin.isCurrent(oldDelivery, buffer));
        assertFalse(plugin.acknowledge(oldDelivery, buffer));
        assertTrue(plugin.update(regenerated, buffer, "regenerated", true));
        assertEquals("regenerated", plugin.prepare(buffer).text);
        assertEquals("你好😀 a source", buffer.text());
    }

    @Test public void networkFailureDiscardsPartialOutputAndAllowsRetryWithoutLosingSource() {
        BufferSession buffer = source(); PluginSession plugin = plugin();
        PluginSession.Request request = plugin.start(buffer);
        plugin.update(request, buffer, "unfinished", false);
        assertTrue(plugin.fail(request, buffer, "HTTP 503"));
        assertEquals(PluginSession.Status.ERROR, plugin.snapshot(buffer).status);
        assertEquals("HTTP 503", plugin.snapshot(buffer).message);
        assertEquals("", plugin.snapshot(buffer).output);
        assertNull(plugin.prepare(buffer));
        assertEquals("你好😀 a source", buffer.text());
        assertNotNull(plugin.start(buffer));
    }

    @Test public void emptyCompletionIsAnErrorAndCannotConsumeSource() {
        BufferSession buffer = source(); PluginSession plugin = plugin();
        PluginSession.Request request = plugin.start(buffer);
        assertFalse(plugin.update(request, buffer, null, false));
        assertTrue(plugin.update(request, buffer, "", true));
        assertEquals(PluginSession.Status.ERROR, plugin.snapshot(buffer).status);
        assertFalse(plugin.snapshot(buffer).message.isEmpty());
        assertNull(plugin.prepare(buffer));
        assertEquals("你好😀 a source", buffer.text());
    }

    @Test public void hiddenOrPrivateTargetCannotRestoreRequestOrConsumeNewTargetSource() {
        BufferSession buffer = source(); PluginSession plugin = plugin();
        PluginSession.Request request = plugin.start(buffer);
        plugin.update(request, buffer, "old output", true);
        PluginSession.Delivery old = plugin.prepare(buffer);
        buffer.finishTarget(); plugin.clear();
        assertFalse(plugin.acknowledge(old, buffer));
        assertFalse(plugin.update(request, buffer, "late", true));
        assertNull(plugin.snapshot(buffer).plugin);
        assertEquals("", plugin.snapshot(buffer).output);
        plugin.select("ask"); assertNull(plugin.start(buffer));
        buffer.beginTarget(true); buffer.setEnabled(true); buffer.appendCommittedBlock("new target");
        assertFalse(plugin.acknowledge(old, buffer));
        assertEquals("new target", buffer.text());
    }

    @Test public void pausingBufferRevokesOutputEvenWhenDraftIsPreserved() {
        BufferSession buffer = source(); PluginSession plugin = plugin();
        PluginSession.Request request = plugin.start(buffer);
        plugin.update(request, buffer, "generated", true);
        PluginSession.Delivery old = plugin.prepare(buffer);
        buffer.setEnabled(false); buffer.setEnabled(true);
        assertFalse(plugin.isCurrent(old, buffer));
        assertEquals(PluginSession.Status.IDLE, plugin.snapshot(buffer).status);
        assertEquals("你好😀 a source", buffer.text());
    }

    @Test public void requestsAndDeliveriesCannotCrossOwners() {
        BufferSession buffer = source(); PluginSession first = plugin(), second = plugin();
        PluginSession.Request request = first.start(buffer);
        assertFalse(second.update(request, buffer, "foreign", true));
        first.update(request, buffer, "owned", true);
        PluginSession.Delivery delivery = first.prepare(buffer);
        assertFalse(second.acknowledge(delivery, buffer));
        BufferSession other = source();
        assertFalse(first.acknowledge(delivery, other));
        assertEquals("你好😀 a source", buffer.text());
        assertEquals("你好😀 a source", other.text());
    }

    @Test public void absentSelectionOrEmptySourceCannotStartRequest() {
        BufferSession buffer = source(); PluginSession plugin = new PluginSession();
        assertNull(plugin.start(buffer));
        plugin.select("poem"); buffer.clear();
        assertNull(plugin.start(buffer));
        assertNull(plugin.start(null));
        assertEquals(PluginSession.Status.IDLE, plugin.snapshot(buffer).status);
    }

    @Test public void exactOutputLimitAcceptsWholeBlockIncludingNonBmpUtf16Units() {
        BufferSession buffer = source(); PluginSession plugin = plugin();
        PluginSession.Request request = plugin.start(buffer);
        String output = "😀".repeat(PluginSession.MAX_OUTPUT_UNITS / 2);
        assertEquals(PluginSession.MAX_OUTPUT_UNITS, output.length());
        assertTrue(plugin.update(request, buffer, output, true));
        assertEquals(output, plugin.prepare(buffer).text);
        assertTrue(plugin.acknowledge(plugin.prepare(buffer), buffer));
        assertEquals("", buffer.text());
    }

    @Test public void oversizedPartialOrFinalFailsClosedAndRetainsSource() {
        for (boolean complete : new boolean[]{false, true}) {
            BufferSession buffer = source(); PluginSession plugin = plugin();
            PluginSession.Request request = plugin.start(buffer);
            plugin.update(request, buffer, "partial", false);
            assertTrue(plugin.update(request, buffer, "x".repeat(PluginSession.MAX_OUTPUT_UNITS + 1), complete));
            assertEquals(PluginSession.Status.ERROR, plugin.snapshot(buffer).status);
            assertEquals("", plugin.snapshot(buffer).output);
            assertFalse(plugin.snapshot(buffer).message.isEmpty());
            assertNull(plugin.prepare(buffer));
            assertFalse(plugin.update(request, buffer, "late smaller completion", true));
            assertEquals("你好😀 a source", buffer.text());
        }
    }

    @Test public void blankValidationSupportsUnicodeWhitespaceWithoutNewRuntimeApis() {
        PluginSession plugin = new PluginSession();
        try { plugin.select(" \t\n\u2003"); fail("blank ID accepted"); }
        catch (IllegalArgumentException expected) { /* All Unicode whitespace is blank. */ }
        BufferSession buffer = source(); plugin.select("translate");
        PluginSession.Request request = plugin.start(buffer);
        assertTrue(plugin.fail(request, buffer, "\u2003\t"));
        assertEquals("Plugin request failed", plugin.snapshot(buffer).message);
    }
}
