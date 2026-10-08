package org.scholay.rimes.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Fixed 123 marks and the one symbol page.
 * The weight row starts as the 16 marks. A symbol-page mark joins only when its
 * commit count is strictly higher than the lowest mark already in the row.
 */
public final class Punctuation {
    /** Starting count of each default mark, so one stray tap cannot evict it. */
    public static final int SEED=8;
    /** Row 2 of the number page, left to right. Ellipsis and dash commit as one string. */
    public static final String[] NUMBER_ROW={"，","。","、","？","！","……","：","；","——","·"};
    /** Row 3 of the number page, between 符号 and delete. */
    public static final String[] NUMBER_TAIL={"“","”","（","）","《","》"};
    /**
     * English face of each default, same index as the Chinese mark.
     * 、 and 《》 have no halfwidth twin and stay. The two quote keys are " and '.
     */
    public static final String[] ENGLISH={",",".","、","?","!","…",":",";","—","·","\"","'","(",")","《","》"};
    /** Same 10-key width as the digit row. : ; " return from the old number page. */
    public static final String[] SYMBOL_TOP={"_","|","\\","^",":",";","\"","~","[","]"};
    public static final String[] SYMBOL_MIDDLE={"{","}","<",">","(",")","@","#","%","*"};
    /** Six marks between the side keys, matching the number page. */
    public static final String[] SYMBOL_BOTTOM={"-","+","=","/","¥","$"};
    private static final String[] CHINESE=join(NUMBER_ROW,NUMBER_TAIL);
    private static final String[] SYMBOLS=join(SYMBOL_TOP,SYMBOL_MIDDLE,SYMBOL_BOTTOM);

    private Punctuation() {}

    /** Label and commit text. Symbol-page keys are already the character they insert. */
    public static String face(String keyText,boolean halfwidth) {
        if(keyText==null) return "";
        for(int i=0;i<CHINESE.length;i++) if(CHINESE[i].equals(keyText)) return halfwidth?ENGLISH[i]:CHINESE[i];
        return keyText;
    }

    public static boolean tracked(String mark,boolean english) {
        return mark!=null && (indexOf(english?ENGLISH:CHINESE,mark)>=0 || indexOf(SYMBOLS,mark)>=0);
    }

    /** Next stored count. Absent defaults start at {@link #SEED}; absent symbols start at zero. */
    public static int bump(String mark,boolean english,Integer stored) {
        if(!tracked(mark,english)) return stored==null?0:stored;
        int base=stored!=null?stored:indexOf(english?ENGLISH:CHINESE,mark)>=0?SEED:0;
        return base+1;
    }

    /**
     * Sixteen marks, most-used on the left. Equal counts keep the default order,
     * then symbol-page order. The big keys are not this list and do not move.
     */
    public static List<String> weightRow(boolean english,Map<String,Integer> stored) {
        String[] defaults=english?ENGLISH:CHINESE;
        List<String> row=new ArrayList<>(defaults.length);
        Collections.addAll(row,defaults);
        List<String> extras=new ArrayList<>();
        for(String mark:SYMBOLS) if(indexOf(defaults,mark)<0 && !extras.contains(mark)) extras.add(mark);
        boolean moved=true;
        while(moved) {
            moved=false;
            int min=Integer.MAX_VALUE, victim=0;
            for(int i=0;i<row.size();i++) {
                int count=countOf(row.get(i),english,stored);
                if(count<min || count==min && orderOf(row.get(i),english)>orderOf(row.get(victim),english)) {
                    min=count; victim=i;
                }
            }
            int best=-1, bestCount=min, bestOrder=Integer.MAX_VALUE;
            for(int i=0;i<extras.size();i++) {
                int count=countOf(extras.get(i),english,stored);
                if(count<=min) continue;
                int order=orderOf(extras.get(i),english);
                if(best<0 || count>bestCount || count==bestCount && order<bestOrder) {
                    best=i; bestCount=count; bestOrder=order;
                }
            }
            if(best>=0) {
                String entering=extras.remove(best);
                String leaving=row.remove(victim);
                row.add(entering);
                if(indexOf(defaults,leaving)<0) extras.add(leaving);
                moved=true;
            }
        }
        row.sort((a,b) -> {
            int byCount=Integer.compare(countOf(b,english,stored),countOf(a,english,stored));
            return byCount!=0?byCount:Integer.compare(orderOf(a,english),orderOf(b,english));
        });
        return Collections.unmodifiableList(row);
    }

    public static String encode(Map<String,Integer> counts) {
        StringBuilder out=new StringBuilder();
        if(counts==null) return "";
        for(Map.Entry<String,Integer> entry:counts.entrySet()) {
            if(entry.getKey()==null || entry.getKey().isEmpty() || entry.getValue()==null || entry.getValue()<0) continue;
            if(out.length()>0) out.append('\n');
            out.append(entry.getKey()).append('\t').append(entry.getValue());
        }
        return out.toString();
    }

    public static void decode(String raw,Map<String,Integer> into) {
        if(raw==null || raw.isEmpty() || into==null) return;
        for(String line:raw.split("\n",-1)) {
            int tab=line.indexOf('\t');
            if(tab<=0 || tab==line.length()-1) continue;
            try {
                int count=Integer.parseInt(line.substring(tab+1));
                if(count>=0) into.put(line.substring(0,tab),count);
            } catch(NumberFormatException ignored) { /* drop a corrupt line */ }
        }
    }

    private static int countOf(String mark,boolean english,Map<String,Integer> stored) {
        Integer value=stored==null?null:stored.get(mark);
        if(value!=null) return value;
        return indexOf(english?ENGLISH:CHINESE,mark)>=0?SEED:0;
    }

    private static int orderOf(String mark,boolean english) {
        String[] defaults=english?ENGLISH:CHINESE;
        int at=indexOf(defaults,mark);
        if(at>=0) return at;
        at=indexOf(SYMBOLS,mark);
        return at>=0?defaults.length+at:Integer.MAX_VALUE;
    }

    private static int indexOf(String[] marks,String mark) {
        for(int i=0;i<marks.length;i++) if(marks[i].equals(mark)) return i;
        return -1;
    }

    private static String[] join(String[]... rows) {
        int n=0; for(String[] row:rows) n+=row.length;
        String[] all=new String[n]; int at=0;
        for(String[] row:rows) { System.arraycopy(row,0,all,at,row.length); at+=row.length; }
        return all;
    }
}
