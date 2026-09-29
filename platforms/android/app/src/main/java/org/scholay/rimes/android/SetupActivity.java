package org.scholay.rimes.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class SetupActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int inset = Math.round(24 * getResources().getDisplayMetrics().density);
        content.setPadding(inset, inset, inset, inset);
        TextView title = new TextView(this);
        title.setText(R.string.welcome);
        title.setTextSize(28);
        content.addView(title);
        for (int message : new int[]{R.string.scope, R.string.privacy}) {
            TextView text = new TextView(this);
            text.setText(message);
            text.setTextSize(16);
            text.setPadding(0, inset, 0, inset);
            content.addView(text);
        }
        Button enable = new Button(this);
        enable.setText(R.string.enable_keyboard);
        enable.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        content.addView(enable);
        Button choose = new Button(this);
        choose.setText(R.string.choose_keyboard);
        choose.setOnClickListener(view -> getSystemService(InputMethodManager.class).showInputMethodPicker());
        content.addView(choose);
        for (int hint : new int[]{R.string.try_typing, R.string.second_field, R.string.password_field}) {
            EditText input = new EditText(this);
            input.setHint(hint);
            input.setInputType(InputType.TYPE_CLASS_TEXT | (hint == R.string.password_field
                    ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_FLAG_MULTI_LINE));
            input.setSaveEnabled(false);
            input.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
            content.addView(input);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        FrameLayout frame = new FrameLayout(this);
        frame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        frame.setOnApplyWindowInsetsListener((view, insets) -> {
            // Keep every playground field scrollable above the IME in edge-to-edge windows.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        setContentView(frame);
    }
}
