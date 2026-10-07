package org.scholay.rimes.android;

/** Letter, candidate, and function-key sizes stay within a few steps of each other. */
final class KeyboardTypography {
    private KeyboardTypography() {}

    /** Single Latin letters. Nine-key groups stay one step smaller so ABC fits the key. */
    static int letterSp(boolean landscape, boolean nineKey) {
        if(nineKey) return 18;
        return landscape?20:22;
    }

    /** Candidate words, kept next to the letter size. */
    static int candidateSp(boolean landscape) {
        return landscape?18:20;
    }

    /** 123, 中/英, 空格, 换行. Short labels match the candidates; long ones scale down on the key. */
    static int functionSp(boolean landscape) {
        return landscape?16:18;
    }

    /** Pinyin line above the candidates. */
    static int preeditSp(boolean landscape) {
        return landscape?14:16;
    }

    /** Idle shortcut chips. They share a 30dp row with an icon, so they sit one step under the keys. */
    static int shortcutSp(boolean landscape) {
        return landscape?15:16;
    }
}
