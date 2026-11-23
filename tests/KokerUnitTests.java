package com.buddyapps.kokerqualizer;

import java.util.List;

/**
 * Plain-Java CLI unit test harness for Kokerqualizer core DSP/store/catalog logic.
 * Run via:
 *   mkdir -p /tmp/koker_test && javac --release 8 -classpath ~/android-sdk/android-36/android.jar \
 *     -d /tmp/koker_test java/com/buddyapps/kokerqualizer/EqStore.java \
 *     java/com/buddyapps/kokerqualizer/AutoEqCatalog.java \
 *     java/com/buddyapps/kokerqualizer/YouTubeStore.java \
 *     java/com/buddyapps/kokerqualizer/YouTubePlaylistSync.java \
 *     tests/Base64Shim.java tests/KokerUnitTests.java && \
 *     java -cp /tmp/koker_test:~/android-sdk/android-36/android.jar com.buddyapps.kokerqualizer.KokerUnitTests
 */
public class KokerUnitTests {
    private static int passed = 0;
    private static int failed = 0;

    private static void check(String name, boolean cond) {
        if (cond) {
            passed++;
        } else {
            failed++;
            System.err.println("FAIL: " + name);
        }
    }

    public static void main(String[] args) throws Exception {
        // 1. NaN / Infinity / Bounds clamp safety
        check("clamp NaN -> 0", EqStore.clamp(Float.NaN, -18f, 18f) == 0f);
        check("clamp +Inf -> 0", EqStore.clamp(Float.POSITIVE_INFINITY, -18f, 18f) == 0f);
        check("clamp -Inf -> 0", EqStore.clamp(Float.NEGATIVE_INFINITY, -18f, 18f) == 0f);
        check("clamp high -> max", EqStore.clamp(42f, -18f, 18f) == 18f);
        check("clamp low -> min", EqStore.clamp(-99f, -18f, 18f) == -18f);

        // 2. UTF-16 Emoji / Surrogate Pair Monogram safety
        check("firstCodePoint ASCII", "K".equals(EqStore.firstCodePoint("Koker", "K")));
        check("firstCodePoint empty -> fallback", "K".equals(EqStore.firstCodePoint("   ", "K")));
        String fire = "\uD83D\uDD25"; // 🔥 (2 UTF-16 chars)
        check("firstCodePoint emoji intact", fire.equals(EqStore.firstCodePoint(fire + "bass", "K")));

        // 3. 10-band <-> 15-band & 5-band resampling invariants
        float[] flat10 = new float[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        float[] flat15 = EqStore.resample10To15(flat10);
        check("resample10To15 length == 15", flat15.length == 15);
        check("resample10To15 preserves flat", flat15[0] == 0f && flat15[7] == 0f && flat15[14] == 0f);

        float[] ramp10 = new float[]{10f, 8f, 6f, 4f, 2f, 0f, -2f, -4f, -6f, -8f};
        float[] ramp15 = EqStore.resample10To15(ramp10);
        check("resample10To15 endpoints match", Math.abs(ramp15[0] - 10f) < 0.01f && Math.abs(ramp15[14] - (-8f)) < 0.01f);
        float[] back10 = EqStore.resample15To10(ramp15);
        check("resample15To10 round-trip endpoints", Math.abs(back10[0] - 10f) < 0.2f && Math.abs(back10[9] - (-8f)) < 0.2f);

        float[] b5 = EqStore.to5Bands(ramp10);
        check("to5Bands length == 5", b5.length == 5);
        check("to5Bands average", Math.abs(b5[0] - 9f) < 0.01f);

        // 4. 3-tap Gaussian curve smoothing
        EqStore.EqPreset sp = new EqStore.EqPreset();
        sp.bands10 = new float[]{0f, 12f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f};
        sp.bands15 = EqStore.resample10To15(sp.bands10);
        EqStore.smoothBands(sp);
        check("smoothBands softens spike", sp.bands10[1] == 6.0f && sp.bands10[0] == 3.0f && sp.bands10[2] == 3.0f);

        // 5. Auto-EQ Catalog caching & immutability
        List<AutoEqCatalog.DeviceProfile> p1 = AutoEqCatalog.getAllProfiles();
        List<AutoEqCatalog.DeviceProfile> p2 = AutoEqCatalog.getAllProfiles();
        check("AutoEqCatalog cached reference", p1 == p2);
        check("AutoEqCatalog has 15 profiles", p1.size() == 15);
        boolean threwUnmod = false;
        try { p1.clear(); } catch (UnsupportedOperationException e) { threwUnmod = true; }
        check("AutoEqCatalog list unmodifiable", threwUnmod);

        EqStore.EqPreset sample = p1.get(0).toPreset();
        sample.preampDb = -2.5f;
        sample.subSonicFilter = true;
        sample.analogWarmth = true;
        sample.crossfeed = true;
        sample.nightWhisper = true;
        sample.limiterCeilingMode = EqStore.LIM_STUDIO;
        sample.bassTiltDb = 3.5f;
        sample.trebleTiltDb = -1.5f;

        // 6. KOKER1: Share Code round-trip encode -> decode
        String code = AutoEqCatalog.encodeShareCode(sample);
        check("share code prefix", code.startsWith("KOKER1:"));
        EqStore.EqPreset decoded = AutoEqCatalog.decodeShareCode("Check out my EQ: " + code + " !");
        check("share code name round-trip", sample.name.equals(decoded.name));
        check("share code preamp round-trip", Math.abs(decoded.preampDb - (-2.5f)) < 0.05f);
        check("share code DSP toggles round-trip", decoded.subSonicFilter && decoded.analogWarmth && decoded.crossfeed && decoded.nightWhisper);
        check("share code ceiling round-trip", decoded.limiterCeilingMode == EqStore.LIM_STUDIO);
        check("share code tilt round-trip", Math.abs(decoded.bassTiltDb - 3.5f) < 0.05f && Math.abs(decoded.trebleTiltDb - (-1.5f)) < 0.05f);
        check("share code band0 round-trip", Math.abs(decoded.bands10[0] - sample.bands10[0]) < 0.11f);

        boolean threwBadCode = false;
        try { AutoEqCatalog.decodeShareCode("not-a-valid-code"); } catch (Exception e) { threwBadCode = true; }
        check("share code rejects invalid input", threwBadCode);

        // 7. YouTube playlist ID extraction
        check("extract YT playlist URL", "PL1234567890abcdef".equals(
                YouTubePlaylistSync.extractPlaylistId("https://www.youtube.com/playlist?list=PL1234567890abcdef&si=xyz")));
        check("extract YT Music share text", "OLAK5uy_abc123456789".equals(
                YouTubePlaylistSync.extractPlaylistId("Listen here: https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=OLAK5uy_abc123456789")));
        check("extract raw playlist ID", "PLabcdefghij1234".equals(
                YouTubePlaylistSync.extractPlaylistId("PLabcdefghij1234")));
        check("reject garbage playlist ID", YouTubePlaylistSync.extractPlaylistId("hello world") == null);

        System.out.println("KokerUnitTests: " + passed + " passed, " + failed + " failed.");
        if (failed > 0) System.exit(1);
    }
}
