package org.scholay.rimes.android;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.widget.FrameLayout;

/** Draws the actual native vectors and accessible buttons. Invoke on the main thread. */
final class KeyboardIconContract {
    private enum State { NORMAL, PRESSED, SELECTED, DISABLED }
    private int checks;

    static int run(Context context) {
        KeyboardIconContract test=new KeyboardIconContract();
        for(boolean dark:new boolean[]{false,true}) {
            Configuration configuration=new Configuration(context.getResources().getConfiguration());
            configuration.uiMode=(configuration.uiMode&~Configuration.UI_MODE_NIGHT_MASK)
                    |(dark?Configuration.UI_MODE_NIGHT_YES:Configuration.UI_MODE_NIGHT_NO);
            test.runAppearance(context.createConfigurationContext(configuration),dark);
        }
        return test.checks;
    }

    private void runAppearance(Context context,boolean dark) {
        float density=context.getResources().getDisplayMetrics().density;
        Fixture actual=new Fixture(context,64,44,false);
        Fixture baseline=new Fixture(context,64,44,false);
        try {
            actual.key.setText("文字🦏");
            actual.key.setContentDescription("可访问的图标按钮");
            for(KeyboardIcon icon:KeyboardIcon.values()) {
                actual.key.icon(icon,24,false);
                for(State state:State.values()) {
                    actual.state(state); baseline.state(state);
                    actual.draw(); baseline.draw();
                    String label=(dark?"dark/":"light/")+icon+"/"+state;
                    Pixels pixels=compare(actual,baseline);
                    check(pixels.count>Math.max(8,Math.round(4*density*density)),label+" visible glyph");
                    check(pixels.left>=actual.width/2-13*density && pixels.right<=actual.width/2+13*density
                            && pixels.top>=actual.height/2-15*density && pixels.bottom<=actual.height/2+15*density,
                            label+" glyph fits requested centered viewport");
                    check(pixels.tintMatches>Math.max(1,Math.round(density*density)),label+" current state tint");
                    check("文字🦏".contentEquals(actual.key.getText())
                            && "可访问的图标按钮".contentEquals(actual.key.getContentDescription()),label+" native labels preserved");
                }
            }
        } finally { actual.close(); baseline.close(); }

        Fixture shortcut=new Fixture(context,68,30,true);
        Fixture shortcutBaseline=new Fixture(context,68,30,true);
        Fixture iconOnly=new Fixture(context,68,30,true);
        try {
            shortcut.key.fontStyle(false,13,true);
            shortcut.key.setText("翻译"); shortcut.key.setContentDescription("翻译 Buffer");
            shortcut.key.icon(KeyboardIcon.TRANSLATE,12,true);
            iconOnly.key.icon(KeyboardIcon.TRANSLATE,12,false);
            for(State state:State.values()) {
                shortcut.state(state); shortcutBaseline.state(state); iconOnly.state(state);
                shortcut.draw(); shortcutBaseline.draw(); iconOnly.draw();
                String label=(dark?"dark/":"light/")+"shortcut/"+state;
                Pixels combined=compare(shortcut,shortcutBaseline);
                Pixels glyph=compare(iconOnly,shortcutBaseline);
                check(combined.count>glyph.count+Math.max(4,Math.round(8*density*density)),label+" Chinese label and glyph drawn");
                check(combined.right-combined.left>glyph.right-glyph.left+5*density,label+" icon and label occupy distinct space");
                check(combined.left>=0 && combined.right<shortcut.width && combined.top>=0 && combined.bottom<shortcut.height,
                        label+" content fits chip");
                check(combined.tintMatches>Math.max(1,Math.round(density*density)),label+" current state tint");
                check("翻译".contentEquals(shortcut.key.getText())
                        && "翻译 Buffer".contentEquals(shortcut.key.getContentDescription()),label+" native labels preserved");
            }
        } finally { shortcut.close(); shortcutBaseline.close(); iconOnly.close(); }
    }

    private Pixels compare(Fixture actual,Fixture baseline) {
        Pixels pixels=new Pixels(actual.width,actual.height);
        int tint=actual.key.getCurrentTextColor();
        for(int y=0;y<actual.height;y++) for(int x=0;x<actual.width;x++) {
            int shown=actual.bitmap.getPixel(x,y),background=baseline.bitmap.getPixel(x,y);
            if(shown==background) continue;
            pixels.count++; pixels.left=Math.min(pixels.left,x); pixels.right=Math.max(pixels.right,x);
            pixels.top=Math.min(pixels.top,y); pixels.bottom=Math.max(pixels.bottom,y);
            if(closeColor(shown,over(tint,background))) pixels.tintMatches++;
        }
        return pixels;
    }
    private static int over(int foreground,int background) {
        int alpha=Color.alpha(foreground);
        return Color.rgb((Color.red(foreground)*alpha+Color.red(background)*(255-alpha)+127)/255,
                (Color.green(foreground)*alpha+Color.green(background)*(255-alpha)+127)/255,
                (Color.blue(foreground)*alpha+Color.blue(background)*(255-alpha)+127)/255);
    }
    private static boolean closeColor(int actual,int expected) {
        return Math.abs(Color.red(actual)-Color.red(expected))<=3
                && Math.abs(Color.green(actual)-Color.green(expected))<=3
                && Math.abs(Color.blue(actual)-Color.blue(expected))<=3;
    }
    private void check(boolean condition,String label) {
        checks++; if(!condition) throw new AssertionError("KeyboardIcon: "+label);
    }
    private static final class Pixels {
        int count,tintMatches,left,top,right=-1,bottom=-1;
        Pixels(int width,int height) { left=width; top=height; }
    }
    private static final class Fixture {
        final KeyButton key;
        final FrameLayout parent;
        final Bitmap bitmap;
        final int width,height;
        Fixture(Context context,int widthDp,int heightDp,boolean shortcut) {
            float density=context.getResources().getDisplayMetrics().density;
            width=Math.round(widthDp*density); height=Math.round(heightDp*density);
            key=new KeyButton(context); key.appearance(true,true,true); key.shortcut(shortcut);
            key.setText("");
            parent=new FrameLayout(context);
            parent.setBackgroundColor(KeyboardTheme.ALL[0].palette(context).background);
            parent.addView(key,new FrameLayout.LayoutParams(width,height));
            bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        }
        void state(State state) {
            key.setPressed(state==State.PRESSED); key.setSelected(state==State.SELECTED);
            key.setEnabled(state!=State.DISABLED);
        }
        void draw() {
            parent.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
            parent.layout(0,0,width,height);
            bitmap.eraseColor(Color.TRANSPARENT);
            // A parent draw applies the child TextView's scroll transform, as the actual IME does.
            parent.draw(new Canvas(bitmap));
        }
        void close() { bitmap.recycle(); }
    }
}
