package pl.visuals;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;

/**
 * Pojedyncza "ciuchcia" ragdoll: postać gracza (ze skinem) wyrzucona w górę, spadająca,
 * odbijająca się, leżąca przez ustawiony czas i znikająca.
 */
public final class Ragdoll {
    private static final float S = 0.9375f / 16f; // piksel modelu -> bloki
    private static final float ORIGIN_Y = 15f;    // punkt obrotu (w pikselach od stóp)

    public final Identifier skin;
    public double x, y, z, px, py, pz;
    private double vx, vy, vz;
    private float yaw, pyaw;
    private float pitch, ppitch, roll, proll;
    private float wPitch, wRoll, wYaw;
    private int age;
    private int bounces;
    private boolean lying;
    private int lieTicks;
    private float flail = 1f, pflail = 1f;
    public boolean dead;
    private double gravity = 0.08;

    /** Tworzy ragdoll. (sx, sy, sz) = pozycja stóp. */
    public Ragdoll(Identifier skin, double sx, double sy, double sz, float yawDeg,
                   double vx, double vy, double vz, double gravity, float spin) {
        this(skin, sx, sy, sz, yawDeg, vx, vy, vz);
        this.gravity = gravity;
        this.wPitch *= spin;
        this.wRoll *= spin;
        this.wYaw *= spin;
    }

    public Ragdoll(Identifier skin, double sx, double sy, double sz, float yawDeg,
                   double vx, double vy, double vz) {
        this.skin = skin;
        this.x = px = sx;
        this.y = py = sy + 0.95;
        this.z = pz = sz;
        this.yaw = pyaw = yawDeg;
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        java.util.Random r = new java.util.Random();
        float sign = r.nextBoolean() ? 1 : -1;
        this.wPitch = sign * (16f + r.nextFloat() * 14f);
        this.wRoll = (r.nextFloat() - 0.5f) * 14f;
        this.wYaw = (r.nextFloat() - 0.5f) * 10f;
    }

    private static boolean solidAt(ClientWorld w, double x, double y, double z) {
        BlockPos bp = BlockPos.ofFloored(x, y, z);
        return !w.getBlockState(bp).getCollisionShape(w, bp).isEmpty();
    }

    /** Wysokość górnej powierzchni najbliższego bloku pod (x,y,z); bardzo mała wartość jeśli brak. */
    private static double groundY(ClientWorld w, double x, double y, double z) {
        BlockPos start = BlockPos.ofFloored(x, y, z);
        for (int dy = 0; dy < 12; dy++) {
            BlockPos bp = start.down(dy);
            VoxelShape shape = w.getBlockState(bp).getCollisionShape(w, bp);
            if (!shape.isEmpty()) {
                double top = bp.getY() + shape.getMax(Direction.Axis.Y);
                if (top <= y + 0.5) return top;
            }
        }
        return -1.0e9;
    }

    private static float wrap(float a) {
        a %= 360f;
        if (a > 180f) a -= 360f;
        if (a <= -180f) a += 360f;
        return a;
    }

    public void tick(ClientWorld world) {
        px = x; py = y; pz = z;
        ppitch = pitch; proll = roll; pyaw = yaw; pflail = flail;
        age++;

        if (!lying) {
            vy -= gravity;
            vy *= 0.985;
            if (Config.I.ragdollTrail && (age & 1) == 0) {
                world.addParticle(net.minecraft.particle.ParticleTypes.END_ROD, x, y, z, 0, 0, 0);
            }
            vx *= 0.992;
            vz *= 0.992;

            // zderzenie ze ścianą
            if (solidAt(world, x + vx + Math.signum(vx) * 0.3, y, z)) vx = -vx * 0.3;
            if (solidAt(world, x, y, z + vz + Math.signum(vz) * 0.3)) vz = -vz * 0.3;
            x += vx;
            y += vy;
            z += vz;

            pitch += wPitch;
            roll += wRoll;
            yaw += wYaw;

            double g = groundY(world, x, y, z);
            if (y - g <= 0.3 && vy < 0) {
                y = g + 0.3;
                if (bounces < 2 && -vy > 0.3) {
                    vy = -vy * 0.36;
                    vx *= 0.65;
                    vz *= 0.65;
                    wPitch *= 0.45f;
                    wRoll *= 0.45f;
                    wYaw *= 0.4f;
                    bounces++;
                } else {
                    lying = true;
                    vy = 0;
                    pitch = wrap(pitch);
                    roll = wrap(roll);
                    ppitch = pitch;
                    proll = roll;
                }
            }
            if (age > 220) dead = true; // spadł w pustkę
            flail = Math.max(0.35f, flail - 0.01f);
        } else {
            lieTicks++;
            double g = groundY(world, x, y, z);
            // lekkie wyślizgnięcie się
            vx *= 0.6;
            vz *= 0.6;
            if (!solidAt(world, x + vx + Math.signum(vx) * 0.3, y, z)) x += vx;
            if (!solidAt(world, x, y, z + vz + Math.signum(vz) * 0.3)) z += vz;

            float targetPitch = pitch > 0 ? 90f : -90f;
            pitch += (targetPitch - pitch) * 0.3f;
            roll += (0f - roll) * 0.3f;
            double targetY = g + 0.14;
            y += (targetY - y) * 0.4;
            flail += (0.15f - flail) * 0.2f;

            int total = Math.round(Config.I.ragdollLie * 20f) + 10;
            if (lieTicks >= total) dead = true;
        }
    }

    /** Przezroczystość: 1 normalnie, zanika w ostatnich 10 tickach leżenia. */
    private float alpha(float td) {
        if (!lying) return 1f;
        int total = Math.round(Config.I.ragdollLie * 20f);
        float t = lieTicks + td - total;
        if (t <= 0) return 1f;
        return Math.max(0f, 1f - t / 10f);
    }

    public void render(MatrixStack ms, VertexConsumer vc, double camX, double camY, double camZ,
                       float td, int light) {
        float a = alpha(td);
        if (a <= 0.02f) return;
        double rx = px + (x - px) * td, ry = py + (y - py) * td, rz = pz + (z - pz) * td;
        float rPitch = ppitch + (pitch - ppitch) * td;
        float rRoll = proll + (roll - proll) * td;
        float rYaw = pyaw + (yaw - pyaw) * td;
        float fl = pflail + (flail - pflail) * td;
        float tt = (age + td) * 0.5f;

        float armX = (float) Math.sin(tt) * 70f * fl;
        float armXr = (float) Math.sin(tt + 2.2f) * 70f * fl;
        float legX = (float) Math.sin(tt + 1.0f) * 40f * fl;
        float legXl = (float) Math.sin(tt + 3.3f) * 40f * fl;
        float splay = 25f + 25f * fl;

        renderPose(ms, vc, camX, camY, camZ, rx, ry, rz, rYaw, rPitch, rRoll, a, light,
                armX, armXr, legX, legXl, splay);
    }

    /** Rysuje model w podanej pozie (używane też przez fake playera). */
    public void renderPose(MatrixStack ms, VertexConsumer vc, double camX, double camY, double camZ,
                           double rx, double ry, double rz, float rYaw, float rPitch, float rRoll,
                           float a, int light, float armX, float armXr, float legX, float legXl, float splay) {
        ms.push();
        ms.translate(rx - camX, ry - camY, rz - camZ);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-rYaw));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rPitch));
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rRoll));

        // głowa + hat
        part(ms, vc, a, light, 0, 0, -4, 24, -4, 4, 32, 4, 0, 0, 28, 0, 0, 0);
        part(ms, vc, a, light, 32, 0, -4, 24, -4, 4, 32, 4, 0.5f, 0, 28, 0, 0, 0);
        // tułów + jacket
        part(ms, vc, a, light, 16, 16, -4, 12, -2, 4, 24, 2, 0, 0, 18, 0, 0, 0);
        part(ms, vc, a, light, 16, 32, -4, 12, -2, 4, 24, 2, 0.25f, 0, 18, 0, 0, 0);
        // prawa ręka (-X)
        part(ms, vc, a, light, 40, 16, -8, 12, -2, -4, 24, 2, 0, -6, 22, armX, -splay, 0);
        part(ms, vc, a, light, 40, 32, -8, 12, -2, -4, 24, 2, 0.25f, -6, 22, armX, -splay, 0);
        // lewa ręka (+X)
        part(ms, vc, a, light, 32, 48, 4, 12, -2, 8, 24, 2, 0, 6, 22, armXr, splay, 0);
        part(ms, vc, a, light, 48, 48, 4, 12, -2, 8, 24, 2, 0.25f, 6, 22, armXr, splay, 0);
        // prawa noga
        part(ms, vc, a, light, 0, 16, -4, 0, -2, 0, 12, 2, 0, -2, 12, legX, -splay * 0.3f, 0);
        part(ms, vc, a, light, 0, 32, -4, 0, -2, 0, 12, 2, 0.25f, -2, 12, legX, -splay * 0.3f, 0);
        // lewa noga
        part(ms, vc, a, light, 16, 48, 0, 0, -2, 4, 12, 2, 0, 2, 12, legXl, splay * 0.3f, 0);
        part(ms, vc, a, light, 0, 48, 0, 0, -2, 4, 12, 2, 0.25f, 2, 12, legXl, splay * 0.3f, 0);

        ms.pop();
    }

    /**
     * Rysuje prostopadłościan o rogach (x0,y0,z0)-(x1,y1,z1) w pikselach modelu,
     * z siatką UV zaczynającą się w (u,v), obrócony wokół punktu (pivotX, pivotY).
     */
    private void part(MatrixStack ms, VertexConsumer vc, float a, int light,
                      int u, int v, float x0, float y0, float z0, float x1, float y1, float z1,
                      float inflate, float pivotX, float pivotY, float rotX, float rotZ, float unused) {
        ms.push();
        ms.translate(pivotX * S, (pivotY - ORIGIN_Y) * S, 0);
        if (rotX != 0) ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotX));
        if (rotZ != 0) ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotZ));

        float w = x1 - x0, h = y1 - y0, d = z1 - z0;
        float X0 = (x0 - inflate - pivotX) * S, X1 = (x1 + inflate - pivotX) * S;
        float Y0 = (y0 - inflate - pivotY) * S, Y1 = (y1 + inflate - pivotY) * S;
        float Z0 = (z0 - inflate) * S, Z1 = (z1 + inflate) * S;

        MatrixStack.Entry e = ms.peek();
        Matrix4f m = e.getPositionMatrix();
        int ai = (int) (a * 255f);

        // prawy bok (x = X0)
        quad(vc, e, m, ai, light, -1, 0, 0,
                X0, Y1, Z0, u, v + d, X0, Y1, Z1, u + d, v + d,
                X0, Y0, Z1, u + d, v + d + h, X0, Y0, Z0, u, v + d + h);
        // przód (z = Z1)
        quad(vc, e, m, ai, light, 0, 0, 1,
                X0, Y1, Z1, u + d, v + d, X1, Y1, Z1, u + d + w, v + d,
                X1, Y0, Z1, u + d + w, v + d + h, X0, Y0, Z1, u + d, v + d + h);
        // lewy bok (x = X1)
        quad(vc, e, m, ai, light, 1, 0, 0,
                X1, Y1, Z1, u + d + w, v + d, X1, Y1, Z0, u + 2 * d + w, v + d,
                X1, Y0, Z0, u + 2 * d + w, v + d + h, X1, Y0, Z1, u + d + w, v + d + h);
        // tył (z = Z0)
        quad(vc, e, m, ai, light, 0, 0, -1,
                X1, Y1, Z0, u + 2 * d + w, v + d, X0, Y1, Z0, u + 2 * d + 2 * w, v + d,
                X0, Y0, Z0, u + 2 * d + 2 * w, v + d + h, X1, Y0, Z0, u + 2 * d + w, v + d + h);
        // góra (y = Y1)
        quad(vc, e, m, ai, light, 0, 1, 0,
                X0, Y1, Z0, u + d, v, X1, Y1, Z0, u + d + w, v,
                X1, Y1, Z1, u + d + w, v + d, X0, Y1, Z1, u + d, v + d);
        // dół (y = Y0)
        quad(vc, e, m, ai, light, 0, -1, 0,
                X0, Y0, Z1, u + d + w, v, X1, Y0, Z1, u + d + 2 * w, v,
                X1, Y0, Z0, u + d + 2 * w, v + d, X0, Y0, Z0, u + d + w, v + d);

        ms.pop();
    }

    private static void quad(VertexConsumer vc, MatrixStack.Entry e, Matrix4f m, int a, int light,
                             float nx, float ny, float nz,
                             float ax, float ay, float az, float au, float av,
                             float bx, float by, float bz, float bu, float bv,
                             float cx, float cy, float cz, float cu, float cv,
                             float dx, float dy, float dz, float du, float dv) {
        vtx(vc, e, m, a, light, nx, ny, nz, ax, ay, az, au, av);
        vtx(vc, e, m, a, light, nx, ny, nz, bx, by, bz, bu, bv);
        vtx(vc, e, m, a, light, nx, ny, nz, cx, cy, cz, cu, cv);
        vtx(vc, e, m, a, light, nx, ny, nz, dx, dy, dz, du, dv);
    }

    private static void vtx(VertexConsumer vc, MatrixStack.Entry e, Matrix4f m, int a, int light,
                            float nx, float ny, float nz, float x, float y, float z, float u, float v) {
        vc.vertex(m, x, y, z)
                .color(255, 255, 255, a)
                .texture(u / 64f, v / 64f)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(e, nx, ny, nz);
    }
}
