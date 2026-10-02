package org.scholay.rimes.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** iOS-style staggered rows. Frames include the gaps so touch targets remain contiguous. */
public final class KeyboardLayout {
    public enum Mode { QWERTY, NINE_KEY, NUMERIC, SYMBOLS, EMOJI }
    public enum Action { TEXT, SHIFT, DELETE, RETURN, NUMBERS, SYMBOLS, LANGUAGE, EMOJI, SPACE, SPELLING, SEPARATOR, PUNCTUATION }
    public static final String[] EMOJIS={"😀","😄","😂","🥹","😊","😍","😘","😎","🤔","😅",
            "😭","🥰","👍","🙏","❤️","🎉","🔥","✨","🌹","💪","👌","🤝","👏","💯","☀️","🌙","🍀","☕","🐱","🐶"};
    public static final class Key {
        public final Action action;
        public final String text;
        public final float x,y,width,height;
        Key(Action action,String text,float x,float y,float width,float height) {
            this.action=action; this.text=text; this.x=x; this.y=y; this.width=width; this.height=height;
        }
    }
    private KeyboardLayout() {}
    public static int height(boolean landscape) { return landscape?144:224; }
    public static List<Key> keys(float width,boolean landscape,Mode mode) {
        if(width<=0) throw new IllegalArgumentException("Keyboard width must be positive");
        List<Key> result=new ArrayList<>(); float row=height(landscape)/4f;
        if(mode==Mode.NINE_KEY) {
            float unit=width/5;
            add(result,Action.NUMBERS,"",0,0,1,1,unit,row);
            add(result,Action.PUNCTUATION,"",1,0,1,1,unit,row);
            add(result,Action.DELETE,"",4,0,1,1,unit,row);
            add(result,Action.SYMBOLS,"",0,1,1,1,unit,row);
            add(result,Action.SEPARATOR,"",4,1,1,1,unit,row);
            add(result,Action.LANGUAGE,"",0,2,1,1,unit,row);
            add(result,Action.RETURN,"",4,2,1,2,unit,row);
            add(result,Action.EMOJI,"",0,3,1,1,unit,row);
            add(result,Action.SPELLING,"",1,3,1,1,unit,row);
            add(result,Action.SPACE,"",2,3,2,1,unit,row);
            for(int digit=2;digit<=9;digit++) add(result,Action.TEXT,String.valueOf(digit),
                    (digit-1)%3+1,(digit-1)/3,1,1,unit,row);
        } else {
            float unit=width/10;
            if(mode==Mode.EMOJI) {
                for(int i=0;i<EMOJIS.length;i++) add(result,Action.TEXT,EMOJIS[i],i%10,i/10,1,1,unit,row);
            } else {
                String[] rows=mode==Mode.QWERTY?new String[]{"qwertyuiop","asdfghjkl","zxcvbnm"}
                        :mode==Mode.NUMERIC?new String[]{"1234567890","-/:;()$&@\"",".,?!'"}
                        :new String[]{"[]{}#%^*+=","_\\|~<>€£¥•",".,?!'"};
                for(int r=0;r<3;r++) {
                    String letters=rows[r]; float start=(10-letters.length())/2f;
                    for(int i=0;i<letters.length();i++) add(result,Action.TEXT,letters.substring(i,i+1),start+i,r,1,1,unit,row);
                }
                add(result,mode==Mode.QWERTY?Action.SHIFT:Action.SYMBOLS,"",0,2,1.3f,1,unit,row);
                add(result,Action.DELETE,"",8.7f,2,1.3f,1,unit,row);
            }
            Action[] footer=mode==Mode.EMOJI
                    ?new Action[]{Action.NUMBERS,Action.EMOJI,Action.LANGUAGE,Action.SPACE,Action.DELETE}
                    :new Action[]{Action.NUMBERS,Action.EMOJI,Action.LANGUAGE,Action.SPACE,Action.RETURN};
            float[] weights={1,1,1,4.8f,2.2f}; float x=0;
            for(int i=0;i<footer.length;i++) { add(result,footer[i],"",x,3,weights[i],1,unit,row); x+=weights[i]; }
        }
        return Collections.unmodifiableList(result);
    }
    private static void add(List<Key> keys,Action action,String text,float x,float y,float w,float h,float unit,float row) {
        keys.add(new Key(action,text,x*unit,y*row,w*unit,h*row));
    }
}
