package com.wildfire.main.entitydata;

/** Cosmetic proportions, normalized to the conservative ranges in BodyDeformation. */
public record BodySettings(float hips, float thighs, float buttocks, float waist, BreastShape shape,
                           boolean physics, float motion) {
    public enum BreastShape { CLASSIC, ROUNDED, NATURAL }
    public static final BodySettings NONE = new BodySettings(0, 0, 0, 0, BreastShape.CLASSIC, false, 0);
    public static final BodySettings DEFAULT = new BodySettings(.3f, .25f, .3f, .25f, BreastShape.NATURAL, true, .5f);

    public BodySettings {
        requireUnit(hips); requireUnit(thighs); requireUnit(buttocks); requireUnit(waist); requireUnit(motion);
        if (shape == null) throw new IllegalArgumentException("Missing breast shape");
    }

    public static void requireUnit(float value) {
        if (!Float.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("Expected finite value in [0, 1]");
    }

    public boolean hasBodyShape() { return hips != 0 || thighs != 0 || buttocks != 0 || waist != 0; }
}
