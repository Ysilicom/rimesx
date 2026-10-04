package org.scholay.rimes.android;

import java.util.concurrent.atomic.AtomicBoolean;

/** Cooperative cancellation shared by local dictionary, mock transport and stream parser. */
final class PluginCancellation {
    private final AtomicBoolean cancelled=new AtomicBoolean();
    void cancel() { cancelled.set(true); }
    boolean isCancelled() { return cancelled.get() || Thread.currentThread().isInterrupted(); }
    void check() { if(isCancelled()) throw new Cancelled(); }
    static final class Cancelled extends RuntimeException {
        Cancelled() { super("Plugin job cancelled",null,false,false); }
    }
}
