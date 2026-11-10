package pl.visuals;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Util;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Rysuje wszystkie efekty w świecie: pierścienie, okręgi, gwiazdki i ragdolle. */
public final class VisualsRenderer {
    private static final double TAU = Math.PI * 2;
    private static float time;
    private static long lastMs;

    private VisualsRenderer() {}

    public static float time() {
        return time;
    }

    private static float rnd(int n) {
        float x = (float) Math.sin(n * 12.9898f) * 43758.5453f;
        return x - (float) Math.floor(x);
    }

    public static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        MatrixStack ms = ctx.matrixStack();
        if (world == null || mc.player == null || ms == null) return;

        Config c = Config.I;
        long now = Util.getMeasuringTimeMs();
        float dt = lastMs == 0 ? 0f : Math.min(0.1f, (now - lastMs) / 1000f);
        lastMs = now;
        time += dt * c.speed;

        float td = mc.getRenderTickCounter().getTickDelta(true);
        Camera cam = ctx.camera();
        Vec3d cp = cam.getPos();
        VertexConsumerProvider.Immediate imm = mc.getBufferBuilders().getEntityVertexConsumers();

        // gracze, na których rysujemy
        List<AbstractClientPlayerEntity> targets = new ArrayList<>();
        boolean firstPerson = mc.options.getPerspective().isFirstPerson();
        for (AbstractClientPlayerEntity p : world.getPlayers()) {
            boolean self = p == mc.player;
            if (p.isSpectator() || p.isDead()) continue;
            if (self && firstPerson) continue;
            if (c.target == 0 && !self) continue;
            if (c.target == 1 && self) continue;
            if (p.squaredDistanceTo(mc.player) > 64 * 64) continue;
            targets.add(p);
        }

        boolean anyLight = !Fx.BOLTS.isEmpty() || !Fx.FLASHES.isEmpty() || c.ambientStars
                || (!targets.isEmpty() && (c.rings || c.groundRing || c.magicCircle || c.playerStars));

        // ---------- faza 1: świecące kształty ----------
        if (anyLight) {
            VertexConsumer vc = imm.getBuffer(RenderLayer.getLightning());
            Draw.begin(vc, ms.peek().getPositionMatrix(), cp.x, cp.y, cp.z);

            Quaternionf q = cam.getRotation();
            Vector3f rv = new Vector3f(1, 0, 0).rotate(q);
            Vector3f uv = new Vector3f(0, 1, 0).rotate(q);
            double[] right = {rv.x, rv.y, rv.z};
            double[] up = {uv.x, uv.y, uv.z};
            float inten = Math.min(1.5f, c.intensity);

            if (c.ambientStars) ambientStars(cp, right, up, inten);
            for (Fx.Bolt b : Fx.BOLTS) bolt(b, right, up);
            flashes(world, td, inten);

            for (AbstractClientPlayerEntity p : targets) {
                Vec3d pos = p.getLerpedPos(td);
                int seed = p.getUuid().hashCode();
                if (c.auraOn) aura(pos, p.getHeight(), inten, right, up);
                if (c.groundRing) groundRing(pos, inten);
                if (c.magicCircle) magicCircleLines(pos, inten);
                if (c.rings) swirls(pos, p.getHeight(), inten);
                if (c.playerStars) playerStars(pos, seed, right, up, inten);
            }
        }

        // ---------- faza 2: napisy na magicznym kręgu ----------
        if (c.magicCircle && !targets.isEmpty()) {
            TextRenderer tr = mc.textRenderer;
            for (AbstractClientPlayerEntity p : targets) {
                Vec3d pos = p.getLerpedPos(td);
                magicCircleText(ms, imm, tr, pos, cp);
            }
        }

        // ---------- faza 3: ragdolle ----------
        RagdollManager.render(ctx, ms, imm, cp, td);

        imm.draw();
    }

    // ------------------------------------------------------------------

    /** Piorun: poszarpana smuga z nieba do punktu zabicia + błysk na ziemi. */
    /** Złota eksplozja jak na filmie: błysk, snop światła, fala na ziemi i rozlatujące się iskry. */
    private static void goldBurst(Fx.Bolt b, double[] right, double[] up) {
        float f = 1f - b.life / (float) b.maxLife;      // 0 -> 1
        float a = (float) Math.pow(1f - f, 0.8);
        int gold = 0xFFC83C, hot = 0xFFF2B0;
        // snop światła
        double[][] layers = {{1.3, 0.22}, {0.6, 0.5}, {0.2, 0.95}};
        for (double[] l : layers) {
            double w = l[0] * (0.6 + 0.4 * a);
            int col = l[1] > 0.9 ? 0xFFFFFF : (l[1] > 0.4 ? hot : gold);
            double top = b.y + 38;
            Draw.vert(b.x - right[0] * w, b.y - right[1] * w, b.z - right[2] * w, col, (float) l[1] * a);
            Draw.vert(b.x + right[0] * w, b.y + right[1] * w, b.z + right[2] * w, col, (float) l[1] * a);
            Draw.vert(b.x + right[0] * w, top, b.z + right[2] * w, col, 0f);
            Draw.vert(b.x - right[0] * w, top, b.z - right[2] * w, col, 0f);
        }
        // błysk
        double fr = 4.5 * (1.0 - f) + 0.5;
        Draw.glowDisc(b.x, b.y + 1.0, b.z, right[0] * fr, right[1] * fr, right[2] * fr,
                up[0] * fr, up[1] * fr, up[2] * fr, hot, 0.9f * a);
        // fala na ziemi
        Draw.glowRing(b.x, b.y + 0.05, b.z, 0.6 + f * 5.0, 0.12, 48, gold, 0.9f * a);
        Draw.glowRing(b.x, b.y + 0.06, b.z, 0.3 + f * 3.0, 0.08, 40, 0xFFFFFF, 0.7f * a);
        // iskry
        for (int i = 0; i < 22; i++) {
            double ang = i * 2.399963 + b.seed % 7;
            double sp = 1.6 + (i % 5) * 0.55;
            double dist = f * sp * 3.2;
            double sx = b.x + Math.cos(ang) * dist;
            double sz = b.z + Math.sin(ang) * dist;
            double sy = b.y + 0.8 + Math.sin(f * Math.PI) * (1.2 + (i % 4) * 0.8) - f * f * 0.8;
            Draw.star(sx, sy, sz, right, up, 0.18 * (1 - f * 0.6), i % 3 == 0 ? 0xFFFFFF : gold, a);
        }
    }

    private static void bolt(Fx.Bolt b, double[] right, double[] up) {
        if (b.type == 1) {
            goldBurst(b, right, up);
            return;
        }
        float a = Math.min(1f, b.life / 8f);
        java.util.Random r = new java.util.Random(b.seed + b.life / 2);
        int n = 16;
        double top = b.y + 45;
        double[] bx = new double[n + 1], by = new double[n + 1], bz = new double[n + 1];
        double ox = 0, oz = 0;
        for (int i = n; i >= 0; i--) {
            double f = i / (double) n; // 1 = góra, 0 = ziemia
            bx[i] = b.x + ox;
            by[i] = b.y + (top - b.y) * f;
            bz[i] = b.z + oz;
            double amp = 2.2 * f + 0.3;
            ox = Math.max(-5, Math.min(5, ox + (r.nextDouble() - 0.5) * amp)) * (i > 1 ? 1 : 0.2);
            oz = Math.max(-5, Math.min(5, oz + (r.nextDouble() - 0.5) * amp)) * (i > 1 ? 1 : 0.2);
        }
        int glow = Config.lerp(0x7FA8FF, Config.color(0.3f, time), 0.35f);
        double[][] passes = {{0.55, 0.20}, {0.22, 0.55}, {0.07, 1.0}};
        for (double[] ps : passes) {
            int col = ps[1] >= 1.0 ? 0xFFFFFF : glow;
            double w = ps[0];
            for (int i = 0; i < n; i++) {
                double x0 = bx[i], y0 = by[i], z0 = bz[i], x1 = bx[i + 1], y1 = by[i + 1], z1 = bz[i + 1];
                Draw.quad(x0 - right[0] * w, y0 - right[1] * w, z0 - right[2] * w,
                        x0 + right[0] * w, y0 + right[1] * w, z0 + right[2] * w,
                        x1 + right[0] * w, y1 + right[1] * w, z1 + right[2] * w,
                        x1 - right[0] * w, y1 - right[1] * w, z1 - right[2] * w,
                        col, (float) ps[1] * a);
            }
        }
        // błysk przy ziemi
        Draw.glowDisc(b.x, b.y + 0.4, b.z, right[0] * 3.5, right[1] * 3.5, right[2] * 3.5,
                up[0] * 3.5, up[1] * 3.5, up[2] * 3.5, glow, 0.8f * a);
        Draw.glowRing(b.x, b.y + 0.05, b.z, 1.2 + (16 - b.life) * 0.25, 0.08, 40, glow, 0.9f * a);
    }

    /** Miękka świecąca aura krążąca wokół ciała (jak na filmie), kolor z palety. */
    private static void aura(Vec3d pos, float height, float inten, double[] right, double[] up) {
        Config c = Config.I;
        double cy = pos.y + height * 0.62;
        double R = 0.62 * c.auraSize;
        float a = inten * c.auraIntensity;
        int seg = 48;
        double[] hw = {0.22, 0.13, 0.07, 0.025};
        float[] al = {0.07f, 0.14f, 0.26f, 0.6f};
        double spin = time * 0.9;
        for (int L = 0; L < hw.length; L++) {
            for (int dy = -1; dy <= 1; dy += (L < 2 ? 1 : 2)) {
                double y = cy + dy * 0.07 * (L < 2 ? 1 : 0);
                for (int i = 0; i < seg; i++) {
                    double a0 = spin + i * TAU / seg, a1 = spin + (i + 1) * TAU / seg;
                    int col = Config.palette(c.auraColor, c.auraHue, (float) i / seg, time);
                    if (L == hw.length - 1) col = Config.lerp(col, 0xFFFFFF, 0.55f);
                    // jasność falująca po obwodzie (dwa jasne "płaty" jak na filmie)
                    float wave = 0.45f + 0.55f * (float) Math.pow(Math.max(0, Math.sin(a0 * 2 - time * 2.4)), 2);
                    float aa = al[L] * wave * a;
                    double r0 = R - hw[L], r1 = R + hw[L];
                    double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
                    Draw.vert(pos.x + c0 * r0, y, pos.z + s0 * r0, col, 0f);
                    Draw.vert(pos.x + c0 * R, y, pos.z + s0 * R, col, aa);
                    Draw.vert(pos.x + c1 * R, y, pos.z + s1 * R, col, aa);
                    Draw.vert(pos.x + c1 * r0, y, pos.z + s1 * r0, col, 0f);
                    Draw.vert(pos.x + c0 * R, y, pos.z + s0 * R, col, aa);
                    Draw.vert(pos.x + c0 * r1, y, pos.z + s0 * r1, col, 0f);
                    Draw.vert(pos.x + c1 * r1, y, pos.z + s1 * r1, col, 0f);
                    Draw.vert(pos.x + c1 * R, y, pos.z + s1 * R, col, aa);
                }
            }
        }
        // dwa świecące orby z ogonem
        for (int k = 0; k < 2; k++) {
            for (int j = 0; j < 7; j++) {
                double ang = time * 2.2 + k * Math.PI - j * 0.13;
                double x = pos.x + Math.cos(ang) * R, z = pos.z + Math.sin(ang) * R;
                double sz = 0.32 * (1.0 - j * 0.1) * c.auraSize;
                int col = Config.palette(c.auraColor, c.auraHue, k * 0.5f, time);
                float aa = (0.85f - j * 0.11f) * a;
                Draw.glowDisc(x, cy, z, right[0] * sz, right[1] * sz, right[2] * sz,
                        up[0] * sz, up[1] * sz, up[2] * sz, j == 0 ? Config.lerp(col, 0xFFFFFF, 0.5f) : col, aa);
            }
        }
    }

    /** Podświetlenie trafionego celu: kolorowa poświata dookoła jego bryły. */
    private static void flashes(ClientWorld world, float td, float inten) {
        Config c = Config.I;
        for (Fx.Flash f : Fx.FLASHES) {
            net.minecraft.util.math.Box b;
            if (f.fake) {
                FakePlayer fp = FakePlayer.I;
                if (fp == null || !fp.alive()) continue;
                b = new net.minecraft.util.math.Box(fp.x() - 0.3, fp.y(), fp.z() - 0.3, fp.x() + 0.3, fp.y() + 1.8, fp.z() + 0.3);
            } else {
                net.minecraft.entity.Entity e = world.getEntityById(f.entityId);
                if (e == null) continue;
                Vec3d d = e.getLerpedPos(td).subtract(e.getPos());
                b = e.getBoundingBox().offset(d);
            }
            float k = f.life / (float) f.max;
            int col = Config.palette(c.hitFlashColor, c.hitFlashHue, 0.5f, time);
            boxGlow(b, 0.03, col, 0.45f * k * inten);
            boxGlow(b, 0.10, col, 0.20f * k * inten);
        }
    }

    private static void boxGlow(net.minecraft.util.math.Box b, double e, int col, float a) {
        double x0 = b.minX - e, y0 = b.minY - e, z0 = b.minZ - e, x1 = b.maxX + e, y1 = b.maxY + e, z1 = b.maxZ + e;
        Draw.quad(x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, col, a);
        Draw.quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, col, a);
        Draw.quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, col, a);
        Draw.quad(x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, col, a);
        Draw.quad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, col, a);
        Draw.quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, col, a * 0.6f);
    }

    private static void groundRing(Vec3d pos, float inten) {
        double y = pos.y + 0.03;
        double r = 0.95 + 0.04 * Math.sin(time * 2.2);
        int a = Config.color(0.1f, time);
        int b = Config.color(0.9f, time);
        Draw.glowRing(pos.x, y, pos.z, r, 0.035, 56, a, 0.9f * inten);
        Draw.glowRing(pos.x, y + 0.002, pos.z, r * 1.38, 0.018, 56, b, 0.55f * inten);
        // rozchodząca się fala
        float f = (time * 0.45f) % 1f;
        double rr = 0.25 + 1.5 * f;
        float fa = (float) Math.pow(1f - f, 1.6) * inten;
        Draw.glowRing(pos.x, y + 0.004, pos.z, rr, 0.02, 48, Config.color(0.5f, time), 0.7f * fa);
    }

    private static void magicCircleLines(Vec3d pos, float inten) {
        double y = pos.y + 0.025;
        double R = 1.75;
        double rot = time * 0.35;
        int a = Config.color(0.0f, time);
        int b = Config.color(1.0f, time);
        Draw.glowRing(pos.x, y, pos.z, R, 0.012, 72, a, 0.8f * inten);
        Draw.glowRing(pos.x, y, pos.z, R * 0.78, 0.010, 72, b, 0.65f * inten);

        // kreseczki na zewnątrz
        for (int k = 0; k < 36; k++) {
            double ang = rot + k * TAU / 36;
            double c0 = Math.cos(ang), s0 = Math.sin(ang);
            double len = (k % 3 == 0) ? 0.14 : 0.07;
            Draw.flatLine(pos.x + c0 * R * 1.03, pos.z + s0 * R * 1.03,
                    pos.x + c0 * (R * 1.03 + len), pos.z + s0 * (R * 1.03 + len), y, 0.008, a, 0.7f * inten);
        }

        // dwa trójkąty (heksagram) obracające się w przeciwne strony
        for (int t = 0; t < 2; t++) {
            double dir = t == 0 ? 1 : -1;
            double base = rot * 1.4 * dir + t * Math.PI / 3;
            double rad = R * 0.7;
            for (int k = 0; k < 3; k++) {
                double a0 = base + k * TAU / 3, a1 = base + (k + 1) * TAU / 3;
                double x0 = pos.x + Math.cos(a0) * rad, z0 = pos.z + Math.sin(a0) * rad;
                double x1 = pos.x + Math.cos(a1) * rad, z1 = pos.z + Math.sin(a1) * rad;
                int col = Config.color(t, time);
                Draw.flatLine(x0, z0, x1, z1, y, 0.03, col, 0.12f * inten);
                Draw.flatLine(x0, z0, x1, z1, y + 0.001, 0.009, 0xFFFFFF, 0.85f * inten);
            }
        }
        // mały środkowy okrąg (jak w filmie)
        Draw.glowRing(pos.x, y, pos.z, 0.18 + 0.02 * Math.sin(time * 3), 0.01, 24,
                Config.color(0.5f, time), 0.8f * inten);
    }

    private static void magicCircleText(MatrixStack ms, VertexConsumerProvider.Immediate imm, TextRenderer tr,
                                        Vec3d pos, Vec3d cp) {
        String src = Config.I.circleText + "  ";
        int n = 16;
        double R = 1.75 * 0.89;
        double rot = -time * 0.35;
        for (int i = 0; i < n; i++) {
            String ch = String.valueOf(src.charAt(i % src.length()));
            if (ch.isBlank()) continue;
            double ang = rot + i * TAU / n;
            double px = pos.x + Math.cos(ang) * R;
            double pz = pos.z + Math.sin(ang) * R;
            ms.push();
            ms.translate(px - cp.x, pos.y + 0.03 - cp.y, pz - cp.z);
            ms.multiply(RotationAxis.POSITIVE_Y.rotation((float) (-ang - Math.PI / 2)));
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f));
            float s = 0.024f;
            ms.scale(s, -s, s);
            int rgb = Config.color((float) i / n, time);
            int argb = (0xD0 << 24) | rgb;
            float w = tr.getWidth(ch);
            tr.draw(ch, -w / 2f, -4f, argb, false, ms.peek().getPositionMatrix(), imm,
                    TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
            ms.pop();
        }
    }

    private static void swirls(Vec3d pos, float height, float inten) {
        Config c = Config.I;
        int n = c.ringCount;
        double cy = pos.y + height * 0.52;
        int seg = 30;
        double[] px = new double[seg + 1], py = new double[seg + 1], pz = new double[seg + 1];
        double[] ox = new double[seg + 1], oy = new double[seg + 1], oz = new double[seg + 1];
        float[] al = new float[seg + 1];
        float[] sv = new float[seg + 1];

        for (int k = 0; k < n; k++) {
            double dir = (k % 2 == 0) ? 1 : -1;
            double phase = k * TAU / n + time * (1.7 + 0.28 * k) * dir;
            double tilt = Math.toRadians(30 + 17 * k) * dir;
            double az = k * 1.3 + time * 0.4 * dir;
            double r = 0.82 + 0.07 * Math.sin(time * 2.0 + k * 1.7);
            double sinT = Math.sin(tilt), cosT = Math.cos(tilt);
            double sinA = Math.sin(az), cosA = Math.cos(az);
            double len = 2.0 + 0.2 * Math.sin(time + k);

            for (int i = 0; i <= seg; i++) {
                float s = (float) i / seg;
                double th = phase - dir * s * len;
                double ct = Math.cos(th), st = Math.sin(th);
                // punkt na pochylonej orbicie
                double lx = r * ct;
                double ly = -r * st * sinT;
                double lz = r * st * cosT;
                // radialny kierunek i normalna płaszczyzny
                double rdx = ct, rdy = -st * sinT, rdz = st * cosT;
                double nx = 0, ny = cosT, nz = sinT;
                // obrót wokół Y o az
                px[i] = pos.x + lx * cosA + lz * sinA;
                py[i] = cy + ly + 0.05 * Math.sin(time * 3 + k + s * 4);
                pz[i] = pos.z - lx * sinA + lz * cosA;
                double w = 0.2 * Math.pow(1 - s, 0.7);
                double rx = rdx * cosA + rdz * sinA, rz = -rdx * sinA + rdz * cosA;
                double nxr = nx * cosA + nz * sinA, nzr = -nx * sinA + nz * cosA;
                ox[i] = nxr * w + rx * w * 0.55;
                oy[i] = ny * w + rdy * w * 0.55;
                oz[i] = nzr * w + rz * w * 0.55;
                al[i] = (float) Math.pow(1 - s, 1.3);
                sv[i] = s;
            }

            float[][] passes = {{2.3f, 0.16f, 0f}, {1.25f, 0.45f, 0f}, {0.45f, 0.95f, 0.75f}};
            for (float[] ps : passes) {
                for (int i = 0; i < seg; i++) {
                    int col0 = Config.lerp(Config.color((sv[i] + k * 0.17f) % 1f, time), 0xFFFFFF, ps[2]);
                    int col1 = Config.lerp(Config.color((sv[i + 1] + k * 0.17f) % 1f, time), 0xFFFFFF, ps[2]);
                    float a0 = al[i] * ps[1] * inten, a1 = al[i + 1] * ps[1] * inten;
                    double m = ps[0];
                    Draw.vert(px[i] + ox[i] * m, py[i] + oy[i] * m, pz[i] + oz[i] * m, col0, a0);
                    Draw.vert(px[i] - ox[i] * m, py[i] - oy[i] * m, pz[i] - oz[i] * m, col0, a0);
                    Draw.vert(px[i + 1] - ox[i + 1] * m, py[i + 1] - oy[i + 1] * m, pz[i + 1] - oz[i + 1] * m, col1, a1);
                    Draw.vert(px[i + 1] + ox[i + 1] * m, py[i + 1] + oy[i + 1] * m, pz[i + 1] + oz[i + 1] * m, col1, a1);
                }
            }
        }
    }

    private static void playerStars(Vec3d pos, int seed, double[] right, double[] up, float inten) {
        int n = 14;
        for (int i = 0; i < n; i++) {
            int b = seed + i * 31;
            float sd = rnd(b);
            float life = (time * 0.33f + sd) % 1f;
            double ang = sd * TAU * 3 + time * 0.9 * (i % 2 == 0 ? 1 : -1);
            double rad = 0.45 + 0.85 * rnd(b + 1);
            double x = pos.x + Math.cos(ang) * rad;
            double z = pos.z + Math.sin(ang) * rad;
            double y = pos.y + 0.1 + life * 2.3;
            double size = 0.05 + 0.06 * rnd(b + 2);
            float tw = 0.65f + 0.35f * (float) Math.sin(time * 6 + sd * 20);
            float a = (float) Math.sin(life * Math.PI) * tw * inten;
            int col = Config.lerp(Config.color(rnd(b + 3), time), 0xFFFFFF, 0.35f);
            Draw.star(x, y, z, right, up, size, col, a);
        }
    }

    private static void ambientStars(Vec3d cp, double[] right, double[] up, float inten) {
        int n = 44;
        double box = 16.0;
        for (int i = 0; i < n; i++) {
            int b = 1000 + i * 17;
            double bx = rnd(b) * box + Math.sin(time * 0.25 + i) * 0.8;
            double by = rnd(b + 1) * box + time * 0.12;
            double bz = rnd(b + 2) * box + Math.cos(time * 0.22 + i * 0.7) * 0.8;
            double dx = bx - cp.x, dy = by - cp.y, dz = bz - cp.z;
            dx -= box * Math.round(dx / box);
            dy -= box * Math.round(dy / box);
            dz -= box * Math.round(dz / box);
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < 1.2) continue;
            float edge = (float) Math.min(1.0, (box * 0.5 - Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)))) / 3.0);
            if (edge <= 0) continue;
            float tw = 0.5f + 0.5f * (float) Math.sin(time * 3 + i * 1.9);
            double size = 0.05 + 0.05 * rnd(b + 3);
            int col = Config.lerp(Config.color(rnd(b + 4), time), 0xFFFFFF, 0.5f);
            Draw.star(cp.x + dx, cp.y + dy, cp.z + dz, right, up, size, col, edge * tw * 0.9f * inten);
        }
    }
}
