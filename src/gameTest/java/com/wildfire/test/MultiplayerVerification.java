package com.wildfire.test;

import com.wildfire.main.WildfireGender;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Isolated localhost test fixture. This class is never packaged in the release jar. */
@EventBusSubscriber(modid="wildfire_gender_test")
public final class MultiplayerVerification {
    private static int together;
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(!"server".equals(System.getProperty("wildfire.verify")) || !(event.getEntity() instanceof ServerPlayer player)) return;
        player.setGameMode(GameType.CREATIVE);
        var level=player.serverLevel(); level.setDayTime(6000);
        for(int x=-5;x<=5;x++) for(int z=-5;z<=5;z++) level.setBlockAndUpdate(new BlockPos(x,80,z),Blocks.SMOOTH_STONE.defaultBlockState());
        player.connection.teleport(player.getGameProfile().getName().endsWith("A") ? -1 : 1,81,0,0,0);
        player.setItemSlot(EquipmentSlot.CHEST,ItemStack.EMPTY); player.setItemSlot(EquipmentSlot.LEGS,ItemStack.EMPTY);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(!"server".equals(System.getProperty("wildfire.verify"))) return;
        var players=event.getServer().getPlayerList().getPlayers();
        if(players.isEmpty() && together>320) { System.out.println("BODY_VERIFY_SERVER: PASS; shutting down test server"); event.getServer().halt(false); return; }
        if(players.size()<2) return;
        together++;
        if(together==80) {
            for(var player:players) {
                var profile=WildfireGender.getPlayerById(player.getUUID());
                if(profile==null || !profile.hasExtendedProfile) throw new IllegalStateException("Extended profile did not reach dedicated server");
                float expected=player.getGameProfile().getName().endsWith("A") ? 1.2f : .9f;
                if(profile.getBustSize()!=expected) throw new IllegalStateException("Dedicated server lost extended size");
            }
            System.out.println("BODY_VERIFY_SERVER: both real clients synchronized profiles");
        }
        if(together==140) {
            for(var player:players) if(player.getGameProfile().getName().endsWith("B")) player.connection.teleport(400,81,0,0,0);
        }
        if(together==180) {
            for(var player:players) if(player.getGameProfile().getName().endsWith("B")) player.connection.teleport(1,81,0,0,0);
        }
        if(together==240) {
            for(var player:players) { player.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.LEATHER_CHESTPLATE)); player.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.LEATHER_LEGGINGS)); }
        }
        if(together==320) {
            for(var player:players) { player.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.DIAMOND_CHESTPLATE)); player.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.DIAMOND_LEGGINGS)); }
            System.out.println("BODY_VERIFY_SERVER: tracking exit/reentry and armor updates dispatched");
        }
    }
}
