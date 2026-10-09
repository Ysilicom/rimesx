package org.scholay.rimes.android;

/** One line of cursor travel, counted in characters. 0 means that line is not there. */
final class CursorLines {
    private CursorLines() {}

    /**
     * Signed steps from the cursor. Negative moves toward the start.
     * Returns 0 when that line is absent, or when the current line's start was not in the fetch.
     */
    static int lineDelta(String before,String after,boolean down) {
        if(before==null || after==null) return 0;
        if(!down) {
            int nl=before.lastIndexOf('\n');
            if(nl<0) return 0;
            int column=before.length()-nl-1;
            String above=before.substring(0,nl);
            int prevNl=above.lastIndexOf('\n');
            int prevLen=prevNl<0?above.length():above.length()-prevNl-1;
            int targetCol=Math.min(column,prevLen);
            return -(column+1+(prevLen-targetCol));
        }
        int nl=after.indexOf('\n');
        if(nl<0) return 0;
        int lastNl=before.lastIndexOf('\n');
        if(lastNl<0 && before.length()>=CURSOR_LOOK) return 0;
        int column=lastNl>=0?before.length()-lastNl-1:before.length();
        int nextBreak=after.indexOf('\n',nl+1);
        int nextLen=nextBreak>=0?nextBreak-nl-1:after.length()-nl-1;
        int targetCol=Math.min(column,nextLen);
        return nl+1+targetCol;
    }

    /** Lookahead used to decide that a line has no break because the fetch was cut. */
    static final int CURSOR_LOOK=8192;
}
