package com.buddyapps.kokerqualizer;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * Builds and populates the expandable floating mini-mixer card shown when the
 * user taps the 3D Neon Green Crystal Orb.
 */
class BubblePopupController {
    interface Host {
        void onCollapseRequested();
        void onPopupContentPopulated(boolean eqActive);
    }

    private final Context ctx;
    private final Handler handler;
    private final Host host;

    private LinearLayout popupCard;
    private boolean userDraggingSlider = false;

    private TextView popBrandTv, popPresetTv, popNowPlayingTv, popPlayBtn;
    private TextView popPowerBtn, popAbBtn, popHwyBtn;
    private TextView popBassLbl, popMbcLbl, popPreampLbl, popVolLbl;
    private SeekBar popBassSeek, popMbcSeek, popPreampSeek, popVolSeek;
    private LinearLayout popChipRow;

    BubblePopupController(Context ctx, Handler handler, Host host) {
        this.ctx = ctx;
        this.handler = handler;
        this.host = host;
    }

    boolean isDraggingSlider() {
        return userDraggingSlider;
    }

    LinearLayout getCard() {
        return popupCard;
    }

    void reset() {
        userDraggingSlider = false;
        popupCard = null;
        popBrandTv = null;
        popPresetTv = null;
        popNowPlayingTv = null;
        popPlayBtn = null;
        popPowerBtn = null;
        popAbBtn = null;
        popHwyBtn = null;
        popBassLbl = null;
        popMbcLbl = null;
        popPreampLbl = null;
        popVolLbl = null;
        popBassSeek = null;
        popMbcSeek = null;
        popPreampSeek = null;
        popVolSeek = null;
        popChipRow = null;
    }

    LinearLayout buildPopupCard(int maxHeightPx) {
        // Intercept Back key to collapse the floating popup smoothly
        LinearLayout outer = new LinearLayout(ctx) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    host.onCollapseRequested();
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int cappedH = MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST);
                super.onMeasure(widthMeasureSpec, cappedH);
            }
        };
        outer.setOrientation(LinearLayout.VERTICAL);
        int pad = UiStyle.dp(ctx, 14);
        outer.setPadding(pad, UiStyle.dp(ctx, 12), pad, UiStyle.dp(ctx, 12));

        // Scrollable inner body so popup never clips in landscape
        ScrollView sv = new ScrollView(ctx);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);

        // 1. Header: Brand + Open Full App + Car Mode + Close + Shutdown
        LinearLayout hdr = new LinearLayout(ctx);
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        hdr.setGravity(Gravity.CENTER_VERTICAL);

        popBrandTv = new TextView(ctx);
        popBrandTv.setText("KOKERQUALIZER");
        popBrandTv.setTextColor(0xFF39FF14);
        popBrandTv.setTextSize(13f);
        popBrandTv.setTypeface(null, android.graphics.Typeface.BOLD);
        popBrandTv.setLetterSpacing(0.08f);
        hdr.addView(popBrandTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView fullBtn = UiStyle.makePill(ctx, "Deck", false, 0xFF39FF14);
        fullBtn.setTextSize(10.5f);
        fullBtn.setPadding(UiStyle.dp(ctx, 9), UiStyle.dp(ctx, 4), UiStyle.dp(ctx, 9), UiStyle.dp(ctx, 4));
        fullBtn.setOnClickListener(v -> {
            host.onCollapseRequested();
            try {
                Intent i = new Intent(ctx, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                ctx.startActivity(i);
            } catch (Throwable ignored) {}
        });
        int gapPx = UiStyle.dp(ctx, 5);
        hdr.addView(fullBtn, UiStyle.wrapLp(gapPx, 0));

        TextView carBtn = UiStyle.makePill(ctx, "🚗 Car", false, 0xFF39FF14);
        carBtn.setTextSize(10.5f);
        carBtn.setPadding(UiStyle.dp(ctx, 9), UiStyle.dp(ctx, 4), UiStyle.dp(ctx, 9), UiStyle.dp(ctx, 4));
        carBtn.setOnClickListener(v -> {
            host.onCollapseRequested();
            try {
                Intent i = new Intent(ctx, CarModeActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                ctx.startActivity(i);
            } catch (Throwable ignored) {}
        });
        hdr.addView(carBtn, UiStyle.wrapLp(gapPx, 0));

        TextView closeBtn = UiStyle.makePill(ctx, "✕", false, 0xFF94A3B8);
        closeBtn.setTextSize(11f);
        closeBtn.setPadding(UiStyle.dp(ctx, 8), UiStyle.dp(ctx, 4), UiStyle.dp(ctx, 8), UiStyle.dp(ctx, 4));
        closeBtn.setOnClickListener(v -> host.onCollapseRequested());
        hdr.addView(closeBtn, UiStyle.wrapLp(gapPx, 0));

        TextView killBtn = UiStyle.makePill(ctx, "⏻", true, 0xFFFF5252);
        killBtn.setTextSize(11f);
        killBtn.setPadding(UiStyle.dp(ctx, 8), UiStyle.dp(ctx, 4), UiStyle.dp(ctx, 8), UiStyle.dp(ctx, 4));
        killBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            host.onCollapseRequested();
            EqService.shutdownAppAndMusic(ctx);
        });
        hdr.addView(killBtn, UiStyle.wrapLp(gapPx, 0));
        card.addView(hdr);

        // 2. Active Preset Switcher Row
        LinearLayout pRow = new LinearLayout(ctx);
        pRow.setOrientation(LinearLayout.HORIZONTAL);
        pRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams prLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        prLp.topMargin = UiStyle.dp(ctx, 10);

        TextView prevP = UiStyle.makePill(ctx, "◀", false, 0xFF39FF14);
        prevP.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            EqStore.get(ctx).cyclePreset(-1);
            EqEngine.get(ctx).setAbFlatCompare(false);
            EqService.startOrRefresh(ctx);
            populatePopupContent();
        });
        pRow.addView(prevP);

        popPresetTv = new TextView(ctx);
        popPresetTv.setTextSize(16f);
        popPresetTv.setTypeface(null, android.graphics.Typeface.BOLD);
        popPresetTv.setGravity(Gravity.CENTER);
        popPresetTv.setSingleLine(true);
        popPresetTv.setEllipsize(TextUtils.TruncateAt.END);
        pRow.addView(popPresetTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView nextP = UiStyle.makePill(ctx, "▶", false, 0xFF39FF14);
        nextP.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            EqStore.get(ctx).cyclePreset(1);
            EqEngine.get(ctx).setAbFlatCompare(false);
            EqService.startOrRefresh(ctx);
            populatePopupContent();
        });
        pRow.addView(nextP);
        card.addView(pRow, prLp);

        // 3. Scrollable Quick Preset Chips
        HorizontalScrollView hsv = new HorizontalScrollView(ctx);
        hsv.setHorizontalScrollBarEnabled(false);
        popChipRow = new LinearLayout(ctx);
        popChipRow.setOrientation(LinearLayout.HORIZONTAL);
        hsv.addView(popChipRow);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hLp.topMargin = UiStyle.dp(ctx, 8);
        card.addView(hsv, hLp);

        // 4. Quick Toggles: Power | A/B | HWY
        LinearLayout tRow = new LinearLayout(ctx);
        tRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tLp.topMargin = UiStyle.dp(ctx, 10);

        popPowerBtn = UiStyle.makePill(ctx, "● ON", true, 0xFF39FF14);
        popPowerBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            EqStore.Settings s = EqStore.get(ctx).getSettings();
            s.eqEnabled = !s.eqEnabled;
            EqStore.get(ctx).save();
            EqService.startOrRefresh(ctx);
            populatePopupContent();
        });

        popAbBtn = UiStyle.makePill(ctx, "A/B", false, 0xFFFFB300);
        popAbBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            EqEngine.get(ctx).setAbFlatCompare(!EqEngine.get(ctx).isAbFlatCompare());
            EqService.startOrRefresh(ctx);
            populatePopupContent();
        });

        popHwyBtn = UiStyle.makePill(ctx, "🛣 HWY", false, 0xFF00E676);
        popHwyBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            EqStore.Settings s = EqStore.get(ctx).getSettings();
            s.highwayBoost = !s.highwayBoost;
            EqStore.get(ctx).save();
            EqService.startOrRefresh(ctx);
            populatePopupContent();
        });

        int wGap = UiStyle.dp(ctx, 6);
        tRow.addView(popPowerBtn, UiStyle.rowLp(1f, wGap));
        tRow.addView(popAbBtn, UiStyle.rowLp(1f, wGap));
        tRow.addView(popHwyBtn, UiStyle.rowLp(1f, 0));
        card.addView(tRow, tLp);

        // 5. Quick Sliders: Bass Boost, Punch (MBC), Preamp, Media Volume
        popBassLbl = makeSliderLabel("Bass Boost");
        card.addView(popBassLbl);
        popBassSeek = makePopupSeek(100, progress -> {
            EqStore.EqPreset p = EqStore.get(ctx).getActivePreset();
            if (p != null) {
                p.bassBoost = progress;
                popBassLbl.setText(String.format(Locale.US, "Bass Boost (%s): %d%%", EqStore.bassStyleName(p.bassStyle), progress));
                EqEngine.get(ctx).applyCurrent();
            }
        });
        card.addView(popBassSeek);

        popMbcLbl = makeSliderLabel("Punch (3-Band MBC)");
        card.addView(popMbcLbl);
        popMbcSeek = makePopupSeek(100, progress -> {
            EqStore.EqPreset p = EqStore.get(ctx).getActivePreset();
            if (p != null) {
                p.punchMbc = progress;
                popMbcLbl.setText(String.format(Locale.US, "Punch (MBC): %d%%", progress));
                EqEngine.get(ctx).applyCurrent();
            }
        });
        card.addView(popMbcSeek);

        popPreampLbl = makeSliderLabel("Preamp");
        card.addView(popPreampLbl);
        popPreampSeek = makePopupSeek(240, progress -> {
            EqStore.EqPreset p = EqStore.get(ctx).getActivePreset();
            if (p != null) {
                float db = (progress - 120) / 10f;
                p.preampDb = db;
                popPreampLbl.setText(String.format(Locale.US, "Preamp: %+.1f dB", db));
                EqEngine.get(ctx).applyCurrent();
            }
        });
        card.addView(popPreampSeek);

        popVolLbl = makeSliderLabel("Media Volume");
        card.addView(popVolLbl);
        popVolSeek = new SeekBar(ctx);
        popVolSeek.setMax(100);
        popVolSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!fromUser) return;
                try {
                    AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
                    if (am != null) {
                        int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                        int target = Math.round((progress / 100f) * max);
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
                        popVolLbl.setText(String.format(Locale.US, "Media Volume: %d%%", progress));
                    }
                } catch (Throwable ignored) {}
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { userDraggingSlider = true; }
            @Override public void onStopTrackingTouch(SeekBar sb) { userDraggingSlider = false; }
        });
        card.addView(popVolSeek);

        // 6. Now Playing + Media Transport Controls
        popNowPlayingTv = new TextView(ctx);
        popNowPlayingTv.setTextSize(11.5f);
        popNowPlayingTv.setTextColor(UiStyle.TEXT_SECONDARY);
        popNowPlayingTv.setSingleLine(true);
        popNowPlayingTv.setEllipsize(TextUtils.TruncateAt.END);
        popNowPlayingTv.setGravity(Gravity.CENTER);
        popNowPlayingTv.setPadding(0, UiStyle.dp(ctx, 8), 0, UiStyle.dp(ctx, 6));
        card.addView(popNowPlayingTv);

        LinearLayout mRow = new LinearLayout(ctx);
        mRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView mPrev = UiStyle.makePill(ctx, "⏮ Prev", false, 0xFF39FF14);
        mPrev.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            dispatchMediaControl(-1);
        });

        popPlayBtn = UiStyle.makePill(ctx, "⏯ Play", true, 0xFF39FF14);
        popPlayBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            dispatchMediaControl(0);
        });

        TextView mNext = UiStyle.makePill(ctx, "Next ⏭", false, 0xFF39FF14);
        mNext.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            dispatchMediaControl(1);
        });

        mRow.addView(mPrev, UiStyle.rowLp(1f, wGap));
        mRow.addView(popPlayBtn, UiStyle.rowLp(1f, wGap));
        mRow.addView(mNext, UiStyle.rowLp(1f, 0));
        card.addView(mRow);

        sv.addView(card);
        outer.addView(sv);
        this.popupCard = outer;
        return outer;
    }

    private TextView makeSliderLabel(String txt) {
        TextView tv = new TextView(ctx);
        tv.setText(txt);
        tv.setTextColor(UiStyle.TEXT_SECONDARY);
        tv.setTextSize(11.5f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(UiStyle.dp(ctx, 2), UiStyle.dp(ctx, 8), 0, 0);
        return tv;
    }

    private interface IntCb { void onChange(int val); }

    private SeekBar makePopupSeek(int max, IntCb cb) {
        SeekBar sb = new SeekBar(ctx);
        sb.setMax(max);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                cb.onChange(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { userDraggingSlider = true; }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                userDraggingSlider = false;
                EqStore.EqPreset p = EqStore.get(ctx).getActivePreset();
                if (p != null) {
                    EqStore.get(ctx).addOrUpdatePreset(p);
                    EqService.startOrRefresh(ctx);
                }
            }
        });
        return sb;
    }

    void populatePopupContent() {
        if (popupCard == null) return;
        EqStore store = EqStore.get(ctx);
        EqStore.Settings s = store.getSettings();
        EqStore.EqPreset p = store.getActivePreset();
        if (p == null) return;
        int accent = p.accentColor;
        boolean ab = EqEngine.get(ctx).isAbFlatCompare();

        host.onPopupContentPopulated(s.eqEnabled && !ab);

        popupCard.setBackground(UiStyle.roundRect(0xF40A0D14, EqStore.withAlpha(accent, 195), UiStyle.dp(ctx, 1.5f), UiStyle.dp(ctx, 22)));
        if (popBrandTv != null) popBrandTv.setTextColor(accent);
        popPresetTv.setText(p.name);
        popPresetTv.setTextColor(accent);

        popPowerBtn.setText(s.eqEnabled ? "● EQ ON" : "○ EQ OFF");
        UiStyle.stylePill(ctx, popPowerBtn, s.eqEnabled, accent);

        popAbBtn.setText(ab ? "A/B FLAT" : "A/B");
        UiStyle.stylePill(ctx, popAbBtn, ab, 0xFFFFB300);

        popHwyBtn.setText(s.highwayBoost ? "🛣 HWY ON" : "🛣 HWY");
        UiStyle.stylePill(ctx, popHwyBtn, s.highwayBoost, 0xFF00E676);

        popBassLbl.setText(String.format(Locale.US, "Bass Boost (%s): %d%%", EqStore.bassStyleName(p.bassStyle), Math.round(p.bassBoost)));
        popBassSeek.setProgress(Math.round(p.bassBoost));
        UiStyle.tintSeekBar(popBassSeek, accent);

        popMbcLbl.setText(String.format(Locale.US, "Punch (MBC): %d%%", Math.round(p.punchMbc)));
        popMbcSeek.setProgress(Math.round(p.punchMbc));
        UiStyle.tintSeekBar(popMbcSeek, accent);

        popPreampLbl.setText(String.format(Locale.US, "Preamp: %+.1f dB", p.preampDb));
        popPreampSeek.setProgress(Math.round((p.preampDb + 12f) * 10f));
        UiStyle.tintSeekBar(popPreampSeek, accent);

        if (popVolSeek != null && popVolLbl != null) {
            try {
                AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                    int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                    int pct = Math.round((cur * 100f) / max);
                    popVolLbl.setText(String.format(Locale.US, "Media Volume: %d%%", pct));
                    popVolSeek.setProgress(pct);
                    UiStyle.tintSeekBar(popVolSeek, accent);
                }
            } catch (Throwable ignored) {}
        }

        popChipRow.removeAllViews();
        List<EqStore.EqPreset> list = store.getPresets();
        for (EqStore.EqPreset item : list) {
            boolean sel = item.id.equals(p.id);
            TextView c = UiStyle.makePill(ctx, item.name, sel, item.accentColor);
            c.setTextSize(10.5f);
            c.setPadding(UiStyle.dp(ctx, 9), UiStyle.dp(ctx, 4), UiStyle.dp(ctx, 9), UiStyle.dp(ctx, 4));
            c.setOnClickListener(v -> {
                UiStyle.hapticTick(v, ctx);
                store.setActivePreset(item.id);
                EqEngine.get(ctx).setAbFlatCompare(false);
                EqService.startOrRefresh(ctx);
                populatePopupContent();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = UiStyle.dp(ctx, 5);
            popChipRow.addView(c, lp);
        }

        refreshNowPlaying();
    }

    private boolean shouldUseInAppYt() {
        YouTubePlayerView.RemoteBridge inApp = YouTubePlayerView.getActiveRemoteBridge();
        if (inApp == null || inApp.getNowPlayingLabel() == null) return false;
        if (inApp.isPlaying()) return true;
        return !KokerMediaListenerService.isPlayingNow(ctx);
    }

    void dispatchMediaControl(int dir) {
        YouTubePlayerView.RemoteBridge inApp = shouldUseInAppYt() ? YouTubePlayerView.getActiveRemoteBridge() : null;
        if (inApp != null) {
            if (dir < 0) inApp.playPrev();
            else if (dir > 0) inApp.playNext();
            else inApp.togglePlayPause();
        } else {
            if (dir < 0) KokerMediaListenerService.skipPrev(ctx);
            else if (dir > 0) KokerMediaListenerService.skipNext(ctx);
            else KokerMediaListenerService.playPause(ctx);
        }
        handler.postDelayed(this::refreshNowPlaying, 300);
    }

    void refreshNowPlaying() {
        if (popNowPlayingTv == null || popPlayBtn == null) return;
        EqStore.EqPreset p = EqStore.get(ctx).getActivePreset();
        int accent = p != null ? p.accentColor : 0xFF39FF14;
        if (shouldUseInAppYt()) {
            YouTubePlayerView.RemoteBridge inApp = YouTubePlayerView.getActiveRemoteBridge();
            popNowPlayingTv.setText(inApp.getNowPlayingLabel());
            popNowPlayingTv.setTextColor(accent);
            popPlayBtn.setText(inApp.isPlaying() ? "⏸ Pause" : "▶ Play");
            UiStyle.stylePill(ctx, popPlayBtn, inApp.isPlaying(), accent);
        } else {
            String np = KokerMediaListenerService.getNowPlayingLabel(ctx);
            boolean playing = KokerMediaListenerService.isPlayingNow(ctx);
            popNowPlayingTv.setText(np != null ? np : "♪  Ready — Media Controls");
            popNowPlayingTv.setTextColor(np != null ? accent : UiStyle.TEXT_MUTED);
            popPlayBtn.setText(playing ? "⏸ Pause" : "▶ Play");
            UiStyle.stylePill(ctx, popPlayBtn, playing, accent);
        }
    }
}
