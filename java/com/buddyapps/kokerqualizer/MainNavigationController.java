package com.buddyapps.kokerqualizer;

import android.app.Activity;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Owns the slide-out navigation drawer and full-screen sub-panel overlay
 * (Presets, Auto-EQ, Calibration, YouTube, Devices, Sound Test, Health, Settings).
 */
class MainNavigationController {
    static final int PANEL_NONE = 0;
    static final int PANEL_PRESETS = 1;
    static final int PANEL_BT = 2;
    static final int PANEL_TEST = 3;
    static final int PANEL_SETTINGS = 4;
    static final int PANEL_HEALTH = 5;
    static final int PANEL_YOUTUBE = 6;
    static final int PANEL_CALIBRATE = 7;
    static final int PANEL_AUTOEQ = 8;

    interface Callbacks {
        void onToggleFloatingBubble();
        void onToggleMorphPad();
        void onShowSleepTimer();
        void onStopSoundTest();
        void onPanelClosed();
    }

    private final Activity activity;
    private final DrawerPanels.Host drawerHost;
    private final Callbacks callbacks;

    private FrameLayout drawerScrim;
    private LinearLayout drawerSheet;
    private FrameLayout subPanelOverlay;
    private LinearLayout subPanelHeader;
    private TextView subPanelTitle;
    private ScrollView subPanelScroll;
    private int openSubPanel = PANEL_NONE;

    private final boolean[] favOnlyRef = new boolean[]{false};
    private final int[] calStepRef = new int[]{0};
    private final float[] calOffsetsRef = new float[7];
    private final String[] presetSearchRef = new String[]{""};
    private final String[] autoEqFilterRef = new String[]{""};
    private final String[] ytFilterRef = new String[]{""};

    MainNavigationController(Activity activity, DrawerPanels.Host drawerHost, Callbacks callbacks) {
        this.activity = activity;
        this.drawerHost = drawerHost;
        this.callbacks = callbacks;
    }

    void attachToRoot(FrameLayout rootFrame) {
        buildSubPanelOverlay();
        rootFrame.addView(subPanelOverlay);
        buildSlideOutDrawer();
        rootFrame.addView(drawerScrim);
    }

    void applyInsets() {
        UiStyle.applyEdgeToEdgeInsets(subPanelOverlay, subPanelHeader, UiStyle.dp(activity, 12), subPanelScroll, UiStyle.dp(activity, 20));
        UiStyle.applyEdgeToEdgeInsets(drawerScrim, drawerSheet, UiStyle.dp(activity, 18), drawerSheet, UiStyle.dp(activity, 20));
    }

    boolean isDrawerOpen() {
        return drawerScrim != null && drawerScrim.getVisibility() == View.VISIBLE;
    }

    int getOpenSubPanel() {
        return openSubPanel;
    }

    boolean hasOpenPanel() {
        return openSubPanel != PANEL_NONE;
    }

    void openDrawer() {
        drawerScrim.setVisibility(View.VISIBLE);
        drawerSheet.setTranslationX(-UiStyle.dp(activity, 300));
        drawerSheet.animate().translationX(0).setDuration(190).start();
    }

    void closeDrawer() {
        drawerSheet.animate().translationX(-UiStyle.dp(activity, 300)).setDuration(150)
                .withEndAction(() -> drawerScrim.setVisibility(View.GONE)).start();
    }

    void openPanel(int panel) {
        if (openSubPanel != panel) {
            callbacks.onStopSoundTest();
        }
        openSubPanel = panel;
        subPanelOverlay.setVisibility(View.VISIBLE);
        subPanelOverlay.requestApplyInsets();
        subPanelScroll.scrollTo(0, 0);
        renderOpenSubPanel();
    }

    void closePanel() {
        callbacks.onStopSoundTest();
        openSubPanel = PANEL_NONE;
        subPanelOverlay.setVisibility(View.GONE);
        callbacks.onPanelClosed();
    }

    void renderOpenSubPanel() {
        if (openSubPanel == PANEL_NONE) return;
        final int savedY = subPanelScroll.getScrollY();
        subPanelScroll.removeAllViews();
        if (openSubPanel == PANEL_PRESETS) {
            subPanelTitle.setText("Preset Library");
            subPanelScroll.addView(DrawerPanels.buildPresetsPanel(activity, drawerHost, favOnlyRef, presetSearchRef));
        } else if (openSubPanel == PANEL_AUTOEQ) {
            subPanelTitle.setText("Auto-EQ Profiles");
            subPanelScroll.addView(DrawerPanels.buildAutoEqPanel(activity, drawerHost, autoEqFilterRef));
        } else if (openSubPanel == PANEL_BT) {
            subPanelTitle.setText("Device Automation");
            subPanelScroll.addView(DrawerPanels.buildBluetoothPanel(activity, drawerHost));
        } else if (openSubPanel == PANEL_TEST) {
            subPanelTitle.setText("Sound Test Suite");
            subPanelScroll.addView(DrawerPanels.buildSoundTestPanel(activity, drawerHost));
        } else if (openSubPanel == PANEL_HEALTH) {
            subPanelTitle.setText("Audio Health Check");
            subPanelScroll.addView(DrawerPanels.buildHealthPanel(activity, drawerHost));
        } else if (openSubPanel == PANEL_YOUTUBE) {
            subPanelTitle.setText("YouTube Playlists");
            subPanelScroll.addView(DrawerPanels.buildYouTubePanel(activity, drawerHost, ytFilterRef));
        } else if (openSubPanel == PANEL_CALIBRATE) {
            subPanelTitle.setText("Speaker Calibration");
            subPanelScroll.addView(DrawerPanels.buildCalibrationPanel(activity, drawerHost, calStepRef, calOffsetsRef));
        } else if (openSubPanel == PANEL_SETTINGS) {
            subPanelTitle.setText("Settings");
            subPanelScroll.addView(DrawerPanels.buildSettingsPanel(activity, drawerHost));
        }
        if (savedY > 0) {
            subPanelScroll.post(() -> subPanelScroll.scrollTo(0, savedY));
        }
    }

    private void buildSubPanelOverlay() {
        subPanelOverlay = new FrameLayout(activity);
        subPanelOverlay.setBackgroundColor(UiStyle.BG_AMOLED);
        subPanelOverlay.setVisibility(View.GONE);
        subPanelOverlay.setClickable(true);

        LinearLayout col = new LinearLayout(activity);
        col.setOrientation(LinearLayout.VERTICAL);

        subPanelHeader = new LinearLayout(activity);
        subPanelHeader.setOrientation(LinearLayout.HORIZONTAL);
        subPanelHeader.setGravity(Gravity.CENTER_VERTICAL);
        subPanelHeader.setPadding(UiStyle.dp(activity, 14), UiStyle.dp(activity, 12), UiStyle.dp(activity, 14), UiStyle.dp(activity, 12));

        TextView backBtn = UiStyle.makePill(activity, "◀ Back to Deck", false, 0xFF39FF14);
        backBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, activity);
            closePanel();
        });
        subPanelHeader.addView(backBtn);

        subPanelTitle = new TextView(activity);
        subPanelTitle.setTextColor(UiStyle.TEXT_PRIMARY);
        subPanelTitle.setTextSize(16.5f);
        subPanelTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        subPanelTitle.setGravity(Gravity.END);
        subPanelHeader.addView(subPanelTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        col.addView(subPanelHeader);

        subPanelScroll = new ScrollView(activity);
        subPanelScroll.setClipToPadding(false);
        subPanelScroll.setPadding(UiStyle.dp(activity, 14), UiStyle.dp(activity, 4), UiStyle.dp(activity, 14), UiStyle.dp(activity, 24));
        col.addView(subPanelScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        subPanelOverlay.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildSlideOutDrawer() {
        drawerScrim = new FrameLayout(activity);
        drawerScrim.setBackgroundColor(0xAA000000);
        drawerScrim.setVisibility(View.GONE);
        drawerScrim.setOnClickListener(v -> closeDrawer());

        drawerSheet = new LinearLayout(activity);
        drawerSheet.setOrientation(LinearLayout.VERTICAL);
        drawerSheet.setBackgroundColor(0xFF0B0E16);
        drawerSheet.setPadding(UiStyle.dp(activity, 18), UiStyle.dp(activity, 24), UiStyle.dp(activity, 18), UiStyle.dp(activity, 24));
        drawerSheet.setClickable(true);

        ScrollView drawerScroll = new ScrollView(activity);
        drawerScroll.setVerticalScrollBarEnabled(false);
        LinearLayout drawerContent = new LinearLayout(activity);
        drawerContent.setOrientation(LinearLayout.VERTICAL);

        TextView brand = new TextView(activity);
        brand.setText("KOKERQUALIZER");
        brand.setTextColor(0xFF39FF14);
        brand.setTextSize(19f);
        brand.setTypeface(null, android.graphics.Typeface.BOLD);
        brand.setLetterSpacing(0.1f);
        drawerContent.addView(brand);

        TextView sub = new TextView(activity);
        sub.setText("v2.0.3 • 15B Pro DSP + Auto-EQ + Morph");
        sub.setTextColor(UiStyle.TEXT_MUTED);
        sub.setTextSize(12f);
        sub.setPadding(0, UiStyle.dp(activity, 2), 0, UiStyle.dp(activity, 18));
        drawerContent.addView(sub);

        addDrawerItem(drawerContent, "🎛  Main Equalizer Deck", () -> { closeDrawer(); closePanel(); });
        addDrawerItem(drawerContent, "🫧  Toggle Floating 3D Orb", () -> { closeDrawer(); callbacks.onToggleFloatingBubble(); });
        addDrawerItem(drawerContent, "✨  Toggle 2D Sound Morph Pad", () -> {
            closeDrawer();
            closePanel();
            callbacks.onToggleMorphPad();
        });
        addDrawerItem(drawerContent, "🎧  Auto-EQ Headphone & Speaker Library", () -> { closeDrawer(); openPanel(PANEL_AUTOEQ); });
        addDrawerItem(drawerContent, "🎯  Speaker Calibration Wizard", () -> { closeDrawer(); openPanel(PANEL_CALIBRATE); });
        addDrawerItem(drawerContent, "🎵  YouTube Playlists & History", () -> { closeDrawer(); openPanel(PANEL_YOUTUBE); });
        addDrawerItem(drawerContent, "⏰  Sleep Timer (Fade & Exit)", () -> { closeDrawer(); callbacks.onShowSleepTimer(); });
        addDrawerItem(drawerContent, "⭐  Preset Library, Tags & Share Codes", () -> { closeDrawer(); openPanel(PANEL_PRESETS); });
        addDrawerItem(drawerContent, "📡  Bluetooth, Wired & Per-App Profiles", () -> { closeDrawer(); openPanel(PANEL_BT); });
        addDrawerItem(drawerContent, "🔊  Sound & Sub Test Suite", () -> { closeDrawer(); openPanel(PANEL_TEST); });
        addDrawerItem(drawerContent, "🚗  Launch Car Mode Dash", () -> {
            closeDrawer();
            activity.startActivity(new Intent(activity, CarModeActivity.class));
        });
        addDrawerItem(drawerContent, "🩺  Audio Health Check", () -> { closeDrawer(); openPanel(PANEL_HEALTH); });
        addDrawerItem(drawerContent, "⚙  Settings", () -> { closeDrawer(); openPanel(PANEL_SETTINGS); });

        TextView shutdownItem = new TextView(activity);
        shutdownItem.setText("⏻  Stop All Music & Exit App");
        shutdownItem.setTextColor(0xFFFF5252);
        shutdownItem.setTextSize(14.5f);
        shutdownItem.setTypeface(null, android.graphics.Typeface.BOLD);
        shutdownItem.setPadding(UiStyle.dp(activity, 14), UiStyle.dp(activity, 13), UiStyle.dp(activity, 14), UiStyle.dp(activity, 13));
        shutdownItem.setBackground(UiStyle.roundRect(0x28FF1744, 0xFFFF5252, UiStyle.dp(activity, 1.3f), UiStyle.dp(activity, 14)));
        shutdownItem.setOnClickListener(v -> {
            UiStyle.hapticTick(v, activity);
            EqService.shutdownAppAndMusic(activity);
        });
        LinearLayout.LayoutParams sdLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sdLp.topMargin = UiStyle.dp(activity, 6);
        drawerContent.addView(shutdownItem, sdLp);

        drawerScroll.addView(drawerContent);
        drawerSheet.addView(drawerScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                UiStyle.dp(activity, 290), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        drawerScrim.addView(drawerSheet, lp);
    }

    private void addDrawerItem(LinearLayout parent, String label, Runnable action) {
        TextView tv = new TextView(activity);
        tv.setText(label);
        tv.setTextColor(UiStyle.TEXT_PRIMARY);
        tv.setTextSize(14.5f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(UiStyle.dp(activity, 14), UiStyle.dp(activity, 13), UiStyle.dp(activity, 14), UiStyle.dp(activity, 13));
        tv.setBackground(UiStyle.roundRect(UiStyle.SURFACE_CARD, UiStyle.BORDER_SUBTLE, UiStyle.dp(activity, 1), UiStyle.dp(activity, 14)));
        tv.setOnClickListener(v -> {
            UiStyle.hapticTick(v, activity);
            action.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UiStyle.dp(activity, 9);
        parent.addView(tv, lp);
    }
}
