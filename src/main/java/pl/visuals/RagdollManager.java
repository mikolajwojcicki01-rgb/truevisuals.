package pl.visuals;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Wykrywa zabicie gracza i zarządza ragdollami. */
public final class RagdollManager {
    private static final List<Ragdoll> RAGDOLLS = new ArrayList<>();
    private static final Set<UUID> DONE = new HashSet<>();
    private static final Map<UUID, Seen> SEEN = new HashMap<>();
    private static final java.util.Random RNG = new java.util.Random();

    private RagdollManager() {}

    private record Seen(Vec3d pos, float yaw, float health, Identifier skin) {}

    public static void tick(MinecraftClient mc) {
        ClientWorld world = mc == null ? null : mc.world;
        if (mc == null || world == null || mc.player == null) {
            RAGDOLLS.clear();
            FakePlayer.remove();
            DONE.clear();
            SEEN.clear();
            return;
        }

        detectDeaths(mc, world);

        if (FakePlayer.I != null) FakePlayer.I.tick(mc);

        Iterator<Ragdoll> it = RAGDOLLS.iterator();
        while (it.hasNext()) {
            Ragdoll r = it.next();
            r.tick(world);
            if (r.dead) it.remove();
        }
    }

    private static void detectDeaths(MinecraftClient mc, ClientWorld world) {
        Set<UUID> present = new HashSet<>();
        for (AbstractClientPlayerEntity p : world.getPlayers()) {
            if (p == mc.player) continue;
            UUID id = p.getUuid();
            present.add(id);

            boolean dying = p.isDead() || p.getHealth() <= 0f;
            if (dying) {
                if (DONE.add(id)) {
                    Fx.killTitleName = p.getName().getString();
                    onKill(mc, p.getSkinTextures().texture(), p.getX(), p.getY(), p.getZ(), p.getYaw());
                }
                // prawdziwe ciało znika – zostaje tylko nasz ragdoll
                p.setInvisible(true);
            } else {
                DONE.remove(id);
                SEEN.put(id, new Seen(p.getPos(), p.getYaw(), p.getHealth(),
                        p.getSkinTextures().texture()));
            }
        }

        // serwery, które usuwają gracza od razu po śmierci (bez animacji)
        Iterator<Map.Entry<UUID, Seen>> it = SEEN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Seen> en = it.next();
            if (present.contains(en.getKey())) continue;
            Seen s = en.getValue();
            boolean stillOnline = mc.getNetworkHandler() != null
                    && mc.getNetworkHandler().getPlayerListEntry(en.getKey()) != null;
            if (stillOnline && s.health() <= 8f && s.pos().squaredDistanceTo(mc.player.getPos()) < 30 * 30
                    && !DONE.contains(en.getKey())) {
                DONE.add(en.getKey());
                onKill(mc, s.skin(), s.pos().x, s.pos().y, s.pos().z, s.yaw());
            }
            it.remove();
        }
    }

    /** Zabicie: efekt (piorun itp.) + dźwięk + ragdoll (jeśli włączony). */
    public static void onKill(MinecraftClient mc, Identifier skin, double x, double y, double z, float yaw) {
        if (mc.player.squaredDistanceTo(x, y, z) < 60 * 60) {
            Fx.killEffect(x, y, z);
        }
        if (mc.player.squaredDistanceTo(x, y, z) < 30 * 30) Fx.showKillTitle(Fx.killTitleName);
        if (Config.I.ragdoll) spawn(mc, skin, x, y, z, yaw);
    }

    /** Wyrzuca ragdolla od lokalnego gracza (czyli "od zabójcy"). */
    private static void spawn(MinecraftClient mc, Identifier skin, double x, double y, double z, float yaw) {
        double dx = x - mc.player.getX();
        double dz = z - mc.player.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4) {
            dx = RNG.nextDouble() - 0.5;
            dz = RNG.nextDouble() - 0.5;
            len = Math.sqrt(dx * dx + dz * dz);
        }
        dx /= len;
        dz /= len;
        launch(skin, x, y, z, yaw, dx, dz);
    }

    private static void launch(Identifier skin, double x, double y, double z, float yaw, double dx, double dz) {
        float pw = Config.I.ragdollPower;
        switch (Math.floorMod(Config.I.ragdollMode, 3)) {
            case 1 -> { // odrzut
                double h = (0.28 + RNG.nextDouble() * 0.12) * pw;
                RAGDOLLS.add(new Ragdoll(skin, x, y, z, yaw, dx * h, 0.75 * pw, dz * h, 0.08, 1f));
            }
            case 2 -> { // rakieta
                double h = 0.06 * pw;
                RAGDOLLS.add(new Ragdoll(skin, x, y, z, yaw, dx * h, 1.35 * pw, dz * h, 0.045, 1.6f));
            }
            default -> { // w górę, jak na filmie: wysoko, w kółko, prawie pionowo
                double h = 0.07 * pw;
                RAGDOLLS.add(new Ragdoll(skin, x, y, z, yaw, dx * h, 0.95 * pw, dz * h, 0.05, 1.5f));
            }
        }
    }

    /** Test z menu: ragdoll twojej postaci kilka kroków przed tobą. */
    public static void spawnTest(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        Vec3d look = mc.player.getRotationVec(1f);
        double lx = look.x, lz = look.z;
        double len = Math.sqrt(lx * lx + lz * lz);
        if (len < 1e-4) { lx = 0; lz = 1; len = 1; }
        lx /= len;
        lz /= len;
        launch(mc.player.getSkinTextures().texture(), mc.player.getX() + lx * 3.0, mc.player.getY(),
                mc.player.getZ() + lz * 3.0, mc.player.getYaw() + 180f, lx, lz);
    }

    /** Test efektu zabicia kilka kroków przed tobą. */
    public static void testKillEffect(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        Vec3d look = mc.player.getRotationVec(1f);
        double lx = look.x, lz = look.z;
        double len = Math.sqrt(lx * lx + lz * lz);
        if (len < 1e-4) { lx = 0; lz = 1; len = 1; }
        Fx.killEffect(mc.player.getX() + lx / len * 4.0, mc.player.getY(), mc.player.getZ() + lz / len * 4.0);
        Fx.showKillTitle("Test");
    }

    public static void render(WorldRenderContext ctx, MatrixStack ms, VertexConsumerProvider.Immediate imm,
                              Vec3d cp, float td) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return;
        FakePlayer fp = FakePlayer.I;
        if (fp != null && fp.alive()) {
            net.minecraft.util.Identifier tex = MinecraftClient.getInstance().player.getSkinTextures().texture();
            VertexConsumer fvc = imm.getBuffer(RenderLayer.getEntityTranslucent(tex));
            int fl = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(fp.x(), fp.y() + 1, fp.z()));
            fp.render(ms, fvc, cp.x, cp.y, cp.z, td, fl);
        }
        if (RAGDOLLS.isEmpty()) return;
        for (Ragdoll r : RAGDOLLS) {
            VertexConsumer vc = imm.getBuffer(RenderLayer.getEntityTranslucent(r.skin));
            int light = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(r.x, r.y + 0.3, r.z));
            r.render(ms, vc, cp.x, cp.y, cp.z, td, light);
        }
    }
}
