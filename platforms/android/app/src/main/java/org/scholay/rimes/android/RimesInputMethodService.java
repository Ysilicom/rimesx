package org.scholay.rimes.android;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.scholay.rimes.core.BufferSession;
import org.scholay.rimes.core.InputEpoch;
import org.scholay.rimes.core.RimeEngine;

/** All host mutations use the exact live InputConnection; engine results carry an editor lease. */
public final class RimesInputMethodService extends InputMethodService {
    private final BufferSession buffer=new BufferSession();
    private final InputEpoch epoch=new InputEpoch();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayDeque<Integer> expectedSelections=new ArrayDeque<>();
    private InputConnection target;
    private SharedPreferences preferences;
    private LinearLayout keyboard, keys, bufferRow, candidateRow;
    private TextView preedit, preview;
    private Button modeButton, schemeButton, bufferButton, retryButton, previous, next, insertNext, insertAll;
    private final List<Button> candidates=new ArrayList<>();
    private boolean uppercase, numeric, directOnly, privateField, english, ready, failed, destroyed;
    private boolean hostComposing;
    private int pending, selection=-1, selectionStart=-1, composingStart=-1;
    private String schema="rimes_pinyin", retained="";
    private final ArrayDeque<Result> retainedResults=new ArrayDeque<>();
    private RimeEngine.Snapshot snapshot=RimeEngine.Snapshot.EMPTY;
    // Worker-owned state. Access only inside EngineWorker.QUEUE.
    private RimeEngine engine;
    private long session;
    private static final String[] SCHEMAS={"rimes_pinyin","rimes_ziranma","rimes_wubi"};
    private static final String[] NAMES={"拼音","自然码","五笔"};

    @Override public void onCreate() {
        super.onCreate();
        preferences=getSharedPreferences("keyboard",MODE_PRIVATE);
        schema=preferences.getString("schema","rimes_pinyin");
        if(!java.util.Arrays.asList(SCHEMAS).contains(schema)) schema=SCHEMAS[0];
        initialize();
    }
    private void initialize() {
        failed=false; ready=false; render();
        EngineWorker.QUEUE.execute(() -> {
            try {
                java.io.File resources=EngineResources.prepare(getApplicationContext());
                if(engine==null) engine=new NativeRimeEngine();
                engine.initialize(resources.getAbsolutePath(),EngineResources.userDirectory(getApplicationContext()).getAbsolutePath());
                main.post(() -> { if(!destroyed) { ready=true; resetEngine(); render(); } });
            } catch(Exception | LinkageError error) {
                android.util.Log.e("RIMES","Engine initialization failed",error);
                main.post(() -> { if(!destroyed) { failed=true; ready=false; render(); } });
            }
        });
    }
    @Override public void onStartInput(EditorInfo info,boolean restarting) {
        super.onStartInput(info,restarting);
        endTarget();
        target=getCurrentInputConnection();
        configure(info);
    }
    private void configure(EditorInfo info) {
        int kind=info.inputType&InputType.TYPE_MASK_CLASS;
        numeric=kind==InputType.TYPE_CLASS_NUMBER || kind==InputType.TYPE_CLASS_PHONE || kind==InputType.TYPE_CLASS_DATETIME;
        directOnly=numeric || isPassword(info) || kind!=InputType.TYPE_CLASS_TEXT;
        privateField=!allowsBuffer(info);
        uppercase=false; selection=info.initialSelEnd; selectionStart=info.initialSelStart;
        buffer.beginTarget(target!=null && allowsBuffer(info));
        resetEngine(); rebuildKeys(); render();
    }
    @Override public void onStartInputView(EditorInfo info,boolean restarting) {
        super.onStartInputView(info,restarting);
        if(target==null) { target=getCurrentInputConnection(); configure(info); }
        render();
    }
    private String effectiveSchema() { return schema+(privateField || !preferences.getBoolean("learning",true) ? "_private" : ""); }
    private void resetEngine() {
        if(!ready) return;
        final String selected=effectiveSchema();
        final boolean active=target!=null;
        final InputEpoch.Ticket ticket=epoch.issue();
        EngineWorker.QUEUE.execute(() -> {
            if(session!=0) engine.destroySession(session);
            session=active ? engine.createSession() : 0;
            if(active && (session==0 || !engine.selectSchema(session,selected))) main.post(() -> { if(epoch.current(ticket)) engineFailure(); });
        });
    }
    private void engineFailure() { if(!destroyed) { failed=true; ready=false; render(); } }
    @Override public boolean onEvaluateFullscreenMode() { return false; }
    @Override public void onFinishInputView(boolean finishingInput) { endTarget(); super.onFinishInputView(finishingInput); }
    @Override public void onFinishInput() { endTarget(); super.onFinishInput(); }
    @Override public void onUnbindInput() { endTarget(); super.onUnbindInput(); }
    @Override public void onDestroy() { endTarget(); destroyed=true; super.onDestroy(); }
    private void endTarget() {
        // Clear the old composition before revoking its connection, never through the new target.
        if(target!=null && target==getCurrentInputConnection() && hostComposing) { target.setComposingText("",1); target.finishComposingText(); }
        target=null; hostComposing=false; composingStart=-1; selection=-1; selectionStart=-1;
        epoch.revoke(); pending=0; expectedSelections.clear(); snapshot=RimeEngine.Snapshot.EMPTY; retained=""; retainedResults.clear();
        buffer.finishTarget(); resetEngine(); render();
    }
    static boolean isPassword(EditorInfo info) {
        int kind=info.inputType&InputType.TYPE_MASK_CLASS, variation=info.inputType&InputType.TYPE_MASK_VARIATION;
        return kind==InputType.TYPE_CLASS_NUMBER && variation==InputType.TYPE_NUMBER_VARIATION_PASSWORD
                || kind==InputType.TYPE_CLASS_TEXT && (variation==InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || variation==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD);
    }
    static boolean allowsBuffer(EditorInfo info) {
        return (info.inputType&InputType.TYPE_MASK_CLASS)==InputType.TYPE_CLASS_TEXT && !isPassword(info)
                && (info.imeOptions&EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)==0;
    }
    private boolean ownsTarget() { return !destroyed && target!=null && target==getCurrentInputConnection(); }

    private interface Operation { Result run(); }
    private static final class Result {
        final RimeEngine.Snapshot state;
        final String text;
        final boolean block;
        final int action; // 0 text/snapshot, 1 host delete, 2 host Return
        Result(RimeEngine.Snapshot state,String text,boolean block,int action) { this.state=state; this.text=text; this.block=block; this.action=action; }
        static Result state(RimeEngine.Snapshot state) { return new Result(state,state.commit,true,0); }
    }
    private void dispatch(Operation operation) {
        if(!ownsTarget()) { endTarget(); return; }
        if(!retained.isEmpty()) { notice(R.string.delivery_pending); return; }
        InputEpoch.Ticket ticket=epoch.issue(); InputConnection connection=target;
        pending++; render();
        EngineWorker.QUEUE.execute(() -> {
            // Revocation also cancels queued engine work, before it could learn an old selection.
            if(!epoch.current(ticket)) return;
            Result result;
            try { result=operation.run(); }
            catch(RuntimeException error) {
                android.util.Log.e("RIMES","Engine operation failed",error);
                main.post(() -> { if(epoch.current(ticket)) { pending--; engineFailure(); } }); return;
            }
            main.post(() -> {
                if(!epoch.accept(ticket) || connection!=target || !ownsTarget()) return;
                pending--;
                if(!retainedResults.isEmpty() || !applyResult(result)) {
                    retainedResults.add(result); retained="pending"; notice(buffer.isEnabled()?R.string.buffer_limit:R.string.delivery_pending);
                }
                render();
            });
        });
    }
    private boolean applyResult(Result result) {
        if(!result.text.isEmpty() && !deliver(result.text,result.block)) return false;
        snapshot=result.state;
        if(result.action==1) deleteHostOrBuffer();
        if(result.action==2) returnHostOrBuffer();
        updateComposition(); return true;
    }
    private RimeEngine.Snapshot state() { return session==0 ? RimeEngine.Snapshot.EMPTY : engine.snapshot(session); }
    private Result literal(String text) {
        RimeEngine.Snapshot before=state();
        if(before.composing()) engine.clearComposition(session);
        return new Result(RimeEngine.Snapshot.EMPTY,before.raw+text,false,0);
    }
    private void type(String text) {
        if(!ownsTarget()) return;
        if(!ready) {
            if(!retained.isEmpty()) { notice(R.string.delivery_pending); return; }
            Result result=new Result(RimeEngine.Snapshot.EMPTY,text,false,0);
            if(!applyResult(result)) { retainedResults.add(result); retained="pending"; notice(buffer.isEnabled()?R.string.buffer_limit:R.string.delivery_pending); }
            render(); return;
        }
        final boolean chinese=!directOnly && !english && !numeric;
        dispatch(() -> {
            if(!chinese || session==0 || Character.isUpperCase(text.codePointAt(0))) return literal(text);
            RimeEngine.Snapshot before=state();
            if(before.raw.length()>=128 && !" ".equals(text)) return Result.state(before);
            if(" ".equals(text) && before.composing() && !before.candidates.isEmpty())
                return Result.state(engine.selectCandidate(session,before.pageStart));
            RimeEngine.Snapshot after=engine.processKey(session,text.codePointAt(0));
            if(!after.handled && after.commit.isEmpty()) return new Result(after,text,false,0);
            return Result.state(after);
        });
    }
    private boolean deliver(String text,boolean block) {
        if(!ownsTarget()) return false;
        if(buffer.isEnabled()) return block ? buffer.appendCommittedBlock(text) : buffer.appendLiteral(text);
        int start=hostComposing ? composingStart : Math.min(selectionStart,selection);
        expect(start<0 ? -1 : start+text.length());
        boolean accepted=target.commitText(text,1);
        if(accepted) { hostComposing=false; composingStart=-1; }
        return accepted;
    }
    private void updateComposition() {
        if(!ownsTarget() || buffer.isEnabled() || directOnly) return;
        if(snapshot.composing()) {
            if(!hostComposing) composingStart=Math.min(selectionStart,selection);
            expect(composingStart<0 ? -1 : composingStart+snapshot.preedit.length());
            hostComposing=target.setComposingText(snapshot.preedit,1);
        } else if(hostComposing && retained.isEmpty()) {
            expect(composingStart); target.setComposingText("",1); target.finishComposingText();
            hostComposing=false; composingStart=-1;
        }
    }
    private void expect(int end) { if(end>=0) { expectedSelections.add(end); selection=end; selectionStart=end; while(expectedSelections.size()>64) expectedSelections.remove(); } }
    @Override public void onUpdateSelection(int oldStart,int oldEnd,int newStart,int newEnd,int candidatesStart,int candidatesEnd) {
        super.onUpdateSelection(oldStart,oldEnd,newStart,newEnd,candidatesStart,candidatesEnd);
        if(target==null) return;
        if(newStart==newEnd && expectedSelections.contains(newEnd)) {
            while(!expectedSelections.isEmpty() && expectedSelections.remove()!=newEnd) { /* retire coalesced updates */ }
            return;
        }
        if(newStart==oldStart && newEnd==oldEnd) return;
        if(hostComposing && newStart==newEnd && newEnd==candidatesEnd) return;
        // A host/user selection change revokes pending work, including keys still in the worker queue.
        epoch.revoke(); pending=0; expectedSelections.clear(); snapshot=RimeEngine.Snapshot.EMPTY; retained=""; retainedResults.clear();
        if(hostComposing) { target.setComposingText("",1); target.finishComposingText(); }
        hostComposing=false; composingStart=-1; selection=newEnd; selectionStart=newStart;
        buffer.beginTarget(buffer.isPermitted()); resetEngine(); render();
    }
    private void insert(boolean all) {
        if(pending!=0 || snapshot.composing()) return;
        insertNow(all);
        retryRetained(); render();
    }
    private void insertNow(boolean all) {
        BufferSession.Delivery delivery=buffer.prepare(all);
        InputConnection connection=target;
        if(!ownsTarget() || !buffer.isCurrent(delivery)) return;
        expect(selection<0 ? -1 : selection+delivery.text.length());
        if(connection.commitText(delivery.text,1) && connection==target && ownsTarget()) buffer.acknowledge(delivery);
        render();
    }
    private void retryRetained() {
        while(!retainedResults.isEmpty()) {
            Result result=retainedResults.peek();
            if(!applyResult(result)) break;
            retainedResults.remove();
        }
        retained=retainedResults.isEmpty()?"":"pending";
        if(retained.isEmpty()) updateComposition();
        render();
    }
    private void delete() {
        if(!retained.isEmpty()) { if(buffer.isEnabled()) { buffer.deleteLastBlock(); retryRetained(); } return; }
        if(!ready) { deleteHostOrBuffer(); render(); return; }
        dispatch(() -> state().composing() ? Result.state(engine.processKey(session,0xff08))
                : new Result(RimeEngine.Snapshot.EMPTY,"",false,1));
    }
    private void deleteHostOrBuffer() {
        if(!ownsTarget()) return;
        if(buffer.isEnabled()) buffer.deleteLastBlock();
        else {
            CharSequence selected=target.getSelectedText(0);
            if(selected!=null && selected.length()>0) { expect(Math.min(selectionStart,selection)); target.commitText("",1); }
            else {
                CharSequence before=target.getTextBeforeCursor(2,0);
                int units=before!=null && before.length()>0 ? Character.charCount(Character.codePointBefore(before,before.length())) : 1;
                expect(selection>0 ? Math.max(0,selection-units) : 0); target.deleteSurroundingTextInCodePoints(1,0);
            }
        }
    }
    private void enter() {
        if(!ready) { returnHostOrBuffer(); return; }
        dispatch(() -> state().composing() ? literal("") : new Result(RimeEngine.Snapshot.EMPTY,"",false,2));
    }
    private void returnHostOrBuffer() {
        if(!ownsTarget()) return;
        if(buffer.isEnabled()) insertNow(false);
        else if(!sendDefaultEditorAction(true)) deliver("\n",false);
    }
    private void settleAndSwitch(Runnable change) {
        if(!retained.isEmpty()) return;
        if(!ready) { change.run(); render(); return; }
        // Route settlement before applying the new mode; subsequent keys queue after it.
        dispatch(() -> literal(""));
        change.run();
        final String selected=effectiveSchema();
        EngineWorker.QUEUE.execute(() -> { if(session!=0) engine.selectSchema(session,selected); });
        render();
    }
    private void select(int index) {
        if(pending!=0 || !ready || index>=snapshot.candidates.size()) return;
        final int absolute=snapshot.pageStart+index;
        dispatch(() -> Result.state(engine.selectCandidate(session,absolute)));
    }
    private void page(boolean forward) {
        if(pending==0 && snapshot.composing()) dispatch(() -> Result.state(engine.processKey(session,forward?0xff56:0xff55)));
    }
    private void notice(int message) { Toast.makeText(this,message,Toast.LENGTH_SHORT).show(); }

    @Override public View onCreateInputView() {
        keyboard=new LinearLayout(this); keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.setBackgroundColor(getColor(android.R.color.background_light));
        keyboard.setOnApplyWindowInsetsListener((view,insets) -> {
            int left,right,bottom;
            if(Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                left=safe.left; right=safe.right; bottom=safe.bottom;
            } else { left=insets.getSystemWindowInsetLeft(); right=insets.getSystemWindowInsetRight(); bottom=insets.getSystemWindowInsetBottom(); }
            view.setPadding(dp(4)+left,dp(2),dp(4)+right,dp(2)+bottom); return insets;
        });
        LinearLayout toolbar=row(keyboard,40);
        modeButton=button(toolbar,"中",() -> settleAndSwitch(() -> english=!english),1);
        modeButton.setContentDescription("中英切换");
        schemeButton=button(toolbar,"拼音",() -> settleAndSwitch(() -> {
            int i=java.util.Arrays.asList(SCHEMAS).indexOf(schema); schema=SCHEMAS[(i+1)%SCHEMAS.length];
            preferences.edit().putString("schema",schema).apply();
        }),1.5f);
        schemeButton.setContentDescription("中文方案");
        bufferButton=button(toolbar,"Buffer",() -> { if(pending==0 && !snapshot.composing() && retained.isEmpty()) { buffer.setEnabled(!buffer.isEnabled()); render(); } },2);
        button(toolbar,"🌐",() -> { endTarget(); getSystemService(InputMethodManager.class).showInputMethodPicker(); },1).setContentDescription(getString(R.string.switch_keyboard));
        preedit=new TextView(this); preedit.setSingleLine(true); preedit.setTextSize(14); preedit.setPadding(dp(8),0,0,0);
        keyboard.addView(preedit,new LinearLayout.LayoutParams(-1,dp(24)));
        candidateRow=row(keyboard,40); previous=button(candidateRow,"‹",() -> page(false),0.6f);
        previous.setContentDescription("上一页候选");
        HorizontalScrollView scroll=new HorizontalScrollView(this); scroll.setFillViewport(true);
        LinearLayout strip=new LinearLayout(this); scroll.addView(strip,new HorizontalScrollView.LayoutParams(-1,-1));
        candidateRow.addView(scroll,new LinearLayout.LayoutParams(0,-1,8));
        candidates.clear();
        for(int i=0;i<9;i++) { final int index=i; candidates.add(button(strip,"",() -> select(index),1)); }
        next=button(candidateRow,"›",() -> page(true),0.6f); next.setContentDescription("下一页候选");
        bufferRow=row(keyboard,40);
        preview=new TextView(this); preview.setSingleLine(true); preview.setTextSize(16);
        HorizontalScrollView bufferScroll=new HorizontalScrollView(this); bufferScroll.addView(preview);
        bufferRow.addView(bufferScroll,new LinearLayout.LayoutParams(0,-1,3));
        insertNext=button(bufferRow,getString(R.string.insert_next),() -> insert(false),1);
        insertAll=button(bufferRow,getString(R.string.insert_all),() -> insert(true),1.4f);
        button(bufferRow,getString(R.string.clear),() -> { if(pending==0 && !snapshot.composing()) { buffer.clear(); retryRetained(); render(); } },1);
        retryButton=new Button(this); retryButton.setText(R.string.retry); retryButton.setOnClickListener(v -> { if(failed) initialize(); else retryRetained(); });
        keyboard.addView(retryButton,new LinearLayout.LayoutParams(-1,dp(40)));
        keys=new LinearLayout(this); keys.setOrientation(LinearLayout.VERTICAL); keyboard.addView(keys);
        rebuildKeys(); render(); return keyboard;
    }
    private void rebuildKeys() {
        if(keys==null) return;
        keys.removeAllViews(); // Only a layout/mode change rebuilds keys, never a text/candidate update.
        int height=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE ? 36 : 48;
        String[] rows=numeric ? new String[]{"1234567890","@#$%&*()-",",.!?:;'\"/"} : new String[]{"qwertyuiop","asdfghjkl","zxcvbnm"};
        for(String letters:rows) {
            LinearLayout row=row(keys,height);
            for(int i=0;i<letters.length();i++) {
                String text=letters.substring(i,i+1); if(uppercase && !numeric) text=text.toUpperCase(Locale.ROOT);
                final String value=text; button(row,text,() -> type(value),1);
            }
        }
        LinearLayout bottom=row(keys,height);
        button(bottom,numeric?"ABC":"123",() -> settleAndSwitch(() -> { numeric=!numeric; rebuildKeys(); }),1);
        button(bottom,"⇧",() -> { uppercase=!uppercase; rebuildKeys(); },1);
        button(bottom,",",() -> type(","),0.8f);
        button(bottom,getString(R.string.space),() -> type(" "),2);
        button(bottom,".",() -> type("."),0.8f);
        button(bottom,"⌫",this::delete,1).setContentDescription(getString(R.string.backspace));
        button(bottom,"↵",this::enter,1).setContentDescription(getString(R.string.enter));
    }
    private void render() {
        if(keyboard==null) return;
        setText(modeButton,english || directOnly ? "英" : "中"); modeButton.setEnabled(!directOnly);
        setText(schemeButton,NAMES[java.util.Arrays.asList(SCHEMAS).indexOf(schema)]); schemeButton.setEnabled(!directOnly && ready);
        setText(bufferButton,getString(buffer.isEnabled()?R.string.buffer_on:R.string.buffer_off));
        bufferButton.setEnabled(buffer.isPermitted() && pending==0 && !snapshot.composing() && retained.isEmpty());
        String status=failed?getString(R.string.engine_failed):!ready?getString(R.string.engine_loading):snapshot.preedit;
        if(!retained.isEmpty()) status=getString(R.string.delivery_pending);
        setText(preedit,status);
        boolean show=ready && !directOnly && !english && snapshot.composing();
        candidateRow.setVisibility(show?View.VISIBLE:View.GONE);
        for(int i=0;i<9;i++) {
            Button item=candidates.get(i); boolean exists=show && i<snapshot.candidates.size();
            item.setVisibility(exists?View.VISIBLE:View.GONE);
            if(exists) { setText(item,snapshot.candidates.get(i)); item.setContentDescription("候选"+(i+1)+" "+snapshot.candidates.get(i)); }
            item.setEnabled(exists && pending==0);
        }
        previous.setEnabled(pending==0 && snapshot.pageStart>0); next.setEnabled(pending==0 && !snapshot.lastPage);
        bufferRow.setVisibility(buffer.isEnabled()?View.VISIBLE:View.GONE);
        setText(preview,buffer.text().isEmpty()?getString(R.string.buffer_empty):buffer.text());
        insertNext.setEnabled(pending==0 && !snapshot.composing() && buffer.blockCount()>0);
        insertAll.setEnabled(insertNext.isEnabled());
        retryButton.setVisibility(failed || !retained.isEmpty()?View.VISIBLE:View.GONE);
    }
    private static void setText(TextView view,String value) {
        if(!android.text.TextUtils.equals(view.getText(),value)) view.setText(value);
    }
    private LinearLayout row(LinearLayout parent,int height) {
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row,new LinearLayout.LayoutParams(-1,dp(height))); return row;
    }
    private Button button(LinearLayout row,String text,Runnable action,float weight) {
        Button button=new Button(this); button.setText(text); button.setAllCaps(false); button.setTextSize(text.length()>4?11:16);
        button.setMinWidth(0); button.setMinimumWidth(0); button.setPadding(0,0,0,0);
        button.setOnClickListener(v -> action.run()); row.addView(button,new LinearLayout.LayoutParams(0,-1,weight)); return button;
    }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
}
