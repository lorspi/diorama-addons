package io.github.dioramaaddons.render;

/** sRGB ⇄ linear conversions and the final colour grade. */
public final class ColorMath {
    private static final float[] SRGB_TO_LINEAR = new float[256];
    private static final int[] LINEAR_TO_SRGB = new int[4096];

    static {
        for (int i = 0; i < 256; i++) {
            double c = i / 255.0;
            SRGB_TO_LINEAR[i] = (float) (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
        }
        for (int i = 0; i < 4096; i++) {
            double l = i / 4095.0;
            double c = l <= 0.0031308 ? l * 12.92 : 1.055 * Math.pow(l, 1 / 2.4) - 0.055;
            LINEAR_TO_SRGB[i] = (int) Math.round(Math.max(0, Math.min(1, c)) * 255);
        }
    }

    private ColorMath() {}

    public static float toLinear(int srgbChannel) {
        return SRGB_TO_LINEAR[srgbChannel & 0xFF];
    }

    public static int toSrgb(float linear) {
        return LINEAR_TO_SRGB[Math.min(4095, Math.max(0, (int) (linear * 4095)))];
    }

    public static float luminance(float r, float g, float b) {
        return 0.2126F * r + 0.7152F * g + 0.0722F * b;
    }

    /** Saturation and exposure, then back to sRGB as opaque ARGB. */
    public static int grade(float r, float g, float b, MapStyle style) {
        float l = luminance(r, g, b);
        r = (l + (r - l) * style.saturation()) * style.exposure();
        g = (l + (g - l) * style.saturation()) * style.exposure();
        b = (l + (b - l) * style.saturation()) * style.exposure();
        return 0xFF000000 | toSrgb(r) << 16 | toSrgb(g) << 8 | toSrgb(b);
    }
}
