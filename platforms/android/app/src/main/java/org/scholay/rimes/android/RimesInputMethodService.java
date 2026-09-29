package org.scholay.rimes.android;

import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;
import org.scholay.rimes.core.BufferSession;

/** Native Android delivery uses only the framework's current InputConnection. */
public final class RimesInputMethodService extends InputMethodService {
    private final BufferSession buffer = new BufferSession();
    private InputConnection target;
    private LinearLayout keyboard;
    private boolean uppercase;
    private boolean numeric;

    @Override public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        target = getCurrentInputConnection();
        uppercase = false;
        int inputClass = info.inputType & InputType.TYPE_MASK_CLASS;
        numeric = inputClass == InputType.TYPE_CLASS_NUMBER || inputClass == InputType.TYPE_CLASS_PHONE;
        buffer.beginTarget(target != null && allowsBuffer(info));
        render();
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (target == null) {
            target = getCurrentInputConnection();
            buffer.beginTarget(target != null && allowsBuffer(info));
        }
        render();
    }

    @Override public View onCreateInputView() {
        keyboard = new LinearLayout(this);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.setBackgroundColor(getColor(android.R.color.background_light));
        keyboard.setPadding(dp(4), dp(6), dp(4), dp(6));
        keyboard.setOnApplyWindowInsetsListener((view, insets) -> {
            // The IME window can extend behind system navigation on recent Android versions.
            // Use the remaining insets so framework-reserved space is not counted twice.
            int left, right, bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets safe = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = safe.left;
                right = safe.right;
                bottom = safe.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(dp(4) + left, dp(6), dp(4) + right, dp(6) + bottom);
            return insets;
        });
        render();
        return keyboard;
    }

    @Override public boolean onEvaluateFullscreenMode() { return false; }

    @Override public void onFinishInputView(boolean finishingInput) {
        endTarget();
        super.onFinishInputView(finishingInput);
    }

    @Override public void onFinishInput() {
        endTarget();
        super.onFinishInput();
    }

    @Override public void onUnbindInput() {
        endTarget();
        super.onUnbindInput();
    }

    @Override public void onDestroy() {
        endTarget();
        super.onDestroy();
    }

    private void endTarget() {
        target = null;
        buffer.finishTarget();
        render();
    }

    static boolean allowsBuffer(EditorInfo info) {
        int kind = info.inputType & InputType.TYPE_MASK_CLASS;
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        if (kind != InputType.TYPE_CLASS_TEXT) return false;
        if ((info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) return false;
        return variation != InputType.TYPE_TEXT_VARIATION_PASSWORD
                && variation != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                && variation != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
    }

    private boolean ownsTarget() { return target != null && target == getCurrentInputConnection(); }

    private void type(String text) {
        if (!ownsTarget()) { endTarget(); return; }
        if (buffer.isEnabled()) {
            if (!buffer.appendLiteral(text)) Toast.makeText(this, R.string.buffer_limit, Toast.LENGTH_SHORT).show();
        } else {
            target.commitText(text, 1);
        }
        render();
    }

    private void insert(boolean all) {
        BufferSession.Delivery delivery = buffer.prepare(all);
        InputConnection connection = target;
        if (!ownsTarget() || !buffer.isCurrent(delivery)) return;
        boolean accepted = connection.commitText(delivery.text, 1);
        if (accepted && connection == target && ownsTarget()) buffer.acknowledge(delivery);
        render();
    }

    private void delete() {
        if (!ownsTarget()) { endTarget(); return; }
        if (buffer.isEnabled()) buffer.deleteLastBlock();
        else {
            CharSequence selection = target.getSelectedText(0);
            if (selection != null && selection.length() > 0) target.commitText("", 1);
            else target.deleteSurroundingTextInCodePoints(1, 0);
        }
        render();
    }

    private void enter() {
        if (!ownsTarget()) { endTarget(); return; }
        if (buffer.isEnabled()) insert(false);
        else if (!sendDefaultEditorAction(true)) target.commitText("\n", 1);
    }

    private void render() {
        if (keyboard == null) return;
        keyboard.removeAllViews();
        LinearLayout toolbar = row();
        Button toggle = button(toolbar, buffer.isEnabled() ? getString(R.string.buffer_on) : getString(R.string.buffer_off),
                () -> { buffer.setEnabled(!buffer.isEnabled()); render(); }, 2);
        toggle.setEnabled(buffer.isPermitted());
        button(toolbar, "🌐", () -> getSystemService(InputMethodManager.class).showInputMethodPicker(), 1)
                .setContentDescription(getString(R.string.switch_keyboard));
        if (buffer.isEnabled()) {
            boolean landscape = getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE;
            TextView preview = new TextView(this);
            preview.setText(buffer.text().isEmpty() ? getString(R.string.buffer_empty) : buffer.text());
            preview.setTextSize(18);
            preview.setSingleLine(true);
            preview.setPadding(dp(10), dp(8), dp(10), dp(8));
            HorizontalScrollView scroll = new HorizontalScrollView(this);
            scroll.addView(preview);
            // Share the wide toolbar in landscape so Buffer cannot push the keys off screen.
            if (landscape) toolbar.addView(scroll, new LinearLayout.LayoutParams(0, -1, 3));
            else keyboard.addView(scroll, new LinearLayout.LayoutParams(-1, dp(44)));
            LinearLayout actions = landscape ? toolbar : row();
            button(actions, getString(R.string.insert_next), () -> insert(false), 1).setEnabled(buffer.blockCount() > 0);
            button(actions, getString(R.string.insert_all), () -> insert(true), 1).setEnabled(buffer.blockCount() > 0);
            button(actions, getString(R.string.clear), () -> { buffer.clear(); render(); }, 1);
        }
        String[] rows = numeric ? new String[]{"1234567890", "@#$%&*()-", ",.!?:;'\"/"}
                : new String[]{"qwertyuiop", "asdfghjkl", "zxcvbnm"};
        for (String keys : rows) {
            LinearLayout row = row();
            for (int offset = 0; offset < keys.length(); offset++) {
                String key = keys.substring(offset, offset + 1);
                String text = uppercase && !numeric ? key.toUpperCase(Locale.ROOT) : key;
                button(row, text, () -> type(text), 1);
            }
        }
        LinearLayout bottom = row();
        button(bottom, numeric ? "ABC" : "123", () -> { numeric = !numeric; render(); }, 1);
        button(bottom, uppercase ? "⇧●" : "⇧", () -> { uppercase = !uppercase; render(); }, 1);
        button(bottom, getString(R.string.space), () -> type(" "), 2);
        button(bottom, "⌫", this::delete, 1).setContentDescription(getString(R.string.backspace));
        button(bottom, "↵", this::enter, 1).setContentDescription(getString(R.string.enter));
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        keyboard.addView(row, new LinearLayout.LayoutParams(-1, dp(48)));
        return row;
    }

    private Button button(LinearLayout row, String title, Runnable action, float weight) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(title.length() > 4 ? 11 : 16);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(0, 0, 0, 0);
        button.setOnClickListener(view -> action.run());
        row.addView(button, new LinearLayout.LayoutParams(0, -1, weight));
        return button;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
