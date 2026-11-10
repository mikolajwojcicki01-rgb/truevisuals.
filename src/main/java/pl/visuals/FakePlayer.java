package pl.visuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

/**
 * Fake player: manekin renderowany po stronie klienta (ze skinem gracza), w który można bić.
 * Reaguje dźwiękiem, cząsteczkami, combo, a po zabiciu odpala efekt zabicia i ragdolla.
 */
public final class FakePlayer {
    public static FakePlayer I;

    private final double x, y, z;
    private float health = 20f;
    private float tilt;
    private int respawn = -1;
    private final Ragdoll model;
    private final net.minecraft.util.Identifier skin;
    private final net.minecraft.client.util.SkinTextures skinTextures;
    private float yaw;

    private FakePlayer(MinecraftClient mc, double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.skinTextures = mc.player.getSkinTextures();
        this.skin = skinTextures.texture();
        this.model = new Ragdoll(skin, x, y, z, 0f, 0, 0, 0);
        this.yaw = mc.player.getYaw() + 180f;
    }

    /** Stawia fake playera kilka kratek przed graczem. */
    public static void spawn(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        Vec3d look = mc.player.getRotationVec(1f);
        double lx = look.x, lz = look.z;
        double len = Math.sqrt(lx * lx + lz * lz);
        if (len < 1e-4) { lx = 0; lz = 1; len = 1; }
        double x = mc.player.getX() + lx / len * 4.0;
        double z = mc.player.getZ() + lz / len * 4.0;
        double y = mc.player.getY();
        // zejdź na ziemię jeśli stoimy na krawędzi
        for (int i = 0; i < 6; i++) {
            BlockPos below = BlockPos.ofFloored(x, y - 0.05, z);
            if (!mc.world.getBlockState(below).getCollisionShape(mc.world, below).isEmpty()) break;
            y -= 1.0;
        }
        I = new FakePlayer(mc, x, y, z);
    }

    public static void remove() {
        I = null;
    }

    public boolean alive() {
        return respawn < 0;
    }

    private Box box() {
        return new Box(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3);
    }

    /** Odległość do trafienia promieniem z oczu gracza (zasięg 3.5) lub -1. */
    public double rayHit(MinecraftClient mc) {
        if (!alive() || mc.player == null) return -1;
        Vec3d from = mc.player.getCameraPosVec(1f);
        Vec3d dir = mc.player.getRotationVec(1f);
        Vec3d to = from.add(dir.multiply(3.5));
        Optional<Vec3d> hit = box().raycast(from, to);
        return hit.map(v -> Math.max(0.01, v.distanceTo(from))).orElse(-1.0);
    }

    public void fillTarget(MinecraftClient mc) {
        Fx.targetName = "FakePlayer";
        Fx.targetHealth = Math.max(0f, health);
        Fx.targetMax = 20f;
        Fx.targetSkin = skinTextures;
        Fx.targetTime = mc.world.getTime();
    }

    public void hit(MinecraftClient mc) {
        Fx.registerHit(x, y + 1.2, z);
        Fx.addFlash(-1, true);
        tilt = 16f;
        health -= Config.I.fakeDamage;
        fillTarget(mc);
        if (health <= 0f) {
            health = 0f;
            respawn = 50;
            Fx.targetHealth = 0f;
            Fx.killTitleName = "FakePlayer";
            RagdollManager.onKill(mc, skin, x, y, z, yaw);
        }
    }

    public void tick(MinecraftClient mc) {
        if (mc.player == null) return;
        double dx = mc.player.getX() - x, dz = mc.player.getZ() - z;
        yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        tilt *= 0.78f;
        if (respawn >= 0) {
            respawn--;
            if (respawn < 0) {
                if (Config.I.fakeRespawn) {
                    health = 20f;
                } else {
                    I = null;
                }
            }
        }
    }

    public void render(net.minecraft.client.util.math.MatrixStack ms, net.minecraft.client.render.VertexConsumer vc,
                       double cx, double cy, double cz, float td, int light) {
        if (!alive()) return;
        float t = (float) ((System.currentTimeMillis() % 100000) / 1000.0);
        float breathe = (float) Math.sin(t * 2.0) * 2f;
        model.renderPose(ms, vc, cx, cy, cz, x, y + 0.95, z, yaw, -tilt, 0f, 1f, light,
                breathe, -breathe, 0f, 0f, 6f + tilt * 0.8f);
    }

    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
}
