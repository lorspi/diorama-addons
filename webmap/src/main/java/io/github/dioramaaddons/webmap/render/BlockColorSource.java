package io.github.dioramaaddons.webmap.render;

/** Resolves block ids to map colours. */
public interface BlockColorSource {
    /** RGB colour (0xRRGGBB) for a block id, or -1 when the block is invisible on maps (air, glass...). */
    int color(String blockId);

    /** True for water and water-filled blocks (seagrass, kelp, bubble columns). */
    boolean isWater(String blockId);

    /** Colour used for water surfaces. */
    int waterColor();
}
