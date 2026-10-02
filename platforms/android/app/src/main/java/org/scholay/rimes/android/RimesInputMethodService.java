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
import org.scholay.rimes.core.ChordGesture;
import org.scholay.rimes.core.ChordLayout;

/** All host mutations use the exact live InputConnection; engine results carry an editor lease. */
public final class RimesInputMethodService extends InputMethodService {
    private final BufferSession buffer=new BufferSession();
    private final InputEpoch epoch=new InputEpoch();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayDeque<Integer> expectedSelections=new ArrayDeque<>();
    private InputConnection target;
    private SharedPreferences preferences;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener=this::preferenceChanged;
    private LinearLayout keyboard, bufferRow, candidateRow, spellingRow, chordFooter;
    private BufferRail bufferRail;
    private ChordSurface chords;
    private TextView metrics;
    private final List<KeyButton> spellingButtons=new ArrayList<>();
    private final List<KeyButton> chordControls=new ArrayList<>();
    private String chordPreview="",hostPreedit="";
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
    private ChordPreview chordReadout;
    private ChordGesture.Preview heldPreview;
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
    private String effectiveSchema() { return (chordLayout()?"rimes_ziranma":nineKeyEngine()?"rimes_pinyin9":schema)+(privateField || !preferences.getBoolean("learning",true) ? "_private" : ""); }
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
    private String readLayout() {
        String value=preferences.getString("layout","qwerty");
        return java.util.Arrays.asList("qwerty","nineKey","orthogonal","splitOrthogonal").contains(value)?value:"qwerty";
    }
    private boolean chordLayout() { return layout.equals("orthogonal") || layout.equals("splitOrthogonal"); }
    private boolean chordVisible() { return ready && chordLayout() && !directOnly && !numeric && !emoji; }
    private void cancelChord() { heldPreview=null; chordPreview=""; if(chords!=null) chords.cancel(); }
    private void chooseSchema(String selected) {
        settleAndSwitch(() -> { schema=selected; if(chordLayout()) layout="qwerty"; spellingOpen=false;
            preferences.edit().putString("schema",schema).putString("layout",layout).apply(); });
    }
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
        cancelChord(); appearanceOpen=false; spellingOpen=false; punctuationOpen=false;
        hostPreedit=""; target=null; hostComposing=false; composingStart=-1; selection=-1; selectionStart=-1;
        epoch.revoke(); pending=0; expectedSelections.clear(); snapshot=RimeEngine.Snapshot.EMPTY; retained=""; retainedResults.clear();
        buffer.finishTarget(); if(bufferRail!=null) bufferRail.clearProjection(); resetEngine(); render();
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
            if(hostComposing && hostPreedit.equals(snapshot.preedit)) return;
            if(!hostComposing) composingStart=Math.min(selectionStart,selection);
            expect(composingStart<0 ? -1 : composingStart+snapshot.preedit.length());
            hostComposing=target.setComposingText(snapshot.preedit,1);
            hostPreedit=hostComposing?snapshot.preedit:"";
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
        cancelChord(); epoch.revoke(); pending=0; expectedSelections.clear(); snapshot=RimeEngine.Snapshot.EMPTY; retained=""; retainedResults.clear();
        if(hostComposing) { target.setComposingText("",1); target.finishComposingText(); }
        hostComposing=false; composingStart=-1; selection=newEnd; selectionStart=newStart;
        buffer.beginTarget(buffer.isPermitted()); if(bufferRail!=null) bufferRail.clearProjection(); resetEngine(); render();
    }
    private void insert(boolean all) {
        if(chords!=null && chords.isChordActive()) return;
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
        cancelChord();
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
        if(chords!=null && chords.isChordActive()) return;
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
            if(chordLayout()) english=false;
            preferences.edit().putString("layout",layout).putString("schema",schema).apply();
        });
    }
    private void typeChord(String code) {
        if(code.isEmpty() || !ownsTarget() || !ready) return;
        final boolean chinese=!english && !directOnly && !uppercase;
        final String literalCode=uppercase?code.toUpperCase(Locale.ROOT):code;
        dispatch(() -> {
            if(!chinese) return literal(literalCode);
            RimeEngine.Snapshot after=state(); StringBuilder committed=new StringBuilder();
            if(after.raw.length()+code.length()>128) return Result.state(after);
            for(int key:code.codePoints().toArray()) {
                after=engine.processKey(session,key); committed.append(after.commit);
                if(!after.handled && after.commit.isEmpty()) committed.appendCodePoint(key);
            }
            return new Result(after,committed.toString(),true,0);
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
        cancelChord();
        appearanceOpen=!appearanceOpen;
        if(appearanceOpen) appearancePanel.scrollTo(0,0);
        render();
    }
    private final KeyboardSurface.Handler keyHandler=new KeyboardSurface.Handler() {
        @Override public String label(KeyboardLayout.Key key) {
            switch(key.action) {
                case TEXT:
                    if(nineKeyVisible()) return new String[]{"ABC","DEF","GHI","JKL","MNO","PQRS","TUV","WXYZ"}[Integer.parseInt(key.text)-2];
                    return !numeric && !emoji?key.text.toUpperCase(Locale.ROOT):key.text;
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
                case TEXT: return nineKeyVisible()?"九键 "+key.text+" "+label(key):!uppercase && !numeric && !emoji?key.text:label(key);
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
            return key.action==KeyboardLayout.Action.SHIFT && uppercase || key.action==KeyboardLayout.Action.SPELLING && spellingOpen
                    || key.action==KeyboardLayout.Action.RETURN && returnSelected();
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
    private boolean returnSelected() {
        if(snapshot.composing() || pending!=0) return false;
        if(buffer.isEnabled()) return buffer.blockCount()>0;
        EditorInfo info=getCurrentInputEditorInfo();
        if(info==null || (info.imeOptions&EditorInfo.IME_FLAG_NO_ENTER_ACTION)!=0) return false;
        switch(info.imeOptions&EditorInfo.IME_MASK_ACTION) {
            case EditorInfo.IME_ACTION_SEARCH:
            case EditorInfo.IME_ACTION_GO:
            case EditorInfo.IME_ACTION_SEND:
            case EditorInfo.IME_ACTION_NEXT:
            case EditorInfo.IME_ACTION_DONE: return true;
            default: return false;
        }
    }
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
        if(Build.VERSION.SDK_INT>=29) keyboard.setForceDarkAllowed(false);
        keyboard.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); chromeButtons.clear(); chordControls.clear();
        keyboard.setPadding(dp(5),dp(5),dp(5),dp(5));
        keyboard.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> { if(r-l!=or-ol) render(); });
        keyboard.setOnApplyWindowInsetsListener((view,insets) -> {
            int left,right,bottom;
            if(Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                left=safe.left; right=safe.right; bottom=safe.bottom;
            } else { left=insets.getSystemWindowInsetLeft(); right=insets.getSystemWindowInsetRight(); bottom=insets.getSystemWindowInsetBottom(); }
            view.setPadding(dp(5)+left,dp(5),dp(5)+right,dp(5)+bottom); return insets;
        });
        preedit=new TextView(this); preedit.setSingleLine(true); preedit.setTextSize(13); preedit.setGravity(Gravity.CENTER_VERTICAL);
        preedit.setPadding(dp(8),0,dp(8),0); keyboard.addView(preedit,new LinearLayout.LayoutParams(-1,dp(28)));
        bufferRow=new LinearLayout(this); bufferRow.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bufferParams=new LinearLayout.LayoutParams(-1,dp(landscape()?60:76)); bufferParams.bottomMargin=dp(4); keyboard.addView(bufferRow,bufferParams);
        int railHeight=landscape()?28:36;
        LinearLayout input=row(bufferRow,railHeight);
        themeButton=button(input,theme.glyph,this::toggleAppearance,0); fixedWidth(themeButton,32); gapRight(themeButton,4);
        themeButton.setContentDescription("布局与配色");
        bufferRail=new BufferRail(this); input.addView(bufferRail,new LinearLayout.LayoutParams(0,-1,1));
        insertNext=button(input,"↑",() -> insert(false),0); fixedWidth(insertNext,32); gapLeft(insertNext,4);
        ((KeyButton)insertNext).appearance(false,false,true); insertNext.setContentDescription(getString(R.string.insert_next)); insertNext.setOnLongClickListener(v -> { insert(true); return true; });
        LinearLayout details=new LinearLayout(this); bufferRow.addView(details,new LinearLayout.LayoutParams(-1,dp(railHeight)));
        KeyButton settings=button(details,"⚙",this::toggleAppearance,0); fixedWidth(settings,32); gapRight(settings,4); settings.setContentDescription("Buffer 设置");
        metrics=new TextView(this); metrics.setGravity(Gravity.CENTER); metrics.setTextSize(landscape()?14:16);
        metrics.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL)); details.addView(metrics,new LinearLayout.LayoutParams(0,-1,1));
        insertAll=button(details,"⇈",() -> insert(true),0); fixedWidth(insertAll,32); gapLeft(insertAll,4); insertAll.setContentDescription(getString(R.string.insert_all));
        candidateRow=row(keyboard,32);
        layoutButton=button(candidateRow,"⚙",this::toggleAppearance,0); fixedWidth(layoutButton,32); gapRight(layoutButton,4); layoutButton.setContentDescription("键位布局");
        previous=button(candidateRow,"‹",() -> candidatePage(false),0); fixedWidth(previous,32); ((KeyButton)previous).plain(true); previous.setContentDescription("上一页候选");
        candidateScroll=new HorizontalScrollView(this); candidateScroll.setFillViewport(false); candidateScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout strip=new LinearLayout(this); candidateScroll.addView(strip,new HorizontalScrollView.LayoutParams(-2,-1));
        FrameLayout center=new FrameLayout(this); candidateRow.addView(center,new LinearLayout.LayoutParams(0,-1,1));
        center.addView(candidateScroll,new FrameLayout.LayoutParams(-1,-1)); chordReadout=new ChordPreview(this); center.addView(chordReadout,new FrameLayout.LayoutParams(-1,-1)); candidates.clear();
        for(int i=0;i<9;i++) {
            final int index=i; KeyButton candidate=button(strip,"",() -> candidateTapped(index),0);
            candidate.setLayoutParams(new LinearLayout.LayoutParams(-2,-1)); candidate.fontStyle(false,20,false); candidate.plain(true);
            candidate.setMinWidth(dp(32)); candidate.setMinimumWidth(dp(32)); candidate.setPadding(dp(6),0,dp(6),0);
            gapRight(candidate,4);
            candidate.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE); candidate.setTextSize(20); candidates.add(candidate);
        }
        next=button(candidateRow,"›",() -> candidatePage(true),0); fixedWidth(next,32); ((KeyButton)next).plain(true); next.setContentDescription("下一页候选");
        bufferButton=button(candidateRow,"▤",() -> { if(pending==0 && !snapshot.composing() && retained.isEmpty()) { cancelChord(); buffer.setEnabled(!buffer.isEnabled()); render(); } },0);
        fixedWidth(bufferButton,32); gapLeft(bufferButton,4); ((KeyButton)bufferButton).appearance(false,false,true);
        spellingRow=row(keyboard,34); spellingButtons.clear();
        for(int i=0;i<9;i++) { final int index=i; KeyButton spelling=button(spellingRow,"",() -> chooseSpelling(spellingPage+index),1); spellingButtons.add(spelling); }
        retryButton=new KeyButton(this); chromeButtons.add((KeyButton)retryButton); retryButton.setText(R.string.retry);
        retryButton.setOnClickListener(v -> { if(failed) initialize(); else retryRetained(); }); keyboard.addView(retryButton,new LinearLayout.LayoutParams(-1,dp(44)));
        surfaceContainer=new FrameLayout(this);
        keys=new KeyboardSurface(this,keyHandler); surfaceContainer.addView(keys,new FrameLayout.LayoutParams(-1,-1));
        chords=new ChordSurface(this,new ChordSurface.Handler() {
            public void onChord(String code) { chordPreview=""; typeChord(code); }
            public void onKey(String text) { type(uppercase?text.toUpperCase(Locale.ROOT):text); }
            public void onPreview(ChordGesture.Preview preview) { heldPreview=preview; String value=preview==null?"":preview.combined!=null?preview.combined:(preview.left==null?"":preview.left.keys)+(preview.right==null?"":preview.right.keys);
                chordPreview=value; render(); }
            public void onControl(ChordLayout.Action action) { if(action==ChordLayout.Action.DELETE) delete(); else { settleAndSwitch(() -> { emoji=true; numeric=false; }); } }
            public String label(ChordLayout.Action action) { return action==ChordLayout.Action.DELETE?"⌫":"☺"; }
            public String description(ChordLayout.Action action) { return action==ChordLayout.Action.DELETE?getString(R.string.backspace):"表情"; }
        }); surfaceContainer.addView(chords,new FrameLayout.LayoutParams(-1,-1));
        appearancePanel=new KeyboardAppearancePanel(this,this::chooseLayout,id -> preferences.edit().putString("theme",id).apply());
        appearancePanel.schemes(schema,this::chooseSchema);
        appearancePanel.action("清空 Buffer",getString(R.string.clear),() -> { if(pending==0 && !snapshot.composing()) { buffer.clear(); retryRetained(); render(); } });
        appearancePanel.action("🌐 系统键盘",getString(R.string.switch_keyboard),() -> { endTarget(); getSystemService(InputMethodManager.class).showInputMethodPicker(); });
        surfaceContainer.addView(appearancePanel,new FrameLayout.LayoutParams(-1,-1));
        keyboard.addView(surfaceContainer,new LinearLayout.LayoutParams(-1,dp(KeyboardLayout.height(landscape()))));
        chordFooter=row(keyboard,landscape()?34:40); ((LinearLayout.LayoutParams)chordFooter.getLayoutParams()).bottomMargin=0;
        KeyboardLayout.Action[] actions={KeyboardLayout.Action.NUMBERS,KeyboardLayout.Action.SHIFT,KeyboardLayout.Action.SPACE,KeyboardLayout.Action.LANGUAGE,KeyboardLayout.Action.RETURN};
        for(KeyboardLayout.Action action:actions) {
            KeyButton button=button(chordFooter,"",() -> chordControl(action),action==KeyboardLayout.Action.SPACE?3.5f:1);
            button.classic(true); button.fontStyle(false,action==KeyboardLayout.Action.SHIFT?17:14,true); button.appearance(action!=KeyboardLayout.Action.SPACE,true,action==KeyboardLayout.Action.RETURN); if(!chordControls.isEmpty()) gapLeft(button,2); chordControls.add(button);
        }
        rebuildKeys(); render(); return keyboard;
    }
    private void chordControl(KeyboardLayout.Action action) {
        cancelChord();
        switch(action) { case NUMBERS:toggleNumbers();break; case SHIFT:settleAndSwitch(() -> uppercase=!uppercase);break;
            case LANGUAGE:toggleLanguage();break; case SPACE:type(" ");break; case RETURN:enter();break; default:break; }
    }
    private void gapLeft(View view,int gap) { ((LinearLayout.LayoutParams)view.getLayoutParams()).leftMargin=dp(gap); }
    private void gapRight(View view,int gap) { ((LinearLayout.LayoutParams)view.getLayoutParams()).rightMargin=dp(gap); }
    private boolean landscape() { return getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE; }
    private void rebuildKeys() { if(keys!=null) keys.render(visibleMode(),theme); }
    private void candidateTapped(int index) {
        if(!chordPreview.isEmpty()) return;
        if(punctuationOpen || !snapshot.composing()) {
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
        setText(themeButton,theme.glyph); setText(layoutButton,appearanceOpen?"✓":"⚙");
        layoutButton.setSelected(appearanceOpen);

        bufferButton.setContentDescription(getString(buffer.isEnabled()?R.string.buffer_on:R.string.buffer_off)); bufferButton.setSelected(buffer.isEnabled());
        bufferButton.setEnabled(buffer.isPermitted() && pending==0 && !snapshot.composing() && retained.isEmpty() && !chords.isChordActive());
        String status=failed?getString(R.string.engine_failed):!ready?getString(R.string.engine_loading):"";
        if(!retained.isEmpty()) status=getString(R.string.delivery_pending);
        setText(preedit,status); preedit.setTextColor(palette.accentText); preedit.setVisibility(!directOnly && !status.isEmpty()?View.VISIBLE:View.GONE);
        boolean show=ready && !directOnly && !english && snapshot.composing();
        spellingChoices=nineKeyVisible()?spellings.choices(snapshot.raw):java.util.Collections.emptyList();
        if(spellingChoices.isEmpty()) spellingOpen=false;
        if(spellingPage>=spellingChoices.size()) spellingPage=0;
        candidateRow.setVisibility(View.VISIBLE);
        candidateScroll.setVisibility(heldPreview==null?View.VISIBLE:View.GONE); chordReadout.setVisibility(heldPreview==null?View.GONE:View.VISIBLE);
        chordReadout.render(heldPreview,theme,landscape());
        boolean marks=punctuationOpen || !show;
        int count=directOnly?0:!chordPreview.isEmpty()?1:marks?MARKS.length:snapshot.candidates.size();
        for(int i=0;i<9;i++) {
            Button item=candidates.get(i); boolean exists=i<count;
            item.setVisibility(exists?View.VISIBLE:View.GONE);
            if(exists) {
                String value=!chordPreview.isEmpty()?chordPreview:marks?(english || numeric || emoji?MARKS[i]:MARK_LABELS[i]):snapshot.candidates.get(i);
                setText(item,value);
                item.setContentDescription(marks?MARKS[i]:"候选"+(i+1)+" "+value);
            }
            item.setEnabled(exists && pending==0 && chordPreview.isEmpty());
        }
        if(renderedPage!=snapshot.pageStart || !renderedRaw.equals(snapshot.raw)) {
            candidateScroll.scrollTo(0,0); renderedPage=snapshot.pageStart; renderedRaw=snapshot.raw;
        }
        previous.setVisibility(marks && !spellingOpen || directOnly || heldPreview!=null || !spellingOpen && snapshot.pageStart==0?View.GONE:View.VISIBLE); next.setVisibility(marks && !spellingOpen || directOnly || heldPreview!=null?View.GONE:View.VISIBLE);
        previous.setEnabled(pending==0 && (spellingOpen?spellingPage>0:snapshot.pageStart>0));
        next.setEnabled(pending==0 && (spellingOpen?spellingPage+9<spellingChoices.size():!snapshot.lastPage));
        bufferRow.setVisibility(buffer.isEnabled()?View.VISIBLE:View.GONE);
        if(buffer.isEnabled()) {
            bufferRail.render(buffer.blocks(),snapshot.preedit,theme,landscape());
            String text=buffer.text(); setText(metrics,text.codePointCount(0,text.length())+" 字 · "+buffer.blockCount()+" 块");
        }
        metrics.setTextColor(palette.ink);
        if(metrics.getTag()==null || !metrics.getTag().equals(palette.ink)) { android.graphics.drawable.GradientDrawable output=new android.graphics.drawable.GradientDrawable(); output.setColor((palette.ink&0xffffff)|0x10000000); output.setCornerRadius(dp(7)); metrics.setBackground(output); metrics.setTag(palette.ink); }
        spellingRow.setVisibility(spellingOpen?View.VISIBLE:View.GONE);
        for(int i=0;i<9;i++) { KeyButton key=spellingButtons.get(i); boolean exists=spellingPage+i<spellingChoices.size(); key.setVisibility(exists?View.VISIBLE:View.GONE);
            if(exists) { String value=spellingChoices.get(spellingPage+i); setText(key,value); key.setContentDescription("拼音 "+value); } key.setEnabled(exists && pending==0); }
        insertNext.setEnabled(pending==0 && !snapshot.composing() && buffer.blockCount()>0 && !chords.isChordActive()); insertAll.setEnabled(insertNext.isEnabled());
        retryButton.setVisibility(failed || !retained.isEmpty()?View.VISIBLE:View.GONE);
        boolean chord=chordVisible();
        float width=(keyboard.getWidth()>0?keyboard.getWidth():getResources().getDisplayMetrics().widthPixels)-keyboard.getPaddingLeft()-keyboard.getPaddingRight();
        float surfaceHeight=chord?ChordLayout.height(Math.max(1,width/getResources().getDisplayMetrics().density),"splitOrthogonal".equals(layout)):KeyboardLayout.height(landscape());
        if(chord && appearanceOpen) surfaceHeight+=landscape()?35:41;
        int desiredHeight=Math.round(surfaceHeight*getResources().getDisplayMetrics().density);
        if(surfaceContainer.getLayoutParams().height!=desiredHeight) { surfaceContainer.getLayoutParams().height=desiredHeight; surfaceContainer.requestLayout(); }
        keys.render(visibleMode(),theme); keys.setVisibility(appearanceOpen || chord?View.GONE:View.VISIBLE);
        chords.render("splitOrthogonal".equals(layout),!english && !uppercase,uppercase,theme); chords.setVisibility(appearanceOpen || !chord?View.GONE:View.VISIBLE);
        chordFooter.setVisibility(!appearanceOpen && chord?View.VISIBLE:View.GONE);
        ((LinearLayout.LayoutParams)surfaceContainer.getLayoutParams()).bottomMargin=chord && !appearanceOpen?dp(1):0;
        String[] footerLabels={"123",uppercase?"⇪":"⇧","空格",english?"EN":"中",returnLabel()};
        String[] footerDescriptions={"数字与字母","Shift",getString(R.string.space),"中英切换",getString(R.string.enter)};
        for(int i=0;i<chordControls.size();i++) {
            KeyButton button=chordControls.get(i); setText(button,footerLabels[i]); button.setContentDescription(footerDescriptions[i]);
            LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)button.getLayoutParams(); int keyWidth=i==2?0:Math.round((width/getResources().getDisplayMetrics().density-10)/7.5f*getResources().getDisplayMetrics().density);
            float weight=i==2?1:0; if(params.width!=keyWidth || params.weight!=weight) { params.width=keyWidth; params.weight=weight; button.requestLayout(); }
            button.setEnabled(!chords.isChordActive());
            button.setSelected(i==1 && uppercase || i==3 && english || i==4 && returnSelected());
        }
        insertNext.setSelected(insertNext.isEnabled());
        if(appearanceOpen) appearancePanel.schemes(schema,this::chooseSchema);
        appearancePanel.setVisibility(appearanceOpen?View.VISIBLE:View.GONE);
        if(appearanceOpen) appearancePanel.render(layout,theme);
    }
    private static void setText(TextView view,String value) {
        if(!android.text.TextUtils.equals(view.getText(),value)) view.setText(value);
    }
    private LinearLayout row(LinearLayout parent,int height) {
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(height)); params.bottomMargin=dp(4); parent.addView(row,params); return row;
    }
    private KeyButton button(LinearLayout row,String text,Runnable action,float weight) {
        KeyButton button=new KeyButton(this); button.setText(text); button.font(15); button.appearance(false,false,false);
        button.setOnClickListener(v -> action.run()); row.addView(button,new LinearLayout.LayoutParams(0,-1,weight)); chromeButtons.add(button); return button;
    }
    private void fixedWidth(View view,int width) { view.setLayoutParams(new LinearLayout.LayoutParams(dp(width),-1)); }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
}
