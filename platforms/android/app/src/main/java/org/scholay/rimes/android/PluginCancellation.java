package org.scholay.rimes.android;

import java.util.concurrent.atomic.AtomicBoolean;

/** Cooperative cancellation plus an asynchronous network disconnect hook. */
final class PluginCancellation {
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private Runnable abort;
    void cancel() {
        Runnable callback;
        synchronized(this) { cancelled.set(true); callback=abort; abort=null; }
        if(callback!=null) callback.run();
    }
    void onCancel(Runnable callback) {
        boolean run;
        synchronized(this) { run=cancelled.get(); if(!run) abort=callback; }
        if(run) callback.run();
    }
    synchronized void removeOnCancel(Runnable callback) { if(abort==callback) abort=null; }
    boolean isCancelled() { return cancelled.get() || Thread.currentThread().isInterrupted(); }
    void check() { if(isCancelled()) throw new Cancelled(); }
    static final class Cancelled extends RuntimeException {
        Cancelled() { super("Plugin job cancelled",null,false,false); }
    }
}
