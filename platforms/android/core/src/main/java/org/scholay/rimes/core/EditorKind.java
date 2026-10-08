package org.scholay.rimes.core;

/**
 * Which editors can compose Chinese.
 * The masks match android.text.InputType. FORCE_ASCII matches EditorInfo.IME_FLAG_FORCE_ASCII.
 * A field that names no class still composes. A hidden password, phone number, or date does not.
 */
public final class EditorKind {
    public static final int MASK_CLASS=0x0000000f;
    public static final int TYPE_NULL=0;
    public static final int CLASS_TEXT=0x00000001;
    public static final int CLASS_NUMBER=0x00000002;
    public static final int CLASS_PHONE=0x00000003;
    public static final int CLASS_DATETIME=0x00000004;
    public static final int MASK_VARIATION=0x00000ff0;
    public static final int TEXT_PASSWORD=0x00000080;
    public static final int TEXT_VISIBLE_PASSWORD=0x00000090;
    public static final int TEXT_WEB_PASSWORD=0x000000e0;
    public static final int NUMBER_PASSWORD=0x00000010;
    /** The field asked to start in Latin. The language key can still select Chinese. */
    public static final int FORCE_ASCII=0x80000000;
    /** EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING. */
    public static final int NO_PERSONALIZED_LEARNING=0x01000000;

    private EditorKind() {}

    /** Letters commit with no composition. An unspecified type returns false. */
    public static boolean directOnly(int inputType) {
        int kind=inputType&MASK_CLASS;
        if(kind==CLASS_NUMBER || kind==CLASS_PHONE || kind==CLASS_DATETIME) return true;
        if(password(inputType)) return true;
        return kind!=CLASS_TEXT && kind!=TYPE_NULL;
    }
    /** Hidden password and web password. A visible password stays a normal text field. */
    public static boolean password(int inputType) {
        int kind=inputType&MASK_CLASS, variation=inputType&MASK_VARIATION;
        return kind==CLASS_NUMBER && variation==NUMBER_PASSWORD
                || kind==CLASS_TEXT && (variation==TEXT_PASSWORD || variation==TEXT_WEB_PASSWORD);
    }
    /** A restart that arrived with no type keeps the field already bound, including a password lock. */
    public static boolean keepTypelessRestart(boolean restarting,int inputType,boolean textFieldLive,boolean directOnly) {
        return restarting && (inputType&MASK_CLASS)==TYPE_NULL && (textFieldLive || directOnly);
    }
    /**
     * Clipboard and AI stay on an editor that names no class.
     * A hidden password, a visible password, a phone or date, and a text field that
     * refuses learning stay without those tools.
     */
    public static boolean showsTools(int inputType,int imeOptions) {
        int kind=inputType&MASK_CLASS;
        if(password(inputType)) return false;
        if((inputType&MASK_VARIATION)==TEXT_VISIBLE_PASSWORD) return false;
        if(kind==TYPE_NULL) return true;
        if(kind!=CLASS_TEXT) return false;
        return (imeOptions&NO_PERSONALIZED_LEARNING)==0;
    }
    public static boolean asciiRequested(int imeOptions) { return (imeOptions&FORCE_ASCII)!=0; }
    /** True only on the visit that newly asks for Latin, so a restart keeps the user's 中/英 choice. */
    public static boolean enterAscii(boolean alreadyAscii,int imeOptions) {
        return asciiRequested(imeOptions) && !alreadyAscii;
    }
}
