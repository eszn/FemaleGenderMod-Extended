package com.wildfire.physics;

import com.wildfire.main.entitydata.BodySettings;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
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
        var response=SecondaryMotion.body(motion,settings,support,entity.isInWater(),entity.onGround(),entity.walkAnimation.position(),entity.walkAnimation.speed());
        response.first().tick(left); response.second().tick(right); response.third().tick(depth);
    }
    public double vertical(boolean isLeft, float partial) { return (isLeft ? left : right).sample(partial); }
    public double depth(float partial) { return depth.sample(partial); }
}
