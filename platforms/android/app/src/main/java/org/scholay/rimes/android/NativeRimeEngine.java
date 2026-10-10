package org.scholay.rimes.android;

import org.scholay.rimes.core.RimeEngine;

final class NativeRimeEngine implements RimeEngine {
    static { System.loadLibrary("rimes_jni"); }
    public void initialize(String system, String user) { initializeNative(system,user); }
    public long createSession() { return createNative(); }
    public void destroySession(long session) { if(session!=0) destroyNative(session); }
    public boolean selectSchema(long session,String schema) { return schemaNative(session,schema); }
    public Snapshot processKey(long session,int key) { return snapshotNative(session,keyNative(session,key)); }
    public Snapshot selectCandidate(long session,int index) { return snapshotNative(session,selectNative(session,index)); }
    public Snapshot snapshot(long session) { return snapshotNative(session,false); }
    public void clearComposition(long session) { clearNative(session); }
    @Override public double[] candidateQualities(long session) { return candidateQualities(session,60); }
    @Override public double[] candidateQualities(long session,int limit) {
        if(session==0 || limit<=0) return new double[0];
        double[] values=qualitiesNative(session,limit);
        return values==null?new double[0]:values;
    }
    @Override public boolean processKeyQuiet(long session,int key) { return session!=0 && keyNative(session,key); }
    @Override public Snapshot snapshotLimited(long session,int limit) { return snapshotCappedNative(session,false,limit); }
    @Override public String compositionRaw(long session) {
        if(session==0) return "";
        String raw=inputNative(session);
        return raw==null?"":raw;
    }
    @Override public void discardCommit(long session) { if(session!=0) discardCommitNative(session); }
    private static native void initializeNative(String system,String user);
    private static native long createNative();
    private static native void destroyNative(long session);
    private static native boolean schemaNative(long session,String schema);
    private static native boolean keyNative(long session,int key);
    private static native boolean selectNative(long session,int index);
    private static native void clearNative(long session);
    private static native double[] qualitiesNative(long session,int limit);
    private static native Snapshot snapshotNative(long session,boolean handled);
    private static native Snapshot snapshotCappedNative(long session,boolean handled,int limit);
    private static native String inputNative(long session);
    private static native void discardCommitNative(long session);
    static native String roundTripNative(String text);
}
