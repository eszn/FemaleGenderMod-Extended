package com.wildfire.physics;

/** Acceleration-driven secondary motion, in model pixels and seconds. No wall clock or RNG. */
public final class DampedSpring {
    private double position, velocity, previous;
    public void reset() { position = velocity = previous = 0; }
    public double position() { return position; }
    public double velocity() { return velocity; }
    public double previous() { return previous; }
    public double sample(double alpha) { return previous + (position - previous) * Math.clamp(alpha, 0, 1); }

    public void tick(double acceleration, double frequency, double damping, double limit) {
        if (!Double.isFinite(acceleration) || !Double.isFinite(frequency) || !Double.isFinite(damping)
                || !Double.isFinite(limit) || frequency <= 0 || damping < 0 || limit <= 0) {
            reset(); return;
        }
        previous = position;
        // Six fixed 1/120 s semi-implicit substeps for each Minecraft tick.
        double omega = 2 * Math.PI * Math.clamp(frequency, .5, 12);
        double drag = 2 * Math.clamp(damping, 0, 2) * omega;
        for (int i = 0; i < 6; i++) {
            // Stronger tension at the envelope boundary; hard constraint catches extreme impulses.
            double stretch = Math.abs(position) / limit;
            double stiffness = omega * omega * (1 + .6 * stretch * stretch);
            velocity += (Math.clamp(acceleration, -1200, 1200) - stiffness * position - drag * velocity) / 120;
            position += velocity / 120;
            if (Math.abs(position) > limit) {
                position = Math.copySign(limit, position);
                if (position * velocity > 0) velocity = 0;
            }
        }
    }
}
