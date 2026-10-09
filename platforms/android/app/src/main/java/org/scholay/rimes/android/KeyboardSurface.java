package org.scholay.rimes.android;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import java.util.List;
import org.scholay.rimes.core.KeyboardLayout;

/** Measured geometry owns every key width. Candidate refreshes never rebuild the touch surface. */
final class KeyboardSurface extends ViewGroup {
    interface Handler {
        String label(KeyboardLayout.Key key);
        String description(KeyboardLayout.Key key);
        boolean enabled(KeyboardLayout.Key key);
        boolean selected(KeyboardLayout.Key key);
        void press(KeyboardLayout.Key key);
        default void press(KeyboardLayout.Key key, float biasX, float biasY) { press(key); }
        default boolean longPress(KeyboardLayout.Key key) { return false; }
        default void slideCursor(int steps) {}
        /** Negative is the previous line. */
        default void slideCursorLine(int lines) {}
        /** Space hold or sideways drag. The key area becomes the cursor pad until release. */
        default void beginCursorPad() {}
        default void endCursorPad() {}
        /** Swipe up on delete. A tap still deletes one character. */
        default void clearAll() {}
        default String hint(KeyboardLayout.Key key) { return hintForLetter(key.text); }
    }
    private KeyboardLayout.Mode mode;
    private List<KeyboardLayout.Key> frames;
    private final Handler handler;
    private KeyboardTheme theme=KeyboardTheme.ALL[0];
    KeyboardSurface(Context context,Handler handler) { super(context); this.handler=handler; setLayoutDirection(LAYOUT_DIRECTION_LTR); }
    void render(KeyboardLayout.Mode mode,KeyboardTheme theme) {
        this.theme=theme;
        if(this.mode!=mode) {
            this.mode=mode; removeAllViews();
            frames=KeyboardLayout.keys(400,landscape(),mode);
            for(KeyboardLayout.Key key:frames) {
                KeyButton button=new KeyButton(getContext());
                button.setOnClickListener(v -> handler.press(key, button.getTouchBiasX(), button.getTouchBiasY()));
                button.setOnLongClickListener(v -> handler.longPress(key));
                if(key.action==KeyboardLayout.Action.DELETE) {
                    setupDeleteRepeat(button,() -> handler.press(key));
                } else if(key.action==KeyboardLayout.Action.SPACE) {
                    setupSpaceCursorSlide(button);
                }
                addView(button);
            }
            requestLayout();
        }
        for(int i=0;i<getChildCount();i++) {
            KeyboardLayout.Key key=frames.get(i); KeyButton button=(KeyButton)getChildAt(i);
            boolean letter=key.action==KeyboardLayout.Action.TEXT;
            boolean functional=mode!=KeyboardLayout.Mode.NINE_KEY && !letter && key.action!=KeyboardLayout.Action.SPACE && key.action!=KeyboardLayout.Action.PUNCTUATION;
            button.appearance(functional,false,key.action==KeyboardLayout.Action.RETURN);
            button.icon(key.action==KeyboardLayout.Action.SHIFT?(handler.selected(key)?KeyboardIcon.SHIFT_FILL:KeyboardIcon.SHIFT)
                    :key.action==KeyboardLayout.Action.DELETE?KeyboardIcon.DELETE:key.action==KeyboardLayout.Action.EMOJI && mode!=KeyboardLayout.Mode.EMOJI?KeyboardIcon.SMILE:null);
            String label=handler.label(key); if(!android.text.TextUtils.equals(button.getText(),label)) button.setText(label);
            // Latin keys use Manrope at the letter size. Full-width punctuation keeps that size on the system face.
            boolean textKey=letter && mode!=KeyboardLayout.Mode.EMOJI;
            boolean nine=mode==KeyboardLayout.Mode.NINE_KEY;
            int baseFont=textKey?KeyboardTypography.letterSp(landscape(),nine):KeyboardTypography.functionSp(landscape());
            int font=Math.round(baseFont*(1.0f+(heightFactor-1.0f)*0.25f));
            if(textKey && fitsLetterFace(label)) button.letterFace(font); else button.fontStyle(false,font,true);
            String description=handler.description(key);
            if(!android.text.TextUtils.equals(button.getContentDescription(),description)) button.setContentDescription(description);
            String hint=mode==KeyboardLayout.Mode.QWERTY && letter?handler.hint(key):null;
            button.hint(hint);
            button.setEnabled(handler.enabled(key));
            button.setSelected(handler.selected(key)); button.theme(theme);
        }
    }
    /** Manrope covers Latin, digits and ASCII symbols. Full-width punctuation stays on the system face. */
    private static boolean fitsLetterFace(String text) {
        for(int i=0;i<text.length();i++) if(text.charAt(i)>0x024F) return false;
        return true;
    }
    static String hintForLetter(String letter) {
        if(letter==null || letter.length()!=1) return null;
        switch(Character.toLowerCase(letter.charAt(0))) {
            case 'q': return "1"; case 'w': return "2"; case 'e': return "3"; case 'r': return "4"; case 't': return "5";
            case 'y': return "6"; case 'u': return "7"; case 'i': return "8"; case 'o': return "9"; case 'p': return "0";
            default: return null;
        }
    }
    private void setupDeleteRepeat(KeyButton button,Runnable onDelete) {
        final float swipe=32f*getResources().getDisplayMetrics().density;
        final android.os.Handler mainHandler=new android.os.Handler(Looper.getMainLooper());
        button.setOnTouchListener(new OnTouchListener() {
            private boolean repeating=false,cleared=false;
            private int repeatCount=0;
            private float downX,downY;
            private final Runnable repeatTask=new Runnable() {
                @Override public void run() {
                    if(cleared) return;
                    repeating=true;
                    repeatCount++;
                    onDelete.run();
                    long nextDelay=repeatCount>15?32:50;
                    mainHandler.postDelayed(this,nextDelay);
                }
            };
            @Override public boolean onTouch(View v,MotionEvent event) {
                switch(event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        repeating=false; cleared=false; repeatCount=0;
                        downX=event.getRawX(); downY=event.getRawY();
                        mainHandler.removeCallbacks(repeatTask);
                        mainHandler.postDelayed(repeatTask,300);
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        if(cleared) return true;
                        float dx=event.getRawX()-downX,dy=event.getRawY()-downY;
                        // Upward travel has to dominate so a sideways slip still deletes one character.
                        if(dy<=-swipe && -dy>Math.abs(dx)*1.2f) {
                            cleared=true; repeating=false;
                            mainHandler.removeCallbacks(repeatTask);
                            v.cancelLongPress(); v.setPressed(false);
                            v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
                            handler.clearAll();
                            return true;
                        }
                        return false;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        mainHandler.removeCallbacks(repeatTask);
                        if(repeating || cleared) {
                            v.setPressed(false);
                            return true;
                        }
                        return false;
                }
                return false;
            }
        });
    }
    private void setupSpaceCursorSlide(KeyButton button) {
        final float density=getResources().getDisplayMetrics().density;
        final float stepPx=16f*density,linePx=24f*density;
        final android.os.Handler mainHandler=new android.os.Handler(Looper.getMainLooper());
        button.setOnTouchListener(new OnTouchListener() {
            private float downX,downY,lastX,lastY,stepX,stepY;
            private boolean pad,fingerDown;
            private final Runnable hold=new Runnable() {
                @Override public void run() {
                    if(!fingerDown || pad || !button.isAttachedToWindow()) return;
                    openPad();
                    track(lastX,lastY);
                }
            };
            private void openPad() {
                if(pad) return;
                pad=true;
                mainHandler.removeCallbacks(hold);
                button.cancelLongPress();
                button.setPressed(false);
                button.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
                handler.beginCursorPad();
            }
            private void track(float x,float y) {
                float dx=x-stepX;
                if(Math.abs(dx)>=stepPx) {
                    int steps=(int)(dx/stepPx);
                    stepX+=steps*stepPx;
                    if(steps!=0) handler.slideCursor(steps);
                }
                float dy=y-stepY;
                if(Math.abs(dy)>=linePx) {
                    int lines=(int)(dy/linePx);
                    stepY+=lines*linePx;
                    if(lines!=0) handler.slideCursorLine(lines);
                }
            }
            @Override public boolean onTouch(View v,MotionEvent event) {
                switch(event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX=lastX=stepX=event.getRawX();
                        downY=lastY=stepY=event.getRawY();
                        pad=false; fingerDown=true;
                        mainHandler.removeCallbacks(hold);
                        mainHandler.postDelayed(hold,300);
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        if(!fingerDown) return false;
                        lastX=event.getRawX(); lastY=event.getRawY();
                        if(!pad) {
                            float dx=lastX-downX,dy=lastY-downY;
                            if(Math.abs(dx)>stepPx && Math.abs(dx)>Math.abs(dy)*1.2f) openPad();
                        }
                        if(pad) { track(lastX,lastY); return true; }
                        return false;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        fingerDown=false;
                        mainHandler.removeCallbacks(hold);
                        if(pad) {
                            pad=false;
                            button.setPressed(false);
                            handler.endCursorPad();
                            return true;
                        }
                        return false;
                    default: return false;
                }
            }
        });
    }
    private float heightFactor=1.0f;
    void setHeightFactor(float factor) {
        if(Math.abs(this.heightFactor-factor)>0.001f) {
            this.heightFactor=factor;
            requestLayout();
        }
    }
    private boolean landscape() { return getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE; }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int width=MeasureSpec.getSize(widthSpec); float density=getResources().getDisplayMetrics().density;
        int height=Math.round(KeyboardLayout.height(landscape(),heightFactor)*density);
        setMeasuredDimension(width,resolveSize(height,heightSpec));
        if(mode==null) return;
        frames=KeyboardLayout.keys(Math.max(1,width/density),landscape(),mode,heightFactor);
        for(int i=0;i<frames.size();i++) {
            KeyboardLayout.Key key=frames.get(i);
            int w=Math.round((key.x+key.width)*density)-Math.round(key.x*density);
            int h=Math.round((key.y+key.height)*density)-Math.round(key.y*density);
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY));
            int x=Math.round(key.x*density),y=Math.round(key.y*density);
            int capX=Math.round(key.visualX*density),capY=Math.round(key.visualY*density);
            int capW=Math.round((key.visualX+key.visualWidth)*density)-capX;
            int capH=Math.round((key.visualY+key.visualHeight)*density)-capY;
            ((KeyButton)getChildAt(i)).capFrame((capX-x)/density,(capY-y)/density,capW/density,capH/density);
        }
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
        float density=getResources().getDisplayMetrics().density;
        if(frames==null) return;
        for(int i=0;i<frames.size();i++) {
            KeyboardLayout.Key key=frames.get(i); View child=getChildAt(i);
            int x=Math.round(key.x*density),y=Math.round(key.y*density);
            child.layout(x,y,x+child.getMeasuredWidth(),y+child.getMeasuredHeight());
        }
    }
}
