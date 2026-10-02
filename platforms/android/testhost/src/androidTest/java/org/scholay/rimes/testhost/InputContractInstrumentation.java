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
    private boolean allowStaleRejection;
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
            if("chord".equals(arguments.getString("mode"))) chordContract();
            else if("touch".equals(arguments.getString("mode"))) nativeTouchContract();
            else if("benchmark".equals(arguments.getString("mode"))) benchmark();
            else if("layout".equals(arguments.getString("mode"))) layoutContract();
            else if("plugins".equals(arguments.getString("mode"))) pluginsContract();
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
    private void expectStable(EditText field,String wanted,String label) {
        long until=SystemClock.uptimeMillis()+600;
        do { check(wanted.equals(read(field)),label); SystemClock.sleep(30); } while(SystemClock.uptimeMillis()<until);
    }
    private void waitKeyboardHidden() {
        long deadline=SystemClock.uptimeMillis()+5000;
        do {
            boolean present=false;
            for(AccessibilityWindowInfo window:getUiAutomation().getWindows())
                if(window.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD) { present=true; break; }
            if(!present) { check(true,"keyboard window hidden after animation"); return; }
            SystemClock.sleep(30);
        } while(SystemClock.uptimeMillis()<deadline);
        throw new AssertionError("Keyboard window did not hide within five seconds");
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
    private AccessibilityNodeInfo searchLabel(AccessibilityNodeInfo node,String value,boolean description,boolean visible) {
        if(node==null) return null;
        CharSequence label=description?node.getContentDescription():node.getText();
        if(IME.contentEquals(node.getPackageName()==null?"":node.getPackageName())
                && value.contentEquals(label==null?"":label) && (!visible || node.isVisibleToUser())) return node;
        for(int i=0;i<node.getChildCount();i++) {
            AccessibilityNodeInfo found=searchLabel(node.getChild(i),value,description,visible); if(found!=null) return found;
        }
        return null;
    }
    /** Output and source rows intentionally are not clickable. */
    private AccessibilityNodeInfo findLabel(String value,boolean description) {
        for(AccessibilityWindowInfo window:getUiAutomation().getWindows()) {
            AccessibilityNodeInfo found=searchLabel(window.getRoot(),value,description,true); if(found!=null) return found;
        }
        return null;
    }
    private AccessibilityNodeInfo waitLabel(String value,boolean description) {
        long deadline=SystemClock.uptimeMillis()+5000;
        do { AccessibilityNodeInfo node=findLabel(value,description); if(node!=null) return node; SystemClock.sleep(30); } while(SystemClock.uptimeMillis()<deadline);
        throw new AssertionError("Missing keyboard label: "+value);
    }
    private AccessibilityNodeInfo searchPluginPanelButton(AccessibilityNodeInfo node,String value) {
        if(node==null) return null;
        if("android.widget.ScrollView".contentEquals(node.getClassName()==null?"":node.getClassName())
                && node.isVisibleToUser() && searchLabel(node,"Buffer 插件设置",false,true)!=null)
            return search(node,value,true);
        for(int i=0;i<node.getChildCount();i++) {
            AccessibilityNodeInfo found=searchPluginPanelButton(node.getChild(i),value); if(found!=null) return found;
        }
        return null;
    }
    private void clickPluginPanel(String value) {
        AccessibilityNodeInfo button=null; long deadline=SystemClock.uptimeMillis()+5000;
        do {
            for(AccessibilityWindowInfo window:getUiAutomation().getWindows()) {
                button=searchPluginPanelButton(window.getRoot(),value); if(button!=null) break;
            }
            if(button!=null && button.isEnabled()) break;
            SystemClock.sleep(30);
        } while(SystemClock.uptimeMillis()<deadline);
        check(button!=null && button.isEnabled() && button.performAction(AccessibilityNodeInfo.ACTION_CLICK),"click panel "+value);
        SystemClock.sleep(120);
    }
    private AccessibilityNodeInfo waitButton(String value) {
        long deadline=SystemClock.uptimeMillis()+5000;
        do { AccessibilityNodeInfo node=find(value,false); if(node!=null && node.isEnabled()) return node; SystemClock.sleep(30); } while(SystemClock.uptimeMillis()<deadline);
        throw new AssertionError("Missing enabled keyboard button: "+value);
    }
    private boolean scrollChooser(AccessibilityNodeInfo node) {
        if(node==null) return false;
        if(IME.contentEquals(node.getPackageName()==null?"":node.getPackageName())
                && "android.widget.ScrollView".contentEquals(node.getClassName()==null?"":node.getClassName())
                && node.isVisibleToUser() && node.isScrollable())
            return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        for(int i=0;i<node.getChildCount();i++) if(scrollChooser(node.getChild(i))) return true;
        return false;
    }
    private boolean scrollPluginBar(AccessibilityNodeInfo node,String label,int action) {
        if(node==null) return false;
        if(IME.contentEquals(node.getPackageName()==null?"":node.getPackageName())
                && "android.widget.HorizontalScrollView".contentEquals(node.getClassName()==null?"":node.getClassName())
                && node.isVisibleToUser() && node.isScrollable() && searchLabel(node,label,true,false)!=null)
            return node.performAction(action);
        for(int i=0;i<node.getChildCount();i++) if(scrollPluginBar(node.getChild(i),label,action)) return true;
        return false;
    }
    private void tap(String value) {
        if((value.equals("拼音") || value.equals("自然码") || value.equals("五笔")) && modern()) {
            schema(value.equals("拼音")?"自然码":value.equals("自然码")?"五笔":"拼音"); return;
        }
        if(value.equals("Insert all") && modern() && find(value,false)==null) {
            AccessibilityNodeInfo send=find("Insert next",false);
            if(send==null) send=waitButton("Insert");
            check(send.isEnabled() && send.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK),"long click Insert next sends all");
            SystemClock.sleep(120); return;
        }
        // Idle candidate space is now the plugin bar. QWERTY punctuation lives on 123;
        // nine-key has its explicit selector, while the chord comma remains a direct key.
        if((value.equals(",") || value.equals(".")) && modern() && find(value,false)==null) {
            if(find("q",false)!=null) {
                click("123"); click(value); click("ABC"); return;
            }
            if(find("中文标点",false)!=null) { click("中文标点"); click(value); return; }
        }
        if(value.startsWith("Buffer 插件：")) {
            for(int action:new int[]{AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD}) {
                for(int attempt=0;find(value,false)==null && attempt<8;attempt++) {
                    boolean moved=false;
                    for(AccessibilityWindowInfo window:getUiAutomation().getWindows())
                        if(scrollPluginBar(window.getRoot(),value,action)) { moved=true; break; }
                    if(!moved) break;
                    SystemClock.sleep(120);
                }
                if(find(value,false)!=null) break;
            }
        }
        // The appearance panel scrolls independently; large fonts can place palettes below its viewport.
        if(value.startsWith("配色 ")) {
            for(int attempt=0;find(value,false)==null && attempt<8;attempt++) {
                boolean moved=false;
                for(AccessibilityWindowInfo window:getUiAutomation().getWindows())
                    if(scrollChooser(window.getRoot())) { moved=true; break; }
                if(!moved) break;
                SystemClock.sleep(120);
            }
        }
        click(value);
    }
    /** Direct click avoids re-entering schema, symbol-page or plugin routing in tap(). */
    private void click(String value) {
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
        if(modern()) { schema("拼音"); return; }
        if(find("自然码",false)!=null) tap("自然码");
        if(find("五笔",false)!=null) tap("五笔");
        waitButton("拼音");
    }
    private boolean modern() { AccessibilityNodeInfo node=find("键位布局",true); return node!=null && ("⚙".contentEquals(node.getText()) || "✓".contentEquals(node.getText())); }
    private void schema(String name) { tap("键位布局"); tap("中文方案 "+name); tap("键位布局"); }
    private void report(String value) {
        Bundle b=new Bundle(); b.putString("stream",value+"\n"); sendStatus(0,b);
        android.util.Log.i("RIMES-TEST",value);
    }
    /** End-to-end ACTION_CLICK acknowledgement to actual EditText mutation, without tap sleeps. */
    private void benchmark() throws Exception {
        focusAny(host.first); layout("26"); pinyin();
        java.util.ArrayList<Long> first=new java.util.ArrayList<>(), commit=new java.util.ArrayList<>();
        java.util.concurrent.atomic.AtomicLong changedAt=new java.util.concurrent.atomic.AtomicLong();
        runOnMainSync(() -> host.first.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { changedAt.set(SystemClock.elapsedRealtimeNanos()); }
            public void afterTextChanged(android.text.Editable s) {}
        }));
        int count=Integer.parseInt(arguments.getString("samples","60"));
        for(int i=0;i<count+5;i++) {
            focus(host.first); pinyin();
            AccessibilityNodeInfo n=waitButton("n"); changedAt.set(0);
            long start=SystemClock.elapsedRealtimeNanos();
            check(n.performAction(AccessibilityNodeInfo.ACTION_CLICK),"benchmark first key accepted");
            expect(host.first,"n","benchmark composing mutation");
            long firstTime=changedAt.get()-start;
            for(char c:"ihao".toCharArray()) {
                check(waitButton(String.valueOf(c)).performAction(AccessibilityNodeInfo.ACTION_CLICK),"benchmark next key accepted");
            }
            waitButton("你好"); AccessibilityNodeInfo space=waitButton("Space"); changedAt.set(0);
            start=SystemClock.elapsedRealtimeNanos();
            check(space.performAction(AccessibilityNodeInfo.ACTION_CLICK),"benchmark candidate accepted");
            expect(host.first,"你好","benchmark actual Chinese commit");
            long commitTime=changedAt.get()-start;
            check(firstTime>0 && commitTime>0,"benchmark monotonic timestamps");
            if(i>=5) { first.add(firstTime); commit.add(commitTime); }
        }
        org.json.JSONObject data=new org.json.JSONObject();
        data.put("scope","accessibility ACTION_CLICK request to actual host TextWatcher; includes IPC/scheduling; no touch hardware or frame presentation");
        data.put("warmup",5); data.put("first_key_ms",stats(first)); data.put("candidate_commit_ms",stats(commit));
        data.put("ime",String.valueOf(android.provider.Settings.Secure.getString(host.getContentResolver(),"default_input_method")));
        try(java.io.FileOutputStream out=getTargetContext().openFileOutput("benchmark.json",0)) {
            out.write(data.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        report("PASS BENCHMARK "+data);
    }
    private org.json.JSONObject stats(java.util.List<Long> samples) throws Exception {
        java.util.Collections.sort(samples); double sum=0; for(long value:samples) sum+=value;
        return new org.json.JSONObject().put("samples",samples.size()).put("mean",sum/samples.size()/1e6)
                .put("p50",samples.get((samples.size()-1)/2)/1e6)
                .put("p95",samples.get((int)Math.ceil(samples.size()*.95)-1)/1e6)
                .put("max",samples.get(samples.size()-1)/1e6);
    }
    private void pluginSource(String wanted,String label) {
        check(waitLabel("Buffer "+wanted,true)!=null,label+" retains source in keyboard");
        expect(host.first,"",label+" does not change host");
    }
    private void pluginPanel(String name) {
        waitLabel("Buffer 插件设置",false);
        check(waitLabel(name+"服务尚未接通\n原文保留在本机，当前不会发送到服务或输入框。",false)!=null,
                name+" settings honestly report unavailable execution");
        expect(host.first,"",name+" settings never insert raw source");
    }
    /** The actual IME owns entries, selection and drafts; no test-side plugin execution. */
    private void pluginsContract() throws Exception {
        final String[] names={"翻译","快问","润色","作诗","画画"};
        focusAny(host.first); layout("26"); pinyin();
        tap("Buffer off"); type("nihao"); tap("Space"); pluginSource("你好","plain Buffer");
        for(String name:names) {
            String shortcut="Buffer 插件："+name;
            tap(shortcut);
            check(waitButton("Buffer on").isEnabled(),name+" enables Buffer");
            check(waitButton(shortcut).isSelected(),name+" shortcut selected");
            AccessibilityNodeInfo output=waitLabel("插件输出："+name,true);
            check((name+"服务尚未接通").contentEquals(output.getText()),name+" output is honest placeholder");
            pluginSource("你好",name+" activation");
            android.graphics.Rect outputBounds=new android.graphics.Rect(),sourceBounds=new android.graphics.Rect();
            output.getBoundsInScreen(outputBounds); waitLabel("Buffer 你好",true).getBoundsInScreen(sourceBounds);
            check(outputBounds.bottom<=sourceBounds.top,name+" output above original Buffer row");
            AccessibilityNodeInfo send=find("Insert",false);
            if(send==null) send=find("Insert next",false);
            check(send!=null && !send.isEnabled(),name+" cannot send raw source through Insert");
            tap("执行"+name); pluginPanel(name);
            if(name.equals("快问")) {
                clickPluginPanel(shortcut); pluginPanel(name);
            }
            tap("返回键盘"); pluginSource("你好",name+" execution entry");
            check(waitButton(shortcut).isSelected(),name+" settings selection remains active");
            tap("Enter"); pluginPanel(name); tap("返回键盘"); pluginSource("你好",name+" Return entry");
            tap(shortcut);
            check(!waitButton(shortcut).isSelected(),name+" same shortcut returns ordinary Buffer");
            check(findLabel("插件输出："+name,true)==null,name+" ordinary Buffer removes plugin output");
            check(find("执行"+name,false)==null,name+" ordinary Buffer removes execution control");
            pluginSource("你好",name+" deactivation");
        }
        tap("Buffer 插件：翻译"); type("ni");
        for(String name:names) check(find("Buffer 插件："+name,false)==null,"composition hides shortcut "+name);
        check(waitLabel("Buffer 你好ni",true)!=null,"plugin composition remains in source Buffer");
        expect(host.first,"","plugin composition never reaches host");
        String selected=candidate(0); tap("Space"); String source="你好"+selected;
        pluginSource(source,"plugin candidate confirmation");
        tap("Buffer 插件：润色");
        check(waitButton("Buffer 插件：润色").isSelected(),"different plugin becomes selected");
        check(findLabel("插件输出：翻译",true)==null,"switch removes previous plugin output");
        pluginSource(source,"switching plugins");
        tap("Buffer 插件设置"); waitLabel("Buffer 插件设置",false); tap("普通 Buffer");
        check(!waitButton("Buffer 插件：润色").isSelected(),"settings returns ordinary Buffer");
        pluginSource(source,"ordinary Buffer settings action");
        tap("Buffer 插件：翻译"); tap("Buffer on");
        check(!waitButton("Buffer 插件：翻译").isSelected(),"Buffer off clears plugin selection");
        check(findLabel("插件输出：翻译",true)==null,"Buffer off hides plugin output");
        expect(host.first,"","Buffer off does not send source");
        tap("Buffer off"); pluginSource(source,"Buffer reopened");
        check(!waitButton("Buffer 插件：翻译").isSelected(),"reopened Buffer is ordinary");
        tap("Buffer 插件：翻译"); focus(host.second);
        check(waitButton("Buffer off").isEnabled(),"new target starts with ordinary Buffer off");
        check(!waitButton("Buffer 插件：翻译").isSelected(),"target retirement clears plugin selection");
        check(findLabel("插件输出：翻译",true)==null && findLabel("Buffer "+source,true)==null,
                "target retirement removes plugin output and source projection");
        expect(host.first,"","retired host receives no unexecuted source"); expect(host.second,"","new host receives no unexecuted source");
        type("nihao"); tap("Space"); expect(host.second,"你好","new target receives only its own confirmed text");
        focus(host.privateInput);
        for(String name:names) {
            AccessibilityNodeInfo shortcut=find("Buffer 插件："+name,false);
            check(shortcut==null || !shortcut.isEnabled(),"private field denies shortcut "+name);
        }
        check(!waitLabel("Buffer off",true).isEnabled(),"private field denies Buffer");
        check(findLabel("插件输出：翻译",true)==null,"private field has no retained plugin output");
        expect(host.privateInput,"","private field receives no plugin source");
        focus(host.first); layout("9"); pinyin(); nine("64426"); tap("Space");
        expect(host.first,"你好","nine-key independent input"); tap(",");
        expect(host.first,"你好，","nine-key explicit punctuation selector remains available");
        layout("26"); focus(host.first);
        report("PASS five plugin entries, Buffer source preservation, output row placement, unavailable execution, composition, privacy and target retirement");
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
        check(waitButton("Enter").isSelected(),"confirmed Buffer Return has active cap");
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
    private void inject(long downTime,int action,String... labels) {
        android.graphics.Rect[] rectangles=new android.graphics.Rect[labels.length];
        for(int i=0;i<labels.length;i++) rectangles[i]=bounds(labels[i]);
        inject(downTime,action,rectangles);
    }
    private void inject(long downTime,int action,android.graphics.Rect... rectangles) {
        android.view.MotionEvent.PointerProperties[] properties=new android.view.MotionEvent.PointerProperties[rectangles.length];
        android.view.MotionEvent.PointerCoords[] coordinates=new android.view.MotionEvent.PointerCoords[rectangles.length];
        for(int i=0;i<rectangles.length;i++) {
            properties[i]=new android.view.MotionEvent.PointerProperties(); properties[i].id=i; properties[i].toolType=android.view.MotionEvent.TOOL_TYPE_FINGER;
            coordinates[i]=new android.view.MotionEvent.PointerCoords(); android.graphics.Rect r=rectangles[i];
            coordinates[i].x=r.exactCenterX(); coordinates[i].y=r.exactCenterY(); coordinates[i].pressure=1; coordinates[i].size=1;
        }
        android.view.MotionEvent event=android.view.MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),action,rectangles.length,properties,coordinates,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);
        try { boolean accepted=getUiAutomation().injectInputEvent(event,true);
            if(!accepted && allowStaleRejection) report("InputDispatcher rejected retired stream event "+action);
            check(accepted || allowStaleRejection,"real multi-touch event "+action); } finally { event.recycle(); }
        SystemClock.sleep(40);
    }
    private void finalRightUp(long downTime,String label) {
        android.graphics.Rect r=bounds(label);
        android.view.MotionEvent.PointerProperties property=new android.view.MotionEvent.PointerProperties(); property.id=1; property.toolType=android.view.MotionEvent.TOOL_TYPE_FINGER;
        android.view.MotionEvent.PointerCoords coord=new android.view.MotionEvent.PointerCoords(); coord.x=r.exactCenterX(); coord.y=r.exactCenterY(); coord.pressure=1; coord.size=1;
        android.view.MotionEvent event=android.view.MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),android.view.MotionEvent.ACTION_UP,1,new android.view.MotionEvent.PointerProperties[]{property},new android.view.MotionEvent.PointerCoords[]{coord},0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);
        try { check(getUiAutomation().injectInputEvent(event,true),"last right finger lift"); } finally { event.recycle(); } SystemClock.sleep(120);
    }
    private void chord(String leftStart,String leftEnd,String right) {
        long down=SystemClock.uptimeMillis();
        inject(down,android.view.MotionEvent.ACTION_DOWN,leftStart);
        inject(down,android.view.MotionEvent.ACTION_POINTER_DOWN|(1<<android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT),leftStart,right);
        inject(down,android.view.MotionEvent.ACTION_MOVE,leftEnd,right);
        inject(down,android.view.MotionEvent.ACTION_POINTER_UP,leftEnd,right);
        finalRightUp(down,right);
    }
    private void chooseChord(boolean split) { tap("键位布局"); tap(split?"布局 分体并击":"布局 正交并击"); tap("键位布局"); waitButton("D"); }
    private void nativeTouchContract() throws Exception {
        focusAny(host.first); layout("26"); pinyin();
        runOnMainSync(() -> host.traceConnections=true);
        long down=SystemClock.uptimeMillis(); inject(down,android.view.MotionEvent.ACTION_DOWN,"n");
        runOnMainSync(() -> host.focus(host.second)); SystemClock.sleep(400);
        allowStaleRejection=true; inject(down,android.view.MotionEvent.ACTION_UP,"n"); allowStaleRejection=false;
        expectStable(host.second,"","held ordinary key cannot type into a new field");
        type("nihao"); tap("Space"); expect(host.second,"你好","new ordinary clicks remain valid");
        focusAny(host.first); runOnMainSync(() -> host.first.setText("abcd")); SystemClock.sleep(200);
        down=SystemClock.uptimeMillis(); inject(down,android.view.MotionEvent.ACTION_DOWN,"n");
        runOnMainSync(() -> host.first.setSelection(1,3)); SystemClock.sleep(300);
        allowStaleRejection=true; inject(down,android.view.MotionEvent.ACTION_UP,"n"); allowStaleRejection=false;
        expectStable(host.first,"abcd","selection retirement rejects old ordinary lift");
        android.graphics.Rect heldKey=bounds("n");
        down=SystemClock.uptimeMillis(); inject(down,android.view.MotionEvent.ACTION_DOWN,heldKey);
        expect(host.first,"abcd","ordinary DOWN before hiding does not type");
        runOnMainSync(() -> host.getSystemService(InputMethodManager.class).hideSoftInputFromWindow(host.first.getWindowToken(),0)); waitKeyboardHidden();
        expect(host.first,"abcd","hiding a held key preserves selected host text");
        allowStaleRejection=true; inject(down,android.view.MotionEvent.ACTION_UP,heldKey); allowStaleRejection=false;
        expectStable(host.first,"abcd","hidden keyboard rejects old ordinary lift");
        focusAny(host.first); type("nihao"); tap("Space"); expect(host.first,"你好","fresh target after hiding remains usable");
        report("PASS native held keys, target/selection/hide retirement and fresh accessibility clicks");
    }
    private void chordContract() throws Exception {
        focusAny(host.first); layout("26"); pinyin(); chooseChord(false); screenshot("chord");
        long down=SystemClock.uptimeMillis();
        inject(down,android.view.MotionEvent.ACTION_DOWN,"D");
        inject(down,android.view.MotionEvent.ACTION_POINTER_DOWN|(1<<android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT),"D","I");
        inject(down,android.view.MotionEvent.ACTION_MOVE,"V","I"); screenshot("chord-held");
        expect(host.first,"","held chord does not preedit or commit");
        inject(down,android.view.MotionEvent.ACTION_POINTER_UP,"V","I"); expect(host.first,"","one hand released does not commit");
        finalRightUp(down,"I"); expect(host.first,"ni","both lifted resolve to Natural Code ni");
        check(!waitButton("Enter").isSelected(),"composition Return has neutral cap");
        chord("X","C","K"); waitButton("你好"); tap("Space"); expect(host.first,"你好","real two-thumb ni+hao commits once");
        focusAny(host.first); tap("Buffer off"); chord("D","V","I"); chord("X","C","K"); tap("Space"); expect(host.first,"","confirmed chord word stays in Buffer");
        check(waitButton("Enter").isSelected(),"confirmed chord Buffer Return has active cap");
        screenshot("chord-buffer"); tap("Insert"); expect(host.first,"你好","chord Buffer exact delivery");
        focusAny(host.first); down=SystemClock.uptimeMillis(); inject(down,android.view.MotionEvent.ACTION_DOWN,"D");
        inject(down,android.view.MotionEvent.ACTION_POINTER_DOWN|(1<<android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT),"D","V");
        inject(down,android.view.MotionEvent.ACTION_POINTER_UP,"D","V"); finalRightUp(down,"V");
        expect(host.first,"","two fingers on same hand cancel all");
        down=SystemClock.uptimeMillis(); inject(down,android.view.MotionEvent.ACTION_DOWN,"D"); inject(down,android.view.MotionEvent.ACTION_CANCEL,"D");
        allowStaleRejection=true; inject(down,android.view.MotionEvent.ACTION_UP,"V"); allowStaleRejection=false; expect(host.first,"","cancelled late lift cannot type");
        down=SystemClock.uptimeMillis(); inject(down,android.view.MotionEvent.ACTION_DOWN,"D");
        runOnMainSync(() -> host.focus(host.second)); SystemClock.sleep(400); allowStaleRejection=true; inject(down,android.view.MotionEvent.ACTION_UP,"V"); allowStaleRejection=false;
        expect(host.second,"","old touch never reaches new target");
        chooseChord(true); chord("D","V","I"); chord("X","C","K"); tap("Space"); expect(host.second,"你好","split chord works"); screenshot("chord-split");
        focusAny(host.privateInput); chord("D","V","I"); chord("X","C","K"); tap("Space"); expect(host.privateInput,"你好","private chord scheme works");
        check(!find("Buffer off",false).isEnabled(),"private chord Buffer disabled");
        runOnMainSync(() -> host.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)); SystemClock.sleep(700);
        focusAny(host.first); chord("D","V","I"); chord("X","C","K"); tap("Space"); expect(host.first,"你好","landscape chord"); screenshot("chord-landscape");
        runOnMainSync(() -> host.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)); SystemClock.sleep(600);
        focusAny(host.first); layout("26"); pinyin(); type("nihao"); tap("Space"); expect(host.first,"你好","ordinary layout restored");
        report("PASS chord real multi-touch, split/orthogonal, all-fingers release, cancellation, Buffer, privacy, target revocation and rotation");
    }
    private void soak(long seconds) throws Exception {
        focusAny(host.first); layout("26"); pinyin();
        boolean mixed=Boolean.parseBoolean(arguments.getString("chords","false"));
        long start=SystemClock.elapsedRealtime(), next=start+60000; int cycles=0;
        while(SystemClock.elapsedRealtime()-start<seconds*1000) {
            EditText field=(cycles%2==0)?host.first:host.second;
            focusAny(field);
            boolean useChord=mixed && cycles%3==2;
            if(useChord) chooseChord(cycles%2==0); else { layout("26"); pinyin(); }
            if(cycles%2==0) tap("Buffer off");
            if(useChord) { chord("D","V","I"); chord("X","C","K"); } else type("nihao");
            tap("Space");
            if(cycles%2==0) { expect(field,"","soak Buffer isolation"); tap("Insert all"); }
            expect(field,"你好","soak exactly one commit"); tap(",");
            if(cycles%2==0) tap("Insert all");
            expect(field,"你好，","soak punctuation");
            cycles++;
            long now=SystemClock.elapsedRealtime();
            if(now>=next) { report("SOAK elapsed="+((now-start)/1000)+"s cycles="+cycles+" assertions="+assertions); next=now+60000; }
        }
        report("PASS SOAK duration="+((SystemClock.elapsedRealtime()-start)/1000)+"s cycles="+cycles+" mixedChords="+mixed+" no duplicate or cross-field commit");
    }
}
