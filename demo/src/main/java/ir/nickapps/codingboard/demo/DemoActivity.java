package ir.nickapps.codingboard.demo;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Html;
import android.text.InputType;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import ir.nickapps.codingboard.CodingBoardKeyboardView;
import ir.nickapps.codingboard.CodingBoardKeyboardSettingsView;

public final class DemoActivity extends Activity {
    private static final int AUDIO_PERMISSION_REQUEST = 17;
    private CodingBoardKeyboardView keyboard;
    private EditText editor;
    private FrameLayout root;
    private AlertDialog keyboardSettingsDialog;
    private View paintBanner;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(18, 22, 31));
        getWindow().setNavigationBarColor(Color.rgb(18, 22, 31));

        root = new FrameLayout(this);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        android.graphics.drawable.GradientDrawable pageBackground =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{0xFF1B2230, 0xFF11151E});
        root.setBackground(pageBackground);
        root.setClipChildren(false);

        TextView title = new TextView(this);
        title.setText("Coding-board keyboard test");
        title.setTextColor(0xFFF0F3FA);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(48), Gravity.TOP);
        titleParams.setMargins(dp(20), dp(12), dp(20), 0);
        root.addView(title, titleParams);

        TextView about = new TextView(this);
        about.setText("About");
        about.setTextColor(0xFF9ABCF4);
        about.setGravity(Gravity.CENTER);
        about.setOnClickListener(view -> showCredits());
        FrameLayout.LayoutParams aboutParams = new FrameLayout.LayoutParams(
                dp(64), dp(48), Gravity.TOP | Gravity.RIGHT);
        aboutParams.setMargins(0, dp(12), dp(16), 0);
        root.addView(about, aboutParams);

        TextView settingsShortcut = new TextView(this);
        settingsShortcut.setText("Keyboard settings");
        settingsShortcut.setTextColor(0xFF9ABCF4);
        settingsShortcut.setTextSize(12);
        settingsShortcut.setGravity(Gravity.CENTER);
        settingsShortcut.setOnClickListener(view -> showKeyboardSettings());
        FrameLayout.LayoutParams settingsShortcutParams = new FrameLayout.LayoutParams(
                dp(112), dp(48), Gravity.TOP | Gravity.RIGHT);
        settingsShortcutParams.setMargins(0, dp(12), dp(80), 0);
        root.addView(settingsShortcut, settingsShortcutParams);

        editor = new EditText(this);
        editor.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        editor.setTextDirection(View.TEXT_DIRECTION_LTR);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setTextSize(16);
        editor.setTextColor(0xFFE6EAF2);
        editor.setHintTextColor(0xFF9AA5B5);
        editor.setMinLines(5);
        editor.setPadding(dp(14), dp(14), dp(14), dp(14));
        editor.setHint("Type code here…");
        android.graphics.drawable.GradientDrawable editorBackground =
                new android.graphics.drawable.GradientDrawable();
        editorBackground.setColor(0xFF202735);
        editorBackground.setCornerRadius(dp(10));
        editorBackground.setStroke(dp(1), 0xFF3C4657);
        editor.setBackground(editorBackground);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setShowSoftInputOnFocus(false);
        FrameLayout.LayoutParams editorParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(190), Gravity.TOP);
        editorParams.setMargins(dp(16), dp(72), dp(16), 0);
        root.addView(editor, editorParams);

        TextView shortcutHelp = new TextView(this);
        shortcutHelp.setText("Ctrl + ←/→: move by word     Ctrl + Shift + ←/→: select words\n"
                + "Alt + ←/→: optional word move     Ctrl + Backspace: delete word");
        shortcutHelp.setTextColor(0xFFB5C0D0);
        shortcutHelp.setTextSize(12);
        shortcutHelp.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams shortcutHelpParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(54), Gravity.TOP);
        shortcutHelpParams.setMargins(dp(20), dp(274), dp(20), 0);
        root.addView(shortcutHelp, shortcutHelpParams);

        keyboard = new CodingBoardKeyboardView(this, "coding_board_demo_keyboard");
        keyboard.setKeyboardSoundEnabled(false);
        keyboard.setListener(new CodingBoardKeyboardView.Listener() {
            @Override
            public void onHideRequested() {
                keyboard.setVisibility(View.GONE);
            }

            @Override
            public void onVoicePermissionRequired() {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},
                        AUDIO_PERMISSION_REQUEST);
            }

            @Override
            public void onClipboardImage(android.net.Uri uri) {
                Toast.makeText(DemoActivity.this, "Clipboard image URI received",
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSettingsRequested() {
                showKeyboardSettings();
            }
        });
        FrameLayout.LayoutParams keyboardParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        root.addView(keyboard, keyboardParams);
        editor.setOnClickListener(view -> keyboard.setVisibility(View.VISIBLE));
        setContentView(root);

        editor.requestFocus();
        editor.post(() -> keyboard.setTargetView(editor));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == AUDIO_PERMISSION_REQUEST && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            keyboard.startVoiceInput();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void showKeyboardSettings() {
        CodingBoardKeyboardSettingsView settings =
                new CodingBoardKeyboardSettingsView(this, keyboard);
        settings.setKeyPaintRequestListener(() -> {
            if (keyboardSettingsDialog != null) keyboardSettingsDialog.dismiss();
            chooseKeyPaintColor();
        });
        settings.setSurfaceColorRequestListener(() -> {
            if (keyboardSettingsDialog != null) keyboardSettingsDialog.dismiss();
            chooseSurfaceColor();
        });
        keyboardSettingsDialog = new AlertDialog.Builder(this)
                .setTitle("Keyboard settings")
                .setView(settings)
                .setPositiveButton("Done", null)
                .create();
        keyboardSettingsDialog.show();
        Window window = keyboardSettingsDialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            window.setDimAmount(0.18f);
            window.setLayout(FrameLayout.LayoutParams.MATCH_PARENT,
                    (int) (getResources().getDisplayMetrics().heightPixels * 0.64f));
        }
    }

    private void chooseKeyPaintColor() {
        String[] colors = {"Blue-grey", "Lavender", "Teal", "Amber", "Rose", "Green", "Custom hex"};
        int[] values = {0xFF607DA7, 0xFF8D78B6, 0xFF4C9398, 0xFFB68B4E,
                0xFFB56370, 0xFF5D8F71};
        new AlertDialog.Builder(this)
                .setTitle("Choose key color")
                .setItems(colors, (dialog, which) -> {
                    if (which == colors.length - 1) showCustomKeyColor();
                    else beginKeyPainting(values[which]);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCustomKeyColor() {
        EditText colorField = new EditText(this);
        colorField.setSingleLine(true);
        colorField.setHint("#607DA7 or #FF607DA7");
        colorField.setInputType(InputType.TYPE_CLASS_TEXT);
        int padding = dp(20);
        colorField.setPadding(padding, dp(12), padding, dp(12));
        new AlertDialog.Builder(this)
                .setTitle("Custom key color")
                .setView(colorField)
                .setPositiveButton("Paint", (dialog, which) -> {
                    try {
                        beginKeyPainting(Color.parseColor(colorField.getText().toString().trim()));
                    } catch (IllegalArgumentException error) {
                        Toast.makeText(this, "Enter a color such as #607DA7", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void chooseSurfaceColor() {
        String[] colors = {"Default screenshot color", "Blue charcoal", "Deep slate",
                "Indigo", "Custom hex", "Reset to palette"};
        int[] values = {0xFF1B1B24, 0xFF1D2635, 0xFF202B3B, 0xFF25233A};
        new AlertDialog.Builder(this)
                .setTitle("Keyboard background")
                .setItems(colors, (dialog, which) -> {
                    if (which == 4) showCustomSurfaceColor();
                    else if (which == 5) keyboard.setKeyboardSurfaceColor(null);
                    else keyboard.setKeyboardSurfaceColor(values[which]);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCustomSurfaceColor() {
        EditText colorField = new EditText(this);
        colorField.setSingleLine(true);
        colorField.setHint("#1B1B24 or #FF1B1B24");
        colorField.setInputType(InputType.TYPE_CLASS_TEXT);
        int padding = dp(20);
        colorField.setPadding(padding, dp(12), padding, dp(12));
        new AlertDialog.Builder(this)
                .setTitle("Custom background color")
                .setView(colorField)
                .setPositiveButton("Apply", (dialog, which) -> {
                    try {
                        keyboard.setKeyboardSurfaceColor(
                                Color.parseColor(colorField.getText().toString().trim()));
                    } catch (IllegalArgumentException error) {
                        Toast.makeText(this, "Enter a color such as #1B1B24", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void beginKeyPainting(int color) {
        keyboard.beginKeyboardKeyFillPainting(color);
        if (paintBanner != null) root.removeView(paintBanner);
        LinearLayout banner = new LinearLayout(this);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(dp(12), dp(4), dp(8), dp(4));
        banner.setBackgroundColor(0xF2242932);
        TextView message = new TextView(this);
        message.setText("Tap or drag across keys to paint");
        message.setTextColor(Color.WHITE);
        banner.addView(message, new LinearLayout.LayoutParams(0, dp(48), 1f));
        TextView done = new TextView(this);
        done.setText("Done");
        done.setTextColor(0xFFB8D4FF);
        done.setGravity(Gravity.CENTER);
        done.setPadding(dp(14), 0, dp(14), 0);
        done.setOnClickListener(view -> finishKeyPainting());
        banner.addView(done, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        params.setMargins(dp(16), dp(64), dp(16), 0);
        paintBanner = banner;
        root.addView(paintBanner, params);
    }

    private void finishKeyPainting() {
        keyboard.stopKeyboardKeyPainting();
        if (paintBanner != null) {
            root.removeView(paintBanner);
            paintBanner = null;
        }
    }

    private void showCredits() {
        TextView credits = new TextView(this);
        credits.setText(Html.fromHtml(
                "This application uses Coding-board keyboard source by silent (Mohammad Nick), "
                        + "adapted from <a href=\"https://github.com/Julow/Unexpected-Keyboard\">"
                        + "Unexpected Keyboard by Julow</a>.",
                Html.FROM_HTML_MODE_LEGACY));
        credits.setMovementMethod(LinkMovementMethod.getInstance());
        credits.setPadding(dp(24), dp(12), dp(24), dp(12));
        new AlertDialog.Builder(this)
                .setTitle("Open-source credits")
                .setView(credits)
                .setPositiveButton("Close", null)
                .show();
    }
}
