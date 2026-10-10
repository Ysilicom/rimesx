package org.scholay.rimes.core;

import java.util.List;

/** Platform-local engine contract. All calls belong to the same serial worker. */
public interface RimeEngine {
    void initialize(String systemDirectory, String userDirectory);
    long createSession();
    void destroySession(long session);
    boolean selectSchema(long session, String schema);
    Snapshot processKey(long session, int key);
    Snapshot selectCandidate(long session, int index);
    Snapshot snapshot(long session);
    void clearComposition(long session);
    /** Weight of each current candidate, in the same order as {@link Snapshot#candidates}. Empty when unknown. */
    default double[] candidateQualities(long session) { return new double[0]; }
    /** Same weights, stopping after {@code limit} candidates. */
    default double[] candidateQualities(long session, int limit) {
        double[] all=candidateQualities(session);
        if(all==null || limit>=all.length) return all==null?new double[0]:all;
        if(limit<=0) return new double[0];
        return java.util.Arrays.copyOf(all,limit);
    }
    /** One key and no candidate list. The default still builds the list. */
    default boolean processKeyQuiet(long session, int key) { processKey(session,key); return true; }
    /** At most {@code limit} candidates. The default returns the full snapshot. */
    default Snapshot snapshotLimited(long session, int limit) { return snapshot(session); }
    /** Letters currently composed. Empty when the session is idle. */
    default String compositionRaw(long session) { Snapshot snap=snapshot(session); return snap==null||snap.raw==null?"":snap.raw; }
    /** Drop a commit produced while trying a neighbor, so it cannot leak out later. */
    default void discardCommit(long session) {}

    final class Snapshot {
        public static final Snapshot EMPTY = new Snapshot(false,"","",0,"",new String[0],new String[0],0,0,true);
        public final boolean handled;
        public final String raw, preedit, commit;
        public final int caret, pageStart, highlighted;
        public final List<String> candidates, comments;
        public final boolean lastPage;
        public Snapshot(boolean handled, String raw, String preedit, int caret, String commit,
                String[] candidates, String[] comments, int pageStart, int highlighted, boolean lastPage) {
            this.handled=handled; this.raw=raw; this.preedit=preedit; this.caret=caret; this.commit=commit;
            this.candidates=java.util.Collections.unmodifiableList(java.util.Arrays.asList(candidates.clone()));
            this.comments=java.util.Collections.unmodifiableList(java.util.Arrays.asList(comments.clone()));
            this.pageStart=pageStart; this.highlighted=highlighted; this.lastPage=lastPage;
        }
        public boolean composing() { return !raw.isEmpty() || !preedit.isEmpty(); }
    }
}
