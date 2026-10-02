package org.scholay.rimes.android;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.widget.Button;

/** A native accessible button with a full touch cell and an inset, visibly pressed keycap. */
final class KeyButton extends Button {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cap=new RectF();
    private KeyboardTheme.Palette palette;
    private KeyboardTheme currentTheme;
    private int currentUiMode=-1;
    private boolean functional,compact,accent;
    KeyButton(Context context) {
        super(context);
        setAllCaps(false); setMinWidth(0); setMinimumWidth(0); setMinHeight(0); setMinimumHeight(0);
        int vertical=Math.round(4*getResources().getDisplayMetrics().density);
        setPadding(1,vertical,1,vertical); setBackgroundColor(android.graphics.Color.TRANSPARENT);
        setStateListAnimator(null); setElevation(0); setIncludeFontPadding(false);
        setSingleLine(true); setGravity(android.view.Gravity.CENTER);
        setAutoSizeTextTypeUniformWithConfiguration(10,24,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        theme(KeyboardTheme.ALL[0]);
    }
    void appearance(boolean functional,boolean compact,boolean accent) {
        this.functional=functional; this.compact=compact; this.accent=accent; invalidate();
    }
    void theme(KeyboardTheme theme) {
        int uiMode=getResources().getConfiguration().uiMode;
        if(currentTheme==theme && currentUiMode==uiMode) return;
        currentTheme=theme; currentUiMode=uiMode;
        palette=theme.palette(getContext());
        setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{android.R.attr.state_pressed},
                new int[]{android.R.attr.state_selected},new int[]{}},
                new int[]{(palette.ink&0xFFFFFF)|0x66000000,palette.accentInk,palette.accentInk,palette.ink}));
        invalidate();
    }
    void font(int size) { setAutoSizeTextTypeUniformWithConfiguration(10,size,1,android.util.TypedValue.COMPLEX_UNIT_SP); }
    @Override public boolean performClick() { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); return super.performClick(); }
    @Override protected void drawableStateChanged() { super.drawableStateChanged(); invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        // Single-line TextView centers its text in a horizontally scrolling layout.
        // Caps belong to the view viewport, not to that text layout's scroll offset.
        int saved=canvas.save(); canvas.translate(getScrollX(),getScrollY());
        float density=getResources().getDisplayMetrics().density;
        float x=2.5f*density,y=(compact?3:5)*density,radius=(palette.system?5:7)*density;
        cap.set(x,y,getWidth()-x,getHeight()-y);
        boolean lit=isPressed() || isSelected() || accent;
        if(!compact) {
            cap.offset(0,density); paint.setColor(0x33000000); canvas.drawRoundRect(cap,radius,radius,paint); cap.offset(0,-density);
        }
        paint.setColor(lit?palette.accent:functional?palette.functional:palette.key);
        if(!isEnabled()) paint.setAlpha(90);
        canvas.drawRoundRect(cap,radius,radius,paint);
        if(!palette.system && !compact) {
            paint.setColor(0x22000000); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(density*0.6f);
            canvas.drawRoundRect(cap,radius,radius,paint); paint.setStyle(Paint.Style.FILL);
        }
        canvas.restoreToCount(saved); super.onDraw(canvas);
    }
}
