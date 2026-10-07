package org.scholay.rimes.android;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Candidate grid. Cells stay allocated and only their labels change between refreshes. */
final class CandidateGridPanel extends ScrollView {
    interface Listener {
        void onSelectCandidate(int index);
        void onClose();
        void onPrevPage();
        void onNextPage();
    }

    private static final int COLUMNS=4;
    private final TextView title;
    private final TextView empty;
    private final KeyButton prevButton, nextButton, closeButton;
    private final LinearLayout gridContainer;
    private final ArrayList<LinearLayout> rows=new ArrayList<>();
    private final ArrayList<KeyButton> cells=new ArrayList<>();
    private final Listener listener;
    /** Null until the first refresh. A new preedit scrolls back to the top. */
    private String shownPreedit;

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

        empty = new TextView(context);
        empty.setText("暂无候选");
        empty.setTextSize(14);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, dp(24), 0, dp(24));
        empty.setVisibility(GONE);
        gridContainer.addView(empty, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    void render(List<String> candidates, List<String> comments, KeyboardTheme theme, boolean canPrev, boolean canNext) {
        render(candidates, comments, theme, canPrev, canNext, "");
    }

    void render(List<String> candidates, List<String> comments, KeyboardTheme theme, boolean canPrev, boolean canNext, String preedit) {
        KeyboardTheme.Palette palette = theme.palette(getContext());
        setBackgroundColor(palette.background);
        title.setTextColor(palette.ink);
        prevButton.theme(theme);
        prevButton.setEnabled(canPrev);
        show(prevButton, canPrev ? VISIBLE : GONE);
        nextButton.theme(theme);
        nextButton.setEnabled(canNext);
        show(nextButton, canNext ? VISIBLE : GONE);
        closeButton.theme(theme);

        String preeditKey = preedit == null ? "" : preedit;
        if (candidates == null || candidates.isEmpty()) {
            hideRows();
            empty.setTextColor(palette.ink & 0x88FFFFFF);
            show(empty, VISIBLE);
            rememberPreedit(preeditKey);
            return;
        }

        show(empty, GONE);
        int count = candidates.size();
        String heading = preeditKey.isEmpty() ? "候选字词 (" + count + ")" : "候选 · " + preeditKey + " (" + count + ")";
        if (!TextUtils.equals(title.getText(), heading)) title.setText(heading);
        ensureCells(count);
        boolean landscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        int font = KeyboardTypography.candidateSp(landscape);
        int usedRows = (count + COLUMNS - 1) / COLUMNS;
        for (int i = 0; i < cells.size(); i++) {
            KeyButton button = cells.get(i);
            int row = i / COLUMNS;
            if (row >= usedRows) break;
            if (i < count) {
                String display = displayText(candidates, comments, i);
                if (!TextUtils.equals(button.getText(), display)) {
                    button.setText(display);
                    button.setContentDescription("候选 " + (i + 1) + " " + display);
                }
                button.fontStyle(false, font, true);
                button.theme(theme);
                show(button, VISIBLE);
                if (button.getImportantForAccessibility() != IMPORTANT_FOR_ACCESSIBILITY_YES) {
                    button.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
                }
            } else {
                // Keep the last row's empty slots so the remaining words stay one quarter wide.
                show(button, INVISIBLE);
                if (button.getImportantForAccessibility() != IMPORTANT_FOR_ACCESSIBILITY_NO) {
                    button.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                }
            }
        }
        for (int row = 0; row < rows.size(); row++) show(rows.get(row), row < usedRows ? VISIBLE : GONE);
        rememberPreedit(preeditKey);
    }

    private void rememberPreedit(String preeditKey) {
        if (shownPreedit != null && shownPreedit.equals(preeditKey)) return;
        shownPreedit = preeditKey;
        scrollTo(0, 0);
    }

    private void hideRows() {
        for (LinearLayout row : rows) show(row, GONE);
    }

    private void ensureCells(int count) {
        while (cells.size() < count) {
            int index = cells.size();
            if (index % COLUMNS == 0) {
                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(44));
                rowParams.bottomMargin = dp(4);
                gridContainer.addView(row, rowParams);
                rows.add(row);
            }
            KeyButton button = new KeyButton(getContext());
            button.plain(true);
            button.setSingleLine(true);
            button.setEllipsize(TextUtils.TruncateAt.END);
            int slot = index;
            button.setOnClickListener(v -> listener.onSelectCandidate(slot));
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
            if (index % COLUMNS > 0) itemParams.leftMargin = dp(4);
            rows.get(index / COLUMNS).addView(button, itemParams);
            cells.add(button);
        }
    }

    private static String displayText(List<String> candidates, List<String> comments, int index) {
        String text = candidates.get(index);
        String comment = comments != null && index < comments.size() ? comments.get(index) : null;
        return comment == null || comment.isEmpty() ? text : text + " " + comment;
    }

    private static void show(View view, int visibility) {
        if (view.getVisibility() != visibility) view.setVisibility(visibility);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
