package org.scholay.rimes.android;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/** Scrollable clipboard history panel allowing user to select and paste specific clips. */
final class ClipboardPanel extends ScrollView {
    interface Listener {
        void onPasteItem(String text);
        void onClear();
        void onClose();
    }

    private final TextView title;
    private final KeyButton clearButton, closeButton;
    private final LinearLayout listContainer;
    private final Listener listener;

    ClipboardPanel(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setFillViewport(true);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(10), dp(8), dp(10), dp(12));
        addView(column, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        column.addView(header, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(36)));

        title = new TextView(context);
        title.setText("剪贴板 · 点击选择粘贴");
        title.setTextSize(14);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        header.addView(title, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        clearButton = new KeyButton(context);
        clearButton.setText("清空");
        clearButton.font(13);
        clearButton.appearance(true, true, false);
        clearButton.icon(KeyboardIcon.CLEAR, 14, true);
        clearButton.setOnClickListener(v -> listener.onClear());
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(dp(72), dp(32));
        clearParams.rightMargin = dp(6);
        header.addView(clearButton, clearParams);

        closeButton = new KeyButton(context);
        closeButton.setText("返回");
        closeButton.font(13);
        closeButton.appearance(true, true, false);
        closeButton.icon(KeyboardIcon.CHEVRON_LEFT, 14, true);
        closeButton.setOnClickListener(v -> listener.onClose());
        header.addView(closeButton, new LinearLayout.LayoutParams(dp(72), dp(32)));

        listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, dp(6), 0, dp(6));
        column.addView(listContainer, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    void render(List<String> items, KeyboardTheme theme) {
        KeyboardTheme.Palette palette = theme.palette(getContext());
        setBackgroundColor(palette.background);
        title.setTextColor(palette.ink);
        clearButton.theme(theme);
        closeButton.theme(theme);

        listContainer.removeAllViews();
        if (items == null || items.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText("剪贴板历史为空，复制的内容将显示在这里");
            empty.setTextSize(13);
            empty.setTextColor(palette.ink & 0x88FFFFFF);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(32), 0, dp(32));
            listContainer.addView(empty, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            clearButton.setEnabled(false);
            return;
        }
        clearButton.setEnabled(true);

        float density = getResources().getDisplayMetrics().density;
        ColorStateList textColors = makeCardTextColor(palette);
        for (int i = 0; i < items.size(); i++) {
            final String text = items.get(i);
            TextView itemCard = new TextView(getContext());
            itemCard.setText(text);
            itemCard.setTextSize(14);
            itemCard.setTextColor(textColors);
            itemCard.setMaxLines(3);
            itemCard.setEllipsize(TextUtils.TruncateAt.END);
            itemCard.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            itemCard.setPadding(dp(12), dp(9), dp(12), dp(9));
            itemCard.setBackground(makeCardBackground(palette, density));
            itemCard.setClickable(true);
            itemCard.setFocusable(true);
            itemCard.setContentDescription("剪贴板条目：" + (text.length() > 20 ? text.substring(0, 20) + "…" : text));
            itemCard.setOnClickListener(v -> listener.onPasteItem(text));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(6);
            listContainer.addView(itemCard, params);
        }
    }

    private static Drawable makeCardBackground(KeyboardTheme.Palette palette, float density) {
        StateListDrawable states = new StateListDrawable();
        GradientDrawable pressed = new GradientDrawable();
        pressed.setCornerRadius(8 * density);
        pressed.setColor(palette.accent);

        GradientDrawable normal = new GradientDrawable();
        normal.setCornerRadius(8 * density);
        normal.setColor(palette.key);
        normal.setStroke(Math.max(1, Math.round(0.75f * density)), palette.dark ? 0x26FFFFFF : 0x1A000000);

        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{}, normal);
        return states;
    }

    private static ColorStateList makeCardTextColor(KeyboardTheme.Palette palette) {
        return new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_pressed}, new int[]{}},
                new int[]{palette.accentInk, palette.ink}
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
