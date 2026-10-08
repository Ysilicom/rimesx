package org.scholay.rimes.android;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.scholay.rimes.core.Punctuation;

/** Chinese and English commit counts, kept apart. Defaults stay at the seed until they are used. */
final class PunctuationWeights {
    private static final String PREFERENCES="rimes_punctuation";
    private static final String CHINESE="zh";
    private static final String ENGLISH="en";
    private final SharedPreferences preferences;
    private final Map<String,Integer> chinese=new HashMap<>();
    private final Map<String,Integer> english=new HashMap<>();

    PunctuationWeights(SharedPreferences preferences) {
        if(preferences==null) throw new IllegalArgumentException("Punctuation preferences are required");
        this.preferences=preferences;
        Punctuation.decode(preferences.getString(CHINESE,""),chinese);
        Punctuation.decode(preferences.getString(ENGLISH,""),english);
    }

    static PunctuationWeights open(android.content.Context context) {
        return new PunctuationWeights(context.getSharedPreferences(PREFERENCES,android.content.Context.MODE_PRIVATE));
    }

    void note(String mark,boolean englishMode) {
        if(!Punctuation.tracked(mark,englishMode)) return;
        Map<String,Integer> counts=englishMode?english:chinese;
        counts.put(mark,Punctuation.bump(mark,englishMode,counts.get(mark)));
        preferences.edit().putString(englishMode?ENGLISH:CHINESE,Punctuation.encode(counts)).apply();
    }

    List<String> row(boolean englishMode) {
        return Punctuation.weightRow(englishMode,englishMode?english:chinese);
    }
}
