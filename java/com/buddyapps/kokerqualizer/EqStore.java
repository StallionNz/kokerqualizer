package com.buddyapps.kokerqualizer;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class EqStore {
    private static final String PREFS = "kokerqualizer_prefs";
    private static final String KEY_PRESETS = "presets_json";
    private static final String KEY_BT_RULES = "bt_rules_json";
    private static final String KEY_SETTINGS = "settings_json";
    private static final String KEY_NEON_GREEN_MIGRATED = "neon_green_v1";

    public static final int DEFAULT_ACCENT = 0xFF39FF14; // Neon Shocking Green!

    public static final int BASS_TIGHT = 0;
    public static final int BASS_DEEP = 1;
    public static final int BASS_BOOMY = 2;

    public static final int REVERB_OFF = 0;
    public static final int REVERB_STUDIO = 1;
    public static final int REVERB_CLUB = 2;
    public static final int REVERB_HALL = 3;
    public static final int REVERB_CAR = 4;

    public static final int BANDS_5 = 0;
    public static final int BANDS_10 = 1;
    public static final int BANDS_15 = 2;

    public static final int VIZ_BARS = 0;
    public static final int VIZ_WAVE = 1;
    public static final int VIZ_VU = 2;

    public static final int HOOK_DUAL = 0;
    public static final int HOOK_GLOBAL = 1;
    public static final int HOOK_SESSION = 2;

    public static final float MAX_GAIN_DB = 18.0f;

    public static final int[] ACCENT_PALETTE = new int[] {
            0xFF39FF14, // Neon Shocking Green (Main!)
            0xFF00E5FF, // Cyan
            0xFFFF2E93, // Neon Magenta
            0xFFFF3D00, // Fire Red
            0xFF00E676, // Emerald
            0xFFB388FF, // Electric Purple
            0xFFFFB300, // Hi-Fi Amber
            0xFF40C4FF, // Ice Blue
            0xFFFF6D00, // Sunset Orange
            0xFFC6FF00  // Acid Lime
    };

    public static final float[] ISO_CENTERS = new float[] {
            31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f
    };
    public static final String[] ISO_LABELS_10 = new String[] {
            "31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k"
    };
    public static final String[] ISO_LABELS_5 = new String[] {
            "45", "180", "750", "3k", "12k"
    };

    public static final float[] SUB_CENTERS = new float[] {
            25f, 45f, 80f, 160f, 320f, 640f, 1250f, 2500f, 6000f, 14000f
    };
    public static final String[] SUB_LABELS_10 = new String[] {
            "25", "45", "80", "160", "320", "640", "1.2k", "2.5k", "6k", "14k"
    };
    public static final String[] SUB_LABELS_5 = new String[] {
            "35", "110", "450", "1.8k", "9k"
    };

    // Classic 15-Band Pro 2/3-Octave Studio EQ
    public static final float[] PRO15_CENTERS = new float[] {
            25f, 40f, 63f, 100f, 160f, 250f, 400f, 630f, 1000f, 1600f, 2500f, 4000f, 6300f, 10000f, 16000f
    };
    public static final String[] PRO15_LABELS = new String[] {
            "25", "40", "63", "100", "160", "250", "400", "630", "1k", "1.6k", "2.5k", "4k", "6.3k", "10k", "16k"
    };

    public static class EqPreset {
        public String id;
        public String name;
        public int accentColor = DEFAULT_ACCENT;
        public boolean builtIn = false;
        public boolean favorite = false;
        public boolean pinnedCar = false;
        public float[] bands10 = new float[10];
        public float[] bands15 = new float[15];
        public float bassBoost = 0f;       // 0..100
        public int bassStyle = BASS_TIGHT; // 0..2
        public float punchMbc = 25f;       // 0..100
        public float preampDb = 0f;        // -12..+12
        public float limiter = 65f;        // 0..100
        public float virtualizer = 0f;     // 0..100
        public float balance = 0f;         // -100..+100
        public int reverbMode = REVERB_OFF;// 0..4
        public boolean carSubMode = false;

        public EqPreset copy(String newId, String newName, boolean isBuiltIn) {
            EqPreset p = new EqPreset();
            p.id = newId;
            p.name = newName;
            p.accentColor = accentColor;
            p.builtIn = isBuiltIn;
            p.favorite = favorite;
            p.pinnedCar = pinnedCar;
            p.bands10 = Arrays.copyOf(bands10, 10);
            p.bands15 = Arrays.copyOf(bands15, 15);
            p.bassBoost = bassBoost;
            p.bassStyle = bassStyle;
            p.punchMbc = punchMbc;
            p.preampDb = preampDb;
            p.limiter = limiter;
            p.virtualizer = virtualizer;
            p.balance = balance;
            p.reverbMode = reverbMode;
            p.carSubMode = carSubMode;
            return p;
        }

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id);
                o.put("name", name);
                o.put("accentColor", accentColor);
                o.put("builtIn", builtIn);
                o.put("favorite", favorite);
                o.put("pinnedCar", pinnedCar);
                JSONArray arr10 = new JSONArray();
                for (int i = 0; i < 10; i++) arr10.put((double) bands10[i]);
                o.put("bands10", arr10);
                JSONArray arr15 = new JSONArray();
                for (int i = 0; i < 15; i++) arr15.put((double) bands15[i]);
                o.put("bands15", arr15);
                o.put("bassBoost", (double) bassBoost);
                o.put("bassStyle", bassStyle);
                o.put("punchMbc", (double) punchMbc);
                o.put("preampDb", (double) preampDb);
                o.put("limiter", (double) limiter);
                o.put("virtualizer", (double) virtualizer);
                o.put("balance", (double) balance);
                o.put("reverbMode", reverbMode);
                o.put("carSubMode", carSubMode);
            } catch (Exception ignored) {}
            return o;
        }

        public static EqPreset fromJson(JSONObject o) {
            EqPreset p = new EqPreset();
            p.id = o.optString("id", UUID.randomUUID().toString());
            p.name = o.optString("name", "Custom");
            p.accentColor = o.optInt("accentColor", DEFAULT_ACCENT);
            p.builtIn = o.optBoolean("builtIn", false);
            p.favorite = o.optBoolean("favorite", false);
            p.pinnedCar = o.optBoolean("pinnedCar", false);
            JSONArray arr10 = o.optJSONArray("bands10");
            if (arr10 != null) {
                for (int i = 0; i < Math.min(10, arr10.length()); i++) {
                    p.bands10[i] = clamp((float) arr10.optDouble(i, 0.0), -MAX_GAIN_DB, MAX_GAIN_DB);
                }
            }
            JSONArray arr15 = o.optJSONArray("bands15");
            if (arr15 != null && arr15.length() >= 15) {
                for (int i = 0; i < 15; i++) {
                    p.bands15[i] = clamp((float) arr15.optDouble(i, 0.0), -MAX_GAIN_DB, MAX_GAIN_DB);
                }
            } else {
                p.bands15 = resample10To15(p.bands10);
            }
            p.bassBoost = clamp((float) o.optDouble("bassBoost", 0.0), 0f, 100f);
            p.bassStyle = Math.max(0, Math.min(2, o.optInt("bassStyle", BASS_TIGHT)));
            p.punchMbc = clamp((float) o.optDouble("punchMbc", 25.0), 0f, 100f);
            p.preampDb = clamp((float) o.optDouble("preampDb", 0.0), -12f, 12f);
            p.limiter = clamp((float) o.optDouble("limiter", 65.0), 0f, 100f);
            p.virtualizer = clamp((float) o.optDouble("virtualizer", 0.0), 0f, 100f);
            p.balance = clamp((float) o.optDouble("balance", 0.0), -100f, 100f);
            p.reverbMode = Math.max(0, Math.min(4, o.optInt("reverbMode", REVERB_OFF)));
            p.carSubMode = o.optBoolean("carSubMode", false);
            return p;
        }
    }

    public static class BtDeviceRule {
        public String address;
        public String name;
        public boolean autoStart = false;
        public String presetId = "car_bass";
        public boolean setVolume = false;
        public int volumePercent = 90;
        public boolean autoCarMode = false;

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("address", address);
                o.put("name", name);
                o.put("autoStart", autoStart);
                o.put("presetId", presetId);
                o.put("setVolume", setVolume);
                o.put("volumePercent", volumePercent);
                o.put("autoCarMode", autoCarMode);
            } catch (Exception ignored) {}
            return o;
        }

        public static BtDeviceRule fromJson(JSONObject o) {
            BtDeviceRule r = new BtDeviceRule();
            r.address = o.optString("address", "");
            r.name = o.optString("name", "Bluetooth Device");
            r.autoStart = o.optBoolean("autoStart", false);
            r.presetId = o.optString("presetId", "car_bass");
            r.setVolume = o.optBoolean("setVolume", false);
            r.volumePercent = Math.max(0, Math.min(100, o.optInt("volumePercent", 90)));
            r.autoCarMode = o.optBoolean("autoCarMode", false);
            return r;
        }
    }

    public static class Settings {
        public boolean eqEnabled = true;
        public int bandMode = BANDS_10; // 0=5B, 1=10B, 2=15B Pro
        public boolean curveViewMode = false;
        public boolean brushMode = false;
        public boolean presetLocked = false;
        public boolean autoGain = true;
        public boolean autoLoudness = false;
        public boolean monoAudio = false;
        public boolean hapticsEnabled = true;
        public boolean reactiveGlow = true;
        public boolean highwayBoost = false;
        public boolean carVizBackdrop = true;
        public boolean wiredAutoSwitch = true;
        public String wiredPresetId = "headphones";
        public int visualizerMode = VIZ_BARS;
        public int hookMode = HOOK_DUAL;
        public boolean startOnBoot = true;
        public boolean carKeepScreenOn = true;
        public boolean bubbleEnabled = false;
        public boolean bubbleAutoOnBt = false;
        public int bubbleY = 380;
        public boolean bubbleRightEdge = true;
        public boolean morphPadVisible = false;
        public String activePresetId = "car_bass";
        public String phonePresetId = "phone_speaker";

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("eqEnabled", eqEnabled);
                o.put("bandMode", bandMode);
                o.put("tenBandMode", bandMode >= BANDS_10);
                o.put("curveViewMode", curveViewMode);
                o.put("brushMode", brushMode);
                o.put("presetLocked", presetLocked);
                o.put("autoGain", autoGain);
                o.put("autoLoudness", autoLoudness);
                o.put("monoAudio", monoAudio);
                o.put("hapticsEnabled", hapticsEnabled);
                o.put("reactiveGlow", reactiveGlow);
                o.put("highwayBoost", highwayBoost);
                o.put("carVizBackdrop", carVizBackdrop);
                o.put("wiredAutoSwitch", wiredAutoSwitch);
                o.put("wiredPresetId", wiredPresetId);
                o.put("visualizerMode", visualizerMode);
                o.put("hookMode", hookMode);
                o.put("startOnBoot", startOnBoot);
                o.put("carKeepScreenOn", carKeepScreenOn);
                o.put("bubbleEnabled", bubbleEnabled);
                o.put("bubbleAutoOnBt", bubbleAutoOnBt);
                o.put("bubbleY", bubbleY);
                o.put("bubbleRightEdge", bubbleRightEdge);
                o.put("morphPadVisible", morphPadVisible);
                o.put("activePresetId", activePresetId);
                o.put("phonePresetId", phonePresetId);
            } catch (Exception ignored) {}
            return o;
        }

        public static Settings fromJson(JSONObject o) {
            Settings s = new Settings();
            if (o == null) return s;
            s.eqEnabled = o.optBoolean("eqEnabled", true);
            if (o.has("bandMode")) {
                s.bandMode = Math.max(0, Math.min(2, o.optInt("bandMode", BANDS_10)));
            } else {
                s.bandMode = o.optBoolean("tenBandMode", true) ? BANDS_10 : BANDS_5;
            }
            s.curveViewMode = o.optBoolean("curveViewMode", false);
            s.brushMode = o.optBoolean("brushMode", false);
            s.presetLocked = o.optBoolean("presetLocked", false);
            s.autoGain = o.optBoolean("autoGain", true);
            s.autoLoudness = o.optBoolean("autoLoudness", false);
            s.monoAudio = o.optBoolean("monoAudio", false);
            s.hapticsEnabled = o.optBoolean("hapticsEnabled", true);
            s.reactiveGlow = o.optBoolean("reactiveGlow", true);
            s.highwayBoost = o.optBoolean("highwayBoost", false);
            s.carVizBackdrop = o.optBoolean("carVizBackdrop", true);
            s.wiredAutoSwitch = o.optBoolean("wiredAutoSwitch", true);
            s.wiredPresetId = o.optString("wiredPresetId", "headphones");
            s.visualizerMode = o.optInt("visualizerMode", VIZ_BARS);
            s.hookMode = o.optInt("hookMode", HOOK_DUAL);
            s.startOnBoot = o.optBoolean("startOnBoot", true);
            s.carKeepScreenOn = o.optBoolean("carKeepScreenOn", true);
            s.bubbleEnabled = o.optBoolean("bubbleEnabled", false);
            s.bubbleAutoOnBt = o.optBoolean("bubbleAutoOnBt", false);
            s.bubbleY = o.optInt("bubbleY", 380);
            s.bubbleRightEdge = o.optBoolean("bubbleRightEdge", true);
            s.morphPadVisible = o.optBoolean("morphPadVisible", false);
            s.activePresetId = o.optString("activePresetId", "car_bass");
            s.phonePresetId = o.optString("phonePresetId", "phone_speaker");
            return s;
        }
    }

    private static EqStore instance;
    private final SharedPreferences prefs;
    private final List<EqPreset> presets = new ArrayList<>();
    private final List<BtDeviceRule> btRules = new ArrayList<>();
    private Settings settings = new Settings();

    public static synchronized EqStore get(Context ctx) {
        if (instance == null) {
            instance = new EqStore(ctx.getApplicationContext());
        }
        return instance;
    }

    private EqStore(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    private void load() {
        presets.clear();
        btRules.clear();
        String pJson = prefs.getString(KEY_PRESETS, null);
        if (pJson != null) {
            try {
                JSONArray arr = new JSONArray(pJson);
                for (int i = 0; i < arr.length(); i++) {
                    presets.add(EqPreset.fromJson(arr.getJSONObject(i)));
                }
            } catch (Exception ignored) {}
        }
        if (presets.isEmpty()) {
            presets.addAll(createDefaultPresets());
        }

        String bJson = prefs.getString(KEY_BT_RULES, null);
        if (bJson != null) {
            try {
                JSONArray arr = new JSONArray(bJson);
                for (int i = 0; i < arr.length(); i++) {
                    btRules.add(BtDeviceRule.fromJson(arr.getJSONObject(i)));
                }
            } catch (Exception ignored) {}
        }

        String sJson = prefs.getString(KEY_SETTINGS, null);
        if (sJson != null) {
            try {
                settings = Settings.fromJson(new JSONObject(sJson));
            } catch (Exception ignored) {
                settings = new Settings();
            }
        }
        if (findPreset(settings.activePresetId) == null && !presets.isEmpty()) {
            settings.activePresetId = presets.get(0).id;
        }
        if (findPreset(settings.phonePresetId) == null && !presets.isEmpty()) {
            settings.phonePresetId = presets.get(0).id;
        }
        if (findPreset(settings.wiredPresetId) == null && !presets.isEmpty()) {
            settings.wiredPresetId = presets.get(0).id;
        }
        if (!prefs.getBoolean(KEY_NEON_GREEN_MIGRATED, false)) {
            for (EqPreset p : presets) {
                if (p.builtIn || p.id.equals(settings.activePresetId)) {
                    p.accentColor = DEFAULT_ACCENT;
                }
            }
            prefs.edit().putBoolean(KEY_NEON_GREEN_MIGRATED, true).apply();
            save();
        }
    }

    public synchronized void save() {
        JSONArray pArr = new JSONArray();
        for (EqPreset p : presets) pArr.put(p.toJson());
        JSONArray bArr = new JSONArray();
        for (BtDeviceRule r : btRules) bArr.put(r.toJson());
        prefs.edit()
                .putString(KEY_PRESETS, pArr.toString())
                .putString(KEY_BT_RULES, bArr.toString())
                .putString(KEY_SETTINGS, settings.toJson().toString())
                .apply();
    }

    public synchronized int restoreFactoryPresets() {
        List<EqPreset> defaults = createDefaultPresets();
        int restored = 0;
        for (EqPreset def : defaults) {
            int existingIdx = -1;
            for (int i = 0; i < presets.size(); i++) {
                if (presets.get(i).id.equals(def.id)) {
                    existingIdx = i;
                    break;
                }
            }
            if (existingIdx >= 0) {
                presets.set(existingIdx, def);
            } else {
                presets.add(0, def);
            }
            restored++;
        }
        save();
        return restored;
    }

    public synchronized Settings getSettings() { return settings; }
    public synchronized List<EqPreset> getPresets() { return new ArrayList<>(presets); }

    public synchronized List<EqPreset> getCarPinnedPresets() {
        List<EqPreset> out = new ArrayList<>();
        for (EqPreset p : presets) {
            if (p.pinnedCar) out.add(p);
        }
        if (out.isEmpty()) {
            for (int i = 0; i < Math.min(6, presets.size()); i++) out.add(presets.get(i));
        }
        return out;
    }

    public synchronized EqPreset findPreset(String id) {
        if (id == null) return null;
        for (EqPreset p : presets) {
            if (id.equals(p.id)) return p;
        }
        return null;
    }

    public synchronized EqPreset getActivePreset() {
        EqPreset p = findPreset(settings.activePresetId);
        if (p == null && !presets.isEmpty()) {
            p = presets.get(0);
            settings.activePresetId = p.id;
        }
        return p;
    }

    public synchronized void setActivePreset(String id) {
        if (findPreset(id) != null) {
            settings.activePresetId = id;
            save();
        }
    }

    public synchronized EqPreset cyclePreset(int delta) {
        if (presets.isEmpty()) return null;
        int idx = 0;
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).id.equals(settings.activePresetId)) {
                idx = i;
                break;
            }
        }
        int next = (idx + delta + presets.size()) % presets.size();
        EqPreset p = presets.get(next);
        settings.activePresetId = p.id;
        save();
        return p;
    }

    public synchronized void addOrUpdatePreset(EqPreset preset) {
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).id.equals(preset.id)) {
                presets.set(i, preset);
                save();
                return;
            }
        }
        presets.add(preset);
        save();
    }

    public synchronized boolean deletePreset(String id) {
        if (presets.size() <= 1) return false;
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).id.equals(id)) {
                presets.remove(i);
                if (id.equals(settings.activePresetId)) settings.activePresetId = presets.get(0).id;
                if (id.equals(settings.phonePresetId)) settings.phonePresetId = presets.get(0).id;
                if (id.equals(settings.wiredPresetId)) settings.wiredPresetId = presets.get(0).id;
                save();
                return true;
            }
        }
        return false;
    }

    public synchronized List<BtDeviceRule> getBtRules() { return new ArrayList<>(btRules); }

    public synchronized BtDeviceRule findBtRule(String address) {
        if (address == null) return null;
        for (BtDeviceRule r : btRules) {
            if (address.equalsIgnoreCase(r.address)) return r;
        }
        return null;
    }

    public synchronized void putBtRule(BtDeviceRule rule) {
        for (int i = 0; i < btRules.size(); i++) {
            if (btRules.get(i).address.equalsIgnoreCase(rule.address)) {
                btRules.set(i, rule);
                save();
                return;
            }
        }
        btRules.add(rule);
        save();
    }

    public synchronized String exportAllJson() {
        JSONObject root = new JSONObject();
        try {
            root.put("app", "Kokerqualizer");
            root.put("formatVersion", 3);
            JSONArray pArr = new JSONArray();
            for (EqPreset p : presets) pArr.put(p.toJson());
            root.put("presets", pArr);
            JSONArray bArr = new JSONArray();
            for (BtDeviceRule r : btRules) bArr.put(r.toJson());
            root.put("btRules", bArr);
            root.put("settings", settings.toJson());
            return root.toString(2);
        } catch (Exception e) {
            return "{}";
        }
    }

    public synchronized String exportSinglePresetJson(EqPreset preset) {
        JSONObject root = new JSONObject();
        try {
            root.put("app", "Kokerqualizer");
            root.put("formatVersion", 3);
            root.put("preset", preset.toJson());
            return root.toString(2);
        } catch (Exception e) {
            return "{}";
        }
    }

    public synchronized int importJson(String jsonText) throws Exception {
        JSONObject root = new JSONObject(jsonText);
        int count = 0;
        if (root.has("preset")) {
            EqPreset p = EqPreset.fromJson(root.getJSONObject("preset"));
            p.id = "imp_" + UUID.randomUUID().toString().substring(0, 8);
            p.builtIn = false;
            presets.add(p);
            settings.activePresetId = p.id;
            count = 1;
        } else if (root.has("presets")) {
            JSONArray arr = root.getJSONArray("presets");
            for (int i = 0; i < arr.length(); i++) {
                EqPreset p = EqPreset.fromJson(arr.getJSONObject(i));
                EqPreset existing = findPreset(p.id);
                if (existing != null) addOrUpdatePreset(p);
                else presets.add(p);
                count++;
            }
            if (root.has("btRules")) {
                JSONArray bArr = root.getJSONArray("btRules");
                for (int i = 0; i < bArr.length(); i++) {
                    putBtRule(BtDeviceRule.fromJson(bArr.getJSONObject(i)));
                }
            }
        } else if (root.has("bands10")) {
            EqPreset p = EqPreset.fromJson(root);
            p.id = "imp_" + UUID.randomUUID().toString().substring(0, 8);
            p.builtIn = false;
            presets.add(p);
            settings.activePresetId = p.id;
            count = 1;
        }
        save();
        return count;
    }

    public static float[] to5Bands(float[] b10) {
        float[] b5 = new float[5];
        for (int i = 0; i < 5; i++) {
            b5[i] = (b10[i * 2] + b10[i * 2 + 1]) * 0.5f;
        }
        return b5;
    }

    public static void apply5BandChange(EqPreset p, int idx5, float valueDb) {
        if (p == null || idx5 < 0 || idx5 >= 5) return;
        float clamped = clamp(valueDb, -MAX_GAIN_DB, MAX_GAIN_DB);
        p.bands10[idx5 * 2] = clamped;
        p.bands10[idx5 * 2 + 1] = clamped;
        p.bands15 = resample10To15(p.bands10);
    }

    public static float[] resample10To15(float[] b10) {
        float[] b15 = new float[15];
        for (int i = 0; i < 15; i++) {
            float pos = (i * 9f) / 14f;
            int lo = (int) Math.floor(pos);
            int hi = Math.min(9, lo + 1);
            float f = pos - lo;
            b15[i] = Math.round((b10[lo] * (1f - f) + b10[hi] * f) * 10f) / 10f;
        }
        return b15;
    }

    public static float[] resample15To10(float[] b15) {
        float[] b10 = new float[10];
        for (int i = 0; i < 10; i++) {
            float pos = (i * 14f) / 9f;
            int lo = (int) Math.floor(pos);
            int hi = Math.min(14, lo + 1);
            float f = pos - lo;
            b10[i] = Math.round((b15[lo] * (1f - f) + b15[hi] * f) * 10f) / 10f;
        }
        return b10;
    }

    public static float clamp(float v, float min, float max) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return Math.max(min, Math.min(max, 0f));
        return Math.max(min, Math.min(max, v));
    }

    public static String bassStyleName(int style) {
        switch (style) {
            case BASS_DEEP: return "Deep";
            case BASS_BOOMY: return "Boomy";
            default: return "Tight";
        }
    }

    public static String reverbName(int mode) {
        switch (mode) {
            case REVERB_STUDIO: return "Studio";
            case REVERB_CLUB: return "Club";
            case REVERB_HALL: return "Hall";
            case REVERB_CAR: return "Cabin";
            default: return "Off";
        }
    }

    private static List<EqPreset> createDefaultPresets() {
        List<EqPreset> list = new ArrayList<>();

        list.add(make("flat", "Flat", DEFAULT_ACCENT, true, false, false,
                new float[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                0f, BASS_TIGHT, 0f, 0f, 55f, 0f, false));

        list.add(make("phone_speaker", "Phone Speaker", DEFAULT_ACCENT, true, true, false,
                new float[]{-2f, 1f, 2.5f, 2f, 1.5f, 2f, 3.5f, 4f, 3f, 2f},
                18f, BASS_TIGHT, 35f, 2f, 70f, 15f, false));

        list.add(make("car_bass", "Car Bass", DEFAULT_ACCENT, true, true, true,
                new float[]{9f, 8f, 5.5f, 2f, 0f, 1f, 3f, 4.5f, 5f, 4f},
                62f, BASS_DEEP, 50f, 2.5f, 75f, 20f, true));

        list.add(make("portable_speaker", "Portable Speaker", DEFAULT_ACCENT, true, true, true,
                new float[]{5f, 6.5f, 4.5f, 2f, 0.5f, 1.5f, 3f, 4f, 4.5f, 3.5f},
                48f, BASS_DEEP, 45f, 2f, 72f, 25f, false));

        list.add(make("headphones", "Headphones", DEFAULT_ACCENT, true, true, true,
                new float[]{3.5f, 4f, 2.5f, 0.5f, -0.5f, 0.5f, 2f, 3.5f, 4f, 3f},
                28f, BASS_TIGHT, 20f, 1f, 65f, 35f, false));

        list.add(make("deep_sub", "Deep Sub", DEFAULT_ACCENT, true, true, true,
                new float[]{13f, 11.5f, 7.5f, 2.5f, -1f, 0f, 2.5f, 4f, 5f, 4f},
                82f, BASS_BOOMY, 60f, 3f, 85f, 15f, true));

        list.add(make("edm_club", "EDM / Club", DEFAULT_ACCENT, true, false, true,
                new float[]{8.5f, 7.5f, 4.5f, 1f, -1.5f, 0.5f, 3.5f, 5.5f, 6.5f, 5.5f},
                68f, BASS_DEEP, 55f, 2f, 80f, 30f, true));

        list.add(make("hip_hop", "Hip-Hop", DEFAULT_ACCENT, true, false, true,
                new float[]{10f, 9f, 6f, 2f, -1f, 1.5f, 3f, 2.5f, 4f, 4.5f},
                75f, BASS_BOOMY, 55f, 2f, 80f, 20f, true));

        list.add(make("rock_metal", "Rock / Metal", DEFAULT_ACCENT, true, false, false,
                new float[]{5f, 4.5f, 2f, -1f, -2.5f, 1f, 4f, 5.5f, 6f, 4.5f},
                35f, BASS_TIGHT, 40f, 1.5f, 70f, 15f, false));

        list.add(make("vocal_podcast", "Vocal / Podcast", DEFAULT_ACCENT, true, false, true,
                new float[]{-4f, -3f, -1f, 1.5f, 3.5f, 5f, 5.5f, 3.5f, 1f, -1f},
                0f, BASS_TIGHT, 45f, 2.5f, 65f, 0f, false));

        list.add(make("late_night", "Late Night", DEFAULT_ACCENT, true, false, false,
                new float[]{4f, 3.5f, 2f, 1f, 1.5f, 2f, 2.5f, 3f, 2.5f, 2f},
                25f, BASS_TIGHT, 75f, 3f, 90f, 20f, false));

        return list;
    }

    private static EqPreset make(String id, String name, int color, boolean builtIn,
                                 boolean fav, boolean pinCar, float[] b10,
                                 float bass, int bassStyle, float punchMbc, float preamp,
                                 float limiter, float virt, boolean subMode) {
        EqPreset p = new EqPreset();
        p.id = id;
        p.name = name;
        p.accentColor = color;
        p.builtIn = builtIn;
        p.favorite = fav;
        p.pinnedCar = pinCar;
        p.bands10 = Arrays.copyOf(b10, 10);
        p.bands15 = resample10To15(p.bands10);
        p.bassBoost = bass;
        p.bassStyle = bassStyle;
        p.punchMbc = punchMbc;
        p.preampDb = preamp;
        p.limiter = limiter;
        p.virtualizer = virt;
        p.balance = 0f;
        p.reverbMode = REVERB_OFF;
        p.carSubMode = subMode;
        return p;
    }

    public static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }
}
