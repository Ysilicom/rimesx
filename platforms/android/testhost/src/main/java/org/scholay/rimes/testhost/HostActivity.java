package org.scholay.rimes.testhost;
import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.*;

/** Separate offline host; never included in the keyboard APK. Only synthetic test text. */
public final class HostActivity extends Activity {
    public EditText first, second, password, privateInput;
    public WebView web;
    public LinearLayout content;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(24,24,24,24);
        TextView title=new TextView(this); title.setText("RIMES independent input host"); content.addView(title);
        first=field("Native first",InputType.TYPE_CLASS_TEXT); second=field("Native second",InputType.TYPE_CLASS_TEXT);
        password=field("Password",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        privateInput=field("Private",InputType.TYPE_CLASS_TEXT); privateInput.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        Button showWeb=new Button(this); showWeb.setText("WebView"); content.addView(showWeb);
        web=new WebView(this); web.getSettings().setJavaScriptEnabled(true);
        web.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        web.loadDataWithBaseURL("https://rimes.test/","<meta name='viewport' content='width=device-width,initial-scale=1'><style>textarea,input{font-size:20px;width:90%;margin:4px}</style><label>Web first<textarea id='first' rows='2'></textarea></label><label>Web second<textarea id='second' rows='2'></textarea></label><input id='password' type='password' aria-label='Web password'>","text/html","UTF-8",null);
        content.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        showWeb.setOnClickListener(v -> { first.setVisibility(android.view.View.GONE); second.setVisibility(android.view.View.GONE); password.setVisibility(android.view.View.GONE); privateInput.setVisibility(android.view.View.GONE); });
        content.setOnApplyWindowInsetsListener((v,insets) -> {
            if(android.os.Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());
                v.setPadding(24+i.left,i.top+12,24+i.right,i.bottom);
            } return insets;
        });
        setContentView(content);
    }
    private EditText field(String hint,int type) {
        EditText value=new EditText(this); value.setHint(hint); value.setInputType(type);
        value.setSingleLine(true); value.setSaveEnabled(false); value.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        content.addView(value); return value;
    }
    public void focus(EditText field) {
        field.requestFocus(); getSystemService(InputMethodManager.class).showSoftInput(field,InputMethodManager.SHOW_IMPLICIT);
    }
}
