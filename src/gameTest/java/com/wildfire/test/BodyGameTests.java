package com.wildfire.test;

import com.mojang.authlib.GameProfile;
import com.wildfire.main.WildfireGender;
import com.wildfire.main.Gender;
import com.wildfire.main.entitydata.*;
import com.wildfire.main.networking.*;
import com.wildfire.main.config.ClientConfiguration;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Proxy;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@GameTestHolder(WildfireGender.MODID)
@PrefixGameTestTemplate(false)
public class BodyGameTests {
    @GameTest(template="empty") public static void extendedPacketUpdatesOnlyItsSender(GameTestHelper h) {
        var sender=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"BodyPacketTest"));
        var config=WildfireGender.getOrAddPlayerById(sender.getUUID());
        IPayloadContext context=(IPayloadContext)Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),new Class[]{IPayloadContext.class},
                (proxy,method,args)-> { if(method.getName().equals("player")) return sender; throw new UnsupportedOperationException(method.getName()); });
        new BodySync.Serverbound(new BodySync.Profile(UUID.randomUUID(),1.2f,BodySettings.DEFAULT)).handle(context);
        h.runAfterDelay(6,()->{
            h.assertTrue(!config.hasExtendedProfile,"Forged UUID was ignored");
            new BodySync.Serverbound(new BodySync.Profile(sender.getUUID(),1.1f,BodySettings.DEFAULT)).handle(context);
            new BodySync.Serverbound(new BodySync.Profile(sender.getUUID(),1.2f,BodySettings.DEFAULT)).handle(context);
            h.runAfterDelay(6,()->{
                h.assertTrue(config.hasExtendedProfile && config.getBustSize()==1.2f,"Final coalesced update was applied");
                h.assertTrue(config.getBodySettings().equals(BodySettings.DEFAULT),"Body settings survived server handling");
                h.succeed();
            });
        });
    }
    @GameTest(template="empty") public static void oldAndNewPacketsCoexist(GameTestHelper h) {
        var config=new PlayerConfig(UUID.randomUUID(),Gender.FEMALE);
        config.updateBustSize(1.2f);
        var buffer=Unpooled.buffer();
        try {
            ServerboundSyncPacket.STREAM_CODEC.encode(buffer,new ServerboundSyncPacket(config));
            buffer.skipBytes(16); buffer.readByte();
            h.assertTrue(buffer.readFloat()==.8f,"Legacy peers receive the upstream maximum");
            buffer.clear();
            BodySync.CODEC.encode(buffer,new BodySync.Profile(config));
            h.assertTrue(BodySync.CODEC.decode(buffer).bust()==1.2f,"Extended peers receive the actual bust size");
        } finally { buffer.release(); }
        h.succeed();
    }
    @GameTest(template="empty") public static void bodyProfilePersistsInConfiguration(GameTestHelper h) {
        var player=new PlayerConfig(UUID.randomUUID());
        player.updateBodySettings(new BodySettings(.9f,.8f,.7f,.6f,BodySettings.BreastShape.ROUNDED,false,.4f));
        player.updateBustSize(1.2f);
        PlayerConfig.saveGenderInfo(player);
        var loaded=new PlayerConfig(player.uuid); loaded.loadFromDisk(false);
        h.assertTrue(loaded.getBodySettings().equals(player.getBodySettings()),"All body settings round trip through disk");
        h.assertTrue(loaded.getBustSize()==1.2f,"Expanded size survives disk reload"); h.succeed();
    }
    @GameTest(template="empty") public static void armorStandSizeAndFiniteValues(GameTestHelper h) {
        CompoundTag inner=new CompoundTag(); inner.putFloat("BreastSize",1.2f);
        CompoundTag tag=new CompoundTag(); tag.put("WildfireGender",inner);
        var data=BreastDataComponent.fromComponent(CustomData.of(tag));
        h.assertTrue(data!=null && data.breastSize()==1.2f,"Armor stands support expanded size");
        inner.putFloat("BreastSize",Float.NaN);
        data=BreastDataComponent.fromComponent(CustomData.of(tag));
        h.assertTrue(data!=null && Float.isFinite(data.breastSize()),"Malformed item data cannot poison rendering");
        h.succeed();
    }
    @GameTest(template="empty") public static void oldConfigurationMigratesWithoutResettingChoices(GameTestHelper h) {
        var player=new PlayerConfig(UUID.randomUUID());
        player.getConfig().set(ClientConfiguration.GENDER,Gender.FEMALE);
        player.getConfig().set(ClientConfiguration.BUST_SIZE,.73f);
        player.getConfig().set(ClientConfiguration.HURT_SOUNDS,false);
        player.loadFromConfig(false);
        h.assertTrue(player.getBustSize()==.73f && !player.hasHurtSounds(),"Existing values remain unchanged");
        h.assertTrue(player.getBodySettings().equals(BodySettings.DEFAULT),"Missing body fields receive natural defaults");
        h.succeed();
    }
}
