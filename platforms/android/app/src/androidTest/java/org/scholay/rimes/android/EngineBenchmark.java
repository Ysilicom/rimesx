package org.scholay.rimes.android;

import android.app.Instrumentation;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import org.scholay.rimes.core.RimeEngine;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/** Optional real-JNI benchmark. It never measures UI rendering or host commit latency. */
final class EngineBenchmark {
    private static final String[][] PROFILES={
        {"pinyin","rimes_pinyin_private","nihao","你好"},
        {"natural_code","rimes_ziranma_private","nihk","你好"},
        {"flypy","rimes_flypy_private","nihc","你好"},
        {"wubi","rimes_wubi_private","wq","你"},
        {"nine_key_pinyin","rimes_pinyin9_private","64426","你好"}
    };

    static String run(Instrumentation instrumentation,Bundle arguments) throws Exception {
        int samples=argument(arguments,"samples",200,20,1000);
        int warmup=argument(arguments,"warmup",20,1,100);
        android.content.Context target=instrumentation.getTargetContext();
        String selectedIme=android.provider.Settings.Secure.getString(target.getContentResolver(),
                android.provider.Settings.Secure.DEFAULT_INPUT_METHOD);
        if(selectedIme!=null && selectedIme.startsWith(target.getPackageName()+"/"))
            throw new IllegalStateException("Switch away from RIMES before the engine benchmark so its service cannot initialize the singleton.");
        // Separate from EngineResources.userDirectory: synthetic benchmark text must never
        // enter the user's dictionary. Private schemas also protect against accidental reuse
        // of an engine already initialized by an IME service in the target process.
        File user=new File(target.getNoBackupFilesDir(),
                "engine-benchmark-user-"+java.util.UUID.randomUUID());
        if(!user.mkdirs()) throw new java.io.IOException("Cannot create benchmark directory");
        JSONObject report=new JSONObject();
        JSONObject device=new JSONObject();
        device.put("manufacturer",Build.MANUFACTURER).put("model",Build.MODEL)
                .put("android_release",Build.VERSION.RELEASE).put("api",Build.VERSION.SDK_INT)
                .put("process_architecture",System.getProperty("os.arch"))
                .put("supported_abis",new JSONArray(Arrays.asList(Build.SUPPORTED_ABIS)));
        report.put("format_version",1).put("timestamp_epoch_ms",System.currentTimeMillis())
                .put("version_name",target.getPackageManager().getPackageInfo(target.getPackageName(),0).versionName)
                .put("device",device).put("samples_per_profile",samples)
                .put("warmup_phrases_per_profile",warmup).put("learning_enabled",false)
                .put("synthetic_user_directory",true)
                .put("benchmark_user_subdirectory",user.getName())
                .put("rimes_not_selected_as_default_ime",selectedIme!=null)
                .put("clock","SystemClock.elapsedRealtimeNanos")
                .put("scope","Real packaged JNI and process-wide serial EngineWorker. Private schemas. "
                        +"No main-thread rendering, touch dispatch, InputConnection, host text or learning-enabled latency.")
                .put("cold_condition","Run after switching away from RIMES, in a fresh instrumentation target process. "
                        +"Native initialization is a process singleton; do not compare a reused-process result as cold.")
                .put("native_metric","Worker execution time including JNI immutable snapshot construction.")
                .put("serial_metric","Submission through worker execution and Future.get wakeup on instrumentation thread; not UI latency.")
                .put("percentile_method","Nearest rank: sorted[ceil(p * count) - 1]; all statistics in milliseconds.");
        long pssBefore=Debug.getPss();
        long nativeHeapBefore=Debug.getNativeHeapAllocatedSize();
        long javaHeapBefore=usedJavaHeap();
        Set<String> existingVersions=new HashSet<>();
        String[] versionNames=new File(target.getNoBackupFilesDir(),"rime-system").list();
        if(versionNames!=null) existingVersions.addAll(Arrays.asList(versionNames));
        long readyStart=now();
        Timed<File> prepared=call(() -> EngineResources.prepare(target));
        Timed<NativeRimeEngine> constructed=call(NativeRimeEngine::new);
        NativeRimeEngine engine=constructed.value;
        Timed<Void> initialized=call(() -> {
            engine.initialize(prepared.value.getAbsolutePath(),user.getAbsolutePath()); return null;
        });
        long readyNanos=now()-readyStart;
        JSONObject startup=new JSONObject();
        startup.put("existing_resource_version_directory",existingVersions.contains(prepared.value.getName()))
                .put("resource_prepare_serial",single(prepared.roundTripNanos))
                .put("resource_prepare_worker",single(prepared.executionNanos))
                .put("library_load_and_construct_worker",single(constructed.executionNanos))
                .put("native_initialize_worker",single(initialized.executionNanos))
                .put("native_initialize_serial",single(initialized.roundTripNanos))
                .put("ready_total",single(readyNanos));
        report.put("startup",startup);
        JSONArray profiles=new JSONArray();
        StringBuilder stream=new StringBuilder("BENCHMARK real JNI / serial worker; private schemas; milliseconds\n");
        stream.append("STARTUP resourcePrepare=").append(decimal(prepared.executionNanos))
                .append(" libraryLoad=").append(decimal(constructed.executionNanos))
                .append(" nativeInitialize=").append(decimal(initialized.executionNanos))
                .append(" existingResources=").append(existingVersions.contains(prepared.value.getName())).append('\n');
        for(String[] profile:PROFILES) {
            JSONObject measured=measureProfile(engine,profile,samples,warmup);
            profiles.put(measured);
            stream.append(profile[0]).append(' ').append(measured.getJSONObject("key_native_snapshot").toString())
                    .append(" keySerial=").append(measured.getJSONObject("key_serial_round_trip").toString())
                    .append(" candidateCommit=").append(measured.getJSONObject("candidate_select_native_snapshot").toString()).append('\n');
        }
        report.put("profiles",profiles);
        JSONObject memory=new JSONObject();
        memory.put("pss_before_kib",pssBefore).put("pss_after_kib",Debug.getPss())
                .put("native_allocated_before_bytes",nativeHeapBefore)
                .put("native_allocated_after_bytes",Debug.getNativeHeapAllocatedSize())
                .put("java_used_before_bytes",javaHeapBefore).put("java_used_after_bytes",usedJavaHeap())
                .put("scope","Whole instrumentation target process. No forced GC; not an IME-only memory or leak measurement.");
        report.put("memory",memory);
        String fileName="engine-benchmark-"+report.getLong("timestamp_epoch_ms")+".json";
        File output=new File(target.getFilesDir(),fileName);
        Files.write(output.toPath(),(report.toString(2)+"\n").getBytes(StandardCharsets.UTF_8));
        stream.append("REPORT files/").append(fileName).append('\n')
                .append("PASS BENCHMARK ").append(PROFILES.length).append(" schemas, ")
                .append(samples).append(" measured phrases per schema, ").append(warmup).append(" warmup phrases per schema\n");
        return stream.toString();
    }

    private static JSONObject measureProfile(NativeRimeEngine engine,String[] profile,int samples,int warmup) throws Exception {
        String schema=profile[1],code=profile[2],expected=profile[3];
        JSONObject result=new JSONObject();
        result.put("name",profile[0]).put("schema",schema).put("input",code).put("expected",expected);
        Timed<Long> firstCreate=call(engine::createSession);
        long session=firstCreate.value;
        if(session==0) throw new AssertionError("Cannot create benchmark session");
        try {
            Timed<Boolean> firstSelect=call(() -> engine.selectSchema(session,schema));
            if(!firstSelect.value) throw new AssertionError("Cannot select "+schema);
            Stats firstKeys=new Stats();
            long firstPhraseStart=now();
            RimeEngine.Snapshot snapshot=RimeEngine.Snapshot.EMPTY;
            for(char key:code.toCharArray()) {
                Timed<RimeEngine.Snapshot> processed=call(() -> engine.processKey(session,key));
                firstKeys.add(processed.executionNanos); snapshot=processed.value;
            }
            long firstPhraseNanos=now()-firstPhraseStart;
            requireCandidate(snapshot,expected,schema);
            Timed<RimeEngine.Snapshot> firstCommit=call(() -> engine.selectCandidate(session,0));
            requireCommit(firstCommit.value,expected,schema);
            JSONObject first=new JSONObject();
            first.put("session_create_worker",single(firstCreate.executionNanos))
                    .put("schema_select_worker",single(firstSelect.executionNanos))
                    .put("key_native_snapshot",firstKeys.json())
                    .put("phrase_input_serial_round_trip",single(firstPhraseNanos))
                    .put("candidate_select_native_snapshot",single(firstCommit.executionNanos));
            result.put("first_use",first);

            Stats sessionCreate=new Stats(),sessionCreateSerial=new Stats(),schemaSelect=new Stats(),schemaSelectSerial=new Stats();
            for(int i=0;i<samples;i++) {
                Timed<Long> created=call(engine::createSession);
                long temporary=created.value;
                if(temporary==0) throw new AssertionError("Cannot create warm session");
                try {
                    Timed<Boolean> selected=call(() -> engine.selectSchema(temporary,schema));
                    if(!selected.value) throw new AssertionError("Cannot select warm "+schema);
                    sessionCreate.add(created.executionNanos); sessionCreateSerial.add(created.roundTripNanos);
                    schemaSelect.add(selected.executionNanos); schemaSelectSerial.add(selected.roundTripNanos);
                } finally { call(() -> { engine.destroySession(temporary); return null; }); }
            }
            for(int i=0;i<warmup;i++) phrase(engine,session,code,expected,schema,null);
            PhraseStats measured=new PhraseStats();
            for(int i=0;i<samples;i++) phrase(engine,session,code,expected,schema,measured);
            result.put("session_create_native",sessionCreate.json()).put("session_create_serial_round_trip",sessionCreateSerial.json())
                    .put("schema_select_native",schemaSelect.json()).put("schema_select_serial_round_trip",schemaSelectSerial.json())
                    .put("key_native_snapshot",measured.keyNative.json()).put("key_serial_round_trip",measured.keySerial.json())
                    .put("key_queue_wait",measured.keyQueue.json()).put("phrase_input_native_snapshot",measured.phraseNative.json())
                    .put("phrase_input_serial_round_trip",measured.phraseSerial.json())
                    .put("candidate_select_native_snapshot",measured.commitNative.json())
                    .put("candidate_select_serial_round_trip",measured.commitSerial.json())
                    .put("successful_commits",samples);
            return result;
        } finally { call(() -> { engine.destroySession(session); return null; }); }
    }

    private static void phrase(NativeRimeEngine engine,long session,String code,String expected,String schema,PhraseStats stats) throws Exception {
        call(() -> { engine.clearComposition(session); return null; });
        long phraseStart=now(),nativeNanos=0;
        RimeEngine.Snapshot snapshot=RimeEngine.Snapshot.EMPTY;
        for(char key:code.toCharArray()) {
            Timed<RimeEngine.Snapshot> processed=call(() -> engine.processKey(session,key));
            snapshot=processed.value; nativeNanos+=processed.executionNanos;
            if(stats!=null) {
                stats.keyNative.add(processed.executionNanos); stats.keySerial.add(processed.roundTripNanos);
                stats.keyQueue.add(processed.queueWaitNanos);
            }
        }
        long phraseNanos=now()-phraseStart;
        requireCandidate(snapshot,expected,schema);
        Timed<RimeEngine.Snapshot> committed=call(() -> engine.selectCandidate(session,0));
        requireCommit(committed.value,expected,schema);
        if(stats!=null) {
            stats.phraseNative.add(nativeNanos); stats.phraseSerial.add(phraseNanos);
            stats.commitNative.add(committed.executionNanos); stats.commitSerial.add(committed.roundTripNanos);
        }
    }

    private static void requireCandidate(RimeEngine.Snapshot snapshot,String expected,String schema) {
        if(snapshot.candidates.isEmpty() || !expected.equals(snapshot.candidates.get(0)))
            throw new AssertionError("Benchmark first candidate failed for "+schema);
    }
    private static void requireCommit(RimeEngine.Snapshot snapshot,String expected,String schema) {
        if(!expected.equals(snapshot.commit)) throw new AssertionError("Benchmark commit failed for "+schema);
    }
    private static int argument(Bundle args,String key,int fallback,int min,int max) {
        String value=args.getString(key);
        int parsed=value==null?fallback:Integer.parseInt(value);
        if(parsed<min || parsed>max) throw new IllegalArgumentException(key+" must be in "+min+".."+max);
        return parsed;
    }
    private static long now() { return SystemClock.elapsedRealtimeNanos(); }
    private static long usedJavaHeap() { Runtime runtime=Runtime.getRuntime(); return runtime.totalMemory()-runtime.freeMemory(); }
    private static String decimal(long nanos) { return String.format(Locale.ROOT,"%.6f ms",nanos/1_000_000.0); }
    private static JSONObject single(long value) throws Exception { Stats stats=new Stats(); stats.add(value); return stats.json(); }
    private static <T> Timed<T> call(Callable<T> operation) throws Exception {
        long submitted=now();
        Timed<T> timed=EngineWorker.QUEUE.submit(() -> {
            long started=now(); T value=operation.call(); long ended=now();
            return new Timed<>(value,ended-started,started-submitted);
        }).get(30,TimeUnit.SECONDS);
        timed.roundTripNanos=now()-submitted;
        return timed;
    }
    private static final class Timed<T> {
        final T value;
        final long executionNanos,queueWaitNanos;
        long roundTripNanos;
        Timed(T value,long executionNanos,long queueWaitNanos) {
            this.value=value; this.executionNanos=executionNanos; this.queueWaitNanos=queueWaitNanos;
        }
    }
    private static final class PhraseStats {
        final Stats keyNative=new Stats(),keySerial=new Stats(),keyQueue=new Stats();
        final Stats phraseNative=new Stats(),phraseSerial=new Stats(),commitNative=new Stats(),commitSerial=new Stats();
    }
    private static final class Stats {
        final List<Long> nanos=new ArrayList<>();
        void add(long duration) { nanos.add(duration); }
        JSONObject json() throws Exception {
            if(nanos.isEmpty()) throw new IllegalStateException("Empty benchmark metric");
            long[] sorted=new long[nanos.size()]; double total=0;
            for(int i=0;i<sorted.length;i++) { sorted[i]=nanos.get(i); total+=sorted[i]; }
            Arrays.sort(sorted);
            return new JSONObject().put("unit","ms").put("count",sorted.length)
                    .put("mean",total/sorted.length/1_000_000.0)
                    .put("p50",percentile(sorted,0.50)/1_000_000.0)
                    .put("p95",percentile(sorted,0.95)/1_000_000.0)
                    .put("max",sorted[sorted.length-1]/1_000_000.0);
        }
        private static long percentile(long[] values,double p) { return values[(int)Math.ceil(p*values.length)-1]; }
    }
    private EngineBenchmark() {}
}
