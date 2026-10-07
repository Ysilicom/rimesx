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

/** Scrollable clipboard history panel allowing user to select, paste, and pin clips. */
final class ClipboardPanel extends ScrollView {
    interface Listener {
        void onPasteItem(String text);
        void onTogglePin(String text);
        void onClear(boolean all);
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
        title.setText("剪贴板 · 点击粘贴 / 长按固定");
        title.setTextSize(13);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        header.addView(title, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        clearButton = new KeyButton(context);
        clearButton.setText("清空");
        clearButton.font(12);
        clearButton.appearance(true, true, false);
        clearButton.icon(KeyboardIcon.CLEAR, 13, true);
        clearButton.setOnClickListener(v -> listener.onClear(false));
        clearButton.setOnLongClickListener(v -> {
            listener.onClear(true);
            return true;
        });
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(dp(84), dp(32));
        clearParams.rightMargin = dp(6);
        header.addView(clearButton, clearParams);

        closeButton = new KeyButton(context);
        closeButton.setText("返回");
        closeButton.font(12);
        closeButton.appearance(true, true, false);
        closeButton.icon(KeyboardIcon.CHEVRON_LEFT, 13, true);
        closeButton.setOnClickListener(v -> listener.onClose());
        header.addView(closeButton, new LinearLayout.LayoutParams(dp(68), dp(32)));

        listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, dp(6), 0, dp(6));
        column.addView(listContainer, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    void render(List<ClipboardStore.Entry> items, KeyboardTheme theme) {
        KeyboardTheme.Palette palette = theme.palette(getContext());
        setBackgroundColor(palette.background);
        title.setTextColor(palette.ink);
        clearButton.theme(theme);
        closeButton.theme(theme);

        listContainer.removeAllViews();
        if (items == null || items.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText("剪贴板历史为空，复制的内容将自动保存在这里");
            empty.setTextSize(13);
            empty.setTextColor(palette.ink & 0x88FFFFFF);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(32), 0, dp(32));
            listContainer.addView(empty, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            clearButton.setText("清空");
            clearButton.setEnabled(false);
            return;
        }
        int pinnedCount = 0;
        for (ClipboardStore.Entry e : items) if (e.pinned) pinnedCount++;
        if (pinnedCount > 0) {
            clearButton.setText("清空未固");
            clearButton.setContentDescription("点击清空未固定内容，长按清空全部");
        } else {
            clearButton.setText("清空");
            clearButton.setContentDescription("点击清空剪贴板历史");
        }
        clearButton.setEnabled(true);

        float density = getResources().getDisplayMetrics().density;
        ColorStateList textColors = makeCardTextColor(palette);

        for (int i = 0; i < items.size(); i++) {
            final ClipboardStore.Entry entry = items.get(i);
            final String text = entry.text;
            final boolean isPinned = entry.pinned;

            LinearLayout card = new LinearLayout(getContext());
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(10), dp(8), dp(10), dp(8));
            card.setBackground(makeCardBackground(palette, density, isPinned));
            card.setClickable(true);
            card.setFocusable(true);
            card.setContentDescription((isPinned ? "已固定：" : "") + (text.length() > 20 ? text.substring(0, 20) + "…" : text));
            card.setOnClickListener(v -> listener.onPasteItem(text));
            card.setOnLongClickListener(v -> {
                listener.onTogglePin(text);
                return true;
            });

            if (isPinned) {
                TextView pinBadge = new TextView(getContext());
                pinBadge.setText("📌");
                pinBadge.setTextSize(13);
                pinBadge.setPadding(0, 0, dp(6), 0);
                card.addView(pinBadge, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            }

            TextView itemText = new TextView(getContext());
            itemText.setText(text);
            itemText.setTextSize(14);
            itemText.setTextColor(textColors);
            itemText.setMaxLines(3);
            itemText.setEllipsize(TextUtils.TruncateAt.END);
            itemText.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            card.addView(itemText, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f));

            TextView pinToggle = new TextView(getContext());
            pinToggle.setText(isPinned ? "取消固定" : "固定");
            pinToggle.setTextSize(11);
            pinToggle.setTextColor(palette.accent);
            pinToggle.setPadding(dp(6), dp(4), dp(4), dp(4));
            pinToggle.setOnClickListener(v -> listener.onTogglePin(text));
            card.addView(pinToggle, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(6);
            listContainer.addView(card, params);
        }
    }

    private static Drawable makeCardBackground(KeyboardTheme.Palette palette, float density, boolean pinned) {
        StateListDrawable states = new StateListDrawable();
        GradientDrawable pressed = new GradientDrawable();
        pressed.setCornerRadius(8 * density);
        pressed.setColor(palette.accent);

        GradientDrawable normal = new GradientDrawable();
        normal.setCornerRadius(8 * density);
        normal.setColor(palette.key);
        int strokeColor = pinned ? palette.accent : (palette.dark ? 0x26FFFFFF : 0x1A000000);
        int strokeWidth = pinned ? Math.max(1, Math.round(1.5f * density)) : Math.max(1, Math.round(0.75f * density));
        normal.setStroke(strokeWidth, strokeColor);

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
