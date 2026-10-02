package org.scholay.rimes.android;
import android.app.Instrumentation;
import android.os.Bundle;

/** Exercises the actual packaged JNI library, including modified-UTF-8 traps. */
public final class EngineInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            runOnMainSync(this::keycapRendering);
            for(String value:new String[]{"中文","A𠮷😀Z","\u0000","你好\u0000𠮷"}) {
                if(!value.equals(NativeRimeEngine.roundTripNative(value))) throw new AssertionError("JNI Unicode round trip");
            }
            java.io.File data=EngineResources.prepare(getTargetContext());
            if(!data.isDirectory()) throw new AssertionError("packaged resources");
            EngineWorker.QUEUE.submit(() -> {
                NativeRimeEngine engine=new NativeRimeEngine();
                try {
                    engine.initialize(data.getAbsolutePath(),EngineResources.userDirectory(getTargetContext()).getAbsolutePath());
                    long session=engine.createSession();
                    if(!engine.selectSchema(session,"rimes_pinyin_private")) throw new AssertionError("private schema");
                    for(char key:"nihao".toCharArray()) engine.processKey(session,key);
                    org.scholay.rimes.core.RimeEngine.Snapshot partial=engine.selectCandidate(session,1);
                    if(!partial.preedit.contains("你") || partial.caret!=partial.preedit.length())
                        throw new AssertionError("UTF-8 byte caret to UTF-16: "+partial.preedit+" caret="+partial.caret);
                    engine.clearComposition(session);
                    for(String schema:new String[]{"rimes_pinyin9","rimes_pinyin9_private"}) {
                        if(!engine.selectSchema(session,schema)) throw new AssertionError("nine-key schema");
                        org.scholay.rimes.core.RimeEngine.Snapshot nine=org.scholay.rimes.core.RimeEngine.Snapshot.EMPTY;
                        for(char key:"64426".toCharArray()) nine=engine.processKey(session,key);
                        if(nine.candidates.isEmpty() || !nine.candidates.get(0).equals("你好")) throw new AssertionError("nine-key candidates: "+nine.candidates);
                        if(!engine.selectCandidate(session,0).commit.equals("你好")) throw new AssertionError("nine-key commit");
                        engine.clearComposition(session);
                    }
                    engine.destroySession(session);
                } catch(java.io.IOException error) { throw new RuntimeException(error); }
            }).get(10,java.util.concurrent.TimeUnit.SECONDS);
            android.view.inputmethod.EditorInfo info=new android.view.inputmethod.EditorInfo();
            for(int type:new int[]{android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                    android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                    android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD}) {
                info.inputType=type;
                if(!RimesInputMethodService.isPassword(info) || RimesInputMethodService.allowsBuffer(info)) throw new AssertionError("password policy");
            }
            info.inputType=android.text.InputType.TYPE_CLASS_TEXT;
            info.imeOptions=android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
            if(RimesInputMethodService.isPassword(info) || RimesInputMethodService.allowsBuffer(info)) throw new AssertionError("private field policy");
            result.putString("stream","PASS 18 keycap palettes in light/dark/pressed states and scrolled text; JNI Chinese/non-BMP/NUL round trips, UTF-16 preedit caret, resources, nine-key normal/private schemas and platform password/private policies\n"); finish(-1,result);
        } catch(Throwable error) { result.putString("stream","FAIL "+android.util.Log.getStackTraceString(error)); finish(0,result); }
    }
    private void keycapRendering() {
        for(int night:new int[]{android.content.res.Configuration.UI_MODE_NIGHT_NO,android.content.res.Configuration.UI_MODE_NIGHT_YES}) {
            android.content.res.Configuration configuration=new android.content.res.Configuration(getTargetContext().getResources().getConfiguration());
            configuration.uiMode=(configuration.uiMode&~android.content.res.Configuration.UI_MODE_NIGHT_MASK)|night;
            android.content.Context context=getTargetContext().createConfigurationContext(configuration);
            for(KeyboardTheme theme:KeyboardTheme.ALL) {
                android.widget.FrameLayout parent=new android.widget.FrameLayout(context);
                KeyButton key=new KeyButton(context); key.setText("q"); key.theme(theme); parent.addView(key,new android.widget.FrameLayout.LayoutParams(160,100));
                parent.measure(android.view.View.MeasureSpec.makeMeasureSpec(160,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(100,android.view.View.MeasureSpec.EXACTLY));
                parent.layout(0,0,160,100); key.scrollTo(20000,0);
                android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(160,100,android.graphics.Bitmap.Config.ARGB_8888);
                parent.draw(new android.graphics.Canvas(bitmap));
                if(bitmap.getPixel(30,30)!=theme.palette(context).key) throw new AssertionError("keycap viewport: "+theme.id+" / "+night);
                key.setPressed(true); parent.draw(new android.graphics.Canvas(bitmap));
                if(bitmap.getPixel(30,30)!=theme.palette(context).accent) throw new AssertionError("pressed keycap: "+theme.id);
                bitmap.recycle();
            }
        }
    }
}
