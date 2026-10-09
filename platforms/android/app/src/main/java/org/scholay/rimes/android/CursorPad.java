package org.scholay.rimes.android;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Blank key area shown while a space-bar hold moves the cursor. */
final class CursorPad extends FrameLayout {
    private final TextView label;
    private final View frame;
    private int painted=Integer.MIN_VALUE;

    CursorPad(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        frame=new View(context);
        int inset=dp(10);
        FrameLayout.LayoutParams box=new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT);
        box.setMargins(inset,inset,inset,inset);
        addView(frame,box);
        label=new TextView(context);
        label.setText(R.string.cursor_pad);
        label.setTextSize(15);
        label.setGravity(Gravity.CENTER);
        addView(label,new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT,Gravity.CENTER));
        setContentDescription(label.getText());
        setVisibility(GONE);
    }

    void render(KeyboardTheme theme) {
        KeyboardTheme.Palette palette=theme.palette(getContext());
        int stamp=palette.background*31+palette.ink;
        if(stamp==painted) return;
        painted=stamp;
        setBackgroundColor(palette.background);
        GradientDrawable outline=new GradientDrawable();
        outline.setColor(android.graphics.Color.TRANSPARENT);
        outline.setCornerRadius(dp(16));
        outline.setStroke(Math.max(1,dp(1)),(palette.ink&0x00ffffff)|0x66000000);
        frame.setBackground(outline);
        label.setTextColor((palette.ink&0x00ffffff)|0xCC000000);
    }

    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
}
