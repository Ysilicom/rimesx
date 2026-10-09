package org.scholay.rimes.android;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.KeyEvent;
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
import org.scholay.rimes.core.EditorKind;
import org.scholay.rimes.core.InputEpoch;
import org.scholay.rimes.core.PluginSession;
import org.scholay.rimes.core.RimeEngine;
import org.scholay.rimes.core.KeyboardLayout;
import org.scholay.rimes.core.NineKeyPinyin;
import org.scholay.rimes.core.Punctuation;
import org.scholay.rimes.core.ChordGesture;
import org.scholay.rimes.core.ChordLayout;
import org.scholay.rimes.core.SmartCorrector;

/** All host mutations use the exact live InputConnection; engine results carry an editor lease. */
public final class RimesInputMethodService extends InputMethodService {
    private final BufferSession buffer=new BufferSession();
    private final InputEpoch epoch=new InputEpoch();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayDeque<Integer> expectedSelections=new ArrayDeque<>();
    private InputConnection target;
    private SharedPreferences preferences;
    private KeyboardSettings settings;
    private OpenAiSettings cometSettings;
    private OpenAiSettings.Snapshot cometProfile=OpenAiSettings.disabled();
    private String translationDirection="auto";
    private boolean aiMockEnabled=true,learningEnabled=true,mixedEnglish=true,changingSettingsPair;
    private KeyboardSettings.Snapshot deferredSettingsPair;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener=this::preferenceChanged;
    private KeyboardRoot keyboard;
    private LinearLayout bufferRow, candidateRow, candidateBox, spellingRow, chordFooter;
    private BufferRail bufferRail,pluginOutput;
    private final PluginSession pluginSession=new PluginSession();
    private BufferPluginExecutor pluginExecutor;
    private OfficialPluginStore officialPlugins;
    private SharedPreferences officialPluginPreferences;
    private final SharedPreferences.OnSharedPreferenceChangeListener officialPluginListener=(prefs,key) -> main.post(() -> {
        if(this.destroyed) return;
        cancelChord(); invalidatePlugin();
        if(this.activePlugin!=null && !officialPlugins.enabled(this.activePlugin)) { this.activePlugin=null; pluginSession.clear(); this.pluginSettingsOpen=false; }
        resetEngine(); render();
    });
    private BufferPluginExecutor.Job pluginJob;
    private ChordSurface chords;
    private TextView metrics;
    private final List<KeyButton> spellingButtons=new ArrayList<>();
    private final List<KeyButton> chordControls=new ArrayList<>();
    private String chordPreview="",hostPreedit="";
    private KeyboardSurface keys;
    private KeyboardAppearancePanel appearancePanel;
    private PluginShortcutBar pluginShortcuts;
    private BufferPluginPanel pluginPanel;
    private String activePlugin;
    private String pluginResultAuthorization;
    private boolean pluginSettingsOpen, renderedPluginMode;
    private LinearLayout bufferTop,bufferBottom;
    private KeyButton bufferSettings,pluginButton,pluginRunButton;
    private FrameLayout surfaceContainer;
    private KeyButton themeButton,layoutButton,hideKeyboardButton;
    private final List<KeyButton> chromeButtons=new ArrayList<>();
    private KeyboardTheme theme=KeyboardTheme.ALL[0];
    private String layout="qwerty";
    private float heightFactor=1.0f;
    private int heightPercent=100;
    private int bottomInset=0;
    private long lastSpaceTime=0;
    private boolean visiblePassword, noSuggestions, asciiOnly, emailField, englishBeforeField, noLearning, passwordField;
    /** Letters already in the box that an English suggestion may replace. */
    private String englishDraft="";
    private static final int DRAFT_NONE=0, DRAFT_APPEND=1, DRAFT_CLEAR=2, DRAFT_BACKSPACE=3, DRAFT_REPLACE=4;
    private boolean symbols,emoji,appearanceOpen,spellingOpen,punctuationOpen,clipboardOpen,candidateGridOpen;
    private ClipboardPanel clipboardPanel;
    private CandidateGridPanel candidateGridPanel;
    private Button candidateGridButton;
    private ClipboardStore clipboardStore;
    private int spellingPage;
    private NineKeyPinyin spellings=new NineKeyPinyin(java.util.Collections.emptyList());
    private List<String> spellingChoices=java.util.Collections.emptyList();
    private PunctuationWeights punctuationWeights;
    /** Marks drawn in the top row. Empty unless that row is the weight row. */
    private List<String> weightMarks=java.util.Collections.emptyList();
    private boolean weightRowOpen;
    /** English and direct fields use the halfwidth twin. 、 stays 、. */
    private boolean halfwidthMarks() { return english || directOnly; }
    private String shapedMark(String text) { return Punctuation.face(text,halfwidthMarks()); }
    /** ASCII twin the schema punctuator already maps. Other marks have no twin. */
    private static String punctuatorKey(String mark) {
        switch(mark) {
            case "，": return ",";
            case "。": return ".";
            case "？": return "?";
            case "！": return "!";
            case "：": return ":";
            case "；": return ";";
            default: return mark;
        }
    }
    /** Count the mark that lands, then type it. A hidden password does not move the weight row. */
    private void typePunctuation(String engineText,String counted) {
        if(!ownsTarget() && !adoptCurrentConnection()) return;
        if(punctuationWeights!=null && !passwordField) punctuationWeights.note(counted,halfwidthMarks());
        type(engineText);
    }
    private TextView preedit;
    private HorizontalScrollView candidateScroll;
    private LinearLayout candidateStrip;
    private ChordPreview chordReadout;
    private ChordGesture.Preview heldPreview;
    /** Visible candidate words. The same list keeps its horizontal scroll. */
    private String renderedCandidates="";
    private int renderedPage=-1;
    private Button bufferButton, retryButton, previous, next, insertNext;
    private final List<KeyButton> candidates=new ArrayList<>();
    private boolean uppercase, numeric, directOnly, privateField, english, ready, failed, destroyed;
    /** The bound field can switch 中/英. A typeless restart keeps this, and keeps a password lock. */
    private boolean textFieldLive;
    private boolean hostComposing;
    private int pending, selection=-1, selectionStart=-1, composingStart=-1;
    private String schema="rimes_pinyin", retained="";
    /** Last key-face inputs. A matching stamp means this pass only refreshes pinyin and candidates. */
    private String renderedKeyStamp="";
    private final ArrayDeque<Result> retainedResults=new ArrayDeque<>();
    private RimeEngine.Snapshot snapshot=RimeEngine.Snapshot.EMPTY;
    // Worker-owned state. Access only inside EngineWorker.QUEUE.
    private RimeEngine engine;
    private long session;
    /** Schema id loaded in the live session. Empty until the first successful select. */
    private String loadedSchema="";
    private int lastTypedChar;
    private float lastBiasX, lastBiasY;
    private static final String[] SCHEMAS={"rimes_pinyin","rimes_ziranma","rimes_flypy","rimes_wubi"};
    private static final String[] NAMES={"拼音","自然码","小鹤","五笔"};

    @Override public void onCreate() {
        super.onCreate();
        officialPlugins=new OfficialPluginStore(this);
        officialPluginPreferences=getSharedPreferences(OfficialPluginStore.PREFERENCES,MODE_PRIVATE);
        officialPluginPreferences.registerOnSharedPreferenceChangeListener(officialPluginListener);
        preferences=getSharedPreferences(KeyboardSettings.PREFERENCES_NAME,MODE_PRIVATE);
        settings=new KeyboardSettings(preferences);
        punctuationWeights=PunctuationWeights.open(this);
        clipboardStore=new ClipboardStore(this);
        // The application context is tied to the default device clipboard.
        // The IME window context can report another device id, and then
        // getPrimaryClip() comes back null even though the system clip is set.
        clipboardManager=(ClipboardManager)getApplicationContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if(clipboardManager!=null) clipboardManager.addPrimaryClipChangedListener(clipboardListener);
        cometSettings=new OpenAiSettings(this);
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        restoreSettings();
        pluginExecutor=new BufferPluginExecutor(getApplicationContext());
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
        bindInput(info,restarting);
    }
    private void configure(EditorInfo info) {
        restoreSettings();
        int kind=info.inputType&InputType.TYPE_MASK_CLASS;
        numeric=kind==InputType.TYPE_CLASS_NUMBER || kind==InputType.TYPE_CLASS_PHONE || kind==InputType.TYPE_CLASS_DATETIME;
        visiblePassword=kind==InputType.TYPE_CLASS_TEXT
                && (info.inputType&InputType.TYPE_MASK_VARIATION)==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        directOnly=EditorKind.directOnly(info.inputType);
        textFieldLive=!directOnly;
        noLearning=(info.imeOptions&EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)!=0;
        passwordField=EditorKind.password(info.inputType);
        privateField=!allowsBuffer(info);
        int textFlags=info.inputType&InputType.TYPE_MASK_FLAGS;
        noSuggestions=kind==InputType.TYPE_CLASS_TEXT && (textFlags&InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)!=0;
        boolean nextEmail=isEmailOrUri(info);
        if(nextEmail && !emailField) { englishBeforeField=english; english=true; }
        else if(!nextEmail && emailField) english=englishBeforeField;
        emailField=nextEmail;
        if(EditorKind.enterAscii(asciiOnly,info.imeOptions)) english=true;
        asciiOnly=EditorKind.asciiRequested(info.imeOptions);
        englishDraft="";
        uppercase=false; symbols=false; emoji=false; appearanceOpen=false; spellingOpen=false; punctuationOpen=false; clipboardOpen=false; candidateGridOpen=false;
        selection=info.initialSelEnd; selectionStart=info.initialSelStart;
        buffer.beginTarget(target!=null && allowsBuffer(info));
        resetEngine(); rebuildKeys(); render();
    }
    @Override public void onStartInputView(EditorInfo info,boolean restarting) {
        super.onStartInputView(info,restarting);
        if(target==null) { target=getCurrentInputConnection(); bindInput(info,restarting || textFieldLive); }
        render();
    }
    @Override public void onWindowShown() {
        super.onWindowShown();
        // Read after the window is up. Reading during onStartInputView blocks the
        // first frames, and that early read is often empty anyway.
        android.view.Window window=getWindow()==null?null:getWindow().getWindow();
        if(window!=null) window.getDecorView().post(this::captureClipboard);
        else main.post(this::captureClipboard);
    }
    private String effectiveSchema() {
        // Suggestions use the English table. Direct English, including Haven Secure, never queries it.
        if(englishComposingField()) return "rimes_english";
        String base=chordLayout()?"rimes_ziranma":nineKeyEngine()?"rimes_pinyin9":schema;
        if(mixedEnglish && ("rimes_pinyin".equals(base) || "rimes_ziranma".equals(base) || "rimes_flypy".equals(base))) base+="_mix";
        if(privateField || noLearning || !learningEnabled) base+="_private";
        return base;
    }
    /**
     * Drop unfinished spelling. Reload the dictionary only when its id changed,
     * so opening another field does not select the same schema again.
     */
    private void resetEngine() {
        if(!ready) return;
        final String selected=effectiveSchema();
        final boolean active=target!=null;
        final InputEpoch.Ticket ticket=epoch.issue();
        EngineWorker.QUEUE.execute(() -> {
            lastTypedChar=0;
            if(!active) {
                if(session!=0) engine.clearComposition(session);
                return;
            }
            if(session!=0 && selected.equals(loadedSchema)) {
                engine.clearComposition(session);
                return;
            }
            if(session!=0) engine.destroySession(session);
            session=engine.createSession();
            loadedSchema="";
            if(session==0 || !engine.selectSchema(session,selected)) {
                main.post(() -> { if(epoch.current(ticket)) engineFailure(); });
                return;
            }
            loadedSchema=selected;
        });
    }
    /** Release the session when the process is going away. Field changes keep it. */
    private void releaseEngine() {
        EngineWorker.QUEUE.execute(() -> {
            if(session!=0 && engine!=null) engine.destroySession(session);
            session=0; loadedSchema="";
        });
    }
    private void engineFailure() { if(!destroyed) { failed=true; ready=false; render(); } }
    @Override public boolean onEvaluateFullscreenMode() { return false; }
    @Override public void onFinishInputView(boolean finishingInput) { endTarget(); super.onFinishInputView(finishingInput); }
    @Override public void onFinishInput() { textFieldLive=false; asciiOnly=false; passwordField=false; endTarget(); super.onFinishInput(); }
    @Override public void onUnbindInput() { textFieldLive=false; asciiOnly=false; passwordField=false; endTarget(); super.onUnbindInput(); }
    @Override public void onDestroy() {
        if(clipboardManager!=null) clipboardManager.removePrimaryClipChangedListener(clipboardListener);
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        officialPluginPreferences.unregisterOnSharedPreferenceChangeListener(officialPluginListener);
        endTarget(); destroyed=true; releaseEngine(); pluginExecutor.close(); super.onDestroy();
    }
    private void restoreSettings() {
        KeyboardSettings.Snapshot saved=settings.snapshot();
        cometProfile=cometSettings.snapshot();
        schema=saved.schema; layout=saved.layout; theme=KeyboardTheme.named(saved.theme);
        heightFactor=saved.heightFactor;
        heightPercent=saved.heightPercent; bottomInset=saved.bottomInset;
        applyBottomInset();
        translationDirection=saved.translationDirection; aiMockEnabled=saved.aiMockEnabled; learningEnabled=saved.learning; mixedEnglish=saved.mixedEnglish;
    }
    private void changeSettingsPair(Runnable write) {
        changingSettingsPair=true;
        try { write.run(); KeyboardSettings.Snapshot saved=settings.snapshot(); schema=saved.schema; layout=saved.layout; }
        finally { changingSettingsPair=false; }
    }
    private void applySettingsPair(KeyboardSettings.Snapshot saved) {
        if(saved.schema.equals(schema) && saved.layout.equals(layout)) { deferredSettingsPair=null; return; }
        // A failed host insertion must be retried with its original route before switching modes.
        if(!retained.isEmpty()) { deferredSettingsPair=saved; return; }
        deferredSettingsPair=null;
        boolean layoutChanged=!layout.equals(saved.layout);
        Runnable change=() -> {
            schema=saved.schema; layout=saved.layout; spellingOpen=false;
            if(layoutChanged) { numeric=false; symbols=false; emoji=false; uppercase=false; if(chordLayout() || layout.equals("nineKey")) english=false; }
        };
        if(ownsTarget()) settleAndSwitch(change); else { cancelChord(); change.run(); render(); }
    }
    private boolean chordLayout() { return officialPlugins!=null && officialPlugins.enabled("chord") && (layout.equals("orthogonal") || layout.equals("splitOrthogonal")); }
    private boolean chordVisible() { return ready && chordLayout() && !directOnly && !numeric && !emoji; }
    private void cancelChord() {
        if(keyboard!=null) keyboard.cancelPendingInputEvents();
        heldPreview=null; chordPreview=""; if(chords!=null) chords.cancel();
    }
    private void chooseSchema(String selected) {
        settleAndSwitch(() -> { changeSettingsPair(() -> settings.setSchema(selected)); spellingOpen=false; });
    }
    private boolean nineKeyEngine() { return layout.equals("nineKey") && schema.equals("rimes_pinyin"); }
    private boolean nineKeyVisible() { return ready && nineKeyEngine() && !directOnly && !english && !numeric && !emoji && !uppercase; }
    private void preferenceChanged(SharedPreferences changed,String key) {
        if(destroyed || changingSettingsPair) return;
        KeyboardSettings.Snapshot saved=settings.snapshot();
        if(OpenAiSettings.KEY.equals(key)) {
            cometProfile=cometSettings.snapshot(); invalidatePlugin(); render(); return;
        }
        if(KeyboardSettings.KEY_THEME.equals(key)) { theme=KeyboardTheme.named(saved.theme); render(); return; }
        if(KeyboardSettings.KEY_HEIGHT_PERCENT.equals(key) || KeyboardSettings.KEY_HEIGHT_SCALE.equals(key)) {
            if(heightPercent==saved.heightPercent && Math.abs(heightFactor-saved.heightFactor)<0.001f) return;
            heightPercent=saved.heightPercent; heightFactor=saved.heightFactor; render(); return;
        }
        if(KeyboardSettings.KEY_SCHEMA.equals(key) || KeyboardSettings.KEY_LAYOUT.equals(key)) {
            // Both per-key notifications see the same atomic pair. Settle the old code only once.
            applySettingsPair(saved);
            return;
        }
        if(KeyboardSettings.KEY_TRANSLATION_DIRECTION.equals(key)) {
            if(!saved.translationDirection.equals(translationDirection)) {
                translationDirection=saved.translationDirection;
                if("translate".equals(activePlugin)) { invalidatePlugin(); scheduleAutoPlugin(); }
                render();
            }
            return;
        }
        if(KeyboardSettings.KEY_AI_MOCK_ENABLED.equals(key)) {
            if(saved.aiMockEnabled!=aiMockEnabled) {
                aiMockEnabled=saved.aiMockEnabled;
                if(activePlugin!=null && !"translate".equals(activePlugin)) invalidatePlugin();
                render();
            }
            return;
        }
        if(KeyboardSettings.KEY_LEARNING.equals(key)) {
            if(saved.learning==learningEnabled) return;
            learningEnabled=saved.learning;
            // A private field already uses the private schema, so this switch does not change it.
            if(!ready || !ownsTarget() || privateField || noLearning) return;
            switchInputSchema();
            return;
        }
        if(!KeyboardSettings.KEY_MIXED_ENGLISH.equals(key) || saved.mixedEnglish==mixedEnglish) return;
        mixedEnglish=saved.mixedEnglish;
        if(!ready || !ownsTarget()) return;
        switchInputSchema();
    }
    /** Settle unfinished code, then select the schema for the current learning and mixed-English policy. */
    private void switchInputSchema() {
        final String selected=effectiveSchema();
        dispatch(() -> {
            Result settled=literal("");
            if(session!=0 && !engine.selectSchema(session,selected)) throw new IllegalStateException("Cannot change input schema");
            if(session!=0) loadedSchema=selected;
            return settled;
        },true);
    }
    private void endTarget() {
        deferredSettingsPair=null;
        cancelPlugin(); pluginSession.clear();
        // Clear the old composition before revoking its connection, never through the new target.
        if(target!=null && target==getCurrentInputConnection() && hostComposing) { target.setComposingText("",1); target.finishComposingText(); }
        cancelChord(); appearanceOpen=false; pluginSettingsOpen=false; clipboardOpen=false; candidateGridOpen=false; activePlugin=null; spellingOpen=false; punctuationOpen=false;
        hostPreedit=""; target=null; hostComposing=false; composingStart=-1; selection=-1; selectionStart=-1;
        epoch.revoke(); pending=0; expectedSelections.clear(); snapshot=RimeEngine.Snapshot.EMPTY; retained=""; retainedResults.clear(); englishDraft="";
        buffer.finishTarget(); if(metrics!=null) { metrics.setText(""); metrics.setContentDescription(null); } if(bufferRail!=null) bufferRail.clearProjection(); if(pluginOutput!=null) pluginOutput.clearProjection(); resetEngine(); render();
    }
    private boolean englishComposingField() {
        return english && !directOnly && !visiblePassword && !numeric && !emoji && !noSuggestions && !asciiOnly && !emailField;
    }
    private boolean englishSuggesting() {
        // Letters are already in the box. The strip only offers a replacement for the current word.
        return englishComposingField() && (buffer==null || !buffer.isEnabled());
    }
    private boolean englishImmediate() {
        return english && !englishSuggesting() && (visiblePassword || noSuggestions || asciiOnly || emailField);
    }
    static boolean isEmailOrUri(EditorInfo info) {
        if(info==null || (info.inputType&InputType.TYPE_MASK_CLASS)!=InputType.TYPE_CLASS_TEXT) return false;
        int variation=info.inputType&InputType.TYPE_MASK_VARIATION;
        return variation==InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || variation==InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                || variation==InputType.TYPE_TEXT_VARIATION_URI;
    }
    static boolean isPassword(EditorInfo info) {
        return EditorKind.password(info.inputType);
    }
    static boolean allowsBuffer(EditorInfo info) {
        return EditorKind.showsTools(info.inputType,info.imeOptions);
    }
    private boolean ownsTarget() { return !destroyed && target!=null && target==getCurrentInputConnection(); }
    /** Attach the current editor. A typeless restart keeps the field already bound. */
    private void bindInput(EditorInfo info,boolean allowKeep) {
        int type=info==null?EditorKind.TYPE_NULL:info.inputType;
        if(EditorKind.keepTypelessRestart(allowKeep,type,textFieldLive,directOnly)) { resetEngine(); render(); return; }
        if(info==null) { resetEngine(); render(); return; }
        configure(info);
    }
    /** Haven replaces the connection while the keyboard stays up. Adopt it instead of dropping the session. */
    private boolean adoptCurrentConnection() {
        if(destroyed) return false;
        InputConnection current=getCurrentInputConnection();
        if(current==null || current==target) return false;
        target=current;
        bindInput(getCurrentInputEditorInfo(),true);
        return ownsTarget();
    }

    private interface Operation { Result run(); }
    private static final class Result {
        final RimeEngine.Snapshot state;
        final String text;
        final boolean block;
        final int action; // 0 text/snapshot, 1 host delete, 2 host Return
        final int draft;
        Result(RimeEngine.Snapshot state,String text,boolean block,int action) { this(state,text,block,action,DRAFT_NONE); }
        Result(RimeEngine.Snapshot state,String text,boolean block,int action,int draft) {
            this.state=state; this.text=text; this.block=block; this.action=action; this.draft=draft;
        }
        static Result state(RimeEngine.Snapshot state) { return new Result(state,state.commit,true,0); }
    }
    private void dispatch(Operation operation) {
        dispatch(operation,false);
    }
    private void dispatch(Operation operation,boolean policyChange) {
        if(!ownsTarget() && !adoptCurrentConnection()) return;
        if(!policyChange && !retained.isEmpty()) { notice(R.string.delivery_pending); return; }
        InputEpoch.Ticket ticket=epoch.issue(); InputConnection connection=target;
        pending++;
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
        if(buffer.isEnabled() && (!result.text.isEmpty() || result.state.composing() && !snapshot.composing())) invalidatePlugin();
        String committed=result.text;
        if(result.draft==DRAFT_REPLACE) {
            committed=matchDraftCase(committed);
            if(!deleteDraft()) return false;
            englishDraft="";
        }
        if(!committed.isEmpty() && !deliver(committed,result.block)) return false;
        if(result.draft==DRAFT_APPEND) englishDraft+=committed;
        else if(result.draft==DRAFT_CLEAR) englishDraft="";
        snapshot=result.state;
        if(result.action==1) deleteHostOrBuffer();
        if(result.action==2) returnHostOrBuffer();
        if(result.draft==DRAFT_BACKSPACE && !englishDraft.isEmpty()) englishDraft=englishDraft.substring(0,englishDraft.offsetByCodePoints(englishDraft.length(),-1));
        updateComposition(); return true;
    }
    private RimeEngine.Snapshot state() { return session==0 ? RimeEngine.Snapshot.EMPTY : engine.snapshot(session); }
    private Result literal(String text) {
        lastTypedChar=0;
        RimeEngine.Snapshot before=state();
        if(before.composing()) engine.clearComposition(session);
        return new Result(RimeEngine.Snapshot.EMPTY,before.raw+text,false,0);
    }
    private String shapeEnglishLetter(String text) {
        if(!english || text.length()!=1 || !Character.isLetter(text.charAt(0)) || text.charAt(0)>127) return text;
        if(!uppercase) return text;
        return text.toUpperCase(Locale.ROOT);
    }
    private String matchDraftCase(String text) {
        if(englishDraft.isEmpty() || text.isEmpty()) return text;
        if(!Character.isUpperCase(englishDraft.charAt(0)) || !Character.isLowerCase(text.charAt(0))) return text;
        return Character.toUpperCase(text.charAt(0))+text.substring(1);
    }
    private boolean deleteDraft() {
        if(englishDraft.isEmpty() || !ownsTarget()) return englishDraft.isEmpty();
        int units=englishDraft.length();
        expect(selection>units?selection-units:0);
        try { return target.deleteSurroundingText(units,0); }
        catch(Throwable ignored) { return false; }
    }
    /** Commit the typed letter now, and keep English words only as replacements. */
    private Result suggestEnglish(String text) {
        boolean letter=text.length()==1 && Character.isLetter(text.charAt(0)) && text.charAt(0)<128;
        if(letter) {
            RimeEngine.Snapshot after=engine.processKey(session,Character.toLowerCase(text.charAt(0)));
            if(!after.composing()) return new Result(RimeEngine.Snapshot.EMPTY,text,false,0,DRAFT_CLEAR);
            return new Result(expandSnapshot(after),text,false,0,DRAFT_APPEND);
        }
        if(" ".equals(text) && state().composing()) {
            RimeEngine.Snapshot before=state();
            if(before.candidates.isEmpty()) {
                engine.clearComposition(session);
                return new Result(RimeEngine.Snapshot.EMPTY," ",false,0,DRAFT_CLEAR);
            }
            RimeEngine.Snapshot chosen=expandSnapshot(engine.selectCandidate(session,before.pageStart));
            String word=chosen.commit.isEmpty()?before.candidates.get(0):chosen.commit;
            if(state().composing()) engine.clearComposition(session);
            return new Result(RimeEngine.Snapshot.EMPTY,word+" ",false,0,DRAFT_REPLACE);
        }
        if(state().composing()) engine.clearComposition(session);
        return new Result(RimeEngine.Snapshot.EMPTY,text,false,0,DRAFT_CLEAR);
    }
    private RimeEngine.Snapshot expandSnapshot(RimeEngine.Snapshot snapshot) {
        if(session==0 || snapshot==null || !snapshot.composing() || snapshot.candidates.isEmpty() || snapshot.candidates.size()>=18 || snapshot.lastPage) return snapshot;
        java.util.ArrayList<String> allCandidates=new java.util.ArrayList<>(snapshot.candidates);
        java.util.ArrayList<String> allComments=new java.util.ArrayList<>(snapshot.comments);
        int pages=0;
        while(pages<5) {
            RimeEngine.Snapshot next=engine.processKey(session,0xff56);
            if(next.candidates.isEmpty() || next.pageStart<=snapshot.pageStart) break;
            allCandidates.addAll(next.candidates);
            allComments.addAll(next.comments);
            pages++;
            if(next.lastPage) break;
        }
        for(int i=0;i<pages;i++) engine.processKey(session,0xff55);
        return new RimeEngine.Snapshot(snapshot.handled,snapshot.raw,snapshot.preedit,snapshot.caret,snapshot.commit,
                allCandidates.toArray(new String[0]),allComments.toArray(new String[0]),0,snapshot.highlighted,true);
    }
    private void type(String text) {
        type(text,0f,0f);
    }
    private void type(String text,float biasX,float biasY) {
        if(!ownsTarget() && !adoptCurrentConnection()) return;
        invalidatePlugin();
        if(!ready) {
            if(!retained.isEmpty()) { notice(R.string.delivery_pending); return; }
            Result result=new Result(RimeEngine.Snapshot.EMPTY,text,false,0);
            if(!applyResult(result)) { retainedResults.add(result); retained="pending"; notice(buffer.isEnabled()?R.string.buffer_limit:R.string.delivery_pending); }
            render(); return;
        }
        final boolean compose=!directOnly && !numeric && !emoji;
        final boolean suggest=englishSuggesting();
        final boolean immediate=englishImmediate();
        final String shaped=shapeEnglishLetter(text);
        dispatch(() -> {
            if(suggest) return suggestEnglish(shaped);
            // Haven Secure, no-suggestion fields, email, URI, and ASCII-only fields commit English at once.
            if(immediate || !compose || session==0 || text.codePointAt(0)>127 || Character.isUpperCase(text.codePointAt(0))) return literal(shaped);
            RimeEngine.Snapshot before=state();
            if(before.raw.length()>=128 && !" ".equals(text)) return Result.state(before);
            if(" ".equals(text) && before.composing() && !before.candidates.isEmpty()) {
                lastTypedChar=0;
                return Result.state(expandSnapshot(engine.selectCandidate(session,before.pageStart)));
            }
            int codePoint=text.codePointAt(0);
            RimeEngine.Snapshot after=engine.processKey(session,codePoint);
            if(!after.handled && after.commit.isEmpty()) {
                lastTypedChar=0;
                return new Result(after,text,false,0);
            }

            // Gboard-level Spatial Touch Error Correction (strictly disabled for Shuangpin / Wubi)
            boolean allowCorrection=!english && settings.isCorrectionEnabled() && "rimes_pinyin".equals(schema) && !nineKeyVisible();
            if(allowCorrection && text.length()==1 && SmartCorrector.isSupportedLetter((char)codePoint)) {
                if((!before.composing() || !before.candidates.isEmpty()) && after.composing() && after.candidates.isEmpty()) {
                    char ch=(char)codePoint;
                    List<Character> neighbors=SmartCorrector.getPrioritizedNeighbors(ch,biasX,biasY);
                    boolean rescued=false;
                    RimeEngine.Snapshot bestSnapshot=null;

                    // Level 1: Rescue current key Kn by evaluating spatial neighbors ordered by touch bias
                    if(!neighbors.isEmpty()) {
                        engine.processKey(session,0xff08); // undo Kn
                        for(char neighbor:neighbors) {
                            RimeEngine.Snapshot trySnapshot=engine.processKey(session,(int)neighbor);
                            if(trySnapshot.composing() && !trySnapshot.candidates.isEmpty()) {
                                rescued=true;
                                bestSnapshot=trySnapshot;
                                lastTypedChar=neighbor;
                                lastBiasX=0f;
                                lastBiasY=0f;
                                break;
                            }
                            engine.processKey(session,0xff08); // undo neighbor attempt
                        }
                    }

                    // Level 2: If current key neighbors failed, check if previous key K(n-1) in this syllable was mistyped
                    // (Crucial for Double Pinyin / Flypy / Ziranma where K(n-1) is initial and Kn is final, e.g. "fc" -> "hc")
                    if(!rescued && lastTypedChar>0 && SmartCorrector.isSupportedLetter((char)lastTypedChar) && before.raw.length()>=1) {
                        List<Character> prevNeighbors=SmartCorrector.getPrioritizedNeighbors((char)lastTypedChar,lastBiasX,lastBiasY);
                        engine.processKey(session,0xff08); // undo K(n-1)
                        for(char prevNeighbor:prevNeighbors) {
                            engine.processKey(session,(int)prevNeighbor);
                            RimeEngine.Snapshot trySnapshot=engine.processKey(session,codePoint);
                            if(trySnapshot.composing() && !trySnapshot.candidates.isEmpty()) {
                                rescued=true;
                                bestSnapshot=trySnapshot;
                                lastTypedChar=codePoint;
                                lastBiasX=biasX;
                                lastBiasY=biasY;
                                break;
                            }
                            engine.processKey(session,0xff08);
                            engine.processKey(session,0xff08);
                        }
                        if(!rescued) {
                            engine.processKey(session,lastTypedChar); // restore K(n-1)
                        }
                    }

                    if(rescued && bestSnapshot!=null) {
                        return Result.state(expandSnapshot(bestSnapshot));
                    }

                    // Level 3: Restore original Kn if no neighbor rescued the candidate list
                    after=engine.processKey(session,codePoint);
                }
            }

            if(after.composing()) {
                lastTypedChar=codePoint;
                lastBiasX=biasX;
                lastBiasY=biasY;
            } else {
                lastTypedChar=0;
            }

            return Result.state(expandSnapshot(after));
        });
    }
    private boolean deliver(String text,boolean block) {
        if(!ownsTarget()) return false;
        if(buffer.isEnabled()) {
            boolean accepted=block ? buffer.appendCommittedBlock(text) : buffer.appendLiteral(text);
            if(accepted && activePlugin!=null) scheduleAutoPlugin();
            return accepted;
        }
        int start=hostComposing ? composingStart : Math.min(selectionStart,selection);
        expect(start<0 ? -1 : start+text.length());
        boolean accepted=target.commitText(text,1);
        if(!accepted) {
            target.finishComposingText();
            accepted=target.commitText(text,1);
        }
        if(accepted) { hostComposing=false; composingStart=-1; }
        return accepted;
    }
    private ClipboardManager clipboardManager;
    private long suppressedClipTimestamp=-1L;
    private final ClipboardManager.OnPrimaryClipChangedListener clipboardListener=() -> main.post(this::captureClipboard);
    private void captureClipboard() {
        if(destroyed || clipboardStore==null) return;
        try {
            ClipData clip=primaryClip();
            String text=clipText(clip);
            if(text==null) return;
            long stamp=clip==null || clip.getDescription()==null?0L:clip.getDescription().getTimestamp();
            if(stamp!=0L && stamp==suppressedClipTimestamp) return;
            if(stamp==0L && clipboardStore.isRecentlyCleared(text)) return;
            clipboardStore.add(text);
            if(clipboardOpen && keyboard!=null) render();
        } catch(Throwable t) {
            android.util.Log.w("RIMES","captureClipboard failed",t);
        }
    }
    private ClipData primaryClip() {
        ClipData fromApp=readClip(clipboardManager);
        if(clipText(fromApp)!=null) return fromApp;
        ClipData fromService=readClip((ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE));
        if(clipText(fromService)!=null) return fromService;
        return fromApp!=null?fromApp:fromService;
    }
    private static ClipData readClip(ClipboardManager manager) {
        if(manager==null) return null;
        try { return manager.getPrimaryClip(); }
        catch(Throwable ignored) { return null; }
    }
    private String clipText(ClipData clip) {
        if(clip==null) return null;
        for(int i=0;i<clip.getItemCount();i++) {
            ClipData.Item item=clip.getItemAt(i);
            if(item==null) continue;
            CharSequence plain=item.getText();
            if(plain!=null && plain.toString().trim().length()>0) return plain.toString();
            String html=item.getHtmlText();
            if(html!=null && !html.isEmpty()) {
                String stripped=android.text.Html.fromHtml(html,android.text.Html.FROM_HTML_MODE_COMPACT).toString();
                if(!stripped.trim().isEmpty()) return stripped;
            }
            try {
                CharSequence coerced=item.coerceToText(this);
                if(coerced!=null && coerced.toString().trim().length()>0) return coerced.toString();
            } catch(Throwable ignored) {}
        }
        return null;
    }
    private void clearSystemClipboard() {
        try {
            ClipData clip=primaryClip();
            String text=clipText(clip);
            if(text!=null && clipboardStore!=null) clipboardStore.setLastClearedText(text);
            if(clip!=null && clip.getDescription()!=null) suppressedClipTimestamp=clip.getDescription().getTimestamp();
            ClipboardManager cm=clipboardManager!=null?clipboardManager:(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if(cm!=null) {
                if(android.os.Build.VERSION.SDK_INT>=android.os.Build.VERSION_CODES.P) cm.clearPrimaryClip();
                else cm.setPrimaryClip(ClipData.newPlainText("",""));
            }
        } catch(Throwable t) {
            android.util.Log.w("RIMES","clearSystemClipboard failed",t);
        }
    }
    private void pasteClipboard() {
        captureClipboard();
        clipboardOpen=!clipboardOpen;
        if(clipboardOpen) {
            appearanceOpen=false;
            pluginSettingsOpen=false;
            candidateGridOpen=false;
        }
        render();
    }
    private void updateComposition() {
        if(!ownsTarget() || buffer.isEnabled() || directOnly) return;
        if(visiblePassword || !englishDraft.isEmpty()) {
            // Visible-password terminals eager-commit composing text into the shell.
            // English suggestions are already committed, so the spelling must not be written again.
            return;
        }
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
        // A field that just opened only reports its cursor. That is not a new dictionary.
        boolean spelling=hostComposing || snapshot.composing() || pending!=0;
        cancelPlugin(); pluginSession.clear();
        cancelChord(); epoch.revoke(); pending=0; expectedSelections.clear(); snapshot=RimeEngine.Snapshot.EMPTY; retained=""; retainedResults.clear(); englishDraft="";
        if(hostComposing) { target.setComposingText("",1); target.finishComposingText(); }
        hostComposing=false; composingStart=-1; selection=newEnd; selectionStart=newStart;
        activePlugin=null; pluginSettingsOpen=false; buffer.beginTarget(buffer.isPermitted()); if(bufferRail!=null) bufferRail.clearProjection(); if(pluginOutput!=null) pluginOutput.clearProjection();
        if(spelling) resetEngine();
        render();
    }
    private void insert(boolean all) {
        if(chords!=null && chords.isChordActive()) return;
        if(pending!=0 || snapshot.composing()) return;
        insertNow(all);
        retryRetained(); render();
    }
    private void insertNow(boolean all) {
        // Plugin output is one complete block, tied to the exact captured source and target.
        if(activePlugin!=null) { insertPluginResult(); return; }
        BufferSession.Delivery delivery=buffer.prepare(all);
        InputConnection connection=target;
        if(!ownsTarget() || !buffer.isCurrent(delivery)) return;
        int end=selection<0 ? -1 : selection+delivery.text.length();
        if(connection.commitText(delivery.text,1) && connection==target && ownsTarget()) { expect(end); buffer.acknowledge(delivery); }
        render();
    }
    private void retryRetained() {
        while(!retainedResults.isEmpty()) {
            Result result=retainedResults.peek();
            if(!applyResult(result)) break;
            retainedResults.remove();
        }
        retained=retainedResults.isEmpty()?"":"pending";
        if(retained.isEmpty()) { updateComposition(); if(deferredSettingsPair!=null) applySettingsPair(deferredSettingsPair); }
        render();
    }
    private void delete() {
        invalidatePlugin();
        if(!retained.isEmpty()) { if(buffer.isEnabled()) { buffer.deleteLastBlock(); retryRetained(); } return; }
        if(!ready) { deleteHostOrBuffer(); render(); return; }
        if(!englishDraft.isEmpty()) {
            dispatch(() -> {
                RimeEngine.Snapshot after=state().composing()?engine.processKey(session,0xff08):RimeEngine.Snapshot.EMPTY;
                if(!after.composing()) after=RimeEngine.Snapshot.EMPTY;
                else after=expandSnapshot(after);
                return new Result(after,"",false,1,DRAFT_BACKSPACE);
            });
            return;
        }
        final boolean nine=nineKeyVisible();
        dispatch(() -> {
            lastTypedChar=0;
            return state().composing() ? (nine?replaceNineKey(NineKeyPinyin.backspace(state().raw)):Result.state(expandSnapshot(engine.processKey(session,0xff08))))
                    : new Result(RimeEngine.Snapshot.EMPTY,"",false,1);
        });
    }
    /** Swipe up on delete. Clears the same place a tap would delete: the whole editor, or the whole Buffer draft. */
    private void clearAll() {
        if(!ownsTarget()) return;
        if(chords!=null && chords.isChordActive()) return;
        candidateGridOpen=false;
        retainedResults.clear(); retained="";
        boolean drafting=buffer.isEnabled();
        if(drafting) buffer.clear();
        invalidatePlugin();
        snapshot=RimeEngine.Snapshot.EMPTY;
        // A key still in flight must not commit again after the field is wiped.
        epoch.revoke(); pending=0; expectedSelections.clear();
        if(!drafting) clearEditorText();
        if(ready) resetEngine();
        render();
    }
    private void clearEditorText() {
        if(!ownsTarget()) return;
        if(hostComposing) {
            int cursor=composingStart>=0?composingStart:Math.max(0,Math.min(selectionStart,selection));
            expect(cursor);
            target.setComposingText("",1); target.finishComposingText();
            hostComposing=false; hostPreedit=""; composingStart=-1;
        }
        EditorInfo info=getCurrentInputEditorInfo();
        boolean terminal=info==null || info.inputType==InputType.TYPE_NULL || visiblePassword;
        if(terminal) {
            target.performContextMenuAction(android.R.id.selectAll);
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            return;
        }
        CharSequence selected=target.getSelectedText(0);
        if(selected!=null && selected.length()>0) {
            expect(Math.max(0,Math.min(selectionStart,selection)));
            target.commitText("",1);
        }
        for(int pass=0;pass<64;pass++) {
            CharSequence before=target.getTextBeforeCursor(4096,0);
            CharSequence after=target.getTextAfterCursor(4096,0);
            int behind=before==null?0:before.length();
            int ahead=after==null?0:after.length();
            if(behind==0 && ahead==0) break;
            if(selection>=0) expect(Math.max(0,selection-behind));
            boolean deleted;
            try { deleted=target.deleteSurroundingText(behind,ahead); }
            catch(Throwable ignored) { break; }
            if(!deleted) break;
        }
    }
    private void deleteHostOrBuffer() {
        if(!ownsTarget()) return;
        if(buffer.isEnabled()) {
            buffer.deleteLastBlock();
            if(activePlugin!=null) {
                if(buffer.blockCount()>0) scheduleAutoPlugin();
                else invalidatePlugin();
            }
        }
        else {
            EditorInfo info=getCurrentInputEditorInfo();
            boolean isTerminal=info==null || info.inputType==android.text.InputType.TYPE_NULL || visiblePassword;
            if(isTerminal) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                return;
            }
            CharSequence selected=target.getSelectedText(0);
            if(selected!=null && selected.length()>0) {
                expect(Math.min(selectionStart,selection));
                if(!target.commitText("",1)) {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                }
            }
            else {
                CharSequence before=target.getTextBeforeCursor(2,0);
                int units=before!=null && before.length()>0 ? Character.charCount(Character.codePointBefore(before,before.length())) : 1;
                expect(selection>0 ? Math.max(0,selection-units) : 0);
                boolean deleted=false;
                if(Build.VERSION.SDK_INT>=24) {
                    try { deleted=target.deleteSurroundingTextInCodePoints(1,0); } catch(Throwable ignored) {}
                }
                if(!deleted) {
                    try { deleted=target.deleteSurroundingText(units,0); } catch(Throwable ignored) {}
                }
                if(!deleted) {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                }
            }
        }
    }
    private void enter() {
        if(!ready) { returnHostOrBuffer(); return; }
        if(!englishDraft.isEmpty()) {
            dispatch(() -> {
                if(state().composing()) engine.clearComposition(session);
                return new Result(RimeEngine.Snapshot.EMPTY,"",false,2,DRAFT_CLEAR);
            });
            return;
        }
        dispatch(() -> {
            lastTypedChar=0;
            return state().composing() ? literal("") : new Result(RimeEngine.Snapshot.EMPTY,"",false,2);
        });
    }
    private void returnHostOrBuffer() {
        if(!ownsTarget()) return;
        if(buffer.isEnabled()) { if(activePlugin!=null) { if(pluginSession.snapshot(buffer).status==PluginSession.Status.READY) insertPluginResult(); else runPlugin(); } else insertNow(false); }
        else if(!sendDefaultEditorAction(true)) deliver("\n",false);
    }
    private void settleAndSwitch(Runnable change) {
        cancelChord();
        if(!retained.isEmpty()) return;
        if(!ready) { change.run(); render(); return; }
        if(!ownsTarget() && !adoptCurrentConnection()) return;
        // Route settlement before applying the new mode; subsequent keys queue after it.
        final boolean dropDraft=!englishDraft.isEmpty();
        dispatch(() -> {
            if(!dropDraft) return literal("");
            if(state().composing()) engine.clearComposition(session);
            return new Result(RimeEngine.Snapshot.EMPTY,"",false,0,DRAFT_CLEAR);
        });
        change.run();
        final String selected=effectiveSchema();
        EngineWorker.QUEUE.execute(() -> { if(session!=0 && engine.selectSchema(session,selected)) loadedSchema=selected; });
        render();
    }
    private void select(int index) {
        if(chords!=null && chords.isChordActive()) return;
        if(!ready || index>=snapshot.candidates.size()) return;
        candidateGridOpen=false;
        if(!englishDraft.isEmpty()) {
            final String word=snapshot.candidates.get(index);
            dispatch(() -> {
                if(state().composing()) engine.clearComposition(session);
                return new Result(RimeEngine.Snapshot.EMPTY,word,false,0,DRAFT_REPLACE);
            });
            return;
        }
        final int absolute=snapshot.pageStart+index;
        dispatch(() -> {
            lastTypedChar=0;
            return Result.state(expandSnapshot(engine.selectCandidate(session,absolute)));
        });
    }
    private void page(boolean forward) {
        if(snapshot.composing()) dispatch(() -> Result.state(engine.processKey(session,forward?0xff56:0xff55)));
    }
    private void notice(int message) { Toast.makeText(this,message,Toast.LENGTH_SHORT).show(); }

    private Result replaceNineKey(String raw) {
        lastTypedChar=0;
        engine.clearComposition(session);
        RimeEngine.Snapshot after=RimeEngine.Snapshot.EMPTY;
        for(int key:raw.codePoints().toArray()) after=engine.processKey(session,key);
        return Result.state(expandSnapshot(after));
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
            changeSettingsPair(() -> settings.setLayout(selected)); numeric=false; symbols=false; emoji=false; uppercase=false; spellingOpen=false;
            if(selected.equals("nineKey")) english=false;
            if(chordLayout()) english=false;
        });
    }
    private void openAppSettings() {
        appearanceOpen=false;
        android.content.Intent intent=new android.content.Intent(this,SetupActivity.class);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        render();
    }
    private void chooseHeightPercent(int percent) {
        settings.setHeightPercent(percent);
        heightPercent=percent;
        heightFactor=KeyboardSettings.heightScaleFactor(percent);
        if(keys!=null) {
            keys.setHeightFactor(heightFactor);
            keys.requestLayout();
        }
        if(surfaceContainer!=null) {
            float width=(keyboard.getWidth()>0?keyboard.getWidth():getResources().getDisplayMetrics().widthPixels)-keyboard.getPaddingLeft()-keyboard.getPaddingRight();
            float surfaceHeight=chordVisible()?ChordLayout.height(Math.max(1,width/getResources().getDisplayMetrics().density),"splitOrthogonal".equals(layout)):KeyboardLayout.height(landscape(),heightFactor);
            if(chordVisible() && (appearanceOpen || pluginSettingsOpen || clipboardOpen || candidateGridOpen)) surfaceHeight+=landscape()?35:41;
            surfaceContainer.getLayoutParams().height=Math.round(surfaceHeight*getResources().getDisplayMetrics().density);
            surfaceContainer.requestLayout();
        }
        if(keyboard!=null) {
            keyboard.requestLayout();
            keyboard.invalidate();
        }
        if(getWindow()!=null && getWindow().getWindow()!=null) {
            getWindow().getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        render();
    }
    private void chooseBottomInset(int dp) {
        settings.setBottomInset(dp);
        bottomInset=dp;
        applyBottomInset();
        render();
    }
    private void applyBottomInset() {
        if(keyboard!=null) {
            keyboard.setPadding(dp(5),dp(5),dp(5),dp(5+bottomInset));
            keyboard.requestLayout();
        }
    }
    private void toggleCandidateGrid() {
        if(snapshot.candidates.isEmpty()) return;
        appearanceOpen=false;
        pluginSettingsOpen=false;
        clipboardOpen=false;
        candidateGridOpen=!candidateGridOpen;
        render();
    }
    private void moveCursor(int steps) {
        if(!ownsTarget()) return;
        int currentSel=selection>=0?selection:composingStart>=0?composingStart:-1;
        if(currentSel>=0) {
            int newSel=Math.max(0,currentSel+steps);
            target.setSelection(newSel,newSel);
            expect(newSel);
        } else {
            int keyCode=steps<0?KeyEvent.KEYCODE_DPAD_LEFT:KeyEvent.KEYCODE_DPAD_RIGHT;
            int count=Math.abs(steps);
            for(int i=0;i<count;i++) sendDownUpKeyEvents(keyCode);
        }
    }
    private void pressSpace() {
        long now=System.currentTimeMillis();
        if((english || directOnly) && !visiblePassword && englishDraft.isEmpty() && !snapshot.composing() && now-lastSpaceTime<600) {
            lastSpaceTime=0;
            delete();
            deliver(". ",false);
            return;
        }
        lastSpaceTime=now;
        type(" ");
    }
    private void typeChord(String code) {
        if(code.isEmpty() || !ready) return;
        if(!ownsTarget() && !adoptCurrentConnection()) return;
        invalidatePlugin();
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
        if(!ownsTarget() && !adoptCurrentConnection()) return;
        if(directOnly) return;
        // A failed write used to swallow this key. Retry it, then switch.
        if(!retained.isEmpty()) {
            retryRetained();
            if(!retained.isEmpty()) { notice(buffer.isEnabled()?R.string.buffer_limit:R.string.delivery_pending); return; }
        }
        settleAndSwitch(() -> { english=!english; uppercase=false; spellingOpen=false; punctuationOpen=false; });
    }
    private void toggleNumbers() {
        settleAndSwitch(() -> { numeric=!numeric; symbols=false; emoji=false; spellingOpen=false; punctuationOpen=false; });
    }
    private String letterReturnLabel() { return nineKeyEngine() && !english && !directOnly?"拼音":"ABC"; }
    private KeyboardLayout.Mode visibleMode() {
        return emoji?KeyboardLayout.Mode.EMOJI:numeric?(symbols?KeyboardLayout.Mode.SYMBOLS:KeyboardLayout.Mode.NUMERIC)
                :nineKeyVisible()?KeyboardLayout.Mode.NINE_KEY:KeyboardLayout.Mode.QWERTY;
    }
    private void toggleAppearance() {
        cancelChord();
        pluginSettingsOpen=false; clipboardOpen=false; appearanceOpen=!appearanceOpen;
        if(appearanceOpen) appearancePanel.scrollTo(0,0);
        render();
    }
    private long lastPunctuationTime=0;
    private void pressPunctuation() {
        long now=SystemClock.uptimeMillis();
        if(now-lastPunctuationTime<500) {
            delete();
            typePunctuation(".",halfwidthMarks()?".":"。");
            lastPunctuationTime=0;
        } else {
            typePunctuation(",",halfwidthMarks()?",":"，");
            lastPunctuationTime=now;
        }
    }
    private boolean longPressPunctuation() {
        typePunctuation(".",halfwidthMarks()?".":"。");
        lastPunctuationTime=0;
        if(keyboard!=null) keyboard.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        return true;
    }
    private final KeyboardSurface.Handler keyHandler=new KeyboardSurface.Handler() {
        @Override public String label(KeyboardLayout.Key key) {
            switch(key.action) {
                case TEXT:
                    if(nineKeyVisible()) return new String[]{"ABC","DEF","GHI","JKL","MNO","PQRS","TUV","WXYZ"}[Integer.parseInt(key.text)-2];
                    if(emoji) return key.text;
                    if(numeric) return shapedMark(key.text);
                    return uppercase?key.text.toUpperCase(Locale.ROOT):key.text;
                case SHIFT: return uppercase?"⇪":"⇧";
                case DELETE: return "⌫";
                case RETURN: return returnLabel();
                case NUMBERS: return numeric?letterReturnLabel():"123";
                case SYMBOLS: return numeric && symbols?"123":"符号";
                case LANGUAGE:
                    if(english || directOnly) return "英";
                    return (schema.equals("rimes_flypy") || schema.equals("rimes_ziranma"))?"双":"中";
                case EMOJI: return emoji?letterReturnLabel():"☺";
                case SPACE: return english || directOnly?"space":"空格";
                case SPELLING: return "选拼音";
                case SEPARATOR: return "分隔";
                case PUNCTUATION: return nineKeyVisible()?"，。?!":english || directOnly?",.":"，。";
                default: throw new IllegalStateException();
            }
        }
        @Override public String description(KeyboardLayout.Key key) {
            switch(key.action) {
                case TEXT: return nineKeyVisible()?"九键 "+key.text+" "+label(key):!uppercase && !numeric && !emoji?key.text:label(key);
                case SHIFT: return "Shift";
                case DELETE: return getString(R.string.backspace_clear);
                case RETURN: return getString(R.string.enter);
                case NUMBERS: return "数字与字母";
                case SYMBOLS: return numeric && symbols?"返回数字":"符号页";
                case LANGUAGE: return "中英切换";
                case EMOJI: return emoji?(nineKeyEngine() && !english && !directOnly?"返回拼音":"返回字母"):"表情";
                case SPACE: return getString(R.string.space);
                case SPELLING: return "选拼音";
                case SEPARATOR: return "分隔音节";
                case PUNCTUATION: return nineKeyVisible()?"中文标点":english || directOnly?"逗号与句号":"中文逗号与句号";
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
        @Override public boolean longPress(KeyboardLayout.Key key) {
            if(key.action==KeyboardLayout.Action.PUNCTUATION && !nineKeyVisible()) return longPressPunctuation();
            if(key.action==KeyboardLayout.Action.TEXT && !numeric && !symbols && !emoji) {
                String digit=KeyboardSurface.hintForLetter(key.text);
                if(digit!=null) {
                    deliver(digit,false);
                    return true;
                }
            }
            return false;
        }
        @Override public void slideCursor(int steps) {
            moveCursor(steps);
        }
        @Override public void clearAll() { RimesInputMethodService.this.clearAll(); }
        @Override public void press(KeyboardLayout.Key key) {
            press(key,0f,0f);
        }
        @Override public void press(KeyboardLayout.Key key,float biasX,float biasY) {
            if(key.action!=KeyboardLayout.Action.PUNCTUATION) lastPunctuationTime=0;
            if(key.action!=KeyboardLayout.Action.SPACE) lastSpaceTime=0;
            switch(key.action) {
                case TEXT:
                    String text=uppercase && !numeric && !emoji?key.text.toUpperCase(Locale.ROOT):key.text;
                    String shaped=shapedMark(text);
                    if(numeric && Punctuation.tracked(shaped,halfwidthMarks())) typePunctuation(shaped,shaped);
                    else type(shaped,biasX,biasY);
                    break;
                case SHIFT: settleAndSwitch(() -> uppercase=!uppercase); break;
                case DELETE: delete(); break;
                case RETURN: enter(); break;
                case NUMBERS: toggleNumbers(); break;
                case SYMBOLS: settleAndSwitch(() -> { symbols=!symbols || !numeric; numeric=true; emoji=false; }); break;
                case LANGUAGE: toggleLanguage(); break;
                case EMOJI: settleAndSwitch(() -> { emoji=!emoji; numeric=false; if(emoji) punctuationOpen=false; }); break;
                case SPACE: pressSpace(); break;
                case SPELLING: spellingOpen=!spellingOpen; punctuationOpen=false; spellingPage=0; render(); break;
                case SEPARATOR: type("'"); break;
                case PUNCTUATION:
                    if(nineKeyVisible()) { punctuationOpen=!punctuationOpen; spellingOpen=false; render(); }
                    else pressPunctuation();
                    break;
                default: throw new IllegalStateException();
            }
        }
        @Override public String hint(KeyboardLayout.Key key) {
            if(key.action!=KeyboardLayout.Action.TEXT) return null;
            if(!english && !directOnly && !numeric && !emoji) {
                if("rimes_flypy".equals(schema)) return flypyHint(key.text);
                if("rimes_ziranma".equals(schema)) return ziranmaHint(key.text);
            }
            return KeyboardSurface.hintForLetter(key.text);
        }
    };
    static String flypyHint(String letter) {
        if(letter==null || letter.length()!=1) return null;
        switch(Character.toLowerCase(letter.charAt(0))) {
            case 'q': return "iu"; case 'w': return "ei"; case 'r': return "uan"; case 't': return "ue";
            case 'y': return "un"; case 'u': return "sh"; case 'i': return "ch"; case 'o': return "uo";
            case 'p': return "ie"; case 's': return "ong"; case 'd': return "ai"; case 'f': return "en";
            case 'g': return "eng"; case 'h': return "ang"; case 'j': return "an"; case 'k': return "ing";
            case 'l': return "iang"; case 'z': return "ou"; case 'x': return "ia"; case 'c': return "ao";
            case 'v': return "zh"; case 'b': return "in"; case 'n': return "iao"; case 'm': return "ian";
            default: return null;
        }
    }
    static String ziranmaHint(String letter) {
        if(letter==null || letter.length()!=1) return null;
        switch(Character.toLowerCase(letter.charAt(0))) {
            case 'q': return "iu"; case 'w': return "ia"; case 'r': return "uan"; case 't': return "ue";
            case 'y': return "ing"; case 'u': return "sh"; case 'i': return "ch"; case 'o': return "uo";
            case 'p': return "un"; case 's': return "ong"; case 'd': return "ai"; case 'f': return "en";
            case 'g': return "eng"; case 'h': return "ang"; case 'j': return "an"; case 'k': return "ao";
            case 'l': return "ai"; case 'z': return "ou"; case 'x': return "ua"; case 'c': return "ao";
            case 'v': return "zh"; case 'b': return "in"; case 'n': return "iao"; case 'm': return "ian";
            default: return null;
        }
    }
    private boolean returnSelected() {
        if(snapshot.composing() || pending!=0) return false;
        if(buffer.isEnabled()) return activePlugin==null?buffer.blockCount()>0:pluginSession.snapshot(buffer).status==PluginSession.Status.READY;
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
        if(buffer.isEnabled()) return activePlugin==null?"插入":pluginSession.snapshot(buffer).status==PluginSession.Status.READY?"发送":pluginSession.snapshot(buffer).status==PluginSession.Status.RUNNING?"处理中":"执行";
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
        keyboard=new KeyboardRoot(this); keyboard.setOrientation(LinearLayout.VERTICAL);
        if(Build.VERSION.SDK_INT>=29) keyboard.setForceDarkAllowed(false);
        keyboard.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); chromeButtons.clear(); chordControls.clear(); renderedKeyStamp=""; renderedCandidates="";
        keyboard.setPadding(dp(5),dp(5),dp(5),dp(5+bottomInset));
        keyboard.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> { if(r-l!=or-ol) render(); });
        keyboard.setOnApplyWindowInsetsListener((view,insets) -> {
            int left,right,bottom;
            if(Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                left=safe.left; right=safe.right; bottom=safe.bottom;
            } else { left=insets.getSystemWindowInsetLeft(); right=insets.getSystemWindowInsetRight(); bottom=insets.getSystemWindowInsetBottom(); }
            view.setPadding(dp(5)+left,dp(5),dp(5)+right,dp(5+bottomInset)+bottom); return insets;
        });
        bufferRow=new LinearLayout(this); bufferRow.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bufferParams=new LinearLayout.LayoutParams(-1,dp(landscape()?60:76)); bufferParams.bottomMargin=dp(4); keyboard.addView(bufferRow,bufferParams);
        int railHeight=landscape()?28:36;
        LinearLayout input=row(bufferRow,railHeight); bufferTop=input;
        themeButton=button(input,theme.glyph,this::toggleAppearance,0); fixedWidth(themeButton,32); gapRight(themeButton,4);
        themeButton.setContentDescription("布局与配色"); themeButton.icon(KeyboardIcon.APPEARANCE);
        bufferRail=new BufferRail(this); input.addView(bufferRail,new LinearLayout.LayoutParams(0,-1,1));
        insertNext=button(input,"↑",() -> insert(false),0); fixedWidth(insertNext,32); gapLeft(insertNext,4);
        ((KeyButton)insertNext).icon(KeyboardIcon.PAPER_PLANE); ((KeyButton)insertNext).appearance(false,false,true); insertNext.setContentDescription(getString(R.string.insert_next)); insertNext.setOnLongClickListener(v -> { insert(true); return true; });
        LinearLayout details=new LinearLayout(this); bufferBottom=details; bufferRow.addView(details,new LinearLayout.LayoutParams(-1,dp(railHeight)));
        bufferSettings=button(details,"设置",() -> openPluginSettings(activePlugin),0); fixedWidth(bufferSettings,32); gapRight(bufferSettings,4); bufferSettings.icon(KeyboardIcon.SLIDERS); bufferSettings.setContentDescription("Buffer 设置");
        metrics=new TextView(this); metrics.setGravity(Gravity.CENTER); metrics.setTextSize(landscape()?14:16);
        metrics.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL)); details.addView(metrics,new LinearLayout.LayoutParams(0,-1,1));
        pluginRunButton=button(details,"执行",this::runOrCancelPlugin,0); fixedWidth(pluginRunButton,32); gapLeft(pluginRunButton,4); pluginRunButton.icon(KeyboardIcon.PLAY);
        pluginButton=button(details,"插件",() -> openPluginSettings(activePlugin),0); fixedWidth(pluginButton,32); gapLeft(pluginButton,4); pluginButton.icon(KeyboardIcon.GRID_9); pluginButton.setContentDescription("Buffer 插件设置");
        candidateRow=row(keyboard,landscape()?50:64);
        hideKeyboardButton=button(candidateRow,"",this::hideKeyboard,0); fixedWidth(hideKeyboardButton,32); gapRight(hideKeyboardButton,4);
        hideKeyboardButton.icon(KeyboardIcon.HIDE_KEYBOARD); hideKeyboardButton.setContentDescription(getString(R.string.hide_keyboard));
        layoutButton=button(candidateRow,"⚙",this::toggleAppearance,0); fixedWidth(layoutButton,32); gapRight(layoutButton,4); layoutButton.setContentDescription("键位布局"); layoutButton.icon(KeyboardIcon.SETTINGS);
        previous=button(candidateRow,"‹",() -> candidatePage(false),0); fixedWidth(previous,32); ((KeyButton)previous).plain(true); previous.setContentDescription("上一页候选"); ((KeyButton)previous).icon(KeyboardIcon.CHEVRON_LEFT,16);
        previous.setVisibility(View.GONE);
        FrameLayout center=new FrameLayout(this); candidateRow.addView(center,new LinearLayout.LayoutParams(0,-1,1));
        candidateBox=new LinearLayout(this);
        candidateBox.setOrientation(LinearLayout.VERTICAL);
        center.addView(candidateBox,new FrameLayout.LayoutParams(-1,-1));
        preedit=new TextView(this); preedit.setSingleLine(true); preedit.setEllipsize(android.text.TextUtils.TruncateAt.END);
        preedit.setTextSize(KeyboardTypography.preeditSp(landscape())); preedit.setGravity(Gravity.CENTER_VERTICAL);
        preedit.setIncludeFontPadding(false);
        preedit.setPadding(dp(12),0,dp(12),0); preedit.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
        preedit.setOnClickListener(v -> { if(snapshot.composing()) enter(); });
        candidateBox.addView(preedit,new LinearLayout.LayoutParams(-1,dp(landscape()?16:22)));
        candidateScroll=new HorizontalScrollView(this); candidateScroll.setFillViewport(false); candidateScroll.setHorizontalScrollBarEnabled(false);
        candidateScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        LinearLayout strip=new LinearLayout(this); candidateStrip=strip; candidateScroll.addView(strip,new HorizontalScrollView.LayoutParams(-2,-1));
        candidateBox.addView(candidateScroll,new LinearLayout.LayoutParams(-1,0,1f));
        pluginShortcuts=new PluginShortcutBar(this,theme,new PluginShortcutBar.Listener() {
            public void onPluginTap(String id) { selectPlugin(id); }
            public void onPluginLongPress(String id) { openPluginSettings(id); }
            public void onPasteTap() { pasteClipboard(); }
            public void onEmojiTap() { settleAndSwitch(() -> { emoji=!emoji; numeric=false; if(emoji) punctuationOpen=false; }); }
        }); center.addView(pluginShortcuts,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM)); chordReadout=new ChordPreview(this); center.addView(chordReadout,new FrameLayout.LayoutParams(-1,-1));
        candidates.clear();
        ensureCandidateButtons(9);
        next=button(candidateRow,"›",() -> candidatePage(true),0); fixedWidth(next,32); ((KeyButton)next).plain(true); next.setContentDescription("下一页候选"); ((KeyButton)next).icon(KeyboardIcon.CHEVRON_RIGHT,16);
        next.setVisibility(View.GONE);
        candidateGridButton=button(candidateRow,"⌄",this::toggleCandidateGrid,0); fixedWidth(candidateGridButton,32); gapLeft(candidateGridButton,2); ((KeyButton)candidateGridButton).plain(true); candidateGridButton.setContentDescription("展开候选");
        bufferButton=button(candidateRow,"▤",() -> { if(pending==0 && !snapshot.composing() && retained.isEmpty()) { cancelChord(); buffer.setEnabled(!buffer.isEnabled()); if(!buffer.isEnabled()) { cancelPlugin(); pluginSession.clear(); activePlugin=null; pluginSettingsOpen=false; } render(); } },0);
        fixedWidth(bufferButton,32); gapLeft(bufferButton,4); ((KeyButton)bufferButton).appearance(false,false,true); ((KeyButton)bufferButton).icon(KeyboardIcon.STACK_LAYERS);
        spellingRow=row(keyboard,34); spellingButtons.clear();
        for(int i=0;i<9;i++) { final int index=i; KeyButton spelling=button(spellingRow,"",() -> chooseSpelling(spellingPage+index),1); spellingButtons.add(spelling); }
        retryButton=new KeyButton(this); chromeButtons.add((KeyButton)retryButton); retryButton.setText(R.string.retry);
        retryButton.setOnClickListener(v -> { if(failed) initialize(); else retryRetained(); }); keyboard.addView(retryButton,new LinearLayout.LayoutParams(-1,dp(44)));
        surfaceContainer=new FrameLayout(this);
        keys=new KeyboardSurface(this,keyHandler); surfaceContainer.addView(keys,new FrameLayout.LayoutParams(-1,-1));
        chords=new ChordSurface(this,new ChordSurface.Handler() {
            public void onChord(String code) { chordPreview=""; typeChord(code); }
            public void onKey(String text) { type(uppercase?text.toUpperCase(Locale.ROOT):text); }
            public void onPreview(ChordGesture.Preview preview) { if(preview!=null && heldPreview==null) invalidatePlugin(); heldPreview=preview; String value=preview==null?"":preview.combined!=null?preview.combined:(preview.left==null?"":preview.left.keys)+(preview.right==null?"":preview.right.keys);
                chordPreview=value; render(); }
            public void onControl(ChordLayout.Action action) { if(action==ChordLayout.Action.DELETE) delete(); else { settleAndSwitch(() -> { emoji=true; numeric=false; }); } }
            public String label(ChordLayout.Action action) { return action==ChordLayout.Action.DELETE?"⌫":"☺"; }
            public String description(ChordLayout.Action action) { return action==ChordLayout.Action.DELETE?getString(R.string.backspace):"表情"; }
        }); surfaceContainer.addView(chords,new FrameLayout.LayoutParams(-1,-1));
        appearancePanel=new KeyboardAppearancePanel(this,this::chooseLayout,settings::setTheme,this::chooseHeightPercent,this::chooseBottomInset);
        appearancePanel.schemes(schema,this::chooseSchema);
        appearancePanel.action("全部插入",getString(R.string.insert_all),() -> insert(true));
        appearancePanel.action("清空 Buffer",getString(R.string.clear),() -> { if(pending==0 && !snapshot.composing()) { invalidatePlugin(); buffer.clear(); retryRetained(); render(); } });
        appearancePanel.action("应用设置","打开 RIMES 应用设置",this::openAppSettings);
        appearancePanel.action("系统键盘",getString(R.string.switch_keyboard),() -> { endTarget(); getSystemService(InputMethodManager.class).showInputMethodPicker(); });
        surfaceContainer.addView(appearancePanel,new FrameLayout.LayoutParams(-1,-1));
        pluginPanel=new BufferPluginPanel(this,new BufferPluginPanel.Listener() {
            public void onPlugin(String id) { openPluginSettings(id); }
            public void onDefaultBuffer() { cancelPlugin(); pluginSession.clear(); activePlugin=null; pluginSettingsOpen=false; render(); }
            public void onClose() { pluginSettingsOpen=false; render(); }
            public void onDirection(String direction) { settings.setTranslationDirection(direction); if("translate".equals(activePlugin)) scheduleAutoPlugin(); render(); }
        }); surfaceContainer.addView(pluginPanel,new FrameLayout.LayoutParams(-1,-1));
        clipboardPanel=new ClipboardPanel(this,new ClipboardPanel.Listener() {
            public void onPasteItem(String text) {
                clipboardOpen=false;
                deliver(text,false);
                render();
            }
            public void onTogglePin(String text) {
                if(clipboardStore!=null) clipboardStore.togglePin(text);
                render();
            }
            public void onClear(boolean all) {
                clearSystemClipboard();
                if(clipboardStore!=null) {
                    if(all) clipboardStore.clearAll();
                    else clipboardStore.clearUnpinned();
                }
                android.widget.Toast.makeText(RimesInputMethodService.this,
                    all?"剪贴板已全部清空":"已清空未固定剪贴板",android.widget.Toast.LENGTH_SHORT).show();
                render();
            }
            public void onClose() {
                clipboardOpen=false;
                render();
            }
        });
        surfaceContainer.addView(clipboardPanel,new FrameLayout.LayoutParams(-1,-1));
        candidateGridPanel=new CandidateGridPanel(this,new CandidateGridPanel.Listener() {
            public void onSelectCandidate(int index) {
                candidateGridOpen=false;
                candidateTapped(index);
            }
            public void onClose() {
                candidateGridOpen=false;
                render();
            }
            public void onPrevPage() { candidatePage(false); }
            public void onNextPage() { candidatePage(true); }
        });
        surfaceContainer.addView(candidateGridPanel,new FrameLayout.LayoutParams(-1,-1));
        pluginOutput=new BufferRail(this);
        pluginOutput.setOnClickListener(v -> {
            if(pluginSession.snapshot(buffer).status==PluginSession.Status.READY) insertPluginResult();
        });
        renderedPluginMode=false;
        keyboard.addView(surfaceContainer,new LinearLayout.LayoutParams(-1,dp(KeyboardLayout.height(landscape(),heightFactor))));
        chordFooter=row(keyboard,landscape()?34:40); ((LinearLayout.LayoutParams)chordFooter.getLayoutParams()).bottomMargin=0;
        KeyboardLayout.Action[] actions={KeyboardLayout.Action.NUMBERS,KeyboardLayout.Action.SHIFT,KeyboardLayout.Action.SPACE,KeyboardLayout.Action.LANGUAGE,KeyboardLayout.Action.RETURN};
        for(KeyboardLayout.Action action:actions) {
            KeyButton button=button(chordFooter,"",() -> chordControl(action),action==KeyboardLayout.Action.SPACE?3.5f:1);
            button.classic(true); button.fontStyle(false,KeyboardTypography.functionSp(landscape()),true); button.appearance(action!=KeyboardLayout.Action.SPACE,true,action==KeyboardLayout.Action.RETURN); if(!chordControls.isEmpty()) gapLeft(button,2); chordControls.add(button);
        }
        rebuildKeys(); render(); return keyboard;
    }
    private static String pluginName(String id) {
        if(id==null) return "";
        switch(id) { case "translate":return "翻译";case "ask":return "快问";case "polish":return "润色";case "poem":return "作诗";case "art":return "画画";default:throw new IllegalArgumentException("Unknown plugin"); }
    }
    private static String pluginPlaceholder(String id) {
        switch(id) { case "translate":return "输入要翻译的文字";case "ask":return "问点什么";case "polish":return "写下要润色的文字";case "poem":return "写下主题或要藏的字";case "art":return "写下要画的东西";default:throw new IllegalArgumentException("Unknown plugin"); }
    }
    private boolean canSelectPlugin() {
        return ownsTarget() && buffer.isPermitted() && pending==0 && !snapshot.composing() && retained.isEmpty() && !chords.isChordActive();
    }
    private void selectPlugin(String id) {
        pluginName(id);
        if(!canSelectPlugin() || !officialPlugins.enabled(id)) return;
        cancelChord(); cancelPlugin(); if(!buffer.isEnabled()) buffer.setEnabled(true); activePlugin=id.equals(activePlugin)?null:id;
        pluginSession.select(activePlugin);
        punctuationOpen=false; appearanceOpen=false; pluginSettingsOpen=false; clipboardOpen=false;
        if(activePlugin!=null && buffer.blockCount()>0) scheduleAutoPlugin();
        render();
    }
    private void openPluginSettings(String id) {
        if(!canSelectPlugin()) return;
        cancelChord(); if(!buffer.isEnabled()) buffer.setEnabled(true);
        if(!java.util.Objects.equals(activePlugin,id)) cancelPlugin(); activePlugin=id; pluginSession.select(id);
        appearanceOpen=false; clipboardOpen=false; pluginSettingsOpen=true; render();
    }
    private final Runnable autoPluginTask=this::autoRunPlugin;
    private void scheduleAutoPlugin() {
        main.removeCallbacks(autoPluginTask);
        if(!destroyed && activePlugin!=null && buffer.isEnabled() && buffer.blockCount()>0 && pluginAllowed()) {
            main.postDelayed(autoPluginTask,280);
        }
    }
    private void autoRunPlugin() {
        if(!destroyed && activePlugin!=null && buffer.isEnabled() && buffer.blockCount()>0 && pluginAllowed()
                && !snapshot.composing() && pending==0 && !chords.isChordActive()) {
            PluginSession.Snapshot state=pluginSession.snapshot(buffer);
            if(state.status!=PluginSession.Status.RUNNING) {
                runPlugin();
            }
        }
    }
    private void cancelPlugin() {
        main.removeCallbacks(autoPluginTask);
        if(pluginJob!=null) { pluginJob.cancel(); pluginJob=null; }
    }
    private void invalidatePlugin() {
        cancelPlugin();
        pluginSession.invalidate();
        if(activePlugin!=null && buffer.isEnabled() && buffer.blockCount()>0) {
            scheduleAutoPlugin();
        }
    }
    private String pluginStatus(PluginSession.Snapshot state) {
        if(cometProfile.remote(activePlugin)) {
            if(state.status==PluginSession.Status.ERROR) return state.message;
            String action="translate".equals(activePlugin)?"实时翻译":pluginName(activePlugin);
            if(state.status==PluginSession.Status.READY) return "AI "+action+"就绪 · 点此或回车上屏";
            return state.status==PluginSession.Status.RUNNING?"AI "+action+"中… · 点停止可取消":"AI "+action+" · "+cometProfile.model;
        }
        if(state.status==PluginSession.Status.RUNNING) return activePlugin.equals("translate")?"实时查译中…":"Mock 生成中…";
        if(state.status==PluginSession.Status.READY) return activePlugin.equals("translate")?"实时翻译就绪 · 点此或回车上屏":"生成完成 · 点此或回车发送";
        if(state.status==PluginSession.Status.ERROR) return state.message;
        if(!"translate".equals(activePlugin) && !aiMockEnabled) return "AI Mock 已关闭 · 在 RIMES 主应用中启用";
        return activePlugin.equals("translate")?"实时中英互译 · 键入文字即可实时翻译":"OpenAI 格式 Mock · 点执行生成";
    }
    private void runOrCancelPlugin() {
        if(pluginSession.snapshot(buffer).status==PluginSession.Status.RUNNING) { invalidatePlugin(); render(); }
        else runPlugin();
    }
    private void runPlugin() {
        if(activePlugin==null || !canSelectPlugin() || !buffer.isEnabled() || !pluginAllowed()) return;
        final String authorization=officialPlugins.grant(activePlugin);
        if(authorization==null) return;
        pluginResultAuthorization=authorization;
        PluginSession.Request request=pluginSession.start(buffer);
        if(request==null) return;
        InputEpoch.Ticket ticket=epoch.issue(); InputConnection connection=target;
        pluginSettingsOpen=false; render();
        pluginJob=pluginExecutor.run(request.plugin,request.source.text,translationDirection,cometProfile,new BufferPluginExecutor.Listener() {
            public void onUpdate(String text,boolean complete) { main.post(() -> {
                if(!epoch.current(ticket) || connection!=target || !ownsTarget() || privateField || !pluginAllowed()
                        || !authorization.equals(officialPlugins.grant(request.plugin))) return;
                if(pluginSession.update(request,buffer,text,complete)) { if(pluginSession.snapshot(buffer).status==PluginSession.Status.ERROR) cancelPlugin(); else if(complete) pluginJob=null; render(); }
            }); }
            public void onFailure(String message) { main.post(() -> {
                if(!epoch.current(ticket) || connection!=target || !ownsTarget() || privateField || !pluginAllowed()
                        || !authorization.equals(officialPlugins.grant(request.plugin))) return;
                if(pluginSession.fail(request,buffer,message)) { pluginJob=null; render(); }
            }); }
        });
    }
    private boolean pluginAllowed() { return officialPlugins.enabled(activePlugin) && ("translate".equals(activePlugin) || cometProfile.enabled || aiMockEnabled); }
    private void insertPluginResult() {
        if(!canSelectPlugin() || !buffer.isEnabled() || !pluginAllowed()
                || pluginResultAuthorization==null || !pluginResultAuthorization.equals(officialPlugins.grant(activePlugin))) return;
        PluginSession.Delivery delivery=pluginSession.prepare(buffer); InputConnection connection=target;
        if(!ownsTarget() || !pluginSession.isCurrent(delivery,buffer)) return;
        int end=selection<0?-1:Math.min(selectionStart,selection)+delivery.text.length();
        if(connection.commitText(delivery.text,1) && connection==target && ownsTarget()) { expect(end); pluginSession.acknowledge(delivery,buffer); }
        render();
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
        if(weightRowOpen) {
            if(index<weightMarks.size()) {
                String mark=weightMarks.get(index);
                // Nine-key opens this row over an unfinished word. The punctuator
                // still commits that word; …… and —— stay a single literal.
                boolean finishWord=punctuationOpen && snapshot.composing() && !halfwidthMarks();
                if(punctuationOpen) punctuationOpen=false;
                typePunctuation(finishWord?punctuatorKey(mark):mark,mark);
            }
        } else if(snapshot.composing()) select(index);
    }
    private void candidatePage(boolean forward) {
        if(spellingOpen) { spellingPage=Math.max(0,Math.min(((spellingChoices.size()-1)/9)*9,spellingPage+(forward?9:-9))); render(); }
        else page(forward);
    }
    /** Ten marks fit in the strip. The dismiss key keeps its own column on the left. */
    private int weightCellWidth() {
        int span=candidateScroll!=null?candidateScroll.getWidth():0;
        if(span<=0 && candidateRow!=null) span=candidateRow.getWidth();
        if(span<=0 && keyboard!=null) span=keyboard.getWidth()-keyboard.getPaddingLeft()-keyboard.getPaddingRight();
        if(span<=0) span=getResources().getDisplayMetrics().widthPixels;
        return Math.max(1,span/10);
    }
    private void hideKeyboard() { requestHideSelf(0); }
    private void applyStripCell(KeyButton item,boolean weight,int cell) {
        LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)item.getLayoutParams();
        int width=weight?cell:LinearLayout.LayoutParams.WRAP_CONTENT;
        int margin=weight?0:dp(2);
        int pad=weight?0:dp(8);
        if(lp.width!=width || lp.rightMargin!=margin) {
            lp.width=width; lp.rightMargin=margin; item.setLayoutParams(lp);
        }
        if(item.getPaddingLeft()!=pad || item.getPaddingRight()!=pad) item.setPadding(pad,0,pad,0);
        int min=weight?0:dp(32);
        if(item.getMinimumWidth()!=min) { item.setMinWidth(min); item.setMinimumWidth(min); }
    }
    private void ensureCandidateButtons(int count) {
        if(candidateStrip==null) return;
        while(candidates.size()<count) {
            final int index=candidates.size();
            KeyButton candidate=button(candidateStrip,"",() -> candidateTapped(index),0);
            candidate.setLayoutParams(new LinearLayout.LayoutParams(-2,-1)); candidate.fontStyle(false,KeyboardTypography.candidateSp(landscape()),true); candidate.plain(true);
            candidate.setMinWidth(dp(32)); candidate.setMinimumWidth(dp(32)); candidate.setPadding(dp(8),0,dp(8),0);
            gapRight(candidate,2);
            candidate.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE); candidate.setTextSize(KeyboardTypography.candidateSp(landscape()));
            candidates.add(candidate);
        }
    }
    /** Everything that changes a key label, hint, enabled state, or face. Pinyin and candidates are not included. */
    private String keyStamp() {
        boolean spellingKey=pending==0 && !spellingChoices.isEmpty();
        boolean separatorKey=pending==0 && snapshot.composing() && !snapshot.raw.endsWith("'");
        int night=getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK;
        return visibleMode().name()+"|"+uppercase+"|"+english+"|"+directOnly+"|"+asciiOnly+"|"+numeric+"|"+symbols+"|"+emoji
                +"|"+schema+"|"+returnLabel()+"|"+returnSelected()
                +"|"+spellingKey+"|"+separatorKey+"|"+spellingOpen
                +"|"+theme.id+"|"+night+"|"+Math.round(heightFactor*1000f)+"|"+landscape()+"|"+ready;
    }
    private void render() {
        if(keyboard==null) return;
        KeyboardTheme.Palette palette=theme.palette(this); keyboard.setBackgroundColor(palette.background);
        for(KeyButton button:chromeButtons) button.theme(theme);
        setText(layoutButton,appearanceOpen?"✓":"⚙"); layoutButton.icon(appearanceOpen?KeyboardIcon.CHECK:KeyboardIcon.SETTINGS);
        layoutButton.setSelected(appearanceOpen);

        bufferButton.setContentDescription(getString(buffer.isEnabled()?R.string.buffer_on:R.string.buffer_off)); bufferButton.setSelected(buffer.isEnabled());
        bufferButton.setEnabled(buffer.isPermitted() && pending==0 && !snapshot.composing() && retained.isEmpty() && !chords.isChordActive());

        int desiredPreeditHeight=dp(landscape()?16:22);
        if(preedit.getLayoutParams().height!=desiredPreeditHeight) {
            preedit.getLayoutParams().height=desiredPreeditHeight;
            preedit.setTextSize(KeyboardTypography.preeditSp(landscape()));
            preedit.requestLayout();
        }

        String status=failed?getString(R.string.engine_failed):!ready?getString(R.string.engine_loading):"";
        if(!retained.isEmpty()) status=getString(R.string.delivery_pending);
        else if(status.isEmpty() && snapshot.composing() && englishDraft.isEmpty()) {
            status=!snapshot.preedit.isEmpty()?snapshot.preedit:snapshot.raw;
        }
        // The top row becomes the weight row only on 123/符号 with nothing to choose,
        // or when nine-key punctuation asks for that same row. Height stays put.
        boolean showWeight=status.isEmpty() && heldPreview==null && chordPreview.isEmpty()
                && ((punctuationOpen && !numeric && !emoji)
                    || (numeric && !emoji && !snapshot.composing() && snapshot.candidates.isEmpty()));
        weightRowOpen=showWeight;
        weightMarks=showWeight && punctuationWeights!=null?punctuationWeights.row(halfwidthMarks()):java.util.Collections.emptyList();
        setText(preedit,status); preedit.setTextColor(palette.ink);
        preedit.setVisibility(showWeight?View.GONE:!directOnly && !status.isEmpty()?View.VISIBLE:View.INVISIBLE);
        spellingChoices=nineKeyVisible()?spellings.choices(snapshot.raw):java.util.Collections.emptyList();
        if(spellingChoices.isEmpty()) spellingOpen=false;
        if(spellingPage>=spellingChoices.size()) spellingPage=0;
        candidateRow.setVisibility(View.VISIBLE);
        boolean idle=!snapshot.composing() && snapshot.candidates.isEmpty() && heldPreview==null && chordPreview.isEmpty() && !chords.isChordActive() && !showWeight && status.isEmpty();
        // Keep one height with or without candidates. Shrinking the idle row
        // resizes the keyboard and jumps the editor on every syllable.
        boolean shortcuts=idle && !privateField;
        int desiredRowHeight=dp(landscape()?50:64);
        if(candidateRow.getLayoutParams().height!=desiredRowHeight) {
            candidateRow.getLayoutParams().height=desiredRowHeight;
            candidateRow.requestLayout();
        }
        pluginShortcuts.setVisibility(shortcuts?View.VISIBLE:View.GONE);
        if(shortcuts) pluginShortcuts.render(theme,buffer.isEnabled()?activePlugin:null,canSelectPlugin(),officialPlugins::enabled);
        candidateBox.setVisibility(heldPreview==null && !idle?View.VISIBLE:View.GONE);
        candidateScroll.setVisibility(heldPreview==null && !idle && (showWeight || !snapshot.candidates.isEmpty())?View.VISIBLE:View.GONE);
        chordReadout.setVisibility(heldPreview==null?View.GONE:View.VISIBLE);
        chordReadout.render(heldPreview,theme,landscape());
        int count=!chordPreview.isEmpty()?1:showWeight?weightMarks.size():directOnly?0:snapshot.candidates.size();
        ensureCandidateButtons(count);
        int candidateSize=KeyboardTypography.candidateSp(landscape());
        int weightCell=showWeight?weightCellWidth():0;
        for(int i=0;i<candidates.size();i++) {
            KeyButton item=candidates.get(i); boolean exists=i<count;
            item.fontStyle(false,candidateSize,true);
            item.setVisibility(exists?View.VISIBLE:View.GONE);
            applyStripCell(item,showWeight && exists,weightCell);
            if(exists) {
                String value=!chordPreview.isEmpty()?chordPreview:showWeight?weightMarks.get(i):snapshot.candidates.get(i);
                setText(item,value);
                item.setContentDescription(showWeight?value:"候选"+(i+1)+" "+value);
                item.candidateHighlight(i==0 && !showWeight && chordPreview.isEmpty());
            } else {
                item.candidateHighlight(false);
            }
            item.setEnabled(exists && chordPreview.isEmpty());
        }
        // Confirming one segment of a phrase keeps the same raw spelling, so follow the words on screen.
        StringBuilder shown=new StringBuilder();
        shown.append(snapshot.preedit).append('\n').append(count).append('\n').append(showWeight?weightCell:0);
        for(int i=0;i<count;i++) shown.append('\n').append(candidates.get(i).getText());
        String nextShown=shown.toString();
        if(!nextShown.equals(renderedCandidates)) {
            candidateScroll.scrollTo(0,0);
            renderedCandidates=nextShown;
        }
        boolean composing=snapshot.composing() || !snapshot.candidates.isEmpty() || showWeight || !status.isEmpty();
        layoutButton.setVisibility(composing?View.GONE:View.VISIBLE);
        bufferButton.setVisibility(composing?View.GONE:View.VISIBLE);
        toolbarSlot(layoutButton,shortcuts);
        toolbarSlot(bufferButton,shortcuts);
        // Dismiss stays on the shortcut-chip size. Growing it while a word is open slides the glyph.
        toolbarSlot(hideKeyboardButton,true);
        previous.setVisibility(View.GONE);
        next.setVisibility(View.GONE);
        if(candidateGridButton!=null) {
            candidateGridButton.setVisibility(composing && !snapshot.candidates.isEmpty()?View.VISIBLE:View.GONE);
            candidateGridButton.setEnabled(!snapshot.candidates.isEmpty());
        }
        bufferRow.setVisibility(buffer.isEnabled()?View.VISIBLE:View.GONE);
        boolean plugin=buffer.isEnabled() && activePlugin!=null;
        if(plugin!=renderedPluginMode) {
            bufferTop.removeView(plugin?bufferRail:pluginOutput); bufferBottom.removeView(plugin?metrics:bufferRail);
            bufferTop.addView(plugin?pluginOutput:bufferRail,1,new LinearLayout.LayoutParams(0,-1,1));
            bufferBottom.addView(plugin?bufferRail:metrics,1,new LinearLayout.LayoutParams(0,-1,1));
            renderedPluginMode=plugin;
        }
        if(buffer.isEnabled()) {
            bufferRail.placeholder(plugin?pluginPlaceholder(activePlugin):"输入内容暂存于此");
            bufferRail.render(buffer.blocks(),snapshot.preedit,theme,landscape());
            String text=buffer.text(); setText(metrics,text.codePointCount(0,text.length())+" 字 · "+buffer.blockCount()+" 块");
            metrics.setContentDescription(null);
            if(plugin) {
                PluginSession.Snapshot state=pluginSession.snapshot(buffer);
                pluginOutput.placeholder(pluginStatus(state));
                pluginOutput.render(state.output.isEmpty()?java.util.Collections.emptyList():java.util.Collections.singletonList(state.output),"",theme,landscape());
                pluginOutput.setContentDescription("插件输出："+pluginName(activePlugin)+" · "+state.status+" · "+(state.output.isEmpty()?pluginStatus(state):state.output));
            }
        }
        metrics.setTextColor(palette.ink);
        if(metrics.getTag()==null || !metrics.getTag().equals(palette.ink)) { android.graphics.drawable.GradientDrawable output=new android.graphics.drawable.GradientDrawable(); output.setColor((palette.ink&0xffffff)|0x10000000); output.setCornerRadius(dp(7)); metrics.setBackground(output); metrics.setTag(palette.ink); }
        spellingRow.setVisibility(spellingOpen?View.VISIBLE:View.GONE);
        int spellingSize=KeyboardTypography.candidateSp(landscape());
        for(int i=0;i<9;i++) { KeyButton key=spellingButtons.get(i); key.fontStyle(false,spellingSize,true); boolean exists=spellingPage+i<spellingChoices.size(); key.setVisibility(exists?View.VISIBLE:View.GONE);
            if(exists) { String value=spellingChoices.get(spellingPage+i); setText(key,value); key.setContentDescription("拼音 "+value); } key.setEnabled(exists && pending==0); }
        PluginSession.Status pluginState=pluginSession.snapshot(buffer).status;
        pluginRunButton.setVisibility(plugin?View.VISIBLE:View.GONE); pluginRunButton.setContentDescription(pluginState==PluginSession.Status.RUNNING?"取消执行":"执行"+pluginName(activePlugin));
        pluginRunButton.icon(pluginState==PluginSession.Status.RUNNING?KeyboardIcon.STOP:KeyboardIcon.PLAY);
        pluginRunButton.setEnabled(plugin && pluginAllowed() && pending==0 && !snapshot.composing() && !chords.isChordActive() && buffer.blockCount()>0);
        pluginButton.setSelected(plugin);
        insertNext.setEnabled(pending==0 && !snapshot.composing() && buffer.blockCount()>0 && !chords.isChordActive() && (!plugin || pluginState==PluginSession.Status.READY));
        retryButton.setVisibility(failed || !retained.isEmpty()?View.VISIBLE:View.GONE);
        boolean chord=chordVisible();
        float width=(keyboard.getWidth()>0?keyboard.getWidth():getResources().getDisplayMetrics().widthPixels)-keyboard.getPaddingLeft()-keyboard.getPaddingRight();
        float surfaceHeight=chord?ChordLayout.height(Math.max(1,width/getResources().getDisplayMetrics().density),"splitOrthogonal".equals(layout)):KeyboardLayout.height(landscape(),heightFactor);
        boolean panelOpen=appearanceOpen || pluginSettingsOpen || clipboardOpen || candidateGridOpen;
        if(chord && panelOpen) surfaceHeight+=landscape()?35:41;
        int desiredHeight=Math.round(surfaceHeight*getResources().getDisplayMetrics().density);
        if(surfaceContainer.getLayoutParams().height!=desiredHeight) {
            surfaceContainer.getLayoutParams().height=desiredHeight;
            surfaceContainer.requestLayout();
            if(keyboard!=null) { keyboard.requestLayout(); keyboard.invalidate(); }
            if(getWindow()!=null && getWindow().getWindow()!=null) {
                getWindow().getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        }
        keys.setHeightFactor(heightFactor);
        String keyStamp=keyStamp();
        if(!keyStamp.equals(renderedKeyStamp)) {
            keys.render(visibleMode(),theme);
            renderedKeyStamp=keyStamp;
        }
        keys.setVisibility(panelOpen || chord?View.GONE:View.VISIBLE);
        if(chord) chords.render("splitOrthogonal".equals(layout),!english && !uppercase,uppercase,theme);
        chords.setVisibility(panelOpen || !chord?View.GONE:View.VISIBLE);
        chordFooter.setVisibility(!panelOpen && chord?View.VISIBLE:View.GONE);
        ((LinearLayout.LayoutParams)surfaceContainer.getLayoutParams()).bottomMargin=chord && !panelOpen?dp(1):0;
        if(chord) {
            String[] footerLabels={"123",uppercase?"⇪":"⇧","空格",english?"EN":"中",returnLabel()};
            String[] footerDescriptions={"数字与字母","Shift",getString(R.string.space),"中英切换",getString(R.string.enter)};
            for(int i=0;i<chordControls.size();i++) {
                KeyButton button=chordControls.get(i); button.fontStyle(false,KeyboardTypography.functionSp(landscape()),true); setText(button,footerLabels[i]); button.setContentDescription(footerDescriptions[i]);
                LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)button.getLayoutParams(); int keyWidth=i==2?0:Math.round((width/getResources().getDisplayMetrics().density-10)/7.5f*getResources().getDisplayMetrics().density);
                float weight=i==2?1:0; if(params.width!=keyWidth || params.weight!=weight) { params.width=keyWidth; params.weight=weight; button.requestLayout(); }
                button.setEnabled(!chords.isChordActive());
                button.icon(i==1?(uppercase?KeyboardIcon.SHIFT_FILL:KeyboardIcon.SHIFT):null);
                button.setSelected(i==1 && uppercase || i==3 && english || i==4 && returnSelected());
            }
        }
        insertNext.setSelected(insertNext.isEnabled());
        if(appearanceOpen) appearancePanel.schemes(schema,this::chooseSchema);
        appearancePanel.setVisibility(appearanceOpen?View.VISIBLE:View.GONE);
        if(appearanceOpen) appearancePanel.render(layout,theme,heightPercent,bottomInset);
        pluginPanel.setVisibility(pluginSettingsOpen?View.VISIBLE:View.GONE);
        if(pluginSettingsOpen) pluginPanel.render(theme,activePlugin,translationDirection);
        if(clipboardPanel!=null) {
            clipboardPanel.setVisibility(clipboardOpen?View.VISIBLE:View.GONE);
            if(clipboardOpen && clipboardStore!=null) clipboardPanel.render(clipboardStore.getEntries(),theme);
        }
        if(candidateGridPanel!=null) {
            candidateGridPanel.setVisibility(candidateGridOpen?View.VISIBLE:View.GONE);
            if(candidateGridOpen) {
                String gridPreedit=!snapshot.preedit.isEmpty()?snapshot.preedit:snapshot.raw;
                candidateGridPanel.render(snapshot.candidates,snapshot.comments,theme,snapshot.pageStart>0,!snapshot.lastPage,gridPreedit);
            }
        }
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
    /** Idle gear and Buffer match the 30dp shortcut chips and sit on the keys. */
    private void toolbarSlot(View view,boolean compact) {
        LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)view.getLayoutParams();
        int height=compact?dp(30):LinearLayout.LayoutParams.MATCH_PARENT;
        int gravity=compact?Gravity.BOTTOM:Gravity.NO_GRAVITY;
        if(params.height!=height || params.gravity!=gravity) {
            params.height=height; params.gravity=gravity; view.requestLayout();
        }
    }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
}
