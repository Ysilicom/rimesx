package org.scholay.rimes.android;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.scholay.rimes.core.Punctuation;

/** One weight row. . and 。 keep their own counts and can both appear in it. */
final class PunctuationWeights {
    private static final String PREFERENCES="rimes_punctuation";
    private static final String COUNTS="counts";
    private final SharedPreferences preferences;
    private final Map<String,Integer> counts=new HashMap<>();

    PunctuationWeights(SharedPreferences preferences) {
        if(preferences==null) throw new IllegalArgumentException("Punctuation preferences are required");
        this.preferences=preferences;
        String shared=preferences.getString(COUNTS,null);
        if(shared!=null) Punctuation.decode(shared,counts);
        else foldLegacy();
    }

    static PunctuationWeights open(android.content.Context context) {
        return new PunctuationWeights(context.getSharedPreferences(PREFERENCES,android.content.Context.MODE_PRIVATE));
    }

    void note(String mark,boolean englishMode) {
        if(!Punctuation.tracked(mark,englishMode)) return;
        counts.put(mark,Punctuation.bump(mark,englishMode,counts.get(mark)));
        preferences.edit().putString(COUNTS,Punctuation.encode(counts)).apply();
    }

    List<String> row(boolean englishMode) {
        return Punctuation.weightRow(englishMode,counts);
    }

    /** Older builds stored zh and en apart. The same character is combined; . is not added to 。. */
    private void foldLegacy() {
        Map<String,Integer> legacy=new HashMap<>();
        Punctuation.decode(preferences.getString("zh",""),legacy);
        for(Map.Entry<String,Integer> entry:legacy.entrySet()) Punctuation.foldCount(counts,entry.getKey(),entry.getValue(),false);
        legacy.clear();
        Punctuation.decode(preferences.getString("en",""),legacy);
        for(Map.Entry<String,Integer> entry:legacy.entrySet()) Punctuation.foldCount(counts,entry.getKey(),entry.getValue(),true);
        if(!counts.isEmpty()) preferences.edit().putString(COUNTS,Punctuation.encode(counts)).apply();
    }
}
