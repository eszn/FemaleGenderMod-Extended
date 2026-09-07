package com.wildfire.physics;

/** Derives local acceleration from tick positions, including remote players and moving vehicles. */
public final class MotionInput {
    private double x, y, z, vx, vy, vz;
    private float yaw;
    private boolean initialized;
    private int tick = Integer.MIN_VALUE;
    private Object level;
    public record Sample(double lateral, double vertical, double forward, double turn, boolean reset) {}

    public Sample update(double px, double py, double pz, float bodyYaw, int now, Object world) {
        double dx = px - x, dy = py - y, dz = pz - z;
        boolean discontinuity = !initialized || world != level || now != tick + 1 || dx*dx + dy*dy + dz*dz > 4
                || !Double.isFinite(px + py + pz) || !Float.isFinite(bodyYaw);
        double ax = (dx - vx) * 400, ay = (dy - vy) * 400, az = (dz - vz) * 400;
        double radians = Math.toRadians(bodyYaw);
        double turn = Math.IEEEremainder(bodyYaw - yaw, 360);
        x = px; y = py; z = pz; vx = dx; vy = dy; vz = dz; yaw = bodyYaw; tick = now; level = world; initialized = true;
        if (discontinuity) { vx = vy = vz = 0; return new Sample(0, 0, 0, 0, true); }
        return new Sample(Math.clamp(ax*Math.cos(radians) + az*Math.sin(radians), -40, 40),
                Math.clamp(ay, -40, 40), Math.clamp(-ax*Math.sin(radians) + az*Math.cos(radians), -40, 40),
                Math.clamp(turn, -30, 30), false);
    }
}
