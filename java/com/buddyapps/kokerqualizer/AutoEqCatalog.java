package com.buddyapps.kokerqualizer;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Built-in Auto-EQ Headphone/Speaker profile catalog (#4)
 * + Compact 1-line Base64 Preset Share Code encoder/decoder (#39).
 */
public class AutoEqCatalog {
    public static class DeviceProfile {
        public final String name;
        public final String category;
        public final String notes;
        public final float[] bands10;
        public final float bassBoost;
        public final int bassStyle;
        public final float punchMbc;
        public final float limiter;
        public final float virtualizer;
        public final boolean subMode;
        public final boolean subSonicFilter;
        public final boolean crossfeed;

        public DeviceProfile(String name, String category, String notes, float[] b10,
                             float bass, int bassStyle, float mbc, float lim, float virt,
                             boolean subMode, boolean subSonic, boolean crossfeed) {
            this.name = name;
            this.category = category;
            this.notes = notes;
            this.bands10 = Arrays.copyOf(b10, 10);
            this.bassBoost = bass;
            this.bassStyle = bassStyle;
            this.punchMbc = mbc;
            this.limiter = lim;
            this.virtualizer = virt;
            this.subMode = subMode;
            this.subSonicFilter = subSonic;
            this.crossfeed = crossfeed;
        }

        public EqStore.EqPreset toPreset() {
            EqStore.EqPreset p = new EqStore.EqPreset();
            p.id = "aeq_" + UUID.randomUUID().toString().substring(0, 8);
            p.name = name;
            p.accentColor = EqStore.DEFAULT_ACCENT;
            p.builtIn = false;
            p.favorite = true;
            p.pinnedCar = "Car".equals(category);
            p.bands10 = Arrays.copyOf(bands10, 10);
            p.bands15 = EqStore.resample10To15(p.bands10);
            p.bassBoost = bassBoost;
            p.bassStyle = bassStyle;
            p.punchMbc = punchMbc;
            p.limiter = limiter;
            p.virtualizer = virtualizer;
            p.carSubMode = subMode;
            p.subSonicFilter = subSonicFilter;
            p.crossfeed = crossfeed;
            p.tags = "#" + category + " #AutoEQ";
            p.notes = notes;
            return p;
        }
    }

    private static final List<DeviceProfile> CACHED_PROFILES = buildProfiles();

    public static List<DeviceProfile> getAllProfiles() {
        return CACHED_PROFILES;
    }

    private static List<DeviceProfile> buildProfiles() {
        List<DeviceProfile> list = new ArrayList<>();
        list.add(new DeviceProfile("Samsung Galaxy Buds3 Pro", "Earbuds", "Harman target clarity + crisp air",
                new float[]{1.5f, 1.0f, -1.0f, -1.5f, 0.5f, 1.5f, 2.0f, 1.0f, 2.0f, 2.5f},
                15f, EqStore.BASS_TIGHT, 20f, 65f, 15f, false, false, true));
        list.add(new DeviceProfile("Samsung Galaxy Buds2 Pro", "Earbuds", "Tames 8k sibilance, tightens sub-bass",
                new float[]{2.0f, 1.5f, -0.5f, -1.5f, 0.5f, 1.0f, 1.5f, -1.5f, -1.0f, 2.0f},
                18f, EqStore.BASS_TIGHT, 22f, 65f, 15f, false, false, true));
        list.add(new DeviceProfile("Apple AirPods Pro 2", "Earbuds", "Adds deep sub extension & vocal sparkle",
                new float[]{3.0f, 2.0f, 0.5f, -0.5f, 0.5f, 1.5f, 2.0f, 1.5f, 2.0f, 2.5f},
                20f, EqStore.BASS_TIGHT, 20f, 65f, 18f, false, false, true));
        list.add(new DeviceProfile("Sony WH-1000XM5", "Headphones", "De-muds upper bass, opens treble stage",
                new float[]{1.0f, -1.5f, -3.5f, -2.5f, 1.0f, 2.0f, 3.0f, 2.5f, 3.0f, 3.5f},
                12f, EqStore.BASS_TIGHT, 20f, 65f, 20f, false, false, true));
        list.add(new DeviceProfile("Sony WH-1000XM4", "Headphones", "Cleans 125-250 Hz boom, lifts vocals",
                new float[]{0.5f, -2.0f, -4.0f, -3.0f, 0.5f, 2.0f, 3.5f, 2.5f, 2.5f, 3.0f},
                10f, EqStore.BASS_TIGHT, 18f, 65f, 20f, false, false, true));
        list.add(new DeviceProfile("Bose QuietComfort Ultra", "Headphones", "Balances warm mids & sparkling highs",
                new float[]{1.5f, 0.5f, -1.5f, -1.0f, 1.0f, 1.5f, 2.5f, 2.0f, 2.0f, 2.5f},
                15f, EqStore.BASS_TIGHT, 20f, 65f, 18f, false, false, true));
        list.add(new DeviceProfile("Sennheiser HD 650 / 6XX", "Headphones", "Sub-bass lift + upper-treble air",
                new float[]{5.5f, 3.5f, 0.5f, -1.5f, -0.5f, 0.5f, 1.0f, 1.5f, 2.5f, 3.0f},
                22f, EqStore.BASS_DEEP, 15f, 60f, 10f, false, false, true));
        list.add(new DeviceProfile("Beats Studio Pro", "Headphones", "Neutralizes upper-bass bump, boosts air",
                new float[]{2.0f, 1.0f, -2.0f, -2.0f, 1.0f, 2.0f, 2.5f, 2.0f, 3.0f, 3.0f},
                18f, EqStore.BASS_TIGHT, 22f, 68f, 15f, false, false, true));
        list.add(new DeviceProfile("JBL Flip 6 / Charge 5", "Speakers", "Protects passive radiators, boosts punch",
                new float[]{-2.0f, 4.5f, 3.5f, 1.0f, 0.5f, 1.5f, 2.5f, 3.0f, 3.5f, 2.5f},
                38f, EqStore.BASS_TIGHT, 42f, 78f, 15f, false, true, false));
        list.add(new DeviceProfile("JBL PartyBox / Boombox", "Speakers", "Clean chest-thump + stadium clarity",
                new float[]{6.0f, 5.5f, 2.5f, -1.0f, 0.0f, 1.5f, 3.0f, 3.5f, 4.0f, 3.0f},
                55f, EqStore.BASS_DEEP, 45f, 80f, 20f, true, false, false));
        list.add(new DeviceProfile("Car Factory Door Speakers", "Car", "Cuts door rattle, boosts kick & road clarity",
                new float[]{-3.0f, 5.5f, 4.0f, 0.5f, -1.0f, 1.5f, 3.5f, 4.5f, 4.0f, 3.0f},
                48f, EqStore.BASS_TIGHT, 48f, 80f, 18f, false, true, false));
        list.add(new DeviceProfile("Car Trunk 12\" Subwoofer", "Car", "Deep 30-55 Hz seismic slam + crisp highs",
                new float[]{11.0f, 9.5f, 5.0f, 0.5f, -1.5f, 1.0f, 3.0f, 4.5f, 5.0f, 4.0f},
                74f, EqStore.BASS_DEEP, 55f, 82f, 15f, true, false, false));
        list.add(new DeviceProfile("Car Highway Cruiser", "Car", "Cuts through tire/wind roar with clean vocals",
                new float[]{5.5f, 6.0f, 3.5f, 1.5f, 1.0f, 3.0f, 4.5f, 4.0f, 3.5f, 2.5f},
                45f, EqStore.BASS_TIGHT, 55f, 80f, 15f, false, true, false));
        list.add(new DeviceProfile("Studio Reference Monitors", "Studio", "Ultra-flat mastering baseline with gentle limiter",
                new float[]{0.5f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.5f, 0.5f, 0.5f},
                0f, EqStore.BASS_TIGHT, 0f, 50f, 0f, false, false, false));
        list.add(new DeviceProfile("Phone Built-In Speakers", "Speakers", "High-pass protects tiny driver, boosts presence",
                new float[]{-6.0f, -1.0f, 2.5f, 3.0f, 2.0f, 2.5f, 4.0f, 4.5f, 3.5f, 2.0f},
                15f, EqStore.BASS_TIGHT, 42f, 82f, 10f, false, true, false));
        return Collections.unmodifiableList(list);
    }

    /**
     * Encodes a preset into a compact 1-line copy-paste code: KOKER1:<base64>
     */
    public static String encodeShareCode(EqStore.EqPreset p) {
        if (p == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(p.name.replace("|", " ")).append('|');
        for (int i = 0; i < 10; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.US, "%.1f", p.bands10[i]));
        }
        sb.append('|')
                .append(Math.round(p.bassBoost)).append(',')
                .append(p.bassStyle).append(',')
                .append(Math.round(p.punchMbc)).append(',')
                .append(String.format(Locale.US, "%.1f", p.preampDb)).append(',')
                .append(Math.round(p.limiter)).append(',')
                .append(Math.round(p.virtualizer)).append(',')
                .append(p.reverbMode).append(',')
                .append(p.carSubMode ? 1 : 0).append(',')
                .append(p.subSonicFilter ? 1 : 0).append(',')
                .append(p.analogWarmth ? 1 : 0).append(',')
                .append(p.crossfeed ? 1 : 0).append(',')
                .append(p.nightWhisper ? 1 : 0).append(',')
                .append(p.limiterCeilingMode).append(',')
                .append(String.format(Locale.US, "%.1f", p.bassTiltDb)).append(',')
                .append(String.format(Locale.US, "%.1f", p.trebleTiltDb));
        byte[] raw = sb.toString().getBytes(StandardCharsets.UTF_8);
        return "KOKER1:" + Base64.encodeToString(raw, Base64.NO_WRAP | Base64.URL_SAFE);
    }

    /**
     * Decodes a KOKER1:<base64> share code back into a full EqPreset.
     */
    public static EqStore.EqPreset decodeShareCode(String code) throws Exception {
        if (code == null) throw new IllegalArgumentException("Empty code");
        String trimmed = code.trim();
        int idx = trimmed.indexOf("KOKER1:");
        if (idx < 0) throw new IllegalArgumentException("Missing KOKER1: prefix");
        String b64 = trimmed.substring(idx + 7).split("\\s+")[0];
        if (b64.isEmpty() || b64.length() > 4096) throw new IllegalArgumentException("Invalid share code length");
        String decoded = new String(Base64.decode(b64, Base64.URL_SAFE), StandardCharsets.UTF_8);
        String[] parts = decoded.split("\\|");
        if (parts.length < 3) throw new IllegalArgumentException("Corrupt share code");

        EqStore.EqPreset p = new EqStore.EqPreset();
        p.id = "shr_" + UUID.randomUUID().toString().substring(0, 8);
        String cleanName = parts[0].replaceAll("[\\r\\n\\t]+", " ").trim();
        p.name = cleanName.isEmpty() ? "Shared Preset" : cleanName;
        p.accentColor = EqStore.DEFAULT_ACCENT;
        p.builtIn = false;
        p.tags = "#Shared";

        String[] bParts = parts[1].split(",");
        for (int i = 0; i < Math.min(10, bParts.length); i++) {
            p.bands10[i] = EqStore.clamp(Float.parseFloat(bParts[i]), -EqStore.MAX_GAIN_DB, EqStore.MAX_GAIN_DB);
        }
        p.bands15 = EqStore.resample10To15(p.bands10);

        String[] m = parts[2].split(",");
        if (m.length >= 8) {
            p.bassBoost = EqStore.clamp(Float.parseFloat(m[0]), 0f, 100f);
            p.bassStyle = Math.max(0, Math.min(2, Integer.parseInt(m[1])));
            p.punchMbc = EqStore.clamp(Float.parseFloat(m[2]), 0f, 100f);
            p.preampDb = EqStore.clamp(Float.parseFloat(m[3]), -12f, 12f);
            p.limiter = EqStore.clamp(Float.parseFloat(m[4]), 0f, 100f);
            p.virtualizer = EqStore.clamp(Float.parseFloat(m[5]), 0f, 100f);
            p.reverbMode = Math.max(0, Math.min(4, Integer.parseInt(m[6])));
            p.carSubMode = "1".equals(m[7]);
        }
        if (m.length >= 15) {
            p.subSonicFilter = "1".equals(m[8]);
            p.analogWarmth = "1".equals(m[9]);
            p.crossfeed = "1".equals(m[10]);
            p.nightWhisper = "1".equals(m[11]);
            p.limiterCeilingMode = Math.max(0, Math.min(2, Integer.parseInt(m[12])));
            p.bassTiltDb = EqStore.clamp(Float.parseFloat(m[13]), -6f, 6f);
            p.trebleTiltDb = EqStore.clamp(Float.parseFloat(m[14]), -6f, 6f);
        }
        return p;
    }
}
