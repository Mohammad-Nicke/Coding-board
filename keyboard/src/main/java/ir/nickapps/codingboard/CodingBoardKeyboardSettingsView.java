package ir.nickapps.codingboard;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public final class CodingBoardKeyboardSettingsView extends ScrollView {
    public interface KeyPaintRequestListener {
        void onRequestKeyPaint();
    }

    public interface SurfaceColorRequestListener {
        void onRequestSurfaceColor();
    }

    private interface ValueFormatter {
        String format(int value);
    }

    private interface ValueListener {
        void onChange(int value);
    }

    private interface BooleanListener {
        void onChange(boolean value);
    }

    private final CodingBoardKeyboardView keyboard;
    private final LinearLayout content;
    private KeyPaintRequestListener keyPaintRequestListener;
    private SurfaceColorRequestListener surfaceColorRequestListener;

    public CodingBoardKeyboardSettingsView(Context context, CodingBoardKeyboardView keyboard) {
        super(context);
        this.keyboard = keyboard;
        setFillViewport(true);
        setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        int inset = dp(18);
        content.setPadding(inset, dp(12), inset, dp(20));
        addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addSection("Keyboard Appearance");
        addPalettePicker();
        Button surfaceColor = new Button(context);
        surfaceColor.setText("Custom Surface Color");
        surfaceColor.setOnClickListener(view -> {
            if (surfaceColorRequestListener != null)
                surfaceColorRequestListener.onRequestSurfaceColor();
        });
        content.addView(surfaceColor, marginParams(0, 0, 0, dp(4)));
        Button resetSurfaceColor = new Button(context);
        resetSurfaceColor.setText("Reset Surface Color");
        resetSurfaceColor.setOnClickListener(view -> keyboard.setKeyboardSurfaceColor(null));
        content.addView(resetSurfaceColor, marginParams(0, 0, 0, dp(6)));
        addSlider("Keyboard Height", 50, 160, Math.round(keyboard.getKeyboardHeightScale() * 100f),
                value -> value + "%", value -> keyboard.setKeyboardHeightScale(value / 100f));
        addSlider("Key Spacing", 0, 800, Math.round(keyboard.getKeyboardKeyMarginScale() * 100f),
                value -> String.format(java.util.Locale.ROOT, "%.2f", value / 100f),
                value -> keyboard.setKeyboardKeyMarginScale(value / 100f));
        addSlider("Corner Radius", 0, 240,
                Math.round(Math.max(0f, keyboard.getKeyboardKeyCornerRadiusDp()) * 10f),
                value -> String.format(java.util.Locale.ROOT, "%.1f dp", value / 10f),
                value -> keyboard.setKeyboardKeyCornerRadiusDp(value / 10f));
        addSlider("Key Opacity", 0, 100,
                Math.round(Math.max(0f, keyboard.getKeyboardKeyOpacity()) * 100f),
                value -> value + "%", value -> keyboard.setKeyboardKeyOpacity(value / 100f));
        addSlider("Keyboard Background Opacity", 0, 100, keyboard.getKeyboardBackgroundOpacity(),
                value -> value + "%", keyboard::setKeyboardBackgroundOpacity);
        addSlider("Horizontal Inset", 0, 48, keyboard.getKeyboardHorizontalInsetDp(),
                value -> value + " dp", keyboard::setKeyboardHorizontalInsetDp);
        addSlider("Bottom Padding", 0, 48, keyboard.getKeyboardBottomPaddingDp(),
                value -> value + " dp", keyboard::setKeyboardBottomPaddingDp);
        addSection("Display Mode");
        addFormPicker();
        addSlider("Floating Width", 35, 100,
                Math.round(keyboard.getKeyboardFloatingWidthScale() * 100f),
                value -> value + "%", value -> keyboard.setKeyboardFloatingWidthScale(value / 100f));
        addSlider("Floating Height", 60, 160,
                Math.round(keyboard.getKeyboardFloatingHeightScale() * 100f),
                value -> value + "%", value -> keyboard.setKeyboardFloatingHeightScale(value / 100f));
        addSlider("Split Gap", 0, 45, Math.round(keyboard.getSplitGap() * 100f),
                value -> value + "%", value -> keyboard.setSplitGap(value / 100f));
        addSection("Language and Layout");
        addLayoutPicker();
        addSection("Movement and Text Selection");
        addSwitch("Move by Word with Ctrl + Left/Right", keyboard.isWordNavigationEnabled(),
                keyboard::setWordNavigationEnabled);
        addSwitch("Select Words with Ctrl + Shift + Left/Right", keyboard.isWordSelectionEnabled(),
                keyboard::setWordSelectionEnabled);
        addSwitch("Move by Word with Alt + Left/Right", keyboard.isAltWordNavigationEnabled(),
                keyboard::setAltWordNavigationEnabled);
        addSwitch("Delete Word with Ctrl + Backspace/Delete", keyboard.isWordDeletionEnabled(),
                keyboard::setWordDeletionEnabled);
        addSection("Behavior");
        addSwitch("Key Sound", keyboard.isKeyboardSoundEnabled(), keyboard::setKeyboardSoundEnabled);
        addSwitch("Haptic Feedback", keyboard.isKeyboardHapticEnabled(), keyboard::setKeyboardHapticEnabled);
        addSwitch("Key Preview", keyboard.isKeyboardKeyPopupEnabled(),
                keyboard::setKeyboardKeyPopupEnabled);
        addSwitch("Adaptive Touch Correction", keyboard.isAdaptiveTouchCorrectionEnabled(),
                keyboard::setAdaptiveTouchCorrectionEnabled);
        Button resetTouchLearning = new Button(context);
        resetTouchLearning.setText("Reset Touch Learning");
        resetTouchLearning.setOnClickListener(view -> keyboard.resetAdaptiveTouchCorrection());
        content.addView(resetTouchLearning, marginParams(0, 0, 0, dp(4)));
        Button clearClipboard = new Button(context);
        clearClipboard.setText("Clear Clipboard History");
        clearClipboard.setOnClickListener(view -> keyboard.clearClipboardHistory());
        content.addView(clearClipboard, marginParams(0, 0, 0, dp(8)));
        addSection("Key Colors");
        TextView paintHelp = new TextView(context);
        paintHelp.setText("Choose a background color for individual keys.");
        paintHelp.setTextColor(0xFF596273);
        content.addView(paintHelp, marginParams(0, 0, 0, dp(8)));
        Button paint = new Button(context);
        paint.setText("Paint Keys");
        paint.setOnClickListener(view -> {
            if (keyPaintRequestListener != null) keyPaintRequestListener.onRequestKeyPaint();
        });
        content.addView(paint, marginParams(0, 0, 0, dp(4)));
        Button resetColors = new Button(context);
        resetColors.setText("Reset Key Colors");
        resetColors.setOnClickListener(view -> keyboard.resetKeyboardKeyColorsForLayout());
        content.addView(resetColors, marginParams(0, 0, 0, dp(4)));
    }

    public void setKeyPaintRequestListener(KeyPaintRequestListener listener) {
        keyPaintRequestListener = listener;
    }

    public void setSurfaceColorRequestListener(SurfaceColorRequestListener listener) {
        surfaceColorRequestListener = listener;
    }

    private void addPalettePicker() {
        RadioGroup group = new RadioGroup(getContext());
        group.setOrientation(RadioGroup.HORIZONTAL);
        CodingBoardKeyboardView.PaletteStyle[] values =
                CodingBoardKeyboardView.PaletteStyle.values();
        String[] labels = {"System", "Reference", "Light", "Dark"};
        for (int i = 0; i < values.length; i++) {
            RadioButton button = new RadioButton(getContext());
            button.setId(1000 + i);
            button.setText(labels[i]);
            group.addView(button, new RadioGroup.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            if (values[i] == keyboard.getKeyboardPaletteStyle()) group.check(button.getId());
        }
        group.setOnCheckedChangeListener((radioGroup, id) -> {
            int index = id - 1000;
            if (index >= 0 && index < values.length)
                keyboard.setKeyboardPaletteStyle(values[index]);
        });
        content.addView(group, marginParams(0, dp(2), 0, dp(10)));
    }

    private void addFormPicker() {
        RadioGroup group = new RadioGroup(getContext());
        group.setOrientation(RadioGroup.HORIZONTAL);
        CodingBoardKeyboardView.Form[] values = CodingBoardKeyboardView.Form.values();
        String[] labels = {"Docked", "Floating", "Split"};
        for (int i = 0; i < values.length; i++) {
            RadioButton button = new RadioButton(getContext());
            button.setId(1100 + i);
            button.setText(labels[i]);
            group.addView(button, new RadioGroup.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            if (values[i] == keyboard.getForm()) group.check(button.getId());
        }
        group.setOnCheckedChangeListener((radioGroup, id) -> {
            int index = id - 1100;
            if (index >= 0 && index < values.length) keyboard.setForm(values[index]);
        });
        content.addView(group, marginParams(0, dp(2), 0, dp(10)));
    }

    private void addLayoutPicker() {
        List<String> ids = keyboard.getLayouts();
        ArrayList<String> labels = new ArrayList<>();
        for (String id : ids) labels.add(keyboard.getLayoutLabel(id));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner picker = new Spinner(getContext());
        picker.setAdapter(adapter);
        int selected = ids.indexOf(keyboard.getTextLayoutId());
        if (selected >= 0) picker.setSelection(selected);
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < ids.size()) keyboard.selectLayout(ids.get(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        content.addView(picker, marginParams(0, dp(2), 0, dp(10)));
    }

    private void addSection(String title) {
        TextView heading = new TextView(getContext());
        heading.setText(title);
        heading.setTextColor(Color.rgb(36, 43, 55));
        heading.setTextSize(16);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(heading, marginParams(0, dp(14), 0, dp(6)));
    }

    private void addSlider(String title, int min, int max, int value,
                           ValueFormatter formatter, ValueListener listener) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout heading = new LinearLayout(getContext());
        heading.setOrientation(LinearLayout.HORIZONTAL);
        TextView label = new TextView(getContext());
        label.setText(title);
        label.setTextColor(0xFF303846);
        label.setTextSize(14);
        heading.addView(label, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView amount = new TextView(getContext());
        amount.setTextColor(0xFF596273);
        amount.setTextSize(13);
        heading.addView(amount, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));
        row.addView(heading);
        SeekBar bar = new SeekBar(getContext());
        bar.setMax(max - min);
        int clamped = Math.max(min, Math.min(max, value));
        bar.setProgress(clamped - min);
        amount.setText(formatter.format(clamped));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int actual = min + progress;
                amount.setText(formatter.format(actual));
                if (fromUser) listener.onChange(actual);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        row.addView(bar);
        content.addView(row, marginParams(0, dp(4), 0, dp(7)));
    }

    private void addSwitch(String label, boolean checked, BooleanListener listener) {
        Switch toggle = new Switch(getContext());
        toggle.setText(label);
        toggle.setTextColor(0xFF303846);
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener((button, value) -> listener.onChange(value));
        content.addView(toggle, marginParams(0, dp(5), 0, dp(5)));
    }

    private LinearLayout.LayoutParams marginParams(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
