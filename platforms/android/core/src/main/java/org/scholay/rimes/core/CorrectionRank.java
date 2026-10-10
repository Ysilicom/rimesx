package org.scholay.rimes.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ranks the pressed spelling against its same-row neighbors.
 * A tap in the middle trusts the pressed key. A tap in the outer third
 * lets that side compete by word weight. The next letter keeps a separate
 * list of spellings, so a full candidate bar cannot drop one.
 */
public final class CorrectionRank {
    public static final class Spelling {
        public final String raw;
        final List<String> texts, comments;
        final double[] qualities;
        public final boolean favored;
        public Spelling(String raw, List<String> texts, List<String> comments, double[] qualities) {
            this(raw,texts,comments,qualities,true);
        }
        public Spelling(String raw, List<String> texts, List<String> comments, double[] qualities, boolean favored) {
            this.raw=raw; this.texts=texts; this.comments=comments; this.qualities=qualities; this.favored=favored;
        }
        public double bestQuality() { return peak(qualities); }
        public Spelling withFavored(boolean favored) { return new Spelling(raw,texts,comments,qualities,favored); }
    }
    public static final class Offer {
        public final String text, comment, raw;
        public final int index;
        public final boolean favored;
        final double quality;
        public Offer(String text, String comment, String raw, int index, double quality) {
            this(text,comment,raw,index,quality,true);
        }
        public Offer(String text, String comment, String raw, int index, double quality, boolean favored) {
            this.text=text; this.comment=comment; this.raw=raw; this.index=index; this.quality=quality; this.favored=favored;
        }
    }
    /** One spelling the next letter continues. Favored means every step matched the landing zone. */
    public static final class Stem {
        public final String raw;
        public final boolean favored;
        public Stem(String raw, boolean favored) { this.raw=raw; this.favored=favored; }
        @Override public boolean equals(Object other) {
            if(!(other instanceof Stem)) return false;
            Stem stem=(Stem)other;
            return favored==stem.favored && raw.equals(stem.raw);
        }
        @Override public int hashCode() { return raw.hashCode()*2+(favored?1:0); }
    }
    /** A spelling tried for this key, before the candidate bar cuts the list. */
    public static final class Probe {
        public final String raw;
        public final boolean favored, hasWords;
        public final double bestQuality;
        public Probe(String raw, boolean favored, boolean hasWords, double bestQuality) {
            this.raw=raw; this.favored=favored; this.hasWords=hasWords; this.bestQuality=bestQuality;
        }
    }

    /** How many spellings the next letter keeps continuing. */
    public static final int STEM_LIMIT=8;
    /** Pressed words kept in front of a center tap, so a neighbor still fits on the bar. */
    public static final int CENTER_HEAD=4;
    /** Outer third of the key. biasX is -1 at the left edge and +1 at the right edge. */
    public static final float SEAM=1f/3f;

    public enum Side { LEFT, CENTER, RIGHT }

    private CorrectionRank() {}

    public static Side zone(float biasX) {
        if(biasX<-SEAM) return Side.LEFT;
        if(biasX>SEAM) return Side.RIGHT;
        return Side.CENTER;
    }

    public static double bestQuality(double[] qualities) { return peak(qualities); }

    private static double peak(double[] qualities) {
        double best=Double.NEGATIVE_INFINITY;
        if(qualities!=null) for(double quality:qualities) if(!Double.isNaN(quality) && quality>best) best=quality;
        return best==Double.NEGATIVE_INFINITY?Double.NaN:best;
    }

    /**
     * Spellings the next letter continues. The pressed spelling stays.
     * A favored slip stays even with no word yet. Other slips stay when they
     * have a word. The candidate bar does not decide this list.
     */
    public static List<Stem> selectStems(String pressedRaw,List<Probe> probes,int limit) {
        if(limit<1) limit=1;
        LinkedHashMap<String,Accumulator> found=new LinkedHashMap<>();
        if(probes!=null) for(Probe probe:probes) {
            if(probe==null || probe.raw==null || probe.raw.isEmpty()) continue;
            Accumulator accumulator=found.get(probe.raw);
            if(accumulator==null) found.put(probe.raw,accumulator=new Accumulator());
            accumulator.add(probe);
        }
        List<Stem> stems=new ArrayList<>();
        if(pressedRaw!=null && !pressedRaw.isEmpty()) {
            Accumulator pressed=found.get(pressedRaw);
            stems.add(new Stem(pressedRaw,pressed==null || pressed.favored));
        }
        List<Map.Entry<String,Accumulator>> favoredWords=new ArrayList<>(), otherWords=new ArrayList<>(), favoredEmpty=new ArrayList<>();
        for(Map.Entry<String,Accumulator> entry:found.entrySet()) {
            if(entry.getKey().equals(pressedRaw)) continue;
            Accumulator accumulator=entry.getValue();
            if(accumulator.favored && accumulator.hasWords) favoredWords.add(entry);
            else if(accumulator.hasWords) otherWords.add(entry);
            else if(accumulator.favored) favoredEmpty.add(entry);
        }
        favoredWords.sort(CorrectionRank::byQuality);
        otherWords.sort(CorrectionRank::byQuality);
        for(Map.Entry<String,Accumulator> entry:favoredWords) addStem(stems,entry,limit);
        for(Map.Entry<String,Accumulator> entry:otherWords) addStem(stems,entry,limit);
        for(Map.Entry<String,Accumulator> entry:favoredEmpty) addStem(stems,entry,limit);
        return stems.isEmpty()?null:stems;
    }

    private static void addStem(List<Stem> stems,Map.Entry<String,Accumulator> entry,int limit) {
        if(stems.size()>=limit) return;
        stems.add(new Stem(entry.getKey(),entry.getValue().favored));
    }

    private static int byQuality(Map.Entry<String,Accumulator> left,Map.Entry<String,Accumulator> right) {
        double a=left.getValue().bestQuality, b=right.getValue().bestQuality;
        boolean missingA=Double.isNaN(a), missingB=Double.isNaN(b);
        if(missingA && missingB) return 0;
        if(missingA) return 1;
        if(missingB) return -1;
        return Double.compare(b,a);
    }

    /** Drop the last letter after backspace. A beam that no longer matches the live spelling is dropped. */
    public static List<Stem> shorten(List<Stem> stems,String liveRaw) {
        if(stems==null || stems.isEmpty() || liveRaw==null || liveRaw.isEmpty()) return null;
        boolean aligned=false;
        for(Stem stem:stems) {
            if(stem!=null && stem.raw!=null && stem.raw.startsWith(liveRaw) && stem.raw.length()>liveRaw.length()) { aligned=true; break; }
        }
        if(!aligned) return null;
        LinkedHashMap<String,Boolean> next=new LinkedHashMap<>();
        next.put(liveRaw,Boolean.TRUE);
        for(Stem stem:stems) {
            if(stem==null || stem.raw==null || stem.raw.isEmpty()) continue;
            int cut=stem.raw.offsetByCodePoints(stem.raw.length(),-1);
            if(cut<=0) continue;
            String prefix=stem.raw.substring(0,cut);
            Boolean previous=next.get(prefix);
            if(previous==null || stem.favored) next.put(prefix,previous==null?stem.favored || prefix.equals(liveRaw):true);
        }
        List<Stem> shortened=new ArrayList<>();
        for(Map.Entry<String,Boolean> entry:next.entrySet()) shortened.add(new Stem(entry.getKey(),entry.getValue()));
        return shortened;
    }

    public static List<Offer> merge(List<Spelling> spellings) {
        return mergePrefer(spellings,null);
    }

    /** Typed spelling first, then the other spellings by weight. The same word keeps the typed spelling. */
    public static List<Offer> mergePrefer(List<Spelling> spellings,String preferredRaw) {
        return rank(offersOf(spellings,preferredRaw,true),offersOf(spellings,preferredRaw,false));
    }

    /**
     * Center tap: the pressed spelling's first words, then the other spellings by weight.
     * Seam tap: a favored neighbor competes with the pressed spelling by weight.
     * The same word keeps the better landing.
     */
    public static List<Offer> mergeByTouch(List<Spelling> spellings,String pressedRaw) {
        List<Offer> favored=new ArrayList<>(), other=new ArrayList<>();
        boolean neighborFavored=false;
        if(spellings!=null) for(Spelling spelling:spellings) {
            if(spelling==null || spelling.raw==null || spelling.texts==null) continue;
            List<Offer> into=spelling.favored?favored:other;
            int before=into.size();
            addOffers(into,spelling);
            if(spelling.favored && !spelling.raw.equals(pressedRaw) && into.size()>before) neighborFavored=true;
        }
        favored.sort((a,b) -> Double.compare(b.quality,a.quality));
        other.sort((a,b) -> Double.compare(b.quality,a.quality));
        if(neighborFavored) return rank(favored,other);
        List<Offer> head=new ArrayList<>(), tail=new ArrayList<>();
        for(Offer offer:favored) {
            if(pressedRaw!=null && pressedRaw.equals(offer.raw) && head.size()<CENTER_HEAD) head.add(offer);
            else tail.add(offer);
        }
        return rank(head,other,tail);
    }

    private static List<Offer> offersOf(List<Spelling> spellings,String preferredRaw,boolean preferred) {
        List<Offer> offers=new ArrayList<>();
        if(spellings==null) return offers;
        for(Spelling spelling:spellings) {
            if(spelling==null || spelling.raw==null || spelling.texts==null) continue;
            boolean matches=spelling.raw.equals(preferredRaw);
            if(matches!=preferred) continue;
            addOffers(offers,spelling);
        }
        offers.sort((a,b) -> Double.compare(b.quality,a.quality));
        return offers;
    }

    private static void addOffers(List<Offer> into,Spelling spelling) {
        for(int i=0;i<spelling.texts.size();i++) {
            String text=spelling.texts.get(i);
            if(text==null || text.isEmpty()) continue;
            String comment=spelling.comments!=null && i<spelling.comments.size() && spelling.comments.get(i)!=null?spelling.comments.get(i):"";
            double quality=spelling.qualities!=null && i<spelling.qualities.length && !Double.isNaN(spelling.qualities[i])?spelling.qualities[i]:-i;
            into.add(new Offer(text,comment,spelling.raw,i,quality,spelling.favored));
        }
    }

    @SafeVarargs private static List<Offer> rank(List<Offer>... groups) {
        List<Offer> ranked=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        for(List<Offer> group:groups) {
            if(group==null) continue;
            for(Offer offer:group) {
                if(offer==null || offer.text==null || offer.text.isEmpty() || !seen.add(offer.text)) continue;
                ranked.add(offer);
                if(ranked.size()==60) return ranked;
            }
        }
        return ranked;
    }

    private static final class Accumulator {
        boolean favored, hasWords;
        double bestQuality=Double.NaN;
        void add(Probe probe) {
            if(probe.favored) favored=true;
            if(!probe.hasWords) return;
            hasWords=true;
            if(Double.isNaN(bestQuality) || !Double.isNaN(probe.bestQuality) && probe.bestQuality>bestQuality) bestQuality=probe.bestQuality;
        }
    }
}
