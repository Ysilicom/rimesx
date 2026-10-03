package org.scholay.rimes.android;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.inputmethodservice.InputMethodService;
import android.os.Binder;
import android.os.Looper;
import android.os.Process;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.InputBinding;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.scholay.rimes.core.BufferSession;
import org.scholay.rimes.core.ChordGesture;
import org.scholay.rimes.core.ChordLayout;
import org.scholay.rimes.core.PluginSession;

/**
 * Calls the real service delivery methods with a local, synchronously rejecting connection.
 * A remote editor's false return can be obscured by InputConnection IPC, so this deliberately
 * tests below IPC. No service onCreate, native engine, network, real IME binding or app data.
 * Reflection accesses only app state/entries and the public API's protected context attachment;
 * the framework connection is installed through InputMethodImpl.bindInput, never hidden fields.
 */
final class ServiceDeliveryContract {
    private static final String SOURCE="你好😀";
    private static final String OUTPUT="Hello 😀\nA complete generated block.";
    private final Context context;
    private int checks;

    private ServiceDeliveryContract(Context context) { this.context=context; }

    /** Call on the instrumentation thread. Each fixture/connection runs on the actual main Looper. */
    static int run(Instrumentation instrumentation) {
        if(Looper.myLooper()==Looper.getMainLooper()) throw new IllegalStateException("ServiceDeliveryContract must run off main");
        AtomicReference<Throwable> failure=new AtomicReference<>();
        int[] count={0};
        instrumentation.runOnMainSync(() -> {
            try {
                ServiceDeliveryContract contract=new ServiceDeliveryContract(instrumentation.getTargetContext());
                contract.rejectThenRetry(false,false);
                contract.rejectThenRetry(false,true);
                contract.rejectThenRetry(true,true);
                for(boolean plugin:new boolean[]{false,true}) {
                    contract.lostFrameworkBinding(plugin,false);
                    contract.lostFrameworkBinding(plugin,true);
                    contract.retireDuringCommit(plugin);
                }
                count[0]=contract.checks;
            } catch(Throwable error) { failure.set(error); }
        });
        if(failure.get()!=null) throw new AssertionError("Service delivery contract failed",failure.get());
        return count[0];
    }

    private void check(boolean condition,String label) {
        checks++; if(!condition) throw new AssertionError(label);
    }

    private void rejectThenRetry(boolean plugin,boolean all) throws Exception {
        try(Fixture fixture=new Fixture(plugin)) {
            String wanted=plugin?OUTPUT:SOURCE;
            List<Integer> before=fixture.expectations();
            fixture.connection.accept=false;
            fixture.send(all);
            check(fixture.connection.attempts==1,"rejected commit is actually attempted");
            check(fixture.connection.accepted.isEmpty(),"rejected commit does not mutate fake host");
            check(wanted.equals(fixture.connection.lastAttempt),"rejected commit contains exact ordinary/generated text");
            check(SOURCE.equals(fixture.buffer.text()),"rejection retains full source");
            check(fixture.selection()==7 && fixture.selectionStart()==7,"rejection keeps both selection positions");
            check(before.equals(fixture.expectations()),"rejection does not add or remove expected selections");
            if(plugin) {
                check(fixture.plugins.snapshot(fixture.buffer).status==PluginSession.Status.READY,"rejected plugin output remains READY");
                check(OUTPUT.equals(fixture.plugins.prepare(fixture.buffer).text),"rejected plugin output remains complete and retryable");
            }
            fixture.connection.accept=true;
            fixture.send(all);
            check(fixture.connection.attempts==2,"retry makes exactly one further commit attempt");
            check(fixture.connection.accepted.size()==1 && wanted.equals(fixture.connection.accepted.get(0)),"retry commits exact text once");
            check(fixture.buffer.text().isEmpty() && fixture.buffer.blockCount()==0,"accepted complete block consumes source once");
            check(fixture.selection()==7+wanted.length() && fixture.selectionStart()==7+wanted.length(),"accepted commit advances by UTF16 units");
            List<Integer> after=new ArrayList<>(before); after.add(7+wanted.length());
            check(after.equals(fixture.expectations()),"accepted commit registers only its actual expected selection");
            if(plugin) {
                check(fixture.plugins.snapshot(fixture.buffer).status==PluginSession.Status.IDLE,"accepted plugin result becomes idle");
                check(fixture.plugins.prepare(fixture.buffer)==null,"accepted plugin result is no longer sendable");
            }
            fixture.send(all);
            check(fixture.connection.attempts==2 && fixture.connection.accepted.size()==1,"repeated Send cannot duplicate consumed output");
            check(after.equals(fixture.expectations()),"empty repeated Send cannot manufacture another selection expectation");
        }
    }

    private void lostFrameworkBinding(boolean plugin,boolean duringCommit) throws Exception {
        try(Fixture fixture=new Fixture(plugin)) {
            Connection next=new Connection(context);
            List<Integer> before=fixture.expectations();
            if(duringCommit) fixture.connection.beforeReturn=() -> fixture.bind(next);
            else fixture.bind(next);
            fixture.connection.accept=true;
            fixture.send(true);
            check(fixture.connection.attempts==(duringCommit?1:0),"old delivery requires the current framework binding");
            check(next.attempts==0,"old delivery never calls the new connection");
            check(SOURCE.equals(fixture.buffer.text()),"lost authorization does not consume captured source");
            check(fixture.selection()==7 && fixture.selectionStart()==7,"lost authorization does not advance selection");
            check(before.equals(fixture.expectations()),"lost authorization leaves expected selections unchanged");
            if(plugin) check(OUTPUT.equals(fixture.plugins.snapshot(fixture.buffer).output),"authorization loss does not acknowledge old plugin result");
        }
    }

    private void retireDuringCommit(boolean plugin) throws Exception {
        try(Fixture fixture=new Fixture(plugin)) {
            Connection next=new Connection(context);
            fixture.connection.accept=true;
            fixture.connection.beforeReturn=() -> {
                // Use the service's real retirement callback, then install a fresh synthetic target.
                fixture.service.onUnbindInput();
                fixture.bind(next);
                set(fixture.service,"target",next);
                fixture.buffer.beginTarget(true); fixture.buffer.setEnabled(true);
                fixture.buffer.appendCommittedBlock("新目标😀");
                set(fixture.service,"selection",19); set(fixture.service,"selectionStart",19);
                fixture.expected().add(17);
            };
            fixture.send(true);
            check(fixture.connection.attempts==1 && fixture.connection.accepted.size()==1,"old connection may accept before retirement returns");
            check(next.attempts==0,"commit-time retirement never forwards output to new target");
            check("新目标😀".equals(fixture.buffer.text()),"old acknowledgement cannot consume the new target draft");
            check(fixture.selection()==19 && fixture.selectionStart()==19,"old commit cannot overwrite new target selection");
            check(fixture.expectations().size()==1 && fixture.expectations().get(0)==17,"old commit cannot add an expectation to new target");
            check(fixture.plugins.snapshot(fixture.buffer).plugin==null && fixture.plugins.snapshot(fixture.buffer).output.isEmpty(),"retirement clears plugin selection and output");
        }
    }

    private final class Fixture implements AutoCloseable {
        final RimesInputMethodService service=new RimesInputMethodService();
        final InputMethodService.InputMethodImpl input;
        final BufferSession buffer;
        final PluginSession plugins;
        final Connection connection=new Connection(context);
        Fixture(boolean plugin) throws Exception {
            Method attach=ContextWrapper.class.getDeclaredMethod("attachBaseContext",Context.class);
            attach.setAccessible(true); attach.invoke(service,context);
            input=(InputMethodService.InputMethodImpl)service.onCreateInputMethodInterface();
            bind(connection);
            check(service.getCurrentInputConnection()==connection,"public framework bind installs the exact fake connection");
            set(service,"target",connection); set(service,"selection",7); set(service,"selectionStart",7);
            // No UI is created. A real idle ChordSurface satisfies the ordinary send policy gate.
            set(service,"chords",new ChordSurface(context,new ChordSurface.Handler() {
                public void onChord(String code) {}
                public void onKey(String text) {}
                public void onPreview(ChordGesture.Preview preview) {}
                public void onControl(ChordLayout.Action action) {}
                public String label(ChordLayout.Action action) { return ""; }
                public String description(ChordLayout.Action action) { return ""; }
            }));
            buffer=(BufferSession)get(service,"buffer"); plugins=(PluginSession)get(service,"pluginSession");
            buffer.beginTarget(true); buffer.setEnabled(true); buffer.appendCommittedBlock(SOURCE);
            expected().add(3);
            if(plugin) {
                set(service,"activePlugin","translate"); plugins.select("translate");
                PluginSession.Request request=plugins.start(buffer);
                check(request!=null && plugins.update(request,buffer,OUTPUT,true),"real model prepares a completed captured-source result");
            }
        }
        void bind(Connection target) { input.bindInput(new InputBinding(target,new Binder(),Process.myUid(),Process.myPid())); }
        void send(boolean all) throws Exception { invoke(service,"insertNow",new Class<?>[]{boolean.class},all); }
        int selection() throws Exception { return (Integer)get(service,"selection"); }
        int selectionStart() throws Exception { return (Integer)get(service,"selectionStart"); }
        @SuppressWarnings("unchecked") ArrayDeque<Integer> expected() throws Exception { return (ArrayDeque<Integer>)get(service,"expectedSelections"); }
        List<Integer> expectations() throws Exception { return new ArrayList<>(expected()); }
        @Override public void close() { input.unbindInput(); }
    }

    private interface BeforeReturn { void run() throws Exception; }
    private static final class Connection extends BaseInputConnection {
        boolean accept;
        int attempts;
        String lastAttempt="";
        final List<String> accepted=new ArrayList<>();
        BeforeReturn beforeReturn;
        Connection(Context context) { super(new View(context),true); }
        @Override public boolean commitText(CharSequence text,int newCursorPosition) {
            if(Looper.myLooper()!=Looper.getMainLooper()) throw new AssertionError("service commit left the main owner");
            attempts++; lastAttempt=text.toString();
            if(newCursorPosition!=1) throw new AssertionError("service commit changed cursor semantics");
            if(accept) accepted.add(lastAttempt);
            if(beforeReturn!=null) {
                BeforeReturn action=beforeReturn; beforeReturn=null;
                try { action.run(); }
                catch(Exception error) { throw new AssertionError("commit-time target transition failed",error); }
            }
            return accept;
        }
    }

    private static Object get(Object instance,String name) throws Exception {
        Field field=RimesInputMethodService.class.getDeclaredField(name); field.setAccessible(true); return field.get(instance);
    }
    private static void set(Object instance,String name,Object value) throws Exception {
        Field field=RimesInputMethodService.class.getDeclaredField(name); field.setAccessible(true); field.set(instance,value);
    }
    private static void invoke(Object instance,String name,Class<?>[] types,Object... values) throws Exception {
        Method method=RimesInputMethodService.class.getDeclaredMethod(name,types); method.setAccessible(true);
        try { method.invoke(instance,values); }
        catch(InvocationTargetException error) {
            Throwable cause=error.getCause();
            if(cause instanceof Error) throw (Error)cause;
            if(cause instanceof Exception) throw (Exception)cause;
            throw error;
        }
    }
}
