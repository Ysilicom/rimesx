package org.scholay.rimes.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** iOS cap geometry with separate, contiguous touch cells. Dimensions are in dp. */
public final class KeyboardLayout {
    public enum Mode { QWERTY, NINE_KEY, NUMERIC, SYMBOLS, EMOJI }
    public enum Action { TEXT, SHIFT, DELETE, RETURN, NUMBERS, SYMBOLS, LANGUAGE, EMOJI, SPACE, SPELLING, SEPARATOR, PUNCTUATION }
    public static final String[] EMOJIS={"😀","😄","😂","🥹","😊","😍","😘","😎","🤔","😅",
            "😭","🥰","👍","🙏","❤️","🎉","🔥","✨","🌹","💪","👌","🤝","👏","💯","☀️","🌙","🍀","☕","🐱","🐶"};
    public static final class Key {
        public final Action action;
        public final String text;
        public final float x,y,width,height;
        public final float visualX,visualY,visualWidth,visualHeight;
        Key(Action action,String text,float x,float y,float width,float height,
                float visualX,float visualY,float visualWidth,float visualHeight) {
            this.action=action; this.text=text; this.x=x; this.y=y; this.width=width; this.height=height;
            this.visualX=visualX; this.visualY=visualY; this.visualWidth=visualWidth; this.visualHeight=visualHeight;
        }
    }
    private KeyboardLayout() {}
    public static int height(boolean landscape,float scale) { return Math.max(80,Math.round((landscape?143:206)*scale)); }
    public static int height(boolean landscape) { return height(landscape,1.0f); }
    public static List<Key> keys(float width,boolean landscape,Mode mode) {
        return keys(width,landscape,mode,1.0f);
    }
    public static List<Key> keys(float width,boolean landscape,Mode mode,float scale) {
        if(width<=0) throw new IllegalArgumentException("Keyboard width must be positive");
        List<Key> result=new ArrayList<>(); float h=height(landscape,scale); float row=h/4f;
        float gap=landscape?5:6,rowGap=landscape?5:10,capHeight=(h-3*rowGap)/4f;
        if(mode==Mode.NINE_KEY) {
            float unit=width/5,capUnit=(width-4*gap)/5;
            nine(result,Action.NUMBERS,"",0,0,1,1,unit,row,capUnit,capHeight,gap,rowGap);
            nine(result,Action.PUNCTUATION,"",1,0,1,1,unit,row,capUnit,capHeight,gap,rowGap);
            nine(result,Action.DELETE,"",4,0,1,1,unit,row,capUnit,capHeight,gap,rowGap);
            nine(result,Action.SYMBOLS,"",0,1,1,1,unit,row,capUnit,capHeight,gap,rowGap);
            nine(result,Action.SEPARATOR,"",4,1,1,1,unit,row,capUnit,capHeight,gap,rowGap);
            nine(result,Action.LANGUAGE,"",0,2,1,1,unit,row,capUnit,capHeight,gap,rowGap);
            nine(result,Action.RETURN,"",4,2,1,2,unit,row,capUnit,capHeight,gap,rowGap);
            nine(result,Action.EMOJI,"",0,3,1,1,unit,row,capUnit,capHeight,gap,rowGap);
            nine(result,Action.SPELLING,"",1,3,1,1,unit,row,capUnit,capHeight,gap,rowGap);
            nine(result,Action.SPACE,"",2,3,2,1,unit,row,capUnit,capHeight,gap,rowGap);
            for(int digit=2;digit<=9;digit++) nine(result,Action.TEXT,String.valueOf(digit),
                    (digit-1)%3+1,(digit-1)/3,1,1,unit,row,capUnit,capHeight,gap,rowGap);
        } else {
            float unit=width/10,capUnit=(width-9*gap)/10;
            if(mode==Mode.EMOJI) {
                for(int i=0;i<EMOJIS.length;i++) letter(result,EMOJIS[i],i%10,i/10,unit,row,capUnit,capHeight,gap,rowGap);
            } else if(mode==Mode.SYMBOLS) {
                symbols(result,width,row,capHeight,gap,rowGap);
            } else {
                String[] rows=mode==Mode.QWERTY?new String[]{"qwertyuiop","asdfghjkl","zxcvbnm"}
                        :new String[]{"1234567890","-/:;()$&@\"",".,?!'"};
                for(int r=0;r<3;r++) {
                    String letters=rows[r]; float start=(10-letters.length())/2f;
                    for(int i=0;i<letters.length();i++) letter(result,letters.substring(i,i+1),start+i,r,unit,row,capUnit,capHeight,gap,rowGap);
                }
                float sideWidth=Math.max(capUnit,width*0.115f);
                add(result,mode==Mode.QWERTY?Action.SHIFT:Action.SYMBOLS,"",0,2*row,1.3f*unit,row,
                        0,2*(capHeight+rowGap),sideWidth,capHeight);
                add(result,Action.DELETE,"",8.7f*unit,2*row,1.3f*unit,row,
                        width-sideWidth,2*(capHeight+rowGap),sideWidth,capHeight);
            }
            Action[] footer=mode==Mode.EMOJI
                    ?new Action[]{Action.NUMBERS,Action.EMOJI,Action.SPACE,Action.LANGUAGE,Action.DELETE}
                    :mode==Mode.QWERTY
                    ?new Action[]{Action.NUMBERS,Action.PUNCTUATION,Action.SPACE,Action.LANGUAGE,Action.RETURN}
                    :new Action[]{Action.NUMBERS,Action.SPACE,Action.LANGUAGE,Action.RETURN};
            float[] weights=mode==Mode.QWERTY
                    ?new float[]{1.4f,1.2f,4.0f,1.4f,2.0f}
                    :mode==Mode.EMOJI
                    ?new float[]{1.5f,1.5f,4.0f,1.5f,1.5f}
                    :new float[]{1.5f,5.0f,1.5f,2.0f};
            float x=0,footerUnit=(width-(footer.length-1)*gap)/10f;
            for(int i=0;i<footer.length;i++) {
                float w=footerUnit*weights[i];
                float left=i==0?0:x-gap/2,right=i==footer.length-1?width:x+w+gap/2;
                add(result,footer[i],"",left,3*row,right-left,row,x,3*(capHeight+rowGap),w,capHeight);
                x+=w+gap;
            }
        }
        return Collections.unmodifiableList(result);
    }
    /** Eleven columns so the third row can hold ellipsis, dash and middle dot beside the page and delete keys. */
    private static void symbols(List<Key> keys,float width,float row,float capHeight,float gap,float rowGap) {
        int columns=11;
        float unit=width/columns, capUnit=(width-(columns-1)*gap)/columns;
        String[] rows=new String[]{"[]{}#%^*+=《","_\\|~<>€£¥•》",".,?!'…—·"};
        for(int r=0;r<3;r++) {
            String letters=rows[r]; float start=(columns-letters.length())/2f;
            for(int i=0;i<letters.length();i++) letter(keys,letters.substring(i,i+1),start+i,r,unit,row,capUnit,capHeight,gap,rowGap);
        }
        float sideUnits=1.4f, sideWidth=Math.max(capUnit,width*0.115f);
        add(keys,Action.SYMBOLS,"",0,2*row,sideUnits*unit,row,0,2*(capHeight+rowGap),sideWidth,capHeight);
        add(keys,Action.DELETE,"",width-sideUnits*unit,2*row,sideUnits*unit,row,
                width-sideWidth,2*(capHeight+rowGap),sideWidth,capHeight);
    }
    private static void letter(List<Key> keys,String text,float column,int r,float unit,float row,
            float capUnit,float capHeight,float gap,float rowGap) {
        add(keys,Action.TEXT,text,column*unit,r*row,unit,row,
                column*(capUnit+gap),r*(capHeight+rowGap),capUnit,capHeight);
    }
    private static void nine(List<Key> keys,Action action,String text,int c,int r,int columns,int rows,
            float unit,float row,float capUnit,float capHeight,float gap,float rowGap) {
        add(keys,action,text,c*unit,r*row,columns*unit,rows*row,
                c*(capUnit+gap),r*(capHeight+rowGap),columns*capUnit+(columns-1)*gap,
                rows*capHeight+(rows-1)*rowGap);
    }
    private static void add(List<Key> keys,Action action,String text,float x,float y,float w,float h,
            float capX,float capY,float capW,float capH) {
        keys.add(new Key(action,text,x,y,w,h,capX,capY,capW,capH));
    }
}
