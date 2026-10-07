package org.scholay.rimes.android;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

/** iOS-sized plugin entries occupying the idle candidate slot; actions belong to the service. */
final class PluginShortcutBar extends HorizontalScrollView {
    interface Listener {
        void onPluginTap(String id);
        void onPluginLongPress(String id);
        default void onPasteTap() {}
        default void onEmojiTap() {}
    }

    private static final String[] IDS={"translate","ask","polish","poem","art"};
    private static final String[] LABELS={"翻译","快问","润色","作诗","画画"};
    private static final KeyboardIcon[] ICONS={KeyboardIcon.TRANSLATE,KeyboardIcon.CHAT_QUESTION,
            KeyboardIcon.MAGIC_WAND,KeyboardIcon.BOOK,KeyboardIcon.GRID_9};
    private final KeyButton pasteButton,emojiButton;
    private final KeyButton[] buttons=new KeyButton[IDS.length];

    PluginShortcutBar(Context context,KeyboardTheme theme,Listener listener) {
        super(context);
        if(listener==null) throw new IllegalArgumentException("Plugin listener is required");
        setFillViewport(true); setHorizontalScrollBarEnabled(false); setOverScrollMode(View.OVER_SCROLL_NEVER);
        setPadding(0,0,0,0); setMinimumHeight(dp(32));
        LinearLayout row=new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER);
        addView(row,new HorizontalScrollView.LayoutParams(LayoutParams.WRAP_CONTENT,dp(32)));
        pasteButton=new KeyButton(context);
        pasteButton.setText("剪贴板"); pasteButton.setContentDescription("打开剪贴板历史选择粘贴");
        pasteButton.fontStyle(false,KeyboardTypography.shortcutSp(landscape()),true); pasteButton.appearance(true,true,true); pasteButton.shortcut(true);
        pasteButton.icon(KeyboardIcon.WRITE,12,true); pasteButton.theme(theme);
        pasteButton.setOnClickListener(view -> listener.onPasteTap());
        LinearLayout.LayoutParams pasteCell=new LinearLayout.LayoutParams(dp(72),dp(30));
        pasteCell.rightMargin=dp(6);
        row.addView(pasteButton,pasteCell);

        emojiButton=new KeyButton(context);
        emojiButton.setText("表情"); emojiButton.setContentDescription("打开表情面板");
        emojiButton.fontStyle(false,KeyboardTypography.shortcutSp(landscape()),true); emojiButton.appearance(true,true,true); emojiButton.shortcut(true);
        emojiButton.icon(KeyboardIcon.SMILE,12,true); emojiButton.theme(theme);
        emojiButton.setOnClickListener(view -> listener.onEmojiTap());
        LinearLayout.LayoutParams emojiCell=new LinearLayout.LayoutParams(dp(68),dp(30));
        emojiCell.rightMargin=dp(6);
        row.addView(emojiButton,emojiCell);

        for(int i=0;i<buttons.length;i++) {
            final String id=IDS[i];
            KeyButton button=new KeyButton(context);
            button.setText(LABELS[i]); button.setContentDescription("Buffer 插件："+LABELS[i]);
            button.fontStyle(false,KeyboardTypography.shortcutSp(landscape()),true); button.appearance(true,true,true); button.shortcut(true);
            button.icon(ICONS[i],12,true); button.theme(theme);
            button.setOnClickListener(view -> { if(button.isEnabled()) listener.onPluginTap(id); });
            button.setOnLongClickListener(view -> {
                if(button.isEnabled()) listener.onPluginLongPress(id);
                return true;
            });
            LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(dp(68),dp(30));
            if(i<buttons.length-1) cell.rightMargin=dp(6);
            row.addView(button,cell); buttons[i]=button;
        }
    }

    /** Keeps entries and scroll position stable while updating selection, availability and colors. */
    void render(KeyboardTheme theme,String selectedID,boolean enabled) {
        render(theme,selectedID,enabled,id -> true);
    }
    void render(KeyboardTheme theme,String selectedID,boolean available,java.util.function.Predicate<String> installed) {
        int label=KeyboardTypography.shortcutSp(landscape());
        pasteButton.fontStyle(false,label,true); pasteButton.theme(theme);
        emojiButton.fontStyle(false,label,true); emojiButton.theme(theme);
        for(int i=0;i<buttons.length;i++) {
            KeyButton button=buttons[i];
            boolean enabled=available && installed.test(IDS[i]);
            button.fontStyle(false,label,true);
            button.theme(theme);
            boolean selected=IDS[i].equals(selectedID);
            if(button.isSelected()!=selected) button.setSelected(selected);
            if(button.isEnabled()!=enabled) {
                if(!enabled) { button.cancelLongPress(); button.setPressed(false); }
                button.setEnabled(enabled);
            }
        }
    }

    private boolean landscape() { return getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE; }
    private int dp(float value) { return Math.round(value*getResources().getDisplayMetrics().density); }
}
