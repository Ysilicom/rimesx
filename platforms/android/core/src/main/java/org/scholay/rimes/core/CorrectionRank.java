package org.scholay.rimes.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Merges words from several spellings into one list, highest weight first. */
public final class CorrectionRank {
    public static final class Spelling {
        public final String raw;
        final List<String> texts, comments;
        final double[] qualities;
        public Spelling(String raw, List<String> texts, List<String> comments, double[] qualities) {
            this.raw=raw; this.texts=texts; this.comments=comments; this.qualities=qualities;
        }
    }
    public static final class Offer {
        public final String text, comment, raw;
        public final int index;
        final double quality;
        public Offer(String text, String comment, String raw, int index, double quality) {
            this.text=text; this.comment=comment; this.raw=raw; this.index=index; this.quality=quality;
        }
    }

    private CorrectionRank() {}

    public static List<Offer> merge(List<Spelling> spellings) {
        List<Offer> found=new ArrayList<>();
        if(spellings!=null) {
            for(Spelling spelling:spellings) {
                if(spelling==null || spelling.raw==null || spelling.texts==null) continue;
                for(int i=0;i<spelling.texts.size();i++) {
                    String text=spelling.texts.get(i);
                    if(text==null || text.isEmpty()) continue;
                    String comment=spelling.comments!=null && i<spelling.comments.size() && spelling.comments.get(i)!=null?spelling.comments.get(i):"";
                    double quality=spelling.qualities!=null && i<spelling.qualities.length && !Double.isNaN(spelling.qualities[i])?spelling.qualities[i]:-i;
                    found.add(new Offer(text,comment,spelling.raw,i,quality));
                }
            }
        }
        found.sort((a,b) -> Double.compare(b.quality,a.quality));
        List<Offer> ranked=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        for(Offer offer:found) {
            if(seen.add(offer.text)) ranked.add(offer);
            if(ranked.size()==60) break;
        }
        return ranked;
    }
}
