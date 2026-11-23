package android.util;

/**
 * Pure-JVM Base64 shim used ONLY when running CLI unit tests outside Android runtime.
 */
public class Base64 {
    public static final int NO_WRAP = 2;
    public static final int URL_SAFE = 8;

    public static String encodeToString(byte[] input, int flags) {
        if ((flags & URL_SAFE) != 0) {
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(input);
        }
        return java.util.Base64.getEncoder().encodeToString(input);
    }

    public static byte[] decode(String str, int flags) {
        if ((flags & URL_SAFE) != 0) {
            return java.util.Base64.getUrlDecoder().decode(str);
        }
        return java.util.Base64.getDecoder().decode(str);
    }
}
