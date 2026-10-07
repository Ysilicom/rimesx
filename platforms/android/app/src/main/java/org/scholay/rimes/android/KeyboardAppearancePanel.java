package org.scholay.rimes.android;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Layout, continuous height, bottom inset, and theme chooser. */
final class KeyboardAppearancePanel extends ScrollView {
    private final List<KeyButton> themes=new ArrayList<>(),actions=new ArrayList<>();
    private final KeyButton qwerty,nine,chord,split;
    private final KeyButton btnMinus,btnPlus;
    private final SeekBar heightSeekBar;
    private final List<KeyButton> heightPresets=new ArrayList<>();
    private final KeyButton pad0,pad8,pad14,pad20;
    private final List<KeyButton> schemas=new ArrayList<>();
    private String schema="rimes_pinyin";
    private final TextView title,heightTitle,insetTitle,note;
    private final Consumer<Integer> heightPercentChange;
    private final Consumer<Integer> bottomInsetChange;
    private boolean updatingProgress=false;
    private int currentPercent=100;
    private int currentBottomInset=0;

    KeyboardAppearancePanel(Context context,Consumer<String> layout,Consumer<String> theme) {
        this(context,layout,theme,p -> {},i -> {});
    }

    KeyboardAppearancePanel(Context context,Consumer<String> layout,Consumer<String> theme,Consumer<String> heightChange) {
        this(context,layout,theme,p -> heightChange.accept(p<=95?"short":p>=115?"tall":"normal"),i -> {});
    }

    KeyboardAppearancePanel(Context context,Consumer<String> layout,Consumer<String> theme,
                           Consumer<Integer> heightPercentChange,Consumer<Integer> bottomInsetChange) {
        super(context); setFillViewport(true);
        this.heightPercentChange=heightPercentChange;
        this.bottomInsetChange=bottomInsetChange;

        LinearLayout column=new LinearLayout(context); column.setOrientation(LinearLayout.VERTICAL); addView(column);
        title=new TextView(context); title.setText("键位布局"); title.setTextSize(13); title.setPadding(dp(8),dp(8),0,dp(4)); column.addView(title);
        LinearLayout layouts=row(column);
        qwerty=button(layouts,"26 键 · QWERTY",() -> layout.accept("qwerty")); qwerty.setContentDescription("布局 26 键");
        nine=button(layouts,"9 键 · 拼音",() -> layout.accept("nineKey")); nine.setContentDescription("布局 9 键");
        LinearLayout chords=row(column);
        chord=button(chords,"并击 · 正交",() -> layout.accept("orthogonal")); chord.setContentDescription("布局 正交并击");
        split=button(chords,"并击 · 分体正交",() -> layout.accept("splitOrthogonal")); split.setContentDescription("布局 分体并击");

        heightTitle=new TextView(context); heightTitle.setText("键盘高度 · 100%"); heightTitle.setTextSize(13); heightTitle.setPadding(dp(8),dp(8),0,dp(4)); column.addView(heightTitle);
        LinearLayout sliderRow=new LinearLayout(context);
        sliderRow.setOrientation(LinearLayout.HORIZONTAL);
        sliderRow.setGravity(Gravity.CENTER_VERTICAL);
        sliderRow.setPadding(dp(4),0,dp(4),dp(4));

        btnMinus=new KeyButton(context); btnMinus.setText("－"); btnMinus.font(16); btnMinus.appearance(true,true,false);
        btnMinus.setContentDescription("降低键盘高度");
        btnMinus.setOnClickListener(v -> setHeight(Math.max(70,currentPercent-5)));
        sliderRow.addView(btnMinus,new LinearLayout.LayoutParams(dp(40),dp(36)));

        heightSeekBar=new SeekBar(context);
        heightSeekBar.setMax(70); // 70% to 140%
        heightSeekBar.setProgress(30); // 100%
        heightSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar,int progress,boolean fromUser) {
                if(updatingProgress) return;
                currentPercent=70+progress;
                heightTitle.setText("键盘高度 · "+currentPercent+"%");
                if(fromUser) heightPercentChange.accept(currentPercent);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                heightPercentChange.accept(currentPercent);
            }
        });
        LinearLayout.LayoutParams seekParams=new LinearLayout.LayoutParams(0,-2,1.0f);
        seekParams.leftMargin=dp(6); seekParams.rightMargin=dp(6);
        sliderRow.addView(heightSeekBar,seekParams);

        btnPlus=new KeyButton(context); btnPlus.setText("＋"); btnPlus.font(16); btnPlus.appearance(true,true,false);
        btnPlus.setContentDescription("增加键盘高度");
        btnPlus.setOnClickListener(v -> setHeight(Math.min(140,currentPercent+5)));
        sliderRow.addView(btnPlus,new LinearLayout.LayoutParams(dp(40),dp(36)));
        column.addView(sliderRow,new LinearLayout.LayoutParams(-1,dp(44)));

        LinearLayout presetsRow=row(column,36);
        int[] presets={85,90,100,110,120,130};
        for(int preset:presets) {
            KeyButton presetBtn=button(presetsRow,preset+"%",() -> setHeight(preset));
            presetBtn.font(12);
            presetBtn.setContentDescription("高度预设 "+preset+"%");
            heightPresets.add(presetBtn);
        }

        insetTitle=new TextView(context); insetTitle.setText("底栏手势垫高 (防误触全面屏手势)"); insetTitle.setTextSize(13); insetTitle.setPadding(dp(8),dp(8),0,dp(4)); column.addView(insetTitle);
        LinearLayout insetRow=row(column);
        pad0=button(insetRow,"0dp · 贴底",() -> setInset(0)); pad0.setContentDescription("垫高 0dp");
        pad8=button(insetRow,"8dp · 适中",() -> setInset(8)); pad8.setContentDescription("垫高 8dp");
        pad14=button(insetRow,"14dp · 较高",() -> setInset(14)); pad14.setContentDescription("垫高 14dp");
        pad20=button(insetRow,"20dp · 加高",() -> setInset(20)); pad20.setContentDescription("垫高 20dp");

        note=new TextView(context); note.setText("配色 · 跟随系统浅色 / 深色"); note.setTextSize(13); note.setPadding(dp(8),dp(8),0,dp(4)); column.addView(note);
        LinearLayout current=null;
        for(int i=0;i<KeyboardTheme.ALL.length;i++) {
            if(i%3==0) current=row(column);
            KeyboardTheme option=KeyboardTheme.ALL[i];
            KeyButton item=button(current,option.title,() -> theme.accept(option.id));
            item.icon(KeyboardIcon.APPEARANCE,16,true); item.setContentDescription("配色 "+option.title); themes.add(item);
        }
    }

    private void setHeight(int percent) {
        currentPercent=Math.max(70,Math.min(140,percent));
        heightTitle.setText("键盘高度 · "+currentPercent+"%");
        updatingProgress=true;
        heightSeekBar.setProgress(currentPercent-70);
        updatingProgress=false;
        heightPercentChange.accept(currentPercent);
    }

    private void setInset(int dp) {
        currentBottomInset=dp;
        bottomInsetChange.accept(dp);
        pad0.setSelected(dp==0);
        pad8.setSelected(dp==8);
        pad14.setSelected(dp==14);
        pad20.setSelected(dp==20);
    }

    void render(String layout,KeyboardTheme theme) { render(layout,theme,currentPercent,currentBottomInset); }
    void render(String layout,KeyboardTheme theme,String heightScale) {
        int percent="short".equals(heightScale)?90:"medium_tall".equals(heightScale)?110:"tall".equals(heightScale)?120:100;
        render(layout,theme,percent,currentBottomInset);
    }
    void render(String layout,KeyboardTheme theme,int heightPercent,int bottomInset) {
        currentPercent=heightPercent;
        currentBottomInset=bottomInset;
        qwerty.setSelected(layout.equals("qwerty")); nine.setSelected(layout.equals("nineKey"));
        chord.setSelected(layout.equals("orthogonal")); split.setSelected(layout.equals("splitOrthogonal"));
        chord.theme(theme); split.theme(theme);
        qwerty.theme(theme); nine.theme(theme);

        updatingProgress=true;
        heightSeekBar.setProgress(Math.max(0,Math.min(70,heightPercent-70)));
        updatingProgress=false;
        heightTitle.setText("键盘高度 · "+heightPercent+"%");
        btnMinus.theme(theme); btnPlus.theme(theme);
        for(int i=0;i<heightPresets.size();i++) {
            KeyButton pBtn=heightPresets.get(i);
            pBtn.theme(theme);
            int[] vals={85,90,100,110,120,130};
            pBtn.setSelected(i<vals.length && vals[i]==heightPercent);
        }

        pad0.setSelected(bottomInset==0); pad8.setSelected(bottomInset==8);
        pad14.setSelected(bottomInset==14); pad20.setSelected(bottomInset==20);
        pad0.theme(theme); pad8.theme(theme); pad14.theme(theme); pad20.theme(theme);

        for(int i=0;i<schemas.size();i++) {
            schemas.get(i).theme(theme);
            schemas.get(i).setSelected(schema.equals(new String[]{"rimes_pinyin","rimes_ziranma","rimes_flypy","rimes_wubi"}[i]));
        }
        for(KeyButton key:actions) key.theme(theme);
        title.setTextColor(theme.palette(getContext()).ink);
        heightTitle.setTextColor(theme.palette(getContext()).ink);
        insetTitle.setTextColor(theme.palette(getContext()).ink);
        note.setTextColor(theme.palette(getContext()).ink);
        for(int i=0;i<themes.size();i++) {
            KeyButton button=themes.get(i);
            button.theme(KeyboardTheme.ALL[i]);
            button.setSelected(KeyboardTheme.ALL[i]==theme);
        }
        setBackgroundColor(theme.palette(getContext()).background);
    }

    void schemes(String selected,Consumer<String> choose) {
        schema=selected;
        if(!schemas.isEmpty()) return;
        LinearLayout column=(LinearLayout)getChildAt(0);
        LinearLayout row=new LinearLayout(getContext()); column.addView(row,2,new LinearLayout.LayoutParams(-1,dp(40)));
        String[] ids={"rimes_pinyin","rimes_ziranma","rimes_flypy","rimes_wubi"},names={"全拼","自然码","小鹤","五笔"};
        for(int i=0;i<ids.length;i++) {
            final String id=ids[i];
            KeyButton key=button(row,names[i],() -> choose.accept(id));
            key.setContentDescription("中文方案 "+names[i]);
            schemas.add(key);
        }
    }

    void action(String title,String description,Runnable perform) {
        LinearLayout column=(LinearLayout)getChildAt(0);
        KeyButton button=button(row(column),title,perform);
        button.setContentDescription(description);
        button.icon(description.equals(getResources().getString(R.string.switch_keyboard))?KeyboardIcon.GLOBE
                :description.equals(getResources().getString(R.string.insert_all))?KeyboardIcon.SEND_ALL
                :title.contains("剪贴") || title.contains("粘贴")?KeyboardIcon.WRITE
                :title.contains("模糊")?KeyboardIcon.SLIDERS:KeyboardIcon.CLEAR,18,true);
        actions.add(button);
    }

    private LinearLayout row(LinearLayout column) {
        return row(column,48);
    }

    private LinearLayout row(LinearLayout column,int heightDp) {
        LinearLayout row=new LinearLayout(getContext());
        column.addView(row,new LinearLayout.LayoutParams(-1,dp(heightDp)));
        return row;
    }

    private KeyButton button(LinearLayout row,String text,Runnable action) {
        KeyButton button=new KeyButton(getContext());
        button.setText(text);
        button.font(14);
        button.setOnClickListener(v -> action.run());
        row.addView(button,new LinearLayout.LayoutParams(0,-1,1));
        return button;
    }

    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
}
