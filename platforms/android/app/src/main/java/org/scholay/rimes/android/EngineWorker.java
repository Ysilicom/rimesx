package org.scholay.rimes.android;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One process-wide serial lane: librime and LevelDB never run on the UI thread. */
final class EngineWorker {
    static final ExecutorService QUEUE=Executors.newSingleThreadExecutor(r -> {
        Thread thread=new Thread(() -> {
            // Foreground keeps a key ahead of ordinary background work. Urgent-display would compete with the press highlight.
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_FOREGROUND);
            r.run();
        },"RIMES-engine");
        thread.setPriority(Thread.NORM_PRIORITY);
        return thread;
    });
    private EngineWorker() {}
}
