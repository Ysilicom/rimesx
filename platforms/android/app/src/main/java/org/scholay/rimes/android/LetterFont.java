package org.scholay.rimes.android;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Typeface;

/** Latin key letters. Manrope is a geometric sans in the same vein as Gboard's Google Sans. */
final class LetterFont {
    private static Typeface face;
    private LetterFont() {}
    static Typeface get(Context context) {
        Typeface cached=face;
        if(cached!=null) return cached;
        synchronized(LetterFont.class) {
            if(face!=null) return face;
            Context app=context.getApplicationContext();
            AssetManager assets=(app!=null?app:context).getAssets();
            try {
                Typeface built=new Typeface.Builder(assets,"fonts/Manrope.ttf").setFontVariationSettings("'wght' 500").build();
                face=built!=null?built:Typeface.create("sans-serif",Typeface.NORMAL);
            } catch(RuntimeException ignored) {
                face=Typeface.create("sans-serif",Typeface.NORMAL);
            }
            return face;
        }
    }
}
