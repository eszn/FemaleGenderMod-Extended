package com.wildfire.physics;

import com.wildfire.main.entitydata.BodySettings;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Items;

public final class BodyPhysics {
    private final MotionInput input = new MotionInput();
    private final DampedSpring left = new DampedSpring(), right = new DampedSpring(), depth = new DampedSpring();
    public void update(LivingEntity entity, BodySettings settings, boolean enabled) {
        MotionInput.Sample motion = input.update(entity.getX(), entity.getY(), entity.getZ(), entity.yBodyRot, entity.tickCount, entity.level());
        var legs = entity.getItemBySlot(EquipmentSlot.LEGS);
        // All rigid and unknown leg coverings are held still; leather permits a little damped movement.
        double support = legs.isEmpty() ? 0 : legs.is(Items.LEATHER_LEGGINGS) ? .8 : 1;
        if (!enabled || !settings.physics() || motion.reset() || entity.isSleeping() || entity.isPassenger() || support == 1) {
            left.reset(); right.reset(); depth.reset(); return;
        }
        double gain = settings.motion() * settings.buttocks() * (1-support) * (entity.isInWater() ? .35 : 1);
        double frequency = 5.5 + support * 2;
        double damping = .72 + support * .5;
        double limit = .55 * gain;
        double gait = entity.onGround() ? Math.sin(entity.walkAnimation.position()*2)*Math.min(entity.walkAnimation.speed(),1)*55 : 0;
        left.tick((motion.vertical() * 14 + motion.turn() * .8 + gait) * gain, frequency, damping, limit);
        right.tick((motion.vertical() * 14 - motion.turn() * .8 - gait) * gain, frequency, damping, limit);
        depth.tick(-motion.forward() * 10 * gain, frequency + .7, damping, limit * .5);
    }
    public double vertical(boolean isLeft, float partial) { return (isLeft ? left : right).sample(partial); }
    public double depth(float partial) { return depth.sample(partial); }
}
