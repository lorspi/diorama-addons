package io.github.dioramaaddons.render;

/**
 * Look of the map, after the default style of the Diorama map mod
 * (https://github.com/SunkenInTime/diorama, MIT, Copyright (c) 2026 Dara Adedeji): warm sunlight, cool
 * shadows, sky occlusion, high ground a little brighter, outlines where higher ground starts, and a colour
 * grade. Light is computed in linear colour space.
 *
 * @param sunElevation degrees above the horizon
 * @param sunAzimuthX  horizontal direction towards the sun, x part (east is +x)
 * @param sunAzimuthZ  horizontal direction towards the sun, z part (south is +z)
 */
public record MapStyle(float saturation, float exposure, float[] sunTint, float[] shadowTint, float outline,
        float skyOcclusion, double sunElevation, double sunAzimuthX, double sunAzimuthZ, float ambient,
        float sunStrength, float heightShade, float cornerOcclusion) {

    /** Diorama 0.6.0's default style values. */
    public static final MapStyle DIORAMA = new MapStyle(1.35F, 1.16F, new float[] {1.14F, 1.0F, 0.8F},
            new float[] {0.74F, 0.86F, 1.18F}, 0.38F, 0.85F, 38, -0.878, 0.479, 0.34F, 0.62F, 0.22F, 1.5F);
}
