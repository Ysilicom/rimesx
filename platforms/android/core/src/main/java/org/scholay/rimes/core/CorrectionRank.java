package org.scholay.rimes.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
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

    /** How many spellings the next letter keeps continuing. */
    public static final int STEM_LIMIT=8;

    private CorrectionRank() {}

    /**
     * Spellings the next letter continues. The live spelling stays, then the
     * typed one, then the heavier remaining spellings.
     */
    public static List<String> continueStems(String sessionRaw,String typedRaw,List<Offer> offers,int limit) {
        if(limit<1) limit=1;
        LinkedHashSet<String> stems=new LinkedHashSet<>();
        if(sessionRaw!=null && !sessionRaw.isEmpty()) stems.add(sessionRaw);
        if(typedRaw!=null && !typedRaw.isEmpty()) stems.add(typedRaw);
        if(offers!=null) {
            for(Offer offer:offers) {
                if(offer==null || offer.raw==null || offer.raw.isEmpty()) continue;
                stems.add(offer.raw);
                if(stems.size()>=limit) break;
            }
        }
        return stems.isEmpty()?null:new ArrayList<>(stems);
    }

    /** Drop the last letter after backspace. A beam that no longer matches the live spelling is dropped. */
    public static List<String> shorten(List<String> stems,String liveRaw) {
        if(stems==null || stems.isEmpty() || liveRaw==null || liveRaw.isEmpty()) return null;
        boolean aligned=false;
        for(String stem:stems) {
            if(stem!=null && stem.startsWith(liveRaw) && stem.length()>liveRaw.length()) { aligned=true; break; }
        }
        if(!aligned) return null;
        LinkedHashSet<String> next=new LinkedHashSet<>();
        next.add(liveRaw);
        for(String stem:stems) {
            if(stem==null || stem.isEmpty()) continue;
            int cut=stem.offsetByCodePoints(stem.length(),-1);
            if(cut<=0) continue;
            next.add(stem.substring(0,cut));
        }
        return new ArrayList<>(next);
    }

    public static List<Offer> merge(List<Spelling> spellings) {
        return mergePrefer(spellings,null);
    }

    /** Typed spelling first, then the other spellings by weight. The same word keeps the typed spelling. */
    public static List<Offer> mergePrefer(List<Spelling> spellings,String preferredRaw) {
        List<Offer> preferred=new ArrayList<>();
        List<Offer> rest=new ArrayList<>();
        if(spellings!=null) {
            for(Spelling spelling:spellings) {
                if(spelling==null || spelling.raw==null || spelling.texts==null) continue;
                List<Offer> into=spelling.raw.equals(preferredRaw)?preferred:rest;
                for(int i=0;i<spelling.texts.size();i++) {
                    String text=spelling.texts.get(i);
                    if(text==null || text.isEmpty()) continue;
                    String comment=spelling.comments!=null && i<spelling.comments.size() && spelling.comments.get(i)!=null?spelling.comments.get(i):"";
                    double quality=spelling.qualities!=null && i<spelling.qualities.length && !Double.isNaN(spelling.qualities[i])?spelling.qualities[i]:-i;
                    into.add(new Offer(text,comment,spelling.raw,i,quality));
                }
            }
        }
        preferred.sort((a,b) -> Double.compare(b.quality,a.quality));
        rest.sort((a,b) -> Double.compare(b.quality,a.quality));
        List<Offer> ranked=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        for(Offer offer:preferred) { if(seen.add(offer.text)) ranked.add(offer); if(ranked.size()==60) return ranked; }
        for(Offer offer:rest) { if(seen.add(offer.text)) ranked.add(offer); if(ranked.size()==60) break; }
        return ranked;
    }
}
