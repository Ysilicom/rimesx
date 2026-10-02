package org.scholay.rimes.testhost;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import java.util.concurrent.atomic.AtomicReference;

/** Drives real RIMES buttons through accessibility, then checks actual host text. No text-injection IME. */
public final class InputContractInstrumentation extends Instrumentation {
    private static final String IME="org.scholay.rimes.android.debug";
    private HostActivity host;
    private Bundle arguments;
    private int assertions;
    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments=args; start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            report("START accessibility");
            AccessibilityServiceInfo info=getUiAutomation().getServiceInfo();
            info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            getUiAutomation().setServiceInfo(info);
            report("START host");
            ActivityMonitor monitor=addMonitor(HostActivity.class.getName(),null,false);
            // Launch under the same shell identity as adb, avoiding OEM background-activity
            // restrictions after instrumentation restarts its target process. Never wait forever.
            try(android.os.ParcelFileDescriptor launch=getUiAutomation().executeShellCommand(
                    "am start -W -f 0x10008000 -n org.scholay.rimes.testhost/.HostActivity")) {
                host=(HostActivity)waitForMonitorWithTimeout(monitor,15000);
            }
            removeMonitor(monitor); check(host!=null,"validation host launch within 15 seconds");
            waitForIdleSync(); report("START contract");
            if("layout".equals(arguments.getString("mode"))) layoutContract();
            else if(!"soak".equals(arguments.getString("mode"))) contract();
            else soak(Long.parseLong(arguments.getString("seconds","1800")));
            result.putString("stream","PASS input contract; assertions="+assertions+"\n"); finish(-1,result);
        } catch(Throwable error) {
            try {
                android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();
                if(bitmap!=null) try(java.io.FileOutputStream out=getTargetContext().openFileOutput("failure.png",0)) {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);
                }
            } catch(Exception ignored) { /* Original assertion remains authoritative. */ }
            result.putString("stream","FAIL "+android.util.Log.getStackTraceString(error)); finish(0,result);
        }
    }
    private void check(boolean value,String label) { assertions++; if(!value) throw new AssertionError(label); }
    private String read(EditText field) {
        AtomicReference<String> value=new AtomicReference<>(); runOnMainSync(() -> value.set(field.getText().toString())); return value.get();
    }
    private void expect(EditText field,String wanted,String label) {
        long deadline=SystemClock.uptimeMillis()+4000;
        String actual;
        do { actual=read(field); if(wanted.equals(actual)) { assertions++; return; } SystemClock.sleep(25); } while(SystemClock.uptimeMillis()<deadline);
        throw new AssertionError(label+" expected=["+wanted+"] actual=["+actual+"]");
    }
    private AccessibilityNodeInfo search(AccessibilityNodeInfo node,String value,boolean description) {
        if(node==null) return null;
        CharSequence label=description?node.getContentDescription():node.getText();
        if(IME.contentEquals(node.getPackageName()==null?"":node.getPackageName()) && value.contentEquals(label==null?"":label)
                && node.isVisibleToUser() && node.isClickable()) return node;
        for(int i=0;i<node.getChildCount();i++) { AccessibilityNodeInfo found=search(node.getChild(i),value,description); if(found!=null) return found; }
        return null;
    }
    private AccessibilityNodeInfo find(String value,boolean description) {
        if(value.equals("↵")) { value="Enter"; description=true; }
        for(AccessibilityWindowInfo window:getUiAutomation().getWindows()) {
            AccessibilityNodeInfo node=search(window.getRoot(),value,description); if(node!=null) return node;
            if(!description) { node=search(window.getRoot(),value,true); if(node!=null) return node; }
        }
        return null;
    }
    private AccessibilityNodeInfo waitButton(String value) {
        long deadline=SystemClock.uptimeMillis()+5000;
        do { AccessibilityNodeInfo node=find(value,false); if(node!=null && node.isEnabled()) return node; SystemClock.sleep(30); } while(SystemClock.uptimeMillis()<deadline);
        throw new AssertionError("Missing enabled keyboard button: "+value);
    }
    private void tap(String value) {
        AccessibilityNodeInfo node=waitButton(value);
        check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK),"click "+value);
        SystemClock.sleep(120);
    }
    private void type(String text) { for(int i=0;i<text.length();i++) tap(text.substring(i,i+1)); }
    private void focus(EditText field) {
        runOnMainSync(() -> { field.setText(""); host.focus(field); getTargetContext().getSystemService(InputMethodManager.class).restartInput(field); });
        waitButton("q"); SystemClock.sleep(400);
    }
    private void pinyin() {
        if(find("英",false)!=null && find("英",false).isEnabled()) tap("英");
        if(find("自然码",false)!=null) tap("自然码");
        if(find("五笔",false)!=null) tap("五笔");
        waitButton("拼音");
    }
    private void report(String value) {
        Bundle b=new Bundle(); b.putString("stream",value+"\n"); sendStatus(0,b);
        android.util.Log.i("RIMES-TEST",value);
    }
    private void contract() throws Exception {
        focus(host.first); pinyin(); type("nihao");
        check(find("Buffer off",false)!=null && !find("Buffer off",false).isEnabled(),"Buffer toggle locked during composition");
        tap("Space"); expect(host.first,"你好","first candidate"); tap(","); tap("."); expect(host.first,"你好，。","punctuation");
        tap("⌫"); expect(host.first,"你好，","host delete");
        focus(host.first); type("nihaox"); tap("⌫"); tap("Space"); expect(host.first,"你好","composition delete");
        tap("Space"); expect(host.first,"你好 ","space without composition");
        for(int cycle=0;cycle<10;cycle++) {
            focus(host.first);
            for(char key:"nihao".toCharArray()) check(waitButton(String.valueOf(key)).performAction(AccessibilityNodeInfo.ACTION_CLICK),"rapid key");
            tap("Space"); expect(host.first,"你好","rapid ordered results");
        }
        focus(host.first); type("zhongguoren");
        String longChoice=candidate(0); check(longChoice.length()>=3,"long phrase candidate");
        android.graphics.Rect phraseBounds=new android.graphics.Rect(); waitButton(longChoice).getBoundsInScreen(phraseBounds);
        check(phraseBounds.width()>=longChoice.length()*14*host.getResources().getDisplayMetrics().scaledDensity,"long candidate is readable");
        tap(longChoice); expect(host.first,longChoice,"long phrase commit");
        focus(host.first); type("nihao"); tap("↵"); expect(host.first,"nihao","raw Return");
        focus(host.first); type("ni"); tap("拟"); expect(host.first,"拟","non-first candidate");
        focus(host.first); type("ni"); tap("›");
        // Candidate labels are read from the actual current page, never assumed.
        String choice=candidate(0); tap(choice); expect(host.first,choice,"second-page selection");
        focus(host.first); type("ni"); String pageOne=candidate(0); tap("›"); tap("‹");
        check(pageOne.equals(candidate(0)),"previous candidate page"); tap("Space"); expect(host.first,pageOne,"first page restored");
        focus(host.first); tap("拼音"); type("nihk"); tap("Space"); expect(host.first,"你好","natural-code");
        focus(host.first); tap("自然码"); type("wq"); tap("Space"); expect(host.first,"你","Wubi"); tap("五笔");
        focus(host.first); type("ni"); tap("拼音"); expect(host.first,"ni","schema switch settles raw");
        type("nihk"); tap("Space"); expect(host.first,"ni你好","new schema follows settled code"); tap("自然码"); tap("五笔");
        focus(host.first); type("ni"); tap("中"); type("abc"); expect(host.first,"niabc","mode switch settles raw"); tap("英");
        focus(host.first); tap("Buffer off"); type("nihao"); tap("Space"); expect(host.first,"","Buffer never changes host");
        tap("中"); type("ab"); tap("Space"); type("c"); tap("Insert"); expect(host.first,"你好","whole Chinese block");
        tap("Insert all"); expect(host.first,"你好ab c","remaining blocks once"); tap("↵"); expect(host.first,"你好ab c","empty Buffer Return");
        tap("英");
        focus(host.first); tap("Buffer off"); type("nihao"); tap("Space"); type("ni"); tap("拼音");
        expect(host.first,"","schema switch stays inside Buffer"); tap("Insert"); expect(host.first,"你好","schema switch preserves Chinese block");
        tap("Insert all"); expect(host.first,"你好ni","schema switch preserves raw block"); tap("自然码"); tap("五笔");
        focus(host.first); tap("Buffer off"); type("ni"); tap("↵"); expect(host.first,"","raw Return only stages"); tap("↵"); expect(host.first,"ni","second Return sends staged raw");
        focus(host.first); tap("Buffer off"); type("nihao"); tap("Space"); tap("Buffer on"); expect(host.first,"","Buffer off never sends");
        tap("Buffer off"); tap("⌫"); check(find("Insert",false)!=null && !find("Insert",false).isEnabled(),"delete whole block");
        focus(host.first); type("ni"); runOnMainSync(() -> host.focus(host.second)); SystemClock.sleep(500);
        // Android may finish the old EditText composition before revoking its InputConnection.
        // Its visible raw code can remain in that old editor; the IME must never replay it.
        check(read(host.first).isEmpty() || read(host.first).equals("ni"),"old editor has no extra commit");
        expect(host.second,"","new field does not receive old results");
        type("nihao"); tap("Space"); expect(host.second,"你好","new field works");
        focus(host.first); runOnMainSync(() -> { host.first.setText("abcd"); host.first.setSelection(1,3); }); SystemClock.sleep(200);
        type("nihao"); tap("Space"); expect(host.first,"a你好d","host selection replacement");
        focus(host.privateInput); type("nihao"); tap("Space"); expect(host.privateInput,"你好","private Chinese");
        check(find("Buffer off",false)!=null && !find("Buffer off",false).isEnabled(),"private Buffer denied");
        if(!"true".equals(arguments.getString("skipPassword"))) {
        focus(host.password); type("nihao"); expect(host.password,"nihao","password direct input");
        check(find("›",false)==null,"password has no candidates");
        check(find("Buffer off",false)!=null && !find("Buffer off",false).isEnabled(),"password Buffer denied");
        } else report("PENDING password keyboard automation");
        focus(host.first); pinyin(); type("ni");
        runOnMainSync(() -> host.getSystemService(InputMethodManager.class).hideSoftInputFromWindow(host.first.getWindowToken(),0));
        SystemClock.sleep(400); expect(host.first,"","hide clears composition");
        focus(host.first); type("nihao"); tap("Space"); expect(host.first,"你好","reopen");
        runOnMainSync(() -> host.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)); SystemClock.sleep(700);
        check(host.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE,"actual landscape configuration");
        focus(host.first); pinyin(); tap("Buffer off"); type("nihao"); tap("Space");
        expect(host.first,"","landscape Buffer isolation"); tap("Insert all"); expect(host.first,"你好","landscape input");
        android.graphics.Rect bounds=new android.graphics.Rect(); waitButton("↵").getBoundsInScreen(bounds);
        check(bounds.bottom<=host.getResources().getDisplayMetrics().heightPixels,"landscape Return visible");
        runOnMainSync(() -> host.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)); SystemClock.sleep(500);
        check(host.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT,"actual portrait configuration");
        webContract(); report("PASS native host, candidates, schemes, Buffer, private fields, selection, hide, orientation and WebView");
    }
    private String candidate(int index) {
        String prefix="候选"+(index+1)+" ";
        for(AccessibilityWindowInfo window:getUiAutomation().getWindows()) {
            String result=candidateLabel(window.getRoot(),prefix); if(result!=null) return result;
        }
        throw new AssertionError("candidate missing");
    }
    private String candidateLabel(AccessibilityNodeInfo node,String prefix) {
        if(node==null) return null;
        String desc=String.valueOf(node.getContentDescription());
        if(desc.startsWith(prefix)) return String.valueOf(node.getText());
        for(int i=0;i<node.getChildCount();i++) { String found=candidateLabel(node.getChild(i),prefix); if(found!=null) return found; }
        return null;
    }
    private String js(String code) throws Exception {
        java.util.concurrent.ArrayBlockingQueue<String> result=new java.util.concurrent.ArrayBlockingQueue<>(1);
        runOnMainSync(() -> host.web.evaluateJavascript(code,result::add));
        String value=result.poll(5,java.util.concurrent.TimeUnit.SECONDS); check(value!=null,"WebView response"); return value;
    }
    private void webContract() throws Exception {
        runOnMainSync(() -> { host.first.setVisibility(android.view.View.GONE); host.second.setVisibility(android.view.View.GONE); host.password.setVisibility(android.view.View.GONE); host.privateInput.setVisibility(android.view.View.GONE); host.web.requestFocus(); });
        SystemClock.sleep(300); js("document.getElementById('first').focus()");
        runOnMainSync(() -> host.getSystemService(InputMethodManager.class).showSoftInput(host.web,InputMethodManager.SHOW_IMPLICIT));
        waitButton("q"); pinyin(); type("nihao"); tap("Space"); check("\"你好\"".equals(js("document.getElementById('first').value")),"WebView Chinese commit");
        tap("Buffer off"); type("nihao"); tap("Space"); check("\"你好\"".equals(js("document.getElementById('first').value")),"WebView Buffer isolated");
        tap("Insert all"); check("\"你好你好\"".equals(js("document.getElementById('first').value")),"WebView exact insertion");
        js("document.getElementById('second').focus()"); SystemClock.sleep(200); type("nihao"); tap("Space");
        check("\"你好\"".equals(js("document.getElementById('second').value")),"WebView target switch");
    }
    private void focusAny(EditText field) {
        runOnMainSync(() -> { field.setText(""); host.focus(field); getTargetContext().getSystemService(InputMethodManager.class).restartInput(field); });
        waitButton("键位布局"); SystemClock.sleep(400);
    }
    private void layout(String value) { tap("键位布局"); tap("布局 "+value+" 键"); tap("键位布局"); }
    private void nine(String code) {
        String[] labels={"ABC","DEF","GHI","JKL","MNO","PQRS","TUV","WXYZ"};
        for(char c:code.toCharArray()) tap("九键 "+c+" "+labels[c-'2']);
    }
    private android.graphics.Rect bounds(String label) {
        android.graphics.Rect result=new android.graphics.Rect(); waitButton(label).getBoundsInScreen(result); return result;
    }
    private void touch(String label) {
        android.graphics.Rect rect=bounds(label); long time=SystemClock.uptimeMillis();
        android.view.MotionEvent down=android.view.MotionEvent.obtain(time,time,android.view.MotionEvent.ACTION_DOWN,rect.exactCenterX(),rect.exactCenterY(),0);
        android.view.MotionEvent up=android.view.MotionEvent.obtain(time,time+60,android.view.MotionEvent.ACTION_UP,rect.exactCenterX(),rect.exactCenterY(),0);
        down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN); up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        try {
            check(getUiAutomation().injectInputEvent(down,true),"touch down "+label);
            check(getUiAutomation().injectInputEvent(up,true),"touch up "+label);
        } finally { down.recycle(); up.recycle(); }
        SystemClock.sleep(120);
    }
    private void screenshot(String name) throws Exception {
        android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();
        check(bitmap!=null,"layout screenshot available");
        try(java.io.FileOutputStream out=getTargetContext().openFileOutput("layout-"+name+".png",0)) {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"layout screenshot saved");
        }
    }
    private void letterGeometry() {
        android.graphics.Rect q=bounds("q"),a=bounds("a"),z=bounds("z"),space=bounds("Space"),shift=bounds("⇧"),del=bounds("⌫");
        for(char c:"qwertyuiopasdfghjklzxcvbnm".toCharArray()) {
            android.graphics.Rect key=bounds(String.valueOf(c));
            check(Math.abs(key.width()-q.width())<=1,"equal width: "+c);
            check(Math.abs(key.height()-q.height())<=1,"equal height: "+c);
            check(key.left>=0 && key.right<=host.getResources().getDisplayMetrics().widthPixels,"key within display: "+c);
        }
        check(a.left>q.left && z.left>a.left,"staggered equal-width rows");
        check(space.width()>q.width()*4,"wide Space");
        check(Math.abs(shift.top-z.top)<=1 && Math.abs(del.top-z.top)<=1,"Shift and Delete flank third row");
        check(bounds("Enter").bottom<=host.getResources().getDisplayMetrics().heightPixels,"Return above system navigation");
    }
    private void layoutContract() throws Exception {
        focusAny(host.first); layout("26"); pinyin();
        letterGeometry(); screenshot("qwerty");
        for(char c:"nihao".toCharArray()) touch(String.valueOf(c)); touch("Space");
        expect(host.first,"你好","real touch coordinates commit once"); focus(host.first);
        tap("键位布局"); screenshot("appearance"); tap("配色 犀牛"); tap("键位布局"); screenshot("rhino");
        type("nihao"); screenshot("candidates"); tap("Space"); expect(host.first,"你好","new QWERTY candidate");
        focus(host.first); tap("Buffer off"); type("nihao"); tap("Space");
        layout("9"); expect(host.first,"","layout switch preserves isolated Buffer");
        screenshot("nine-buffer"); nine("64426"); tap("Space"); expect(host.first,"","nine-key confirmed text stays in Buffer");
        tap("Insert all"); expect(host.first,"你好你好","both layouts retain complete blocks");
        focusAny(host.first); nine("64"); tap("选拼音"); screenshot("spelling"); tap("拼音 ni"); nine("426");
        tap("选拼音"); tap("拼音 hao"); screenshot("nine-composition"); tap("Space"); expect(host.first,"你好","nine-key spelling constraints");
        focusAny(host.first); nine("64"); tap("选拼音"); tap("拼音 ni"); tap("⌫"); nine("426"); tap("Space");
        expect(host.first,"你好","nine-key delete unpins syllable");
        focusAny(host.first); nine("64"); layout("26"); expect(host.first,"64","layout switch settles unconfirmed raw code");
        type("hao"); tap("Space"); expect(host.first,"64好","26-key restored engine");
        focus(host.first); tap("123"); type("123"); screenshot("numbers"); tap("符号页"); tap("#"); screenshot("symbols");
        tap("ABC"); expect(host.first,"123#","numeric and symbol pages");
        tap("表情"); screenshot("emoji"); tap("😀"); expect(host.first,"123#😀","non-BMP emoji"); tap("⌫"); expect(host.first,"123#","emoji code-point delete"); tap("表情");
        tap("键位布局"); tap("配色 原生"); tap("键位布局");
        runOnMainSync(() -> host.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)); SystemClock.sleep(800);
        focus(host.first); pinyin(); letterGeometry(); screenshot("landscape");
        type("nihao"); tap("Space"); expect(host.first,"你好","landscape QWERTY");
        layout("9"); focusAny(host.first); nine("64426"); tap("Space"); expect(host.first,"你好","landscape nine-key"); screenshot("nine-landscape");
        layout("26");
        runOnMainSync(() -> host.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)); SystemClock.sleep(600);
        focus(host.first); letterGeometry(); screenshot("portrait-restored");
        report("PASS keyboard layouts, equal touch geometry, theme, spelling, emoji, Buffer and orientation");
    }
    private void soak(long seconds) throws Exception {
        focus(host.first); pinyin();
        long start=SystemClock.elapsedRealtime(), next=start+60000; int cycles=0;
        while(SystemClock.elapsedRealtime()-start<seconds*1000) {
            EditText field=(cycles%2==0)?host.first:host.second;
            focus(field); pinyin();
            if(cycles%2==0) { tap("Buffer off"); type("nihao"); tap("Space"); expect(field,"","soak Buffer isolation"); tap("Insert all"); }
            else { type("nihao"); tap("Space"); }
            expect(field,"你好","soak exactly one commit"); tap(",");
            if(cycles%2==0) tap("Insert all");
            expect(field,"你好，","soak punctuation");
            cycles++;
            long now=SystemClock.elapsedRealtime();
            if(now>=next) { report("SOAK elapsed="+((now-start)/1000)+"s cycles="+cycles+" assertions="+assertions); next=now+60000; }
        }
        report("PASS SOAK duration="+((SystemClock.elapsedRealtime()-start)/1000)+"s cycles="+cycles+" no duplicate or cross-field commit");
    }
}
