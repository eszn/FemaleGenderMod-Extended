/*
 * Wildfire's Female Gender Mod is a female gender mod created for Minecraft.
 * Copyright (C) 2023-present WildfireRomeo
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.wildfire.physics;

import com.wildfire.api.IGenderArmor;
import com.wildfire.main.entitydata.EntityConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

/** Deterministic damped secondary motion; values exposed through the original renderer API. */
public class BreastPhysics {
    private final EntityConfig config;
    private final MotionInput input = new MotionInput();
    private final DampedSpring vertical = new DampedSpring(), lateral = new DampedSpring(), rotation = new DampedSpring();
    private float size, previousSize;
    private final int side;

    public BreastPhysics(EntityConfig config) { this(config,1); }
    public BreastPhysics(EntityConfig config,int side) { this.config = config; this.side = side; }

    public void update(LivingEntity entity, IGenderArmor armor) {
        MotionInput.Sample motion = input.update(entity.getX(), entity.getY(), entity.getZ(), entity.yBodyRot, entity.tickCount, entity.level());
        boolean legacyOverride = config.getArmorPhysicsOverride() && config.getBodySettings().shape() == com.wildfire.main.entitydata.BodySettings.BreastShape.CLASSIC;
        double support = armor.coversBreasts() && !legacyOverride ? Math.clamp(armor.physicsResistance(), 0, 1) : 0;
        if (!Double.isFinite(support)) support = 1;
        float target = config.getGender().canHaveBreasts() ? config.getBustSize() : 0;
        if (armor.coversBreasts() && !legacyOverride) target *= 1 - .12f * (float)Math.clamp(armor.tightness(), 0, 1);
        previousSize = size;
        size = motion.reset() ? target : size + (target-size)*.5f;
        if (motion.reset()) previousSize = size;
        if (motion.reset() || !config.hasBreastPhysics() || target < .02 || support >= .99
                || entity instanceof ArmorStand || entity.isSleeping()) {
            vertical.reset(); lateral.reset(); rotation.reset(); return;
        }
        var response=SecondaryMotion.breast(motion,target,config.getBounceMultiplier(),config.getFloppiness(),side,support,
                entity.isInWater(),entity.onGround() && !entity.isPassenger(),entity.walkAnimation.position(),entity.walkAnimation.speed());
        response.first().tick(vertical); response.second().tick(lateral); response.third().tick(rotation);
    }
    public float getBreastSize(float partial) { return previousSize + (size-previousSize)*Math.clamp(partial,0,1); }
    // Legacy renderer expects half-pixel units.
    public float getPrePositionY() { return (float)vertical.previous()*2; }
    public float getPositionY() { return (float)vertical.position()*2; }
    public float getPrePositionX() { return (float)lateral.previous()*2; }
    public float getPositionX() { return (float)lateral.position()*2; }
    public float getBounceRotation() { return (float)rotation.position(); }
    public float getPreBounceRotation() { return (float)rotation.previous(); }
}
