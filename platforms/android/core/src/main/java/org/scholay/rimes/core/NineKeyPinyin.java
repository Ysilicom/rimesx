package org.scholay.rimes.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/** Spelling constraints for the same digit-derived Rime prism used on iOS. */
public final class NineKeyPinyin {
    private static final String[] GROUPS={"abc","def","ghi","jkl","mno","pqrs","tuv","wxyz"};
    private final List<String> syllables;
    public NineKeyPinyin(Collection<String> source) {
        TreeSet<String> valid=new TreeSet<>();
        for(String value:source) if(value!=null && value.matches("[a-z]{1,8}")) valid.add(value);
        syllables=new ArrayList<>(valid);
    }
    public static String digits(String spelling) {
        StringBuilder out=new StringBuilder();
        for(char c:spelling.toLowerCase(Locale.ROOT).toCharArray()) {
            boolean found=false;
            for(int i=0;i<GROUPS.length;i++) if(GROUPS[i].indexOf(c)>=0) { out.append(i+2); found=true; break; }
            if(!found) out.append(c);
        }
        return out.toString();
    }
    private static int start(String raw) { for(int i=0;i<raw.length();i++) if(digit(raw.charAt(i))) return i; return -1; }
    private static boolean digit(char c) { return c>='2' && c<='9'; }
    public List<String> choices(String raw) {
        List<String> result=new ArrayList<>(); int start=start(raw); if(start<0) return result;
        String pending=raw.substring(start);
        for(String syllable:syllables) if(pending.startsWith(digits(syllable))) result.add(syllable);
        result.sort((a,b) -> a.length()==b.length()?a.compareTo(b):Integer.compare(b.length(),a.length()));
        return result;
    }
    public String select(String syllable,String raw) {
        int start=start(raw); if(start<0 || !syllables.contains(syllable)) return null;
        String digits=digits(syllable); if(!raw.substring(start).startsWith(digits)) return null;
        String rest=raw.substring(start+digits.length());
        return raw.substring(0,start)+syllable+(rest.startsWith("'")?"":"'")+rest;
    }
    public static String backspace(String raw) {
        if(raw.isEmpty()) return raw;
        String rest=raw.substring(0,raw.length()-1);
        if(!raw.endsWith("'")) return rest;
        int boundary=rest.lastIndexOf('\'')+1; String last=rest.substring(boundary);
        return last.matches("[a-z]+")?rest.substring(0,boundary)+digits(last):rest;
    }
}
