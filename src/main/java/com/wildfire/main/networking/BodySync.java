package com.wildfire.main.networking;

import com.wildfire.main.WildfireGender;
import com.wildfire.main.entitydata.BodySettings;
import com.wildfire.main.entitydata.PlayerConfig;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.UUID;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Optional extension: the upstream v1 packet and registration remain byte-for-byte compatible. */
public final class BodySync {
    private static final Map<ServerPlayer, Profile> PENDING = new WeakHashMap<>();
    public record Profile(UUID uuid, float bust, BodySettings body) {
        public Profile {
            if (uuid == null || body == null || !Float.isFinite(bust) || bust < 0 || bust > 1.2f)
                throw new IllegalArgumentException("Invalid body profile");
        }
        public Profile(PlayerConfig player) { this(player.uuid, player.getBustSize(), player.getBodySettings()); }
        public void apply(PlayerConfig player) { player.updateBustSize(bust); player.updateBodySettings(body); player.hasExtendedProfile = true; }
    }
    public static final StreamCodec<ByteBuf, Profile> CODEC = new StreamCodec<>() {
        @Override public Profile decode(ByteBuf b) {
            try {
                UUID id = new UUID(b.readLong(), b.readLong());
                float bust = b.readFloat(), hips = b.readFloat(), thighs = b.readFloat(), butt = b.readFloat(), waist = b.readFloat();
                int shape = b.readUnsignedByte();
                if (shape >= BodySettings.BreastShape.values().length) throw new IllegalArgumentException("Unknown shape");
                boolean physics = b.readBoolean();
                float motion = b.readFloat();
                return new Profile(id, bust, new BodySettings(hips, thighs, butt, waist, BodySettings.BreastShape.values()[shape], physics, motion));
            } catch (IllegalArgumentException | IndexOutOfBoundsException e) { throw new DecoderException("Invalid extended body profile", e); }
        }
        @Override public void encode(ByteBuf b, Profile p) {
            b.writeLong(p.uuid.getMostSignificantBits()).writeLong(p.uuid.getLeastSignificantBits()).writeFloat(p.bust);
            BodySettings s = p.body;
            b.writeFloat(s.hips()).writeFloat(s.thighs()).writeFloat(s.buttocks()).writeFloat(s.waist())
                    .writeByte(s.shape().ordinal()).writeBoolean(s.physics()).writeFloat(s.motion());
        }
    };

    public record Serverbound(Profile profile) implements CustomPacketPayload {
        public static final Type<Serverbound> TYPE = new Type<>(WildfireGender.rl("extended_body_send"));
        public static final StreamCodec<ByteBuf, Serverbound> STREAM_CODEC = CODEC.map(Serverbound::new, Serverbound::profile);
        @Override public Type<Serverbound> type() { return TYPE; }
        public void handle(IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer sender) || !sender.getUUID().equals(profile.uuid)) return;
            // Coalesce rapid slider edits, retaining the final value even during server tick lag.
            PENDING.put(sender, profile);
        }
    }
    public record Clientbound(Profile profile) implements CustomPacketPayload {
        public static final Type<Clientbound> TYPE = new Type<>(WildfireGender.rl("extended_body_sync"));
        public static final StreamCodec<ByteBuf, Clientbound> STREAM_CODEC = CODEC.map(Clientbound::new, Clientbound::profile);
        @Override public Type<Clientbound> type() { return TYPE; }
        public void handle(IPayloadContext context) {
            if (context.player().getUUID().equals(profile.uuid)) return;
            PlayerConfig player = WildfireGender.getOrAddPlayerById(profile.uuid);
            profile.apply(player);
            player.syncStatus = PlayerConfig.SyncStatus.SYNCED;
        }
    }
    public static void send(ServerPlayer target, PlayerConfig player) {
        if (target.connection.hasChannel(Clientbound.TYPE)) PacketDistributor.sendToPlayer(target, new Clientbound(new Profile(player)));
    }
    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 5 != 0) return;
        PENDING.forEach((sender, profile) -> {
            PlayerConfig player = WildfireGender.getOrAddPlayerById(sender.getUUID());
            profile.apply(player);
            for (ServerPlayer tracker : WildfireGender.getTrackers(sender)) send(tracker, player);
        });
        PENDING.clear();
    }
    public static void forget(ServerPlayer player) { PENDING.remove(player); }
}
