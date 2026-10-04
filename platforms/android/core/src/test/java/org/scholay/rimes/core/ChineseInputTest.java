package org.scholay.rimes.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class ChineseInputTest {
    private BufferSession buffer() { BufferSession b=new BufferSession(); b.beginTarget(true); b.setEnabled(true); return b; }
    @Test public void rimeConfirmationsAndFollowingEnglishKeepBoundaries() {
        BufferSession b=buffer(); b.appendCommittedBlock("你好"); b.appendCommittedBlock("世界𠮷"); b.appendLiteral("hello world ");
        assertEquals(4,b.blockCount()); assertEquals("你好",b.prepare(false).text);
        assertTrue(b.acknowledge(b.prepare(false))); assertEquals("世界𠮷",b.prepare(false).text);
        b.deleteLastBlock(); b.deleteLastBlock(); b.deleteLastBlock(); assertEquals("",b.text());
    }
    @Test public void failedOrOverCapacityConfirmationDoesNotChangeExistingBlocks() {
        BufferSession b=buffer(); b.appendCommittedBlock("你".repeat(BufferSession.MAX_CHARACTERS));
        assertFalse(b.appendCommittedBlock("好")); assertEquals(1,b.blockCount());
        b.setEnabled(false); assertFalse(b.appendCommittedBlock("好")); b.setEnabled(true);
        assertEquals(BufferSession.MAX_CHARACTERS,b.prepare(true).text.length());
    }
    @Test public void rapidResultsAreAcceptedInOrderAndNeverTwice() {
        InputEpoch epoch=new InputEpoch(); InputEpoch.Ticket a=epoch.issue(), b=epoch.issue();
        assertTrue(epoch.accept(a)); assertTrue(epoch.accept(b)); assertFalse(epoch.accept(b)); assertFalse(epoch.accept(a));
    }
    @Test public void fieldHideAndSelectionRevocationRejectLateResults() {
        InputEpoch epoch=new InputEpoch(); InputEpoch.Ticket old=epoch.issue(); epoch.revoke();
        InputEpoch.Ticket fresh=epoch.issue(); assertFalse(epoch.current(old)); assertFalse(epoch.accept(old)); assertTrue(epoch.accept(fresh));
    }
    @Test public void snapshotCannotBeMutatedByProducerOrConsumer() {
        String[] values={"你好"}; RimeEngine.Snapshot s=new RimeEngine.Snapshot(true,"nihao","ni hao",6,"",values,new String[]{""},0,0,true);
        values[0]="different"; assertEquals("你好",s.candidates.get(0));
        try { s.candidates.set(0,"wrong"); fail(); } catch(UnsupportedOperationException expected) { assertTrue(s.composing()); }
    }
}
