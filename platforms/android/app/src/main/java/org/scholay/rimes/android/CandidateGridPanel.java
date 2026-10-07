package org.scholay.rimes.android;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/** Expandable candidate grid panel for multi-row browsing of homophones and rare characters. */
final class CandidateGridPanel extends ScrollView {
    interface Listener {
        void onSelectCandidate(int index);
        void onClose();
        void onPrevPage();
        void onNextPage();
    }

    private final TextView title;
    private final KeyButton prevButton, nextButton, closeButton;
    private final LinearLayout gridContainer;
    private final Listener listener;

    CandidateGridPanel(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setFillViewport(true);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(8), dp(6), dp(8), dp(10));
        addView(column, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        column.addView(header, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(34)));

        title = new TextView(context);
        title.setText("全部候选");
        title.setTextSize(13);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.addView(title, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        prevButton = new KeyButton(context);
        prevButton.setText("‹");
        prevButton.font(14);
        prevButton.plain(true);
        prevButton.setContentDescription("上一页");
        prevButton.setOnClickListener(v -> listener.onPrevPage());
        header.addView(prevButton, new LinearLayout.LayoutParams(dp(44), dp(30)));

        nextButton = new KeyButton(context);
        nextButton.setText("›");
        nextButton.font(14);
        nextButton.plain(true);
        nextButton.setContentDescription("下一页");
        nextButton.setOnClickListener(v -> listener.onNextPage());
        header.addView(nextButton, new LinearLayout.LayoutParams(dp(44), dp(30)));

        closeButton = new KeyButton(context);
        closeButton.setText("⌃");
        closeButton.font(16);
        closeButton.appearance(true, true, false);
        closeButton.setContentDescription("收起候选面板");
        closeButton.setOnClickListener(v -> listener.onClose());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(52), dp(30));
        closeParams.leftMargin = dp(6);
        header.addView(closeButton, closeParams);

        gridContainer = new LinearLayout(context);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        gridContainer.setPadding(0, dp(4), 0, dp(4));
        column.addView(gridContainer, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    void render(List<String> candidates, List<String> comments, KeyboardTheme theme, boolean canPrev, boolean canNext) {
        KeyboardTheme.Palette palette = theme.palette(getContext());
        setBackgroundColor(palette.background);
        title.setTextColor(palette.ink);
        prevButton.theme(theme);
        prevButton.setEnabled(canPrev);
        prevButton.setVisibility(canPrev?VISIBLE:GONE);
        nextButton.theme(theme);
        nextButton.setEnabled(canNext);
        nextButton.setVisibility(canNext?VISIBLE:GONE);
        closeButton.theme(theme);

        gridContainer.removeAllViews();
        if (candidates == null || candidates.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText("暂无候选");
            empty.setTextSize(14);
            empty.setTextColor(palette.ink & 0x88FFFFFF);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(24), 0, dp(24));
            gridContainer.addView(empty, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            return;
        }

        int count = candidates.size();
        title.setText("候选字词 (" + count + ")");
        int cols = 4;
        LinearLayout currentRow = null;
        for (int i = 0; i < count; i++) {
            if (i % cols == 0) {
                currentRow = new LinearLayout(getContext());
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(44));
                rowParams.bottomMargin = dp(4);
                gridContainer.addView(currentRow, rowParams);
            }

            final int index = i;
            String text = candidates.get(i);
            String comment = (comments != null && i < comments.size()) ? comments.get(i) : null;
            String displayText = text + (comment != null && !comment.isEmpty() ? " " + comment : "");

            KeyButton btn = new KeyButton(getContext());
            btn.setText(displayText);
            btn.font(16);
            btn.plain(true);
            btn.setSingleLine(true);
            btn.setEllipsize(TextUtils.TruncateAt.END);
            btn.setContentDescription("候选 " + (index + 1) + " " + displayText);
            btn.theme(theme);
            btn.setOnClickListener(v -> listener.onSelectCandidate(index));

            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f);
            if (i % cols > 0) itemParams.leftMargin = dp(4);
            currentRow.addView(btn, itemParams);
        }

        // Fill remaining spaces in last row
        int remaining = count % cols;
        if (remaining > 0 && currentRow != null) {
            for (int j = remaining; j < cols; j++) {
                TextView spacer = new TextView(getContext());
                LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f);
                spacerParams.leftMargin = dp(4);
                currentRow.addView(spacer, spacerParams);
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
