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
import android.widget.FrameLayout;
import android.view.Gravity;
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
import org.scholay.rimes.core.KeyboardLayout;
import org.scholay.rimes.core.NineKeyPinyin;

/** All host mutations use the exact live InputConnection; engine results carry an editor lease. */
public final class RimesInputMethodService extends InputMethodService {
    private final BufferSession buffer=new BufferSession();
    private final InputEpoch epoch=new InputEpoch();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayDeque<Integer> expectedSelections=new ArrayDeque<>();
    private InputConnection target;
    private SharedPreferences preferences;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener=this::preferenceChanged;
    private LinearLayout keyboard, bufferRow, candidateRow;
    private KeyboardSurface keys;
    private KeyboardAppearancePanel appearancePanel;
    private FrameLayout surfaceContainer;
    private KeyButton themeButton,layoutButton;
    private final List<KeyButton> chromeButtons=new ArrayList<>();
    private KeyboardTheme theme=KeyboardTheme.ALL[0];
    private String layout="qwerty";
    private boolean symbols,emoji,appearanceOpen,spellingOpen,punctuationOpen;
    private int spellingPage;
    private NineKeyPinyin spellings=new NineKeyPinyin(java.util.Collections.emptyList());
    private List<String> spellingChoices=java.util.Collections.emptyList();
    private static final String[] MARKS={",",".","?","!","、",":",";","'"};
    private static final String[] MARK_LABELS={"，","。","？","！","、","：","；","'"};
    private TextView preedit, preview;
    private HorizontalScrollView candidateScroll;
    private String renderedRaw="";
    private int renderedPage=-1;
    private Button schemeButton, bufferButton, retryButton, previous, next, insertNext, insertAll;
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
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        schema=preferences.getString("schema","rimes_pinyin");
        layout=readLayout(); theme=KeyboardTheme.named(preferences.getString("theme","apple"));
        if(!java.util.Arrays.asList(SCHEMAS).contains(schema)) schema=SCHEMAS[0];
        initialize();
    }
    private void initialize() {
        failed=false; ready=false; render();
        EngineWorker.QUEUE.execute(() -> {
            try {
                java.io.File resources=EngineResources.prepare(getApplicationContext());
                org.json.JSONArray syllables=new org.json.JSONArray(new String(java.nio.file.Files.readAllBytes(new java.io.File(resources,"nine-key-syllables.json").toPath()),java.nio.charset.StandardCharsets.UTF_8));
                List<String> syllableList=new ArrayList<>();
                for(int i=0;i<syllables.length();i++) syllableList.add(syllables.getString(i));
                NineKeyPinyin spelling=new NineKeyPinyin(syllableList);
                if(engine==null) engine=new NativeRimeEngine();
                engine.initialize(resources.getAbsolutePath(),EngineResources.userDirectory(getApplicationContext()).getAbsolutePath());
                main.post(() -> { if(!destroyed) { spellings=spelling; ready=true; resetEngine(); render(); } });
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
        layout=readLayout(); theme=KeyboardTheme.named(preferences.getString("theme","apple"));
        String savedSchema=preferences.getString("schema","rimes_pinyin");
        if(java.util.Arrays.asList(SCHEMAS).contains(savedSchema)) schema=savedSchema;
        int kind=info.inputType&InputType.TYPE_MASK_CLASS;
        numeric=kind==InputType.TYPE_CLASS_NUMBER || kind==InputType.TYPE_CLASS_PHONE || kind==InputType.TYPE_CLASS_DATETIME;
        directOnly=numeric || isPassword(info) || kind!=InputType.TYPE_CLASS_TEXT;
        privateField=!allowsBuffer(info);
        uppercase=false; symbols=false; emoji=false; appearanceOpen=false; spellingOpen=false; punctuationOpen=false;
        selection=info.initialSelEnd; selectionStart=info.initialSelStart;
        buffer.beginTarget(target!=null && allowsBuffer(info));
        resetEngine(); rebuildKeys(); render();
    }
    @Override public void onStartInputView(EditorInfo info,boolean restarting) {
        super.onStartInputView(info,restarting);
        if(target==null) { target=getCurrentInputConnection(); configure(info); }
        render();
    }
    private String effectiveSchema() { return (nineKeyEngine()?"rimes_pinyin9":schema)+(privateField || !preferences.getBoolean("learning",true) ? "_private" : ""); }
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
    @Override public void onDestroy() {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        endTarget(); destroyed=true; super.onDestroy();
    }
    private String readLayout() { return "nineKey".equals(preferences.getString("layout","qwerty"))?"nineKey":"qwerty"; }
    private boolean nineKeyEngine() { return layout.equals("nineKey") && schema.equals("rimes_pinyin"); }
    private boolean nineKeyVisible() { return ready && nineKeyEngine() && !directOnly && !english && !numeric && !emoji && !uppercase; }
    private void preferenceChanged(SharedPreferences changed,String key) {
        if(destroyed) return;
        if("theme".equals(key)) { theme=KeyboardTheme.named(changed.getString("theme","apple")); render(); return; }
        if("schema".equals(key)) {
            String selected=changed.getString("schema","rimes_pinyin");
            if(!selected.equals(schema) && java.util.Arrays.asList(SCHEMAS).contains(selected)) {
                if(ownsTarget()) settleAndSwitch(() -> schema=selected); else { schema=selected; render(); }
            }
            return;
        }
        if("layout".equals(key)) {
            String selected=readLayout();
            if(!selected.equals(layout)) {
                if(ownsTarget()) settleAndSwitch(() -> { layout=selected; spellingOpen=false; });
                else { layout=selected; render(); }
            }
            return;
        }
        if(!"learning".equals(key) || destroyed || !ready || !ownsTarget() || privateField) return;
        final String selected=effectiveSchema();
        // Serialize policy changes before subsequent keys. Existing confirmed blocks stay intact;
        // unfinished code settles exactly like a schema switch, without selecting/learning a word.
        dispatch(() -> {
            Result settled=literal("");
            if(session!=0 && !engine.selectSchema(session,selected)) throw new IllegalStateException("Cannot change learning mode");
            return settled;
        },true);
    }
    private void endTarget() {
        // Clear the old composition before revoking its connection, never through the new target.
        if(target!=null && target==getCurrentInputConnection() && hostComposing) { target.setComposingText("",1); target.finishComposingText(); }
        appearanceOpen=false; spellingOpen=false; punctuationOpen=false;
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
        dispatch(operation,false);
    }
    private void dispatch(Operation operation,boolean policyChange) {
        if(!ownsTarget()) { endTarget(); return; }
        if(!policyChange && !retained.isEmpty()) { notice(R.string.delivery_pending); return; }
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
        final boolean chinese=!directOnly && !english && !numeric && !emoji;
        dispatch(() -> {
            if(!chinese || session==0 || text.codePointAt(0)>127 || Character.isUpperCase(text.codePointAt(0))) return literal(text);
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
        final boolean nine=nineKeyVisible();
        dispatch(() -> state().composing() ? (nine?replaceNineKey(NineKeyPinyin.backspace(state().raw)):Result.state(engine.processKey(session,0xff08)))
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

    private Result replaceNineKey(String raw) {
        engine.clearComposition(session);
        RimeEngine.Snapshot after=RimeEngine.Snapshot.EMPTY;
        for(int key:raw.codePoints().toArray()) after=engine.processKey(session,key);
        return Result.state(after);
    }
    private void chooseSpelling(int index) {
        if(pending!=0 || index>=spellingChoices.size()) return;
        String raw=spellings.select(spellingChoices.get(index),snapshot.raw);
        if(raw==null) return;
        spellingOpen=false;
        dispatch(() -> replaceNineKey(raw));
    }
    private void chooseLayout(String selected) {
        if(selected.equals(layout) && (!selected.equals("nineKey") || schema.equals("rimes_pinyin"))) return;
        settleAndSwitch(() -> {
            layout=selected; numeric=false; symbols=false; emoji=false; uppercase=false; spellingOpen=false;
            if(selected.equals("nineKey")) { schema="rimes_pinyin"; english=false; }
            preferences.edit().putString("layout",layout).putString("schema",schema).apply();
        });
    }
    private void toggleLanguage() {
        settleAndSwitch(() -> { english=!english; uppercase=false; spellingOpen=false; punctuationOpen=false; });
    }
    private void toggleNumbers() {
        settleAndSwitch(() -> { numeric=!numeric; symbols=false; emoji=false; spellingOpen=false; punctuationOpen=false; });
    }
    private KeyboardLayout.Mode visibleMode() {
        return emoji?KeyboardLayout.Mode.EMOJI:numeric?(symbols?KeyboardLayout.Mode.SYMBOLS:KeyboardLayout.Mode.NUMERIC)
                :nineKeyVisible()?KeyboardLayout.Mode.NINE_KEY:KeyboardLayout.Mode.QWERTY;
    }
    private void toggleAppearance() {
        appearanceOpen=!appearanceOpen;
        if(appearanceOpen) appearancePanel.scrollTo(0,0);
        render();
    }
    private final KeyboardSurface.Handler keyHandler=new KeyboardSurface.Handler() {
        @Override public String label(KeyboardLayout.Key key) {
            switch(key.action) {
                case TEXT:
                    if(nineKeyVisible()) return new String[]{"ABC","DEF","GHI","JKL","MNO","PQRS","TUV","WXYZ"}[Integer.parseInt(key.text)-2];
                    return uppercase && !numeric && !emoji?key.text.toUpperCase(Locale.ROOT):key.text;
                case SHIFT: return uppercase?"⇪":"⇧";
                case DELETE: return "⌫";
                case RETURN: return returnLabel();
                case NUMBERS: return numeric || emoji?(nineKeyEngine() && !english && !directOnly?"拼音":"ABC"):"123";
                case SYMBOLS: return numeric && symbols?"123":"#+=";
                case LANGUAGE: return english || directOnly?"英":"中";
                case EMOJI: return emoji?"ABC":"☺";
                case SPACE: return english || directOnly?"space":"空格";
                case SPELLING: return "选拼音";
                case SEPARATOR: return "分隔";
                case PUNCTUATION: return "，。?!";
                default: throw new IllegalStateException();
            }
        }
        @Override public String description(KeyboardLayout.Key key) {
            switch(key.action) {
                case TEXT: return nineKeyVisible()?"九键 "+key.text+" "+label(key):label(key);
                case SHIFT: return "Shift";
                case DELETE: return getString(R.string.backspace);
                case RETURN: return getString(R.string.enter);
                case NUMBERS: return "数字与字母";
                case SYMBOLS: return "符号页";
                case LANGUAGE: return "中英切换";
                case EMOJI: return "表情";
                case SPACE: return getString(R.string.space);
                case SPELLING: return "选拼音";
                case SEPARATOR: return "分隔音节";
                case PUNCTUATION: return "中文标点";
                default: throw new IllegalStateException();
            }
        }
        @Override public boolean enabled(KeyboardLayout.Key key) {
            if(key.action==KeyboardLayout.Action.LANGUAGE) return !directOnly;
            if(key.action==KeyboardLayout.Action.SPELLING) return pending==0 && !spellingChoices.isEmpty();
            if(key.action==KeyboardLayout.Action.SEPARATOR) return pending==0 && snapshot.composing() && !snapshot.raw.endsWith("'");
            return true;
        }
        @Override public boolean selected(KeyboardLayout.Key key) {
            return key.action==KeyboardLayout.Action.SHIFT && uppercase || key.action==KeyboardLayout.Action.SPELLING && spellingOpen;
        }
        @Override public void press(KeyboardLayout.Key key) {
            switch(key.action) {
                case TEXT: type(uppercase && !numeric && !emoji?key.text.toUpperCase(Locale.ROOT):key.text); break;
                case SHIFT: settleAndSwitch(() -> uppercase=!uppercase); break;
                case DELETE: delete(); break;
                case RETURN: enter(); break;
                case NUMBERS:
                    if(emoji) settleAndSwitch(() -> { emoji=false; numeric=false; }); else toggleNumbers(); break;
                case SYMBOLS: settleAndSwitch(() -> { symbols=!symbols || !numeric; numeric=true; emoji=false; }); break;
                case LANGUAGE: toggleLanguage(); break;
                case EMOJI: settleAndSwitch(() -> { emoji=!emoji; numeric=false; }); break;
                case SPACE: type(" "); break;
                case SPELLING: spellingOpen=!spellingOpen; punctuationOpen=false; spellingPage=0; render(); break;
                case SEPARATOR: type("'"); break;
                case PUNCTUATION: punctuationOpen=!punctuationOpen; spellingOpen=false; render(); break;
                default: throw new IllegalStateException();
            }
        }
    };
    private String returnLabel() {
        if(snapshot.composing()) return "原码";
        if(buffer.isEnabled()) return "插入";
        EditorInfo info=getCurrentInputEditorInfo();
        if(info==null || (info.imeOptions&EditorInfo.IME_FLAG_NO_ENTER_ACTION)!=0) return "换行";
        switch(info.imeOptions&EditorInfo.IME_MASK_ACTION) {
            case EditorInfo.IME_ACTION_SEARCH: return "搜索";
            case EditorInfo.IME_ACTION_GO: return "前往";
            case EditorInfo.IME_ACTION_SEND: return "发送";
            case EditorInfo.IME_ACTION_NEXT: return "下一项";
            case EditorInfo.IME_ACTION_DONE: return "完成";
            default: return "换行";
        }
    }
    @Override public View onCreateInputView() {
        keyboard=new LinearLayout(this); keyboard.setOrientation(LinearLayout.VERTICAL);
        if(Build.VERSION.SDK_INT>=29) keyboard.setForceDarkAllowed(false); // Palettes already have explicit dark colors.
        keyboard.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); chromeButtons.clear();
        keyboard.setPadding(dp(3),dp(2),dp(3),dp(2));
        keyboard.setOnApplyWindowInsetsListener((view,insets) -> {
            int left,right,bottom;
            if(Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                left=safe.left; right=safe.right; bottom=safe.bottom;
            } else { left=insets.getSystemWindowInsetLeft(); right=insets.getSystemWindowInsetRight(); bottom=insets.getSystemWindowInsetBottom(); }
            view.setPadding(dp(3)+left,dp(2),dp(3)+right,dp(2)+bottom); return insets;
        });
        LinearLayout toolbar=row(keyboard,landscape()?36:44);
        themeButton=button(toolbar,"◐",this::toggleAppearance,0); fixedWidth(themeButton,44);
        themeButton.setContentDescription("布局与配色");
        schemeButton=button(toolbar,"拼音",() -> settleAndSwitch(() -> {
            int i=java.util.Arrays.asList(SCHEMAS).indexOf(schema); schema=SCHEMAS[(i+1)%SCHEMAS.length];
            spellingOpen=false; preferences.edit().putString("schema",schema).apply();
        }),1);
        schemeButton.setContentDescription("中文方案");
        bufferButton=button(toolbar,"Buffer",() -> { if(pending==0 && !snapshot.composing() && retained.isEmpty()) { buffer.setEnabled(!buffer.isEnabled()); render(); } },1.2f);
        layoutButton=button(toolbar,"26 键",this::toggleAppearance,1);
        layoutButton.setContentDescription("键位布局");
        KeyButton globe=button(toolbar,"🌐",() -> { endTarget(); getSystemService(InputMethodManager.class).showInputMethodPicker(); },0);
        fixedWidth(globe,44); globe.setContentDescription(getString(R.string.switch_keyboard));
        preedit=new TextView(this); preedit.setSingleLine(true); preedit.setTextSize(13); preedit.setGravity(Gravity.CENTER_VERTICAL);
        preedit.setPadding(dp(8),0,dp(8),0); preedit.setEllipsize(android.text.TextUtils.TruncateAt.START);
        if(landscape()) toolbar.addView(preedit,3,new LinearLayout.LayoutParams(0,-1,1.5f));
        else keyboard.addView(preedit,new LinearLayout.LayoutParams(-1,dp(20)));
        candidateRow=row(keyboard,landscape()?36:44); previous=button(candidateRow,"‹",() -> candidatePage(false),0); fixedWidth(previous,36);
        previous.setContentDescription("上一页候选");
        candidateScroll=new HorizontalScrollView(this); candidateScroll.setFillViewport(false); candidateScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout strip=new LinearLayout(this); candidateScroll.addView(strip,new HorizontalScrollView.LayoutParams(-2,-1));
        candidateRow.addView(candidateScroll,new LinearLayout.LayoutParams(0,-1,1));
        candidates.clear();
        for(int i=0;i<9;i++) {
            final int index=i; KeyButton candidate=button(strip,"",() -> candidateTapped(index),0);
            candidate.setLayoutParams(new LinearLayout.LayoutParams(-2,-1)); candidate.font(20);
            candidate.setMinWidth(dp(52)); candidate.setMinimumWidth(dp(52)); candidate.setPadding(dp(12),0,dp(12),0);
            // Natural text width preserves complete long phrases inside the horizontal rail.
            candidate.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE); candidate.setTextSize(landscape()?18:20);
            candidates.add(candidate);
        }
        next=button(candidateRow,"›",() -> candidatePage(true),0); fixedWidth(next,36); next.setContentDescription("下一页候选");
        bufferRow=row(keyboard,landscape()?36:44);
        preview=new TextView(this); preview.setSingleLine(true); preview.setTextSize(16); preview.setGravity(Gravity.CENTER_VERTICAL);
        preview.setPadding(dp(8),0,dp(8),0);
        HorizontalScrollView bufferScroll=new HorizontalScrollView(this); bufferScroll.setFillViewport(true); bufferScroll.addView(preview,new HorizontalScrollView.LayoutParams(-2,-1));
        bufferRow.addView(bufferScroll,new LinearLayout.LayoutParams(0,-1,1));
        insertNext=button(bufferRow,"插入",() -> insert(false),0); fixedWidth(insertNext,48); insertNext.setContentDescription(getString(R.string.insert_next));
        insertAll=button(bufferRow,"全部",() -> insert(true),0); fixedWidth(insertAll,48); insertAll.setContentDescription(getString(R.string.insert_all));
        KeyButton clear=button(bufferRow,"清空",() -> { if(pending==0 && !snapshot.composing()) { buffer.clear(); retryRetained(); render(); } },0);
        fixedWidth(clear,48); clear.setContentDescription(getString(R.string.clear));
        retryButton=new KeyButton(this); chromeButtons.add((KeyButton)retryButton); retryButton.setText(R.string.retry);
        retryButton.setOnClickListener(v -> { if(failed) initialize(); else retryRetained(); }); keyboard.addView(retryButton,new LinearLayout.LayoutParams(-1,dp(44)));
        surfaceContainer=new FrameLayout(this);
        keys=new KeyboardSurface(this,keyHandler); surfaceContainer.addView(keys,new FrameLayout.LayoutParams(-1,-1));
        appearancePanel=new KeyboardAppearancePanel(this,this::chooseLayout,id -> preferences.edit().putString("theme",id).apply());
        surfaceContainer.addView(appearancePanel,new FrameLayout.LayoutParams(-1,-1));
        keyboard.addView(surfaceContainer,new LinearLayout.LayoutParams(-1,dp(KeyboardLayout.height(landscape()))));
        rebuildKeys(); render(); return keyboard;
    }
    private boolean landscape() { return getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE; }
    private void rebuildKeys() { if(keys!=null) keys.render(visibleMode(),theme); }
    private void candidateTapped(int index) {
        if(spellingOpen) chooseSpelling(spellingPage+index);
        else if(punctuationOpen || !snapshot.composing()) {
            if(index<MARKS.length) { punctuationOpen=false; type(MARKS[index]); }
        } else select(index);
    }
    private void candidatePage(boolean forward) {
        if(spellingOpen) { spellingPage=Math.max(0,Math.min(((spellingChoices.size()-1)/9)*9,spellingPage+(forward?9:-9))); render(); }
        else page(forward);
    }
    private void render() {
        if(keyboard==null) return;
        KeyboardTheme.Palette palette=theme.palette(this); keyboard.setBackgroundColor(palette.background);
        for(KeyButton button:chromeButtons) button.theme(theme);
        setText(themeButton,theme.glyph); setText(layoutButton,appearanceOpen?"完成":nineKeyVisible()?"9 键":"26 键");
        layoutButton.setSelected(appearanceOpen);
        setText(schemeButton,NAMES[java.util.Arrays.asList(SCHEMAS).indexOf(schema)]); schemeButton.setEnabled(!directOnly && ready);
        bufferButton.setContentDescription(getString(buffer.isEnabled()?R.string.buffer_on:R.string.buffer_off)); bufferButton.setSelected(buffer.isEnabled());
        bufferButton.setEnabled(buffer.isPermitted() && pending==0 && !snapshot.composing() && retained.isEmpty());
        String status=failed?getString(R.string.engine_failed):!ready?getString(R.string.engine_loading):snapshot.preedit;
        if(!retained.isEmpty()) status=getString(R.string.delivery_pending);
        setText(preedit,status); preedit.setTextColor(palette.accentText); preedit.setVisibility(directOnly?View.GONE:View.VISIBLE);
        boolean show=ready && !directOnly && !english && snapshot.composing();
        spellingChoices=nineKeyVisible()?spellings.choices(snapshot.raw):java.util.Collections.emptyList();
        if(spellingChoices.isEmpty()) spellingOpen=false;
        if(spellingPage>=spellingChoices.size()) spellingPage=0;
        candidateRow.setVisibility(directOnly?View.GONE:View.VISIBLE);
        boolean marks=punctuationOpen || !show;
        int count=spellingOpen?Math.min(9,spellingChoices.size()-spellingPage):marks?MARKS.length:snapshot.candidates.size();
        for(int i=0;i<9;i++) {
            Button item=candidates.get(i); boolean exists=i<count;
            item.setVisibility(exists?View.VISIBLE:View.GONE);
            if(exists) {
                String value=spellingOpen?spellingChoices.get(spellingPage+i):marks?(english || numeric || emoji?MARKS[i]:MARK_LABELS[i]):snapshot.candidates.get(i);
                setText(item,value);
                item.setContentDescription(spellingOpen?"拼音 "+value:marks?MARKS[i]:"候选"+(i+1)+" "+value);
            }
            item.setEnabled(exists && pending==0);
        }
        if(renderedPage!=snapshot.pageStart || !renderedRaw.equals(snapshot.raw)) {
            candidateScroll.scrollTo(0,0); renderedPage=snapshot.pageStart; renderedRaw=snapshot.raw;
        }
        previous.setVisibility(marks && !spellingOpen?View.GONE:View.VISIBLE); next.setVisibility(previous.getVisibility());
        previous.setEnabled(pending==0 && (spellingOpen?spellingPage>0:snapshot.pageStart>0));
        next.setEnabled(pending==0 && (spellingOpen?spellingPage+9<spellingChoices.size():!snapshot.lastPage));
        bufferRow.setVisibility(buffer.isEnabled()?View.VISIBLE:View.GONE);
        setText(preview,buffer.text().isEmpty()?getString(R.string.buffer_empty):buffer.text()); preview.setTextColor(palette.ink);
        insertNext.setEnabled(pending==0 && !snapshot.composing() && buffer.blockCount()>0); insertAll.setEnabled(insertNext.isEnabled());
        retryButton.setVisibility(failed || !retained.isEmpty()?View.VISIBLE:View.GONE);
        keys.render(visibleMode(),theme); keys.setVisibility(appearanceOpen?View.GONE:View.VISIBLE);
        appearancePanel.setVisibility(appearanceOpen?View.VISIBLE:View.GONE);
        if(appearanceOpen) appearancePanel.render(layout,theme);
    }
    private static void setText(TextView view,String value) {
        if(!android.text.TextUtils.equals(view.getText(),value)) view.setText(value);
    }
    private LinearLayout row(LinearLayout parent,int height) {
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row,new LinearLayout.LayoutParams(-1,dp(height))); return row;
    }
    private KeyButton button(LinearLayout row,String text,Runnable action,float weight) {
        KeyButton button=new KeyButton(this); button.setText(text); button.font(15); button.appearance(false,true,false);
        button.setOnClickListener(v -> action.run()); row.addView(button,new LinearLayout.LayoutParams(0,-1,weight)); chromeButtons.add(button); return button;
    }
    private void fixedWidth(View view,int width) { view.setLayoutParams(new LinearLayout.LayoutParams(dp(width),-1)); }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
}
