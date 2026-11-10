package pl.visuals;

import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix4f;

/**
 * Niskopoziomowe rysowanie świecących kształtów w świecie (warstwa lightning – addytywna, bez tekstury).
 * Wszystkie współrzędne podajemy w świecie, klasa sama odejmuje pozycję kamery.
 */
public final class Draw {
    private static VertexConsumer vc;
    private static Matrix4f mat;
    private static double cx, cy, cz;

    private Draw() {}

    public static void begin(VertexConsumer consumer, Matrix4f matrix, double camX, double camY, double camZ) {
        vc = consumer;
        mat = matrix;
        cx = camX;
        cy = camY;
        cz = camZ;
    }

    public static void vert(double x, double y, double z, int rgb, float a) {
        int ai = (int) (Math.max(0f, Math.min(1f, a)) * 255f);
        vc.vertex(mat, (float) (x - cx), (float) (y - cy), (float) (z - cz))
                .color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, ai);
    }

    public static void quad(double x1, double y1, double z1, double x2, double y2, double z2,
                            double x3, double y3, double z3, double x4, double y4, double z4,
                            int rgb, float a) {
        vert(x1, y1, z1, rgb, a);
        vert(x2, y2, z2, rgb, a);
        vert(x3, y3, z3, rgb, a);
        vert(x4, y4, z4, rgb, a);
    }

    /** Pierścień płaski w XZ (opaska od r0 do r1) z płynnym zanikiem do krawędzi. */
    public static void flatRing(double x, double y, double z, double r0, double r1, int segments,
                                int rgb, float alpha, double rotation) {
        for (int i = 0; i < segments; i++) {
            double a0 = rotation + i * Math.PI * 2 / segments;
            double a1 = rotation + (i + 1) * Math.PI * 2 / segments;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            quad(x + c0 * r0, y, z + s0 * r0, x + c0 * r1, y, z + s0 * r1,
                    x + c1 * r1, y, z + s1 * r1, x + c1 * r0, y, z + s1 * r0, rgb, alpha);
        }
    }

    /** Świecący pierścień: środek jasny, krawędzie zanikają (kilka warstw). */
    public static void glowRing(double x, double y, double z, double radius, double halfWidth,
                                int segments, int rgb, float alpha) {
        flatRing(x, y, z, radius - halfWidth * 2.6, radius + halfWidth * 2.6, segments, rgb, alpha * 0.10f, 0);
        flatRing(x, y, z, radius - halfWidth * 1.6, radius + halfWidth * 1.6, segments, rgb, alpha * 0.20f, 0);
        flatRing(x, y, z, radius - halfWidth, radius + halfWidth, segments, rgb, alpha * 0.55f, 0);
        flatRing(x, y, z, radius - halfWidth * 0.35, radius + halfWidth * 0.35, segments, 0xFFFFFF, alpha * 0.95f, 0);
    }

    /** Płaska linia w XZ. */
    public static void flatLine(double x1, double z1, double x2, double z2, double y, double w,
                                int rgb, float alpha) {
        double dx = x2 - x1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-6) return;
        double nx = -dz / len * w, nz = dx / len * w;
        quad(x1 + nx, y, z1 + nz, x1 - nx, y, z1 - nz, x2 - nx, y, z2 - nz, x2 + nx, y, z2 + nz, rgb, alpha);
    }

    /** Okrągły "glow" zwrócony do kamery: środek jasny, brzeg przezroczysty. */
    public static void glowDisc(double x, double y, double z,
                                double rx, double ry, double rz, double ux, double uy, double uz,
                                int rgb, float alpha) {
        int n = 8;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            // środek
            vert(x, y, z, rgb, alpha);
            // brzeg (przezroczysty)
            vert(x + rx * c0 + ux * s0, y + ry * c0 + uy * s0, z + rz * c0 + uz * s0, rgb, 0f);
            vert(x + rx * c1 + ux * s1, y + ry * c1 + uy * s1, z + rz * c1 + uz * s1, rgb, 0f);
            vert(x + rx * c1 + ux * s1, y + ry * c1 + uy * s1, z + rz * c1 + uz * s1, rgb, 0f);
        }
    }

    /**
     * Gwiazdka 4-ramienna (dwa skrzyżowane romby + obrócony mały) + halo.
     * right/up – wektory kamery, size – promień w blokach.
     */
    public static void star(double x, double y, double z, double[] right, double[] up,
                            double size, int rgb, float alpha) {
        double rx = right[0], ry = right[1], rz = right[2];
        double ux = up[0], uy = up[1], uz = up[2];
        double th = size * 0.16;
        // halo
        glowDisc(x, y, z, rx * size * 1.7, ry * size * 1.7, rz * size * 1.7,
                ux * size * 1.7, uy * size * 1.7, uz * size * 1.7, rgb, alpha * 0.35f);
        // poziomy romb
        diamond(x, y, z, rx * size, ry * size, rz * size, ux * th, uy * th, uz * th, rgb, alpha);
        // pionowy romb
        diamond(x, y, z, ux * size, uy * size, uz * size, rx * th, ry * th, rz * th, rgb, alpha);
        // mały obrócony o 45°
        double k = 0.7071 * size * 0.55;
        double ax = (rx + ux) * k, ay = (ry + uy) * k, az = (rz + uz) * k;
        double bx = (rx - ux) * k, by = (ry - uy) * k, bz = (rz - uz) * k;
        diamond(x, y, z, ax, ay, az, bx * 0.22, by * 0.22, bz * 0.22, 0xFFFFFF, alpha * 0.8f);
        diamond(x, y, z, bx, by, bz, ax * 0.22, ay * 0.22, az * 0.22, 0xFFFFFF, alpha * 0.8f);
        // biały rdzeń
        glowDisc(x, y, z, rx * size * 0.35, ry * size * 0.35, rz * size * 0.35,
                ux * size * 0.35, uy * size * 0.35, uz * size * 0.35, 0xFFFFFF, alpha);
    }

    private static void diamond(double x, double y, double z, double ax, double ay, double az,
                                double bx, double by, double bz, int rgb, float alpha) {
        vert(x + ax, y + ay, z + az, rgb, 0f);
        vert(x + bx, y + by, z + bz, rgb, alpha);
        vert(x - ax, y - ay, z - az, rgb, 0f);
        vert(x - bx, y - by, z - bz, rgb, alpha);
    }
}
