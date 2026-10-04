package org.scholay.rimes.android;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.cert.Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;

/** Fake connections for failure policy; real CometAPI is a separate, explicit live mode. */
final class NetworkAiContract {
    private static final String TEST_KEY="synthetic-test-credential";
    private static final String WIRE="data: {\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"你好 😀\"},\"finish_reason\":null}]}\n\n"
            +"data: {\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
            +"data: [DONE]\n\n";
    private int checks;
    private void check(boolean value,String label) { checks++; if(!value) throw new AssertionError(label); }
    static int run(Instrumentation instrumentation) throws Exception {
        NetworkAiContract test=new NetworkAiContract();
        test.requests(); test.transport(); test.cancellation(); test.storage(instrumentation.getTargetContext());
        return test.checks;
    }
    private void requests() throws Exception {
        JSONObject ask=request("ask","你好😀","auto");
        check(ask.getBoolean("stream") && ask.getInt("max_tokens")==2048,"bounded streaming request");
        check("disabled".equals(ask.getJSONObject("thinking").getString("type")),"DeepSeek thinking disabled");
        check("你好😀".equals(ask.getJSONArray("messages").getJSONObject(1).getString("content")),"source Unicode preserved");
        check(!ask.toString().contains(TEST_KEY),"request JSON never holds credential");
        check(request("translate","你好","auto").toString().contains("into English"),"auto Chinese to English");
        check(request("translate","hello","auto").toString().contains("into Simplified Chinese"),"auto English to Chinese");
        check(request("translate","hello","zh-en").toString().contains("into English"),"explicit direction");
        try { request("translate","text","invalid"); throw new AssertionError("direction accepted"); }
        catch(OpenAiChatCodec.Failure e) { check(e.code==OpenAiChatCodec.Code.INVALID_DIRECTION,"bad direction rejected"); }
    }
    private JSONObject request(String plugin,String text,String direction) throws Exception {
        return new JSONObject(new String(OpenAiChatCodec.makeRemoteRequest(plugin,text,CometAiSettings.DEFAULT_MODEL,direction),StandardCharsets.UTF_8));
    }
    private void transport() throws Exception {
        FakeConnection connection=new FakeConnection(200,"text/event-stream; charset=utf-8");
        AtomicInteger calls=new AtomicInteger();
        PluginCancellation token=new PluginCancellation();
        OpenAiChatCodec.Decoder decoder=new OpenAiChatCodec.Decoder(token,(text,complete) -> {});
        byte[] body=OpenAiChatCodec.makeRemoteRequest("ask","hello",CometAiSettings.DEFAULT_MODEL,"auto");
        new HttpOpenAiTransport(url -> { calls.incrementAndGet(); check(CometAiSettings.ENDPOINT.equals(url.toString()),"fixed HTTPS recipient"); return connection; })
                .stream(body,TEST_KEY,token,decoder::append);
        check("你好 😀".equals(decoder.finish()),"real transport boundary decodes complete SSE");
        check(calls.get()==1 && connection.disconnected,"one connection closed");
        check(!connection.getInstanceFollowRedirects() && !connection.getUseCaches(),"redirect and disk cache disabled");
        check(("Bearer "+TEST_KEY).equals(connection.getRequestProperty("Authorization")),"Bearer header");
        check(java.util.Arrays.equals(body,connection.output.toByteArray()),"exact UTF8 request bytes");
        for(int status:new int[]{301,302,307,401,403,429,500}) {
            FakeConnection failed=new FakeConnection(status,"application/json");
            try { new HttpOpenAiTransport(url -> failed).stream(body,TEST_KEY,new PluginCancellation(),(b,o,n) -> { throw new AssertionError("error body delivered"); }); throw new AssertionError("HTTP error accepted"); }
            catch(OpenAiChatCodec.Failure e) {
                check(e.code==OpenAiChatCodec.Code.HTTP_ERROR,"HTTP error typed "+status);
                check(!e.getMessage().contains(TEST_KEY) && failed.reads==0 && failed.disconnected,"HTTP body stays private "+status);
            }
        }
        try { new HttpOpenAiTransport(url -> new FakeConnection(200,"application/json")).stream(body,TEST_KEY,new PluginCancellation(),(b,o,n) -> {}); throw new AssertionError("JSON accepted as SSE"); }
        catch(OpenAiChatCodec.Failure e) { check(e.code==OpenAiChatCodec.Code.INVALID_FRAME,"wrong content type rejected"); }
        try { new HttpOpenAiTransport(url -> { throw new java.io.IOException(TEST_KEY); }).stream(body,TEST_KEY,new PluginCancellation(),(b,o,n) -> {}); throw new AssertionError("IO error accepted"); }
        catch(OpenAiChatCodec.Failure e) { check(e.code==OpenAiChatCodec.Code.NETWORK_ERROR && !e.getMessage().contains(TEST_KEY),"network exception redacted"); }
        FakeConnection region=new FakeConnection(403,"application/json") {
            @Override public InputStream getErrorStream() {
                return new ByteArrayInputStream(("{\"error\":{\"code\":\"region_restricted\",\"message\":\""+TEST_KEY+"\"}}").getBytes(StandardCharsets.UTF_8));
            }
        };
        try { new HttpOpenAiTransport(url -> region).stream(body,TEST_KEY,new PluginCancellation(),(b,o,n) -> {}); throw new AssertionError("region accepted"); }
        catch(OpenAiChatCodec.Failure e) { check(e.getMessage().contains("当前地区") && !e.getMessage().contains(TEST_KEY),"region error actionable without provider-message disclosure"); }
    }
    private void cancellation() throws Exception {
        PluginCancellation early=new PluginCancellation(); early.cancel();
        try { new HttpOpenAiTransport(url -> { throw new AssertionError("cancelled request connected"); }).stream(new byte[0],TEST_KEY,early,(b,o,n) -> {}); throw new AssertionError("cancellation ignored"); }
        catch(PluginCancellation.Cancelled expected) { check(true,"cancel before connection"); }
        CountDownLatch reading=new CountDownLatch(1),disconnectSignal=new CountDownLatch(1),finished=new CountDownLatch(1);
        FakeConnection connection=new FakeConnection(200,"text/event-stream") {
            @Override public InputStream getInputStream() {
                return new InputStream() { @Override public int read() throws java.io.IOException {
                    reading.countDown(); try { disconnectSignal.await(3,TimeUnit.SECONDS); }
                    catch(InterruptedException e) { Thread.currentThread().interrupt(); }
                    throw new java.io.IOException("cancelled");
                }};
            }
            @Override public void disconnect() { super.disconnect(); disconnectSignal.countDown(); }
        };
        PluginCancellation token=new PluginCancellation(); AtomicReference<Throwable> unexpected=new AtomicReference<>();
        Thread worker=new Thread(() -> {
            try { new HttpOpenAiTransport(url -> connection).stream(new byte[0],TEST_KEY,token,(b,o,n) -> {}); unexpected.set(new AssertionError("cancel completed")); }
            catch(PluginCancellation.Cancelled expected) { /* No completion or failure callback. */ }
            catch(Throwable e) { unexpected.set(e); }
            finally { finished.countDown(); }
        }); worker.start();
        check(reading.await(2,TimeUnit.SECONDS),"read in flight"); token.cancel();
        check(finished.await(2,TimeUnit.SECONDS) && unexpected.get()==null,"cancel disconnects blocked reader");
        check(connection.disconnected,"cancel closes connection");
    }
    private void storage(Context context) throws Exception {
        SharedPreferences prefs=context.getSharedPreferences(KeyboardSettings.PREFERENCES_NAME,Context.MODE_PRIVATE);
        String old=prefs.getString(CometAiSettings.KEY,null);
        CometAiSettings settings=new CometAiSettings(context);
        try {
            settings.clear();
            try { settings.save(CometAiSettings.DEFAULT_MODEL,"",true,false); throw new AssertionError("missing key accepted"); }
            catch(OpenAiChatCodec.Failure e) { check(e.code==OpenAiChatCodec.Code.NOT_CONFIGURED,"missing key fails closed"); }
            settings.save(CometAiSettings.DEFAULT_MODEL,TEST_KEY,true,true);
            CometAiSettings.Snapshot profile=settings.snapshot();
            check(profile.remote("ask") && profile.remote("translate"),"saved online route");
            check(TEST_KEY.equals(settings.credential(profile)),"Keystore roundtrip");
            check(!prefs.getString(CometAiSettings.KEY,"").contains(TEST_KEY),"no plaintext key in preferences");
            settings.save(CometAiSettings.DEFAULT_MODEL,"",true,false);
            check(!settings.snapshot().remote("translate") && TEST_KEY.equals(settings.credential(settings.snapshot())),"blank keeps key and offline translation");
            settings.save(CometAiSettings.DEFAULT_MODEL,"",false,false);
            check(!settings.snapshot().remote("ask") && settings.snapshot().hasKey(),"disable preserves key but not authority");
            check(profile.remote("translate"),"captured profile immutable");
            settings.clear(); check(!settings.snapshot().enabled && !settings.snapshot().hasKey(),"remove disables and removes wrapped key");
        } finally {
            SharedPreferences.Editor edit=prefs.edit(); if(old==null) edit.remove(CometAiSettings.KEY); else edit.putString(CometAiSettings.KEY,old); edit.commit();
        }
    }
    /** Opt-in only: caller provides a private cache file over stdin, never instrumentation args. */
    static String prepareLive(Instrumentation instrumentation) throws Exception {
        configureLive(instrumentation);
        Context context=instrumentation.getTargetContext();
        CometAiSettings settings=new CometAiSettings(context);
        long start=android.os.SystemClock.elapsedRealtime();
        AtomicReference<String> result=new AtomicReference<>(),failure=new AtomicReference<>();
        AtomicInteger updates=new AtomicInteger(); CountDownLatch done=new CountDownLatch(1);
        try(BufferPluginExecutor executor=new BufferPluginExecutor(context)) {
            executor.run("ask","只回复：Android 连接成功", "auto",settings.snapshot(),new BufferPluginExecutor.Listener() {
                public void onUpdate(String text,boolean complete) { updates.incrementAndGet(); if(complete) { result.set(text); done.countDown(); } }
                public void onFailure(String message) { failure.set(message); done.countDown(); }
            });
            if(!done.await(65,TimeUnit.SECONDS) || failure.get()!=null || result.get()==null
                    || !result.get().contains("Android") || result.get().contains("Mock")) {
                settings.clear(); throw new AssertionError("Live Android request failed: "+failure.get());
            }
        }
        return "PASS live CometAPI Android request; model="+CometAiSettings.DEFAULT_MODEL+" updates="+updates.get()
                +" elapsedMs="+(android.os.SystemClock.elapsedRealtime()-start)+"; temporary encrypted profile ready for keyboard test\n";
    }
    static void configureLive(Instrumentation instrumentation) throws Exception {
        foreground(instrumentation);
        Context context=instrumentation.getTargetContext(); File file=new File(context.getCacheDir(),"comet-test-key");
        String secret;
        try { secret=new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8).trim(); }
        finally { Files.deleteIfExists(file.toPath()); }
        CometAiSettings settings=new CometAiSettings(context);
        if(settings.snapshot().hasKey() && !secret.equals(settings.credential(settings.snapshot())))
            throw new AssertionError("Existing AI key must be preserved; refusing test replacement");
        settings.save(CometAiSettings.DEFAULT_MODEL,secret,true,true);
    }
    static void foreground(Instrumentation instrumentation) throws Exception {
        // This physical host restricts networking for a background instrumentation-only UID.
        // Exercise the ordinary foreground application instead of relaxing device policy.
        try(android.os.ParcelFileDescriptor.AutoCloseInputStream launch=new android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.getUiAutomation().executeShellCommand("am start -W -n "+instrumentation.getTargetContext().getPackageName()+"/org.scholay.rimes.android.SetupActivity"))) {
            byte[] bytes=new byte[1024]; while(launch.read(bytes)!=-1) { /* Launch diagnostics contain no credentials. */ }
        }
    }
    private static class FakeConnection extends HttpsURLConnection {
        final ByteArrayOutputStream output=new ByteArrayOutputStream();
        final int code; final String type; volatile boolean disconnected; int reads;
        FakeConnection(int code,String type) throws java.net.MalformedURLException { super(new URL(CometAiSettings.ENDPOINT)); this.code=code; this.type=type; }
        public void disconnect() { disconnected=true; }
        public boolean usingProxy() { return false; }
        public void connect() {}
        public String getCipherSuite() { return "test"; }
        public Certificate[] getLocalCertificates() { return null; }
        public Certificate[] getServerCertificates() { return null; }
        public int getResponseCode() { return code; }
        public String getContentType() { return type; }
        public OutputStream getOutputStream() { return output; }
        public InputStream getInputStream() { reads++; return new ByteArrayInputStream(WIRE.getBytes(StandardCharsets.UTF_8)); }
    }
}
