package ir.nickapps.codingboard;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.KeyCharacterMap;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import juloo.keyboard2.Config;
import juloo.keyboard2.KeyValue;
import juloo.keyboard2.Keyboard2View;
import juloo.keyboard2.KeyboardData;
import juloo.keyboard2.LayoutModifier;
import juloo.keyboard2.Pointers;
import juloo.keyboard2.Theme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class CodingBoardKeyboardView extends LinearLayout implements Config.IKeyEventHandler {
    public enum Form { DOCKED, FLOATING, SPLIT }
    public enum PaletteStyle { SYSTEM, SCREENSHOT, LIGHT, DARK }

    public interface Listener {
        default void onAction(String id) {}
        default void onHideRequested() {}
        default void onVoicePermissionRequired() {}
        default void onVoiceStateChanged(boolean listening) {}
        default void onTextChanged(String text) {}
        default void onClipboardImage(Uri uri) {}
        default void onSettingsRequested() {}
    }

    private static final String PREFS = "coding_board_keyboard";
    private static final String HISTORY = "clipboard_history";
    private static final String ACCURACY = "adaptive_touch_correction";
    private static final String SELECTED_LAYOUT = "selected_layout";
    private static final String KEY_SOUND = "key_sound";
    private static final String HAPTIC = "haptic_feedback";
    private static final String WORD_NAVIGATION = "word_navigation";
    private static final String ALT_WORD_NAVIGATION = "alt_word_navigation";
    private static final String WORD_SELECTION = "word_selection";
    private static final String WORD_DELETION = "word_deletion";
    private static final String KEY_MARGIN = "key_margin_scale";
    private static final String KEY_RADIUS = "key_radius_dp";
    private static final String KEY_OPACITY = "key_opacity";
    private static final String KEY_BACKGROUND_OPACITY = "background_opacity";
    private static final String KEY_SURFACE_COLOR = "surface_color";
    private static final String KEY_HAS_SURFACE_COLOR = "has_surface_color";
    private static final String KEY_HORIZONTAL_INSET = "horizontal_inset_dp";
    private static final String KEY_BOTTOM_PADDING = "bottom_padding_dp";
    private static final String KEY_PALETTE_STYLE = "palette_style";
    private static final String KEY_FLOATING_WIDTH = "floating_width_scale";
    private static final String KEY_FLOATING_HEIGHT = "floating_height_scale";
    private static final String KEY_POPUP = "key_popup";
    private static final String KEY_KEY_COLORS = "key_colors";
    private static final int MAX_HISTORY = 30;
    private static final ExecutorService TOUCH_EXECUTOR = Executors.newSingleThreadExecutor();
    private final Keyboard2View keyboard;
    private final LinearLayout panel;
    private final TextView floatingHandle;
    private final ClipboardManager clipboard;
    private final SharedPreferences preferences;
    private final TapCorrectionController touchCorrection;
    private final ArrayList<String> layouts = new ArrayList<>();
    private final ArrayList<String> history = new ArrayList<>();
    private final Map<String, String> layoutLabels = new HashMap<>();
    private final Map<String, Map<String, Integer>> keyColors = new HashMap<>();
    private final Map<String, Keyboard2View.KeyColorOverride> hostKeyColorOverrides = new HashMap<>();
    private final Map<Integer, PopupWindow> keyPopups = new HashMap<>();
    private final ClipboardManager.OnPrimaryClipChangedListener clipChangedListener =
            this::updatePastePictureState;
    private InputConnection input;
    private Listener listener = new Listener() {};
    private String textLayout = "coding_board_qwerty";
    private String activeLayout = textLayout;
    private Form form = Form.DOCKED;
    private float splitGap = 0.12f;
    private float keyboardHeightScale = 0.973f;
    private float floatingWidthScale = 0.9f;
    private float floatingHeightScale = 1f;
    private float keyboardKeyMarginScale = 3.04f;
    private float keyboardKeyCornerRadiusDp = 9.6f;
    private float keyboardKeyOpacity = 1f;
    private float keyboardBackgroundOpacity = 1f;
    private Integer keyboardSurfaceColor;
    private int keyboardHorizontalInsetDp = 13;
    private int keyboardBottomPaddingDp;
    private PaletteStyle paletteStyle = PaletteStyle.SCREENSHOT;
    private Theme.Palette basePalette;
    private boolean adaptiveTouchCorrectionEnabled;
    private boolean keyboardKeyPopupEnabled = true;
    private boolean wordNavigationEnabled = true;
    private boolean altWordNavigationEnabled = true;
    private boolean wordSelectionEnabled = true;
    private boolean wordDeletionEnabled = true;
    private SpeechRecognizer speechRecognizer;
    private boolean clipboardOpen;
    private boolean layoutPickerOpen;
    private boolean emojiPickerOpen;
    private boolean clipboardWatching;
    private boolean capsLocked;
    private float dragX;
    private float dragY;

    public CodingBoardKeyboardView(Context context) {
        this(context, PREFS);
    }

    public CodingBoardKeyboardView(Context context, String preferenceName) {
        super(context);
        setOrientation(VERTICAL);
        setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        setClipChildren(false);
        setClipToPadding(false);
        clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        String storeName = preferenceName == null || preferenceName.trim().isEmpty()
                ? PREFS : preferenceName.trim();
        preferences = context.getSharedPreferences(storeName, Context.MODE_PRIVATE);
        String savedLayout = preferences.getString(SELECTED_LAYOUT, textLayout);
        if (layoutResource(savedLayout) != 0 && !"numeric".equals(savedLayout)
                && !"greekmath".equals(savedLayout)) textLayout = savedLayout;
        activeLayout = textLayout;
        try {
            JSONArray saved = new JSONArray(preferences.getString(HISTORY, "[]"));
            for (int i = 0; i < saved.length(); i++) history.add(saved.getString(i));
        } catch (JSONException ignored) {
            history.clear();
        }
        loadLayoutIds();

        Config.Builder configBuilder = new Config.Builder(getResources(), this);
        configBuilder.addBottomRow = true;
        configBuilder.addNumberRow = false;
        configBuilder.keySoundEnabled = false;
        configBuilder.hapticEnabled = true;
        paletteStyle = parsePaletteStyle(preferences.getString(KEY_PALETTE_STYLE, "screenshot"));
        basePalette = paletteForStyle(paletteStyle);
        keyboardBackgroundOpacity = preferences.getInt(KEY_BACKGROUND_OPACITY, 100) / 100f;
        if (preferences.getBoolean(KEY_HAS_SURFACE_COLOR, false))
            keyboardSurfaceColor = preferences.getInt(KEY_SURFACE_COLOR, 0xFF1B1B24);
        keyboardHorizontalInsetDp = preferences.getInt(KEY_HORIZONTAL_INSET, 13);
        keyboardBottomPaddingDp = preferences.getInt(KEY_BOTTOM_PADDING, 0);
        keyboardKeyMarginScale = preferences.getFloat(KEY_MARGIN, 3.04f);
        keyboardKeyCornerRadiusDp = preferences.getFloat(KEY_RADIUS, 9.6f);
        keyboardKeyOpacity = preferences.getFloat(KEY_OPACITY, 1f);
        floatingWidthScale = preferences.getFloat(KEY_FLOATING_WIDTH, 0.9f);
        floatingHeightScale = preferences.getFloat(KEY_FLOATING_HEIGHT, 1f);
        Theme.Palette palette = paletteForKeyboardView(basePalette);
        keyboard = new Keyboard2View(context, configBuilder.build(), palette);
        keyboard.setKeySoundEnabled(preferences.getBoolean(KEY_SOUND, false));
        keyboard.setHapticEnabled(preferences.getBoolean(HAPTIC, true));
        keyboardKeyPopupEnabled = preferences.getBoolean(KEY_POPUP, true);
        wordNavigationEnabled = preferences.getBoolean(WORD_NAVIGATION, true);
        altWordNavigationEnabled = preferences.getBoolean(ALT_WORD_NAVIGATION, true);
        wordSelectionEnabled = preferences.getBoolean(WORD_SELECTION, true);
        wordDeletionEnabled = preferences.getBoolean(WORD_DELETION, true);
        keyboard.setKeyMarginScale(keyboardKeyMarginScale);
        keyboard.setKeyCornerRadiusOverride(keyboardKeyCornerRadiusDp < 0f
                ? -1f : dp(keyboardKeyCornerRadiusDp));
        keyboard.setKeyOpacity(keyboardKeyOpacity);
        keyboard.setPadding(keyboard.getPaddingLeft(), keyboard.getPaddingTop(),
                keyboard.getPaddingRight(), dp(keyboardBottomPaddingDp));
        keyboardHeightScale = preferences.getFloat("height_scale", 0.973f);
        keyboard.setHeightScale(effectiveKeyboardHeightScale());
        splitGap = preferences.getFloat("split_gap", 0.12f);
        int savedForm = preferences.getInt("form", Form.DOCKED.ordinal());
        if (savedForm >= 0 && savedForm < Form.values().length) form = Form.values()[savedForm];
        touchCorrection = new TapCorrectionController(TapCorrectionController.modelFile(context),
                TOUCH_EXECUTOR, new Handler(Looper.getMainLooper()));
        keyboard.setTapResolver(touchCorrection);
        adaptiveTouchCorrectionEnabled = preferences.getBoolean(ACCURACY, false);
        touchCorrection.setEnabled(adaptiveTouchCorrectionEnabled);
        panel = new LinearLayout(context);
        panel.setOrientation(VERTICAL);
        panel.setVisibility(GONE);
        floatingHandle = new TextView(context);
        floatingHandle.setText("⋮⋮ Coding-board");
        floatingHandle.setGravity(Gravity.CENTER);
        floatingHandle.setTextColor(Color.WHITE);
        floatingHandle.setVisibility(GONE);
        floatingHandle.setOnTouchListener(this::dragFloatingKeyboard);

        addView(panel, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(floatingHandle, new LayoutParams(LayoutParams.MATCH_PARENT, dp(32)));
        addView(keyboard, keyboardLayoutParams());
        loadKeyColors();
        loadTextLayout(textLayout);
        installKeyPopupListener();
        applySurfaceBackground();
        if (form != Form.DOCKED) setForm(form);
    }

    public void setInputConnection(InputConnection connection) {
        input = connection;
    }

    public void setTargetView(View target) {
        input = target == null ? null : target.onCreateInputConnection(new EditorInfo());
    }

    public void setListener(Listener value) {
        listener = value == null ? new Listener() {} : value;
    }

    public void setLayouts(List<String> ids) {
        layouts.clear();
        if (ids != null) {
            for (String id : ids) {
                if (layoutResource(id) != 0
                        && !"numeric".equals(id) && !"greekmath".equals(id)) layouts.add(id);
            }
        }
        if (layouts.isEmpty()) layouts.add("coding_board_qwerty");
        if (!layouts.contains(textLayout)) textLayout = layouts.get(0);
        loadTextLayout(textLayout);
    }

    public List<String> getLayouts() {
        return new ArrayList<>(layouts);
    }

    public String getActiveLayout() {
        return activeLayout;
    }

    public String getTextLayoutId() {
        return textLayout;
    }

    public String getLayoutLabel(String id) {
        return layoutLabels.getOrDefault(id, id == null ? "" : id.replace('_', ' '));
    }

    public boolean selectLayout(String id) {
        if (id == null || !layouts.contains(id) || "numeric".equals(id)
                || "greekmath".equals(id) || layoutResource(id) == 0) return false;
        loadTextLayout(id);
        return true;
    }

    public void setSplitGap(float fraction) {
        splitGap = Math.max(0f, Math.min(0.45f, fraction));
        preferences.edit().putFloat("split_gap", splitGap).apply();
        applyLayout();
    }

    public float getSplitGap() {
        return splitGap;
    }

    public void setForm(Form value) {
        form = value == null ? Form.DOCKED : value;
        preferences.edit().putInt("form", form.ordinal()).apply();
        ViewGroup parent = getParent() instanceof ViewGroup ? (ViewGroup) getParent() : null;
        if (form == Form.FLOATING && parent != null && !(parent instanceof FrameLayout)) {
            form = Form.DOCKED;
            throw new IllegalStateException("Floating keyboard requires a FrameLayout parent");
        }
        if (form == Form.FLOATING) {
            int width = parent instanceof View ? ((View) parent).getWidth()
                    : getResources().getDisplayMetrics().widthPixels;
            if (width <= 0) width = getResources().getDisplayMetrics().widthPixels;
            if (parent instanceof FrameLayout) {
                ViewGroup.LayoutParams current = getLayoutParams();
                FrameLayout.LayoutParams params = current instanceof FrameLayout.LayoutParams
                        ? (FrameLayout.LayoutParams) current
                        : new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                params.width = (int) (width * floatingWidthScale);
                params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                params.leftMargin = 0;
                params.rightMargin = 0;
                setLayoutParams(params);
            }
            setElevation(dp(12));
            applySurfaceBackground();
            floatingHandle.setVisibility(VISIBLE);
        } else {
            if (parent instanceof FrameLayout) {
                ViewGroup.LayoutParams current = getLayoutParams();
                FrameLayout.LayoutParams params = current instanceof FrameLayout.LayoutParams
                        ? (FrameLayout.LayoutParams) current
                        : new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                params.width = LayoutParams.MATCH_PARENT;
                params.gravity = Gravity.BOTTOM;
                params.leftMargin = dp(keyboardHorizontalInsetDp);
                params.rightMargin = dp(keyboardHorizontalInsetDp);
                setLayoutParams(params);
            }
            setElevation(0f);
            applySurfaceBackground();
            floatingHandle.setVisibility(GONE);
            setTranslationX(0f);
            setTranslationY(0f);
        }
        keyboard.setHeightScale(effectiveKeyboardHeightScale());
        applyLayout();
    }

    public Form getForm() {
        return form;
    }

    public void setKeyboardPalette(Theme.Palette palette) {
        if (palette == null) return;
        basePalette = palette;
        applyKeyboardPalette();
    }

    public void setKeyboardPaletteStyle(PaletteStyle style) {
        paletteStyle = style == null ? PaletteStyle.SCREENSHOT : style;
        preferences.edit().putString(KEY_PALETTE_STYLE, paletteStyle.name().toLowerCase()).apply();
        basePalette = paletteForStyle(paletteStyle);
        applyKeyboardPalette();
    }

    public PaletteStyle getKeyboardPaletteStyle() {
        return paletteStyle;
    }

    public void setKeyboardHeightScale(float scale) {
        keyboardHeightScale = Math.max(0.5f, Math.min(1.6f, scale));
        preferences.edit().putFloat("height_scale", keyboardHeightScale).apply();
        keyboard.setHeightScale(effectiveKeyboardHeightScale());
    }

    public float getKeyboardHeightScale() {
        return keyboardHeightScale;
    }

    public void setKeyboardFloatingWidthScale(float scale) {
        floatingWidthScale = Math.max(0.35f, Math.min(1f, scale));
        preferences.edit().putFloat(KEY_FLOATING_WIDTH, floatingWidthScale).apply();
        if (form == Form.FLOATING) setForm(Form.FLOATING);
    }

    public float getKeyboardFloatingWidthScale() {
        return floatingWidthScale;
    }

    public void setKeyboardFloatingHeightScale(float scale) {
        floatingHeightScale = Math.max(0.6f, Math.min(1.6f, scale));
        preferences.edit().putFloat(KEY_FLOATING_HEIGHT, floatingHeightScale).apply();
        keyboard.setHeightScale(effectiveKeyboardHeightScale());
    }

    public float getKeyboardFloatingHeightScale() {
        return floatingHeightScale;
    }

    public void setKeyboardSoundEnabled(boolean enabled) {
        keyboard.setKeySoundEnabled(enabled);
        preferences.edit().putBoolean(KEY_SOUND, enabled).apply();
    }

    public void setKeyboardHapticEnabled(boolean enabled) {
        keyboard.setHapticEnabled(enabled);
        preferences.edit().putBoolean(HAPTIC, enabled).apply();
    }

    public boolean isKeyboardSoundEnabled() {
        return preferences.getBoolean(KEY_SOUND, false);
    }

    public boolean isKeyboardHapticEnabled() {
        return preferences.getBoolean(HAPTIC, true);
    }

    public void setKeyboardKeyMarginScale(float scale) {
        keyboardKeyMarginScale = Math.max(0f, Math.min(8f, scale));
        preferences.edit().putFloat(KEY_MARGIN, keyboardKeyMarginScale).apply();
        keyboard.setKeyMarginScale(keyboardKeyMarginScale);
    }

    public float getKeyboardKeyMarginScale() {
        return keyboardKeyMarginScale;
    }

    public void setKeyboardKeyCornerRadiusDp(float radiusDp) {
        keyboardKeyCornerRadiusDp = radiusDp < 0f ? -1f : Math.max(0f, Math.min(24f, radiusDp));
        float radiusPx = keyboardKeyCornerRadiusDp < 0f
                ? -1f : dp(keyboardKeyCornerRadiusDp);
        keyboard.setKeyCornerRadiusOverride(radiusPx);
        preferences.edit().putFloat(KEY_RADIUS, keyboardKeyCornerRadiusDp).apply();
    }

    public float getKeyboardKeyCornerRadiusDp() {
        return keyboardKeyCornerRadiusDp;
    }

    public void setKeyboardKeyOpacity(float opacity) {
        keyboardKeyOpacity = opacity < 0f && Float.compare(opacity, -1f) == 0
                ? -1f : Math.max(0f, Math.min(1f, opacity));
        keyboard.setKeyOpacity(keyboardKeyOpacity);
        preferences.edit().putFloat(KEY_OPACITY, keyboardKeyOpacity).apply();
    }

    public float getKeyboardKeyOpacity() {
        return keyboardKeyOpacity;
    }

    public void setKeyboardBackgroundOpacity(int opacityPercent) {
        keyboardBackgroundOpacity = Math.max(0, Math.min(100, opacityPercent)) / 100f;
        preferences.edit().putInt(KEY_BACKGROUND_OPACITY,
                Math.round(keyboardBackgroundOpacity * 100f)).apply();
        applyKeyboardPalette();
    }

    public int getKeyboardBackgroundOpacity() {
        return Math.round(keyboardBackgroundOpacity * 100f);
    }

    public void setKeyboardSurfaceColor(Integer color) {
        keyboardSurfaceColor = color;
        SharedPreferences.Editor editor = preferences.edit()
                .putBoolean(KEY_HAS_SURFACE_COLOR, color != null);
        if (color == null) editor.remove(KEY_SURFACE_COLOR);
        else editor.putInt(KEY_SURFACE_COLOR, color);
        editor.apply();
        applySurfaceBackground();
    }

    public Integer getKeyboardSurfaceColor() {
        return keyboardSurfaceColor;
    }

    public void setKeyboardKeyPopupEnabled(boolean enabled) {
        keyboardKeyPopupEnabled = enabled;
        preferences.edit().putBoolean(KEY_POPUP, enabled).apply();
        installKeyPopupListener();
    }

    public boolean isKeyboardKeyPopupEnabled() {
        return keyboardKeyPopupEnabled;
    }

    public void setKeyboardHorizontalInsetDp(int insetDp) {
        keyboardHorizontalInsetDp = Math.max(0, Math.min(48, insetDp));
        preferences.edit().putInt(KEY_HORIZONTAL_INSET, keyboardHorizontalInsetDp).apply();
        applyKeyboardHorizontalInset();
    }

    public int getKeyboardHorizontalInsetDp() {
        return keyboardHorizontalInsetDp;
    }

    public void setKeyboardBottomPaddingDp(int paddingDp) {
        keyboardBottomPaddingDp = Math.max(0, Math.min(48, paddingDp));
        preferences.edit().putInt(KEY_BOTTOM_PADDING, keyboardBottomPaddingDp).apply();
        keyboard.setPadding(keyboard.getPaddingLeft(), keyboard.getPaddingTop(),
                keyboard.getPaddingRight(), dp(keyboardBottomPaddingDp));
    }

    public int getKeyboardBottomPaddingDp() {
        return keyboardBottomPaddingDp;
    }

    public void setKeyboardKeyFillColor(String keyId, Integer color) {
        if (keyId == null || !keyId.matches("[0-9]+:[0-9]+")) return;
        Map<String, Integer> layoutColors = keyColors.computeIfAbsent(activeLayout,
                ignored -> new HashMap<>());
        if (color == null) layoutColors.remove(keyId);
        else layoutColors.put(keyId, color);
        if (layoutColors.isEmpty()) keyColors.remove(activeLayout);
        persistKeyColors();
        applyKeyColors();
    }

    public void clearKeyboardKeyFillColors() {
        keyColors.remove(activeLayout);
        persistKeyColors();
        applyKeyColors();
    }

    public void beginKeyboardKeyFillPainting(int color) {
        keyboard.setOnKeyPaintListener(keyId -> setKeyboardKeyFillColor(keyId, color));
    }

    public void stopKeyboardKeyPainting() {
        keyboard.setOnKeyPaintListener(null);
    }

    public void setAdaptiveTouchCorrectionEnabled(boolean enabled) {
        adaptiveTouchCorrectionEnabled = enabled;
        touchCorrection.setEnabled(enabled);
        preferences.edit().putBoolean(ACCURACY, enabled).apply();
    }

    public boolean isAdaptiveTouchCorrectionEnabled() {
        return adaptiveTouchCorrectionEnabled;
    }

    public void setWordNavigationEnabled(boolean enabled) {
        wordNavigationEnabled = enabled;
        preferences.edit().putBoolean(WORD_NAVIGATION, enabled).apply();
    }

    public boolean isWordNavigationEnabled() {
        return wordNavigationEnabled;
    }

    public void setAltWordNavigationEnabled(boolean enabled) {
        altWordNavigationEnabled = enabled;
        preferences.edit().putBoolean(ALT_WORD_NAVIGATION, enabled).apply();
    }

    public boolean isAltWordNavigationEnabled() {
        return altWordNavigationEnabled;
    }

    public void setWordSelectionEnabled(boolean enabled) {
        wordSelectionEnabled = enabled;
        preferences.edit().putBoolean(WORD_SELECTION, enabled).apply();
    }

    public boolean isWordSelectionEnabled() {
        return wordSelectionEnabled;
    }

    public void setWordDeletionEnabled(boolean enabled) {
        wordDeletionEnabled = enabled;
        preferences.edit().putBoolean(WORD_DELETION, enabled).apply();
    }

    public boolean isWordDeletionEnabled() {
        return wordDeletionEnabled;
    }

    public void setKeyboardKeyColorOverrides(Map<String, Keyboard2View.KeyColorOverride> overrides) {
        hostKeyColorOverrides.clear();
        if (overrides != null) hostKeyColorOverrides.putAll(overrides);
        applyKeyColors();
    }

    public void setKeyboardKeyPaintListener(Keyboard2View.OnKeyPaintListener listener) {
        keyboard.setOnKeyPaintListener(listener);
    }

    public void resetKeyboardKeyColorsForLayout() {
        clearKeyboardKeyFillColors();
    }

    public void resetAdaptiveTouchCorrection() {
        touchCorrection.reset();
    }

    public List<String> getClipboardHistory() {
        return new ArrayList<>(history);
    }

    public void clearClipboardHistory() {
        history.clear();
        preferences.edit().remove(HISTORY).apply();
        if (clipboardOpen) showClipboard();
    }

    public void cycleLayout(boolean forward) {
        if (layouts.isEmpty()) return;
        int index = layouts.indexOf(textLayout);
        if (index < 0) index = 0;
        index = (index + (forward ? 1 : layouts.size() - 1)) % layouts.size();
        textLayout = layouts.get(index);
        loadTextLayout(textLayout);
    }

    @Override
    public void key_down(KeyValue value, boolean isSwipe) {
    }

    @Override
    public void key_up(KeyValue value, Pointers.Modifiers modifiers) {
        if (value == null) return;
        switch (value.getKind()) {
            case Char:
                commitCharacter(value.getChar(), modifiers);
                break;
            case String:
                commitString(value.getString(), modifiers);
                break;
            case Keyevent:
                dispatchKeyEvent(value.getKeyevent(), modifiers);
                break;
            case Editing:
                handleEditing(value.getEditing(), modifiers);
                break;
            case Event:
                handleEvent(value.getEvent());
                break;
            case Macro:
                for (KeyValue key : value.getMacro()) key_up(key, modifiers);
                break;
            case Host_action:
                runHostAction(value.getHostAction().toolId);
                break;
            case Slider:
                slideCursor(value.getSlider(), value.getSliderRepeat(), modifiers);
                break;
            case Stateful:
                listener.onAction("completion:" + value.getStateful().name());
                break;
            case Placeholder:
            case Compose_pending:
            case Hangul_initial:
            case Hangul_medial:
            case Modifier:
                break;
        }
    }

    @Override
    public void mods_changed(Pointers.Modifiers modifiers) {}

    @Override
    public void suggestion_entered(String text) {
        commit(text);
    }

    public void startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(getContext())) {
            listener.onAction("voice_unavailable");
            return;
        }
        if (getContext().checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            listener.onVoicePermissionRequired();
            return;
        }
        if (speechRecognizer == null) speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getContext());
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { setVoiceState(true); }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { setVoiceState(false); }
            @Override public void onError(int error) { setVoiceState(false); }
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) commit(matches.get(0));
                setVoiceState(false);
            }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        speechRecognizer.startListening(intent);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!clipboardWatching && clipboard != null) {
            clipboard.addPrimaryClipChangedListener(clipChangedListener);
            clipboardWatching = true;
        }
        updatePastePictureState();
        installKeyPopupListener();
        applySurfaceBackground();
        applyKeyboardHorizontalInset();
        if (form == Form.FLOATING) setForm(Form.FLOATING);
    }

    @Override
    protected void onDetachedFromWindow() {
        touchCorrection.flush();
        dismissKeyPopups();
        if (clipboardWatching && clipboard != null) {
            clipboard.removePrimaryClipChangedListener(clipChangedListener);
            clipboardWatching = false;
        }
        setVoiceState(false);
        if (speechRecognizer != null) {
            speechRecognizer.cancel();
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (paletteStyle == PaletteStyle.SYSTEM) {
            basePalette = paletteForStyle(paletteStyle);
            applyKeyboardPalette();
        }
        applyKeyboardHorizontalInset();
        if (form == Form.FLOATING) setForm(Form.FLOATING);
        applyLayout();
    }

    private Theme.Palette paletteForStyle(PaletteStyle style) {
        if (style == PaletteStyle.SYSTEM) {
            int mode = getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            return mode == Configuration.UI_MODE_NIGHT_YES
                    ? paletteForStyle(PaletteStyle.SCREENSHOT) : paletteForStyle(PaletteStyle.LIGHT);
        }
        if (style == PaletteStyle.LIGHT) {
            return new Theme.Palette(0xFFE5E7EC, 0xFFF4F5F8, 0xFFE4E7EE, 0xFFEFF1F5,
                    0xFFBEC9DA, 0xFF171B24, 0xFF596273, 0xFF17243A, 0xFF35445C,
                    0xFF315C9A, 0xFF929AAA, true, dp(0.8f), dp(9.6f), 1f,
                    0.25f, 0.5f, 0xFFFFFFFF, 0xFF596273, null,
                    0x14FFFFFF, 0x12000000, 0xFFE9ECF2, 0xFF586174);
        }
        if (style == PaletteStyle.DARK) {
            return new Theme.Palette(0xFF111319, 0xFF20242D, 0xFF292E38, 0xFF222731,
                    0xFF435A7D, 0xFFF0F2F7, 0xFFABB4C2, 0xFFFFFFFF, 0xFFDAE4F6,
                    0xFF8CA3C5, 0xFF58616F, true, dp(0.8f), dp(9.6f), 1f,
                    0.25f, 0.5f, 0xFFE1E6EF, 0xFFAAB3C1, null,
                    0x18FFFFFF, 0x26000000, 0xFF1B2029, 0xFFD0D7E1);
        }
        return new Theme.Palette(0xFF1B1B24, 0xFF28303E, 0xFF354052, 0xFF2A3240,
                0xFF52698E, 0xFFF1F3F8, 0xFFAAB2C0, 0xFFFFFFFF, 0xFFDCE5F4,
                0xFF8DA4C7, 0xFF596270, true, dp(0.8f), dp(9.6f), 1f,
                0.25f, 0.5f, 0xFFE4E8F0, 0xFFA6AFBD, null,
                0x20FFFFFF, 0x26080B12, 0xFF242B36, 0xFFD2D8E2);
    }

    private PaletteStyle parsePaletteStyle(String value) {
        if ("system".equalsIgnoreCase(value)) return PaletteStyle.SYSTEM;
        if ("light".equalsIgnoreCase(value)) return PaletteStyle.LIGHT;
        if ("dark".equalsIgnoreCase(value)) return PaletteStyle.DARK;
        return PaletteStyle.SCREENSHOT;
    }

    private Theme.Palette paletteForKeyboardView(Theme.Palette source) {
        return new Theme.Palette(Color.TRANSPARENT, source.keyBackground,
                source.actionKeyBackground, source.spaceBarBackground,
                source.activatedKeyBackground, source.labelColor, source.subLabelColor,
                source.activatedLabelColor, source.pressedLabelColor, source.lockedModifierColor,
                source.borderColor, source.borderEnabled, source.borderWidth, source.borderRadius,
                1f, source.secondaryDimming,
                source.greyedDimming, source.actionLabelColor, source.actionSubLabelColor,
                source.indicatorColors, source.keyGradientTopOverlay,
                source.keyGradientBottomOverlay, source.functionKeyBackground,
                source.functionLabelColor);
    }

    private void applyKeyboardPalette() {
        keyboard.setPalette(paletteForKeyboardView(basePalette));
        applySurfaceBackground();
    }

    private void applySurfaceBackground() {
        int base = keyboardSurfaceColor != null ? keyboardSurfaceColor
                : basePalette == null ? 0xFF1B1B24 : basePalette.keyboardBackground;
        int top = blendColor(base, Color.WHITE, 0.06f);
        int bottom = blendColor(base, Color.BLACK, 0.14f);
        android.graphics.drawable.GradientDrawable surface = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {top, base, bottom});
        surface.setCornerRadius(dp(17));
        surface.setStroke(Math.max(1, dp(0.65f)), 0x244E5360);
        surface.setAlpha(Math.round(255f * keyboardBackgroundOpacity * Color.alpha(base) / 255f));
        setBackground(surface);
    }

    private int blendColor(int from, int to, float fraction) {
        float amount = Math.max(0f, Math.min(1f, fraction));
        return Color.rgb(
                Math.round(Color.red(from) * (1f - amount) + Color.red(to) * amount),
                Math.round(Color.green(from) * (1f - amount) + Color.green(to) * amount),
                Math.round(Color.blue(from) * (1f - amount) + Color.blue(to) * amount));
    }

    private LayoutParams keyboardLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }

    private void applyKeyboardHorizontalInset() {
        ViewGroup parent = getParent() instanceof ViewGroup ? (ViewGroup) getParent() : null;
        if (parent instanceof FrameLayout && form != Form.FLOATING) {
            ViewGroup.LayoutParams current = getLayoutParams();
            FrameLayout.LayoutParams params = current instanceof FrameLayout.LayoutParams
                    ? (FrameLayout.LayoutParams) current
                    : new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            params.width = LayoutParams.MATCH_PARENT;
            params.leftMargin = dp(keyboardHorizontalInsetDp);
            params.rightMargin = dp(keyboardHorizontalInsetDp);
            params.gravity = Gravity.BOTTOM;
            setLayoutParams(params);
        } else if (!(parent instanceof FrameLayout)) {
            ViewGroup.LayoutParams current = keyboard.getLayoutParams();
            LayoutParams params = current instanceof LayoutParams
                    ? (LayoutParams) current
                    : new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            params.leftMargin = dp(keyboardHorizontalInsetDp);
            params.rightMargin = dp(keyboardHorizontalInsetDp);
            keyboard.setLayoutParams(params);
        }
        applySurfaceBackground();
    }

    private void installKeyPopupListener() {
        if (!keyboardKeyPopupEnabled) {
            keyboard.setKeyPopupListener(null);
            dismissKeyPopups();
            return;
        }
        keyboard.setKeyPopupListener(new Keyboard2View.KeyPopupListener() {
            @Override
            public void onKeyPopupShow(int pointerId, Keyboard2View.KeyPopupInfo info) {
                showKeyPopup(pointerId, info.label, info.keyBounds.centerX(), info.keyBounds.top);
            }

            @Override
            public void onKeyPopupTarget(int pointerId, String label, boolean labelKeyFont, int slot) {
                PopupWindow popup = keyPopups.get(pointerId);
                if (popup != null && popup.getContentView() instanceof TextView)
                    ((TextView) popup.getContentView()).setText(label);
            }

            @Override
            public void onKeyPopupLatch(int pointerId, boolean latched) {
                PopupWindow popup = keyPopups.get(pointerId);
                if (popup != null && popup.getContentView() instanceof TextView) {
                    TextView label = (TextView) popup.getContentView();
                    label.setAlpha(latched ? 0.78f : 1f);
                }
            }

            @Override
            public void onKeyPopupHide(int pointerId) {
                dismissKeyPopup(pointerId);
            }

            @Override
            public void onKeyPopupHideAll() {
                dismissKeyPopups();
            }
        });
    }

    private void showKeyPopup(int pointerId, String text, float centerX, float keyTop) {
        dismissKeyPopup(pointerId);
        TextView label = new TextView(getContext());
        label.setText(text == null ? "" : text);
        label.setTextColor(basePalette == null ? Color.WHITE : basePalette.labelColor);
        label.setTextSize(24);
        label.setTypeface(keyboard.labelFont(), Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        int horizontal = dp(14);
        label.setPadding(horizontal, dp(8), horizontal, dp(8));
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(basePalette == null ? 0xFF28303E : basePalette.keyBackground);
        background.setCornerRadius(dp(14));
        background.setStroke(Math.max(1, dp(0.8f)), basePalette == null
                ? 0xFF52698E : basePalette.borderColor);
        label.setBackground(background);
        PopupWindow popup = new PopupWindow(label, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, false);
        popup.setElevation(dp(8));
        popup.setClippingEnabled(false);
        int xOffset = Math.round(centerX - keyboard.getWidth() / 2f);
        int yOffset = Math.round(keyTop - keyboard.getHeight() - dp(54));
        popup.showAsDropDown(keyboard, xOffset, yOffset, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        keyPopups.put(pointerId, popup);
    }

    private void dismissKeyPopup(int pointerId) {
        PopupWindow popup = keyPopups.remove(pointerId);
        if (popup != null && popup.isShowing()) popup.dismiss();
    }

    private void dismissKeyPopups() {
        for (PopupWindow popup : keyPopups.values())
            if (popup != null && popup.isShowing()) popup.dismiss();
        keyPopups.clear();
    }

    private float effectiveKeyboardHeightScale() {
        return form == Form.FLOATING ? keyboardHeightScale * floatingHeightScale
                : keyboardHeightScale;
    }

    private void loadKeyColors() {
        keyColors.clear();
        try {
            JSONObject root = new JSONObject(preferences.getString(KEY_KEY_COLORS, "{}"));
            java.util.Iterator<String> layouts = root.keys();
            while (layouts.hasNext()) {
                String layoutId = layouts.next();
                JSONObject keys = root.optJSONObject(layoutId);
                if (keys == null) continue;
                Map<String, Integer> colors = new HashMap<>();
                java.util.Iterator<String> ids = keys.keys();
                while (ids.hasNext()) {
                    String id = ids.next();
                    if (id.matches("[0-9]+:[0-9]+")) colors.put(id, keys.optInt(id));
                }
                if (!colors.isEmpty()) keyColors.put(layoutId, colors);
            }
        } catch (JSONException ignored) {
            keyColors.clear();
        }
    }

    private void persistKeyColors() {
        JSONObject root = new JSONObject();
        for (Map.Entry<String, Map<String, Integer>> layout : keyColors.entrySet()) {
            JSONObject keys = new JSONObject();
            for (Map.Entry<String, Integer> color : layout.getValue().entrySet()) {
                try {
                    keys.put(color.getKey(), color.getValue());
                } catch (JSONException ignored) {
                }
            }
            try {
                root.put(layout.getKey(), keys);
            } catch (JSONException ignored) {
            }
        }
        preferences.edit().putString(KEY_KEY_COLORS, root.toString()).apply();
    }

    private void applyKeyColors() {
        Map<String, Keyboard2View.KeyColorOverride> overrides = new HashMap<>();
        Map<String, Integer> saved = keyColors.get(activeLayout);
        if (saved != null) {
            for (Map.Entry<String, Integer> entry : saved.entrySet()) {
                overrides.put(entry.getKey(), new Keyboard2View.KeyColorOverride(
                        entry.getValue(), null, null, null, null));
            }
        }
        overrides.putAll(hostKeyColorOverrides);
        keyboard.setKeyColorOverrides(overrides);
    }

    private void loadLayoutIds() {
        String[] ids = getResources().getStringArray(juloo.keyboard2.R.array.inapp_layout_values);
        String[] labels = getResources().getStringArray(juloo.keyboard2.R.array.inapp_layout_entries);
        for (int i = 0; i < Math.min(ids.length, labels.length); i++) layoutLabels.put(ids[i], labels[i]);
        layouts.addAll(Arrays.asList(ids));
        if (!layouts.contains(textLayout)) layouts.add(0, textLayout);
    }

    private void loadTextLayout(String id) {
        if ("numeric".equals(id) || "greekmath".equals(id)) return;
        textLayout = id;
        preferences.edit().putString(SELECTED_LAYOUT, id).apply();
        activeLayout = id;
        applyLayout();
    }

    private void applyLayout() {
        String resourceName = activeLayout;
        if ("numeric".equals(resourceName)
                && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            resourceName = "numeric_landscape";
        }
        int resource = layoutResource(resourceName);
        KeyboardData data = KeyboardData.load(getResources(), resource);
        if (data == null) return;
        if (form == Form.SPLIT) data = LayoutModifier.split(data, LayoutModifier.gapUnits(data, splitGap));
        keyboard.setKeyboard(data);
        applyKeyColors();
        touchCorrection.setLayoutId(activeLayout);
    }

    private int layoutResource(String id) {
        if (id == null || id.trim().isEmpty()) return 0;
        int resource = getResources().getIdentifier(id, "xml", getContext().getPackageName());
        return resource != 0 ? resource : getResources().getIdentifier(id, "xml", "juloo.keyboard2");
    }

    private void handleEvent(KeyValue.Event event) {
        switch (event) {
            case SWITCH_NUMERIC:
                activeLayout = "numeric";
                applyLayout();
                break;
            case SWITCH_GREEKMATH:
                activeLayout = "greekmath";
                applyLayout();
                break;
            case SWITCH_TEXT:
            case SWITCH_BACK_EMOJI:
            case SWITCH_BACK_CLIPBOARD:
                closePanels();
                loadTextLayout(textLayout);
                break;
            case SWITCH_CLIPBOARD:
                showClipboard();
                break;
            case SWITCH_FORWARD:
            case CHANGE_METHOD_NEXT:
                cycleLayout(true);
                break;
            case SWITCH_BACKWARD:
            case CHANGE_METHOD_PREV:
                cycleLayout(false);
                break;
            case CHANGE_METHOD_PICKER:
                toggleLayoutPicker();
                break;
            case CONFIG:
                listener.onSettingsRequested();
                break;
            case SWITCH_VOICE_TYPING:
            case SWITCH_VOICE_TYPING_CHOOSER:
                startVoiceInput();
                break;
            case HIDE_SELF:
                listener.onHideRequested();
                break;
            case CAPS_LOCK:
                capsLocked = !capsLocked;
                keyboard.set_shift_state(capsLocked, capsLocked);
                break;
            case ACTION:
                sendKey(KeyEvent.KEYCODE_ENTER, Pointers.Modifiers.EMPTY);
                break;
            case SWITCH_EMOJI:
                showEmojiPicker();
                break;
            default:
                listener.onAction(event.name().toLowerCase());
                break;
        }
    }

    private void handleEditing(KeyValue.Editing editing, Pointers.Modifiers modifiers) {
        switch (editing) {
            case COPY:
                copy();
                break;
            case CUT:
                cut();
                break;
            case PASTE:
                paste();
                break;
            case PASTE_PLAIN:
                paste(true);
                break;
            case SELECT_ALL:
                performContextAction(android.R.id.selectAll);
                break;
            case BACKSPACE:
                if (input != null) {
                    CharSequence selected = input.getSelectedText(0);
                    if (selected != null && selected.length() > 0) input.commitText("", 1);
                    else input.deleteSurroundingText(1, 0);
                }
                break;
            case DELETE_WORD:
                deleteWord(false);
                break;
            case FORWARD_DELETE_WORD:
                deleteWord(true);
                break;
            case SPACE_BAR:
                commitCharacter(' ', modifiers);
                break;
            case SELECTION_CANCEL:
                cancelSelection();
                break;
            case UNDO:
                sendKey(KeyEvent.KEYCODE_Z, Pointers.Modifiers.EMPTY.with_extra_mod(
                        KeyValue.makeInternalModifier(KeyValue.Modifier.CTRL)));
                break;
            case REDO:
                sendKey(KeyEvent.KEYCODE_Y, Pointers.Modifiers.EMPTY.with_extra_mod(
                        KeyValue.makeInternalModifier(KeyValue.Modifier.CTRL)));
                break;
            case REPLACE:
            case SHARE:
            case ASSIST:
            case AUTOFILL:
                listener.onAction("edit:" + editing.name().toLowerCase());
                break;
        }
    }

    private void sendKey(int code, Pointers.Modifiers modifiers) {
        if (input == null) return;
        int meta = 0;
        if (modifiers.has(KeyValue.Modifier.SHIFT)) meta |= KeyEvent.META_SHIFT_ON;
        if (modifiers.has(KeyValue.Modifier.CTRL)) meta |= KeyEvent.META_CTRL_ON;
        if (modifiers.has(KeyValue.Modifier.ALT)) meta |= KeyEvent.META_ALT_ON;
        if (modifiers.has(KeyValue.Modifier.META)) meta |= KeyEvent.META_META_ON;
        long now = android.os.SystemClock.uptimeMillis();
        input.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD));
        input.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD));
    }

    private void dispatchKeyEvent(int code, Pointers.Modifiers modifiers) {
        boolean leftRight = code == KeyEvent.KEYCODE_DPAD_LEFT
                || code == KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean backspace = code == KeyEvent.KEYCODE_DEL;
        boolean forwardDelete = code == KeyEvent.KEYCODE_FORWARD_DEL;
        boolean control = modifiers.has(KeyValue.Modifier.CTRL);
        boolean alt = modifiers.has(KeyValue.Modifier.ALT);
        boolean shift = modifiers.has(KeyValue.Modifier.SHIFT);
        boolean meta = modifiers.has(KeyValue.Modifier.META);

        if (leftRight) {
            if (alt && !control && altWordNavigationEnabled && wordNavigationEnabled) {
                control = true;
                alt = false;
            }
            if (shift && control && !wordSelectionEnabled) control = false;
            else if (control && !wordNavigationEnabled
                    && !(shift && wordSelectionEnabled)) control = false;
            sendKey(code, makeModifiers(control, alt, shift, meta));
            return;
        }

        if ((backspace || forwardDelete) && control) {
            if (wordDeletionEnabled) {
                deleteWord(forwardDelete);
                return;
            }
            control = false;
            modifiers = makeModifiers(false, alt, shift, meta);
        }
        sendKey(code, modifiers);
    }

    private Pointers.Modifiers makeModifiers(boolean control, boolean alt,
                                             boolean shift, boolean meta) {
        Pointers.Modifiers result = Pointers.Modifiers.EMPTY;
        if (control) result = result.with_extra_mod(
                KeyValue.makeInternalModifier(KeyValue.Modifier.CTRL));
        if (alt) result = result.with_extra_mod(
                KeyValue.makeInternalModifier(KeyValue.Modifier.ALT));
        if (shift) result = result.with_extra_mod(
                KeyValue.makeInternalModifier(KeyValue.Modifier.SHIFT));
        if (meta) result = result.with_extra_mod(
                KeyValue.makeInternalModifier(KeyValue.Modifier.META));
        return result;
    }

    private void commit(String text) {
        if (input == null || text == null || text.isEmpty()) return;
        input.commitText(text, 1);
        listener.onTextChanged(text);
        if (clipboardOpen || layoutPickerOpen || emojiPickerOpen) closePanels();
    }

    private void commitCharacter(char value, Pointers.Modifiers modifiers) {
        if (input == null) return;
        boolean hasCommandModifier = modifiers.has(KeyValue.Modifier.CTRL)
                || modifiers.has(KeyValue.Modifier.ALT)
                || modifiers.has(KeyValue.Modifier.META);
        int code = hasCommandModifier ? asciiKeyCode(value) : 0;
        if (code != 0) {
            sendKey(code, modifiers);
            return;
        }
        commit(String.valueOf(value));
    }

    private void commitString(String value, Pointers.Modifiers modifiers) {
        if (value == null || value.isEmpty()) return;
        boolean hasCommandModifier = modifiers.has(KeyValue.Modifier.CTRL)
                || modifiers.has(KeyValue.Modifier.ALT)
                || modifiers.has(KeyValue.Modifier.META);
        if (hasCommandModifier) {
            boolean allMapped = true;
            for (int i = 0; i < value.length(); i++) {
                if (asciiKeyCode(value.charAt(i)) == 0) {
                    allMapped = false;
                    break;
                }
            }
            if (allMapped) {
                for (int i = 0; i < value.length(); i++)
                    sendKey(asciiKeyCode(value.charAt(i)), modifiers);
                return;
            }
        }
        commit(value);
    }

    private int asciiKeyCode(char value) {
        char lower = Character.toLowerCase(value);
        if (lower >= 'a' && lower <= 'z') return KeyEvent.KEYCODE_A + lower - 'a';
        if (value >= '1' && value <= '9') return KeyEvent.KEYCODE_1 + value - '1';
        if (value == '0') return KeyEvent.KEYCODE_0;
        if (value == ' ') return KeyEvent.KEYCODE_SPACE;
        return 0;
    }

    private void copy() {
        CharSequence selected = input == null ? null : input.getSelectedText(0);
        if (clipboard != null && selected != null && selected.length() > 0) {
            clipboard.setPrimaryClip(ClipData.newPlainText("text", selected));
            remember(selected.toString());
        }
    }

    private void cut() {
        CharSequence selected = input == null ? null : input.getSelectedText(0);
        if (clipboard != null && selected != null && selected.length() > 0) {
            clipboard.setPrimaryClip(ClipData.newPlainText("text", selected));
            remember(selected.toString());
            input.commitText("", 1);
        }
    }

    private void paste() {
        paste(false);
    }

    private void paste(boolean plainTextOnly) {
        if (clipboard == null || !clipboard.hasPrimaryClip()) return;
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        Uri uri = clip.getItemAt(0).getUri();
        if (uri != null && !plainTextOnly) {
            listener.onClipboardImage(uri);
            return;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(getContext());
        if (text != null) {
            commit(text.toString());
            remember(text.toString());
        }
    }

    private void updatePastePictureState() {
        ClipDescription description = clipboard == null ? null : clipboard.getPrimaryClipDescription();
        keyboard.setPasteShowsPicture(description != null && description.hasMimeType("image/*"));
    }

    private void setVoiceState(boolean listening) {
        if (keyboard != null) keyboard.setVoiceTypingActive(listening);
        listener.onVoiceStateChanged(listening);
    }

    private void remember(String text) {
        if (text == null || text.trim().isEmpty()) return;
        history.remove(text);
        history.add(0, text);
        while (history.size() > MAX_HISTORY) history.remove(history.size() - 1);
        JSONArray saved = new JSONArray();
        for (String value : history) saved.put(value);
        preferences.edit().putString(HISTORY, saved.toString()).apply();
    }

    private void showClipboard() {
        closePanels();
        clipboardOpen = true;
        emojiPickerOpen = false;
        panel.setVisibility(VISIBLE);
        panel.removeAllViews();
        addPanelTitle("Clipboard");
        addPanelButton("ABC", () -> {
            closePanels();
            loadTextLayout(textLayout);
        });
        addPanelButton("Paste", this::paste);
        addPanelButton("Copy", this::copy);
        addPanelButton("Cut", this::cut);
        addPanelButton("Clear", this::clearClipboardHistory);
        HorizontalScrollView scroll = new HorizontalScrollView(getContext());
        LinearLayout items = new LinearLayout(getContext());
        items.setOrientation(HORIZONTAL);
        for (String item : history) addButton(items, preview(item), () -> commit(item));
        scroll.addView(items);
        panel.addView(scroll);
        keyboard.setVisibility(GONE);
    }

    private void toggleLayoutPicker() {
        if (layoutPickerOpen) {
            closePanels();
            return;
        }
        closePanels();
        layoutPickerOpen = true;
        emojiPickerOpen = false;
        panel.setVisibility(VISIBLE);
        panel.removeAllViews();
        addPanelTitle("Keyboard layouts");
        addPanelButton("Cancel", this::closePanels);
        HorizontalScrollView scroll = new HorizontalScrollView(getContext());
        LinearLayout list = new LinearLayout(getContext());
        list.setOrientation(HORIZONTAL);
        for (String id : layouts) addButton(list, layoutLabels.getOrDefault(id, id), () -> {
            closePanels();
            textLayout = id;
            loadTextLayout(id);
        });
        scroll.addView(list);
        panel.addView(scroll);
        keyboard.setVisibility(GONE);
    }

    private void showEmojiPicker() {
        closePanels();
        emojiPickerOpen = true;
        panel.setVisibility(VISIBLE);
        panel.removeAllViews();
        addPanelTitle("Emoji");
        addPanelButton("Done", this::closePanels);
        HorizontalScrollView scroll = new HorizontalScrollView(getContext());
        LinearLayout list = new LinearLayout(getContext());
        list.setOrientation(HORIZONTAL);
        String[] emoji = {"😀", "😂", "🥹", "😍", "🤔", "😎", "😭", "👍", "🙏", "❤️", "🔥", "🎉", "🚀", "💻", "🐛", "✅", "❌", "⚠️", "✨", "🌍", "☕", "🧠", "📱", "🎧"};
        for (String value : emoji) addButton(list, value, () -> commit(value));
        scroll.addView(list);
        panel.addView(scroll);
        keyboard.setVisibility(GONE);
    }

    private void closePanels() {
        clipboardOpen = false;
        layoutPickerOpen = false;
        emojiPickerOpen = false;
        panel.removeAllViews();
        panel.setVisibility(GONE);
        keyboard.setVisibility(VISIBLE);
    }

    private void addPanelButton(String label, Runnable action) {
        addButton(panel, label, action);
    }

    private void addButton(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(getContext());
        button.setText(label);
        button.setTextSize(12);
        button.setMinWidth(dp(48));
        button.setOnClickListener(view -> action.run());
        parent.addView(button, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(44)));
    }

    private void addPanelTitle(String text) {
        TextView title = new TextView(getContext());
        title.setText(text);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(dp(12), dp(8), dp(12), dp(8));
        panel.addView(title);
    }

    private void performContextAction(int action) {
        if (input != null) input.performContextMenuAction(action);
    }

    private void deleteWord(boolean forward) {
        if (input == null) return;
        CharSequence selected = input.getSelectedText(0);
        if (selected != null && selected.length() > 0) {
            input.commitText("", 1);
            return;
        }
        CharSequence text = forward ? input.getTextAfterCursor(2048, 0)
                : input.getTextBeforeCursor(2048, 0);
        if (text == null || text.length() == 0) return;
        String value = text.toString();
        if (forward) {
            int offset = 0;
            while (offset < value.length()) {
                int codePoint = value.codePointAt(offset);
                if (!Character.isWhitespace(codePoint)) break;
                offset += Character.charCount(codePoint);
            }
            while (offset < value.length()) {
                int codePoint = value.codePointAt(offset);
                if (!isWordCharacter(codePoint)) break;
                offset += Character.charCount(codePoint);
            }
            if (offset == 0) offset += Character.charCount(value.codePointAt(0));
            int count = value.codePointCount(0, offset);
            deleteSurroundingCodePoints(0, count, "", value.substring(0, offset));
        } else {
            int offset = value.length();
            while (offset > 0) {
                int codePoint = value.codePointBefore(offset);
                if (!Character.isWhitespace(codePoint)) break;
                offset -= Character.charCount(codePoint);
            }
            while (offset > 0) {
                int codePoint = value.codePointBefore(offset);
                if (!isWordCharacter(codePoint)) break;
                offset -= Character.charCount(codePoint);
            }
            if (offset == value.length())
                offset -= Character.charCount(value.codePointBefore(offset));
            int count = value.codePointCount(offset, value.length());
            deleteSurroundingCodePoints(count, 0, value.substring(offset), "");
        }
    }

    private boolean isWordCharacter(int value) {
        return Character.isLetterOrDigit(value) || value == '_';
    }

    private void deleteSurroundingCodePoints(int beforeCount, int afterCount,
                                             String before, String after) {
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            input.deleteSurroundingTextInCodePoints(beforeCount, afterCount);
        } else {
            int beforeUnits = before.offsetByCodePoints(before.length(), -beforeCount);
            int afterUnits = after.offsetByCodePoints(0, afterCount);
            input.deleteSurroundingText(beforeUnits, afterUnits);
        }
    }

    private void cancelSelection() {
        if (input == null) return;
        CharSequence selected = input.getSelectedText(0);
        if (selected != null && selected.length() > 0)
            sendKey(KeyEvent.KEYCODE_DPAD_RIGHT, Pointers.Modifiers.EMPTY);
    }

    private void slideCursor(KeyValue.Slider slider, int repeat, Pointers.Modifiers modifiers) {
        int code;
        switch (slider) {
            case Cursor_left:
            case Selection_cursor_left:
                code = KeyEvent.KEYCODE_DPAD_LEFT;
                break;
            case Cursor_right:
            case Selection_cursor_right:
                code = KeyEvent.KEYCODE_DPAD_RIGHT;
                break;
            case Cursor_up:
                code = KeyEvent.KEYCODE_DPAD_UP;
                break;
            default:
                code = KeyEvent.KEYCODE_DPAD_DOWN;
                break;
        }
        if (slider == KeyValue.Slider.Selection_cursor_left
                || slider == KeyValue.Slider.Selection_cursor_right)
            modifiers = modifiers.with_extra_mod(KeyValue.makeInternalModifier(KeyValue.Modifier.SHIFT));
        int count = Math.max(1, Math.min(32, Math.abs(repeat)));
        for (int i = 0; i < count; i++) sendKey(code, modifiers);
    }

    private void runHostAction(String id) {
        switch (id) {
            case "keyboard.command_palette":
                toggleLayoutPicker();
                break;
            case "keyboard.layout_next":
                cycleLayout(true);
                break;
            case "keyboard.layout_previous":
                cycleLayout(false);
                break;
            case "keyboard.clipboard":
                showClipboard();
                break;
            case "keyboard.voice":
                startVoiceInput();
                break;
            case "keyboard.float":
                setForm(form == Form.FLOATING ? Form.DOCKED : Form.FLOATING);
                break;
            case "keyboard.split":
                setForm(form == Form.SPLIT ? Form.DOCKED : Form.SPLIT);
                break;
            case "keyboard.hide":
                listener.onHideRequested();
                break;
            default:
                listener.onAction(id);
                break;
        }
    }

    private boolean dragFloatingKeyboard(View view, MotionEvent event) {
        if (form != Form.FLOATING) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragX = event.getRawX() - getTranslationX();
                dragY = event.getRawY() - getTranslationY();
                return true;
            case MotionEvent.ACTION_MOVE:
                setTranslationX(event.getRawX() - dragX);
                setTranslationY(event.getRawY() - dragY);
                return true;
            default:
                return false;
        }
    }

    private String preview(String text) {
        String value = text.replace('\n', ' ');
        return value.length() > 28 ? value.substring(0, 28) + "…" : value;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
