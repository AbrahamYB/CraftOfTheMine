package com.botwclimb.network;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.climb.ClimbData;
import com.botwclimb.common.climb.ClimbState;
import com.botwclimb.common.bullettime.BulletFall;
import com.botwclimb.common.paraglider.ParagliderController;
import com.botwclimb.common.climb.ClimbConstants;
import com.botwclimb.common.climb.ClimbController;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NetworkHandler {
    private NetworkHandler() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1").optional(); // lets the client join servers without the mod
        r.playToClient(PlayerStatePayload.TYPE, PlayerStatePayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            var me = ctx.player();
            me.setData(BotwClimb.FABRIC_UNLOCKS, p.fabricUnlocks());
            me.setData(BotwClimb.STAMINA_UPGRADES, p.staminaUpgrades());
            me.setData(BotwClimb.ALTAR_CREDIT, p.altarCredit());
        }));
        r.playBidirectional(StaminaPayload.TYPE, StaminaPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            var me = ctx.player();
            float max = ClimbController.maxStamina(me);
            if (me instanceof ServerPlayer) { // the client reports its level: saved for the next join, clamped to what the wheel can hold
                if (Float.isFinite(p.stamina()) && p.stamina() >= 0f) {
                    me.setData(BotwClimb.STAMINA_LEFT, Math.min(p.stamina(), max));
                    me.setData(BotwClimb.STAMINA_TIRED, p.exhausted());
                }
                return;
            }
            ClimbData d = me.getData(BotwClimb.CLIMB); // the server tells us what was left when we last left
            if (p.stamina() >= 0f && Float.isFinite(p.stamina())) {
                d.stamina = Math.min(p.stamina(), max);
                d.exhausted = p.exhausted();
                d.lastMax = max;
            }
            d.sentStamina = d.stamina;
            d.sentTired = d.exhausted;
            d.restored = true;
        }));
        r.playToServer(ClimbInputPayload.TYPE, ClimbInputPayload.CODEC, NetworkHandler::onInput);
        r.playToClient(ClimbStatePayload.TYPE, ClimbStatePayload.CODEC, NetworkHandler::onState);
        r.playToServer(ParagliderTogglePayload.TYPE, ParagliderTogglePayload.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) ParagliderController.serverToggle(sp, p.open());
        });
        r.playToServer(ParagliderFabricPayload.TYPE, ParagliderFabricPayload.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) ParagliderController.setFabric(sp, p.index());
        });
        r.playToServer(ClothCraftPayload.TYPE, ClothCraftPayload.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) ctx.enqueueWork(() -> com.botwclimb.common.paraglider.ClothCrafting.craft(sp, p.index()));
        });
        r.playToServer(BulletShotPayload.TYPE, BulletShotPayload.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) ctx.enqueueWork(() -> {
                com.botwclimb.common.bullettime.BulletSlow.heartbeat(sp); // the lag in the packet is ignored: believing it let a client rewind other players' hitboxes by up to 30 s
            });
        });
        r.playToClient(BulletSlowPayload.TYPE, BulletSlowPayload.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.botwclimb.client.bullettime.BulletTime.onSlowed(p)));
        r.playToServer(BulletFallPayload.TYPE, BulletFallPayload.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) BulletFall.apply(sp, p.on());
        });
        r.playToServer(AltarOfferPayload.TYPE, AltarOfferPayload.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) com.botwclimb.common.altar.AltarServer.handle(sp, p);
        });
        r.playToClient(AltarResultPayload.TYPE, AltarResultPayload.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.botwclimb.client.altar.AltarClient.onResult(p)));
        r.playToClient(ParagliderStatePayload.TYPE, ParagliderStatePayload.CODEC, NetworkHandler::onGlideState);
        r.playToClient(TerminalRidePayload.TYPE, TerminalRidePayload.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.botwclimb.client.shrine.MudHandClient.onState(p)));
        r.playToClient(TerminalOpenPayload.TYPE, TerminalOpenPayload.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.botwclimb.client.shrine.TerminalClient.open(p)));
        r.playToServer(TerminalActionPayload.TYPE, TerminalActionPayload.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) com.botwclimb.common.shrine.PlotTravel.handle(sp, p);
        });
    }

    /** Runs on the client; common classes only. */
    private static void onGlideState(ParagliderStatePayload p, IPayloadContext ctx) {
        Player self = ctx.player();
        if (!(self.level().getEntity(p.entityId()) instanceof Player target)) return;
        var g = target.getData(BotwClimb.PARAGLIDER);
        g.fabric = p.fabric();
        if (target == self && !p.correction()) return; // the owner predicted the open/close already
        g.gliding = p.gliding();
    }

    private static void onInput(ClimbInputPayload p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer sp) {
            ClimbData d = sp.getData(BotwClimb.CLIMB);
            d.claim = p.state();
            d.claimDir = p.dir();
        }
    }

    /** Runs on the client. Only touches common classes, so it is safe to load on a dedicated server. */
    private static void onState(ClimbStatePayload p, IPayloadContext ctx) {
        Player self = ctx.player();
        Entity e = self.level().getEntity(p.entityId());
        if (!(e instanceof Player target)) return;
        ClimbData d = target.getData(BotwClimb.CLIMB);
        if (target == self) {
            if (p.correction() && d.state != ClimbState.NONE) {
                d.setState(ClimbState.NONE);
                d.attachCooldown = ClimbConstants.ATTACH_COOLDOWN * 2;
                if (self.hurtTime > 0) { // knocked off by damage: fall and flail
                    d.ragdollTicks = ClimbConstants.RAGDOLL_TICKS;
                    d.ragdollAge = 0;
                    d.attachCooldown = ClimbConstants.RAGDOLL_TICKS + ClimbConstants.ATTACH_COOLDOWN;
                }
            }
            return;
        }
        d.dir = p.dir();
        d.setState(p.state());
    }
}
