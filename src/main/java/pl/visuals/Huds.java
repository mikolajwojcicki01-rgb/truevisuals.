package pl.visuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** HUD-y: Target Render, Combo, Cooldowns (jak w Soup). */
public final class Huds {
    private Huds() {}

    private static int accA() {
        return Config.color(0f, VisualsRenderer.time());
    }

    private static int accB() {
        return Config.color(1f, VisualsRenderer.time());
    }

    private static void rr(DrawContext c, int x1, int y1, int x2, int y2, int r, int col) {
        r = Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        c.fill(x1, y1 + r, x2, y2 - r, col);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            c.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, col);
            c.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, col);
        }
    }

    // ------------------------------------------------------------------ Target Render
    public static void target(DrawContext c, MinecraftClient mc) {
        if (mc.world == null) return;
        long age = mc.world.getTime() - Fx.targetTime;
        if (age > 80 || Fx.targetName.isEmpty()) return;
        TextRenderer tr = mc.textRenderer;

        float fade = age > 60 ? 1f - (age - 60) / 20f : 1f;
        int al = (int) (fade * 255) << 24;
        int W = c.getScaledWindowWidth(), H = c.getScaledWindowHeight();
        int w = 150, h = 44;
        int x = W / 2 - w / 2 + 10, y = H / 2 + 34;

        rr(c, x - 1, y - 1, x + w + 1, y + h + 1, 9, ((int) (fade * 140) << 24) | (accA() & 0xFFFFFF));
        rr(c, x, y, x + w, y + h, 8, ((int) (fade * 215) << 24) | 0x0A0F1A);

        int faceX = x + 6, faceY = y + 6;
        if (Fx.targetSkin != null) {
            PlayerSkinDrawer.draw(c, Fx.targetSkin, faceX, faceY, 32);
        } else {
            rr(c, faceX, faceY, faceX + 32, faceY + 32, 5, ((int) (fade * 120) << 24) | 0xFFFFFF);
        }

        int tx = x + 44;
        c.drawText(tr, Fx.targetName, tx, y + 7, al | 0xFFFFFF, true);
        float frac = Math.max(0f, Math.min(1f, Fx.targetAnim / Fx.targetMax));
        int bw = w - 44 - 8;
        rr(c, tx, y + 20, tx + bw, y + 28, 4, ((int) (fade * 160) << 24) | 0x000000);
        int fw = (int) (bw * frac);
        if (fw > 1) {
            for (int i = 0; i < fw; i += 2) {
                int col = Config.lerp(accA(), accB(), i / (float) bw);
                c.fill(tx + i, y + 20, tx + Math.min(fw, i + 2), y + 28, al | col);
            }
        }
        String hp = String.format("%.1f", Math.max(0f, Fx.targetHealth)) + " HP";
        c.drawText(tr, hp, tx, y + 31, al | 0xC8D2E6, false);
    }

    // ------------------------------------------------------------------ Combo
    public static void combo(DrawContext c, MinecraftClient mc) {
        if (Fx.combo <= 0) return;
        TextRenderer tr = mc.textRenderer;
        int W = c.getScaledWindowWidth(), H = c.getScaledWindowHeight();
        float pop = 1f + Fx.comboPop * 0.05f;
        String s = Fx.combo + " combo";
        int x = W / 2 + 24, y = H / 2 - 26;
        c.getMatrices().push();
        c.getMatrices().translate(x, y, 0);
        c.getMatrices().scale(1.4f * pop, 1.4f * pop, 1f);
        int col = Config.lerp(accA(), accB(), Math.min(1f, Fx.combo / 12f));
        c.drawText(tr, s, 0, 0, 0xFF000000 | col, true);
        c.getMatrices().pop();
    }

    // ------------------------------------------------------------------ Cooldowns
    public static void cooldowns(DrawContext c, MinecraftClient mc, float td) {
        if (mc.player == null) return;
        var cm = mc.player.getItemCooldownManager();
        List<ItemStack> active = new ArrayList<>();
        Set<Item> seen = new HashSet<>();
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack st = mc.player.getInventory().getStack(i);
            if (st.isEmpty() || !seen.add(st.getItem())) continue;
            if (cm.isCoolingDown(st)) active.add(st);
        }
        if (active.isEmpty()) return;

        TextRenderer tr = mc.textRenderer;
        int W = c.getScaledWindowWidth();
        int w = 138;
        int h = 22 + active.size() * 17 + 4;
        int x = W - w - 8, y = 40;
        rr(c, x - 1, y - 1, x + w + 1, y + h + 1, 9, 0x60000000 | (accA() & 0xFFFFFF));
        rr(c, x, y, x + w, y + h, 8, 0xD00A0F1A);
        c.drawText(tr, "Cooldowns", x + 9, y + 7, 0xFFFFFFFF, true);
        int ry = y + 22;
        for (ItemStack st : active) {
            float p = Math.max(0f, Math.min(1f, cm.getCooldownProgress(st, td)));
            c.drawItem(st, x + 6, ry - 1);
            String name = st.getName().getString();
            if (name.length() > 14) name = name.substring(0, 13) + ".";
            c.drawText(tr, name, x + 26, ry + 4, 0xFFE6EAF5, false);
            int bx = x + w - 40, bw = 32;
            rr(c, bx, ry + 3, bx + bw, ry + 12, 4, 0xFF1C2638);
            int fw = (int) (bw * p);
            if (fw > 1) rr(c, bx, ry + 3, bx + fw, ry + 12, 4, 0xFF000000 | accA());
            String pct = (int) (p * 100) + "%";
            c.drawText(tr, pct, bx + bw / 2 - tr.getWidth(pct) / 2, ry + 4, 0xFFFFFFFF, false);
            ry += 17;
        }
    }

    // ------------------------------------------------------------------ Keystrokes + CPS
    private static void key(DrawContext c, TextRenderer tr, int x, int y, int w, int h, String label, boolean down) {
        rr(c, x, y, x + w, y + h, 5, down ? (0xE0000000 | accA()) : 0x90090C16);
        c.drawText(tr, label, x + w / 2 - tr.getWidth(label) / 2, y + h / 2 - 4, down ? 0xFF05070C : 0xFFE6EAF5, false);
    }

    public static void keystrokes(DrawContext c, MinecraftClient mc) {
        TextRenderer tr = mc.textRenderer;
        int H = c.getScaledWindowHeight();
        int s = 22, g = 2;
        int x = 8, y = H / 2 - 36;
        key(c, tr, x + s + g, y, s, s, "W", mc.options.forwardKey.isPressed());
        key(c, tr, x, y + s + g, s, s, "A", mc.options.leftKey.isPressed());
        key(c, tr, x + s + g, y + s + g, s, s, "S", mc.options.backKey.isPressed());
        key(c, tr, x + 2 * (s + g), y + s + g, s, s, "D", mc.options.rightKey.isPressed());
        int w2 = (3 * s + 2 * g - g) / 2;
        key(c, tr, x, y + 2 * (s + g), w2, s, "LMB " + Fx.leftCps(), mc.options.attackKey.isPressed());
        key(c, tr, x + w2 + g, y + 2 * (s + g), w2, s, "RMB " + Fx.rightCps(), mc.options.useKey.isPressed());
        key(c, tr, x, y + 3 * (s + g), 3 * s + 2 * g, 14, "SPACE", mc.options.jumpKey.isPressed());
    }

    // ------------------------------------------------------------------ FPS / ping / koordynaty
    public static void info(DrawContext c, MinecraftClient mc, boolean showInfo, boolean showCoords) {
        TextRenderer tr = mc.textRenderer;
        List<String> lines = new ArrayList<>();
        if (showInfo) {
            int ping = 0;
            var e = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (e != null) ping = e.getLatency();
            lines.add(mc.getCurrentFps() + " FPS   " + ping + " ms");
        }
        if (showCoords) {
            lines.add(String.format("XYZ %.0f %.0f %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        }
        if (lines.isEmpty()) return;
        int H = c.getScaledWindowHeight();
        int w = 0;
        for (String l : lines) w = Math.max(w, tr.getWidth(l));
        int h = lines.size() * 11 + 8;
        int x = 6, y = H - h - 40;
        rr(c, x - 1, y - 1, x + w + 17, y + h + 1, 7, 0x50000000 | (accA() & 0xFFFFFF));
        rr(c, x, y, x + w + 16, y + h, 6, 0xC00A0F1A);
        for (int i = 0; i < lines.size(); i++) {
            c.drawText(tr, lines.get(i), x + 8, y + 5 + i * 11, 0xFFE6EAF5, false);
        }
    }

    // ------------------------------------------------------------------ Armor
    public static void armor(DrawContext c, MinecraftClient mc) {
        TextRenderer tr = mc.textRenderer;
        net.minecraft.entity.EquipmentSlot[] slots = {
                net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
                net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET,
                net.minecraft.entity.EquipmentSlot.MAINHAND};
        int W = c.getScaledWindowWidth(), H = c.getScaledWindowHeight();
        int x = W - 62, y = H / 2 - 50;
        int row = 0;
        for (var slot : slots) {
            ItemStack st = mc.player.getEquippedStack(slot);
            if (st.isEmpty()) continue;
            int yy = y + row * 20;
            rr(c, x - 2, yy - 2, x + 58, yy + 18, 5, 0x90090C16);
            c.drawItem(st, x, yy);
            if (st.isDamageable()) {
                int left = st.getMaxDamage() - st.getDamage();
                float f = left / (float) st.getMaxDamage();
                int col = f > 0.5f ? 0xFF7CFF8A : (f > 0.2f ? 0xFFFFD24A : 0xFFFF5A5A);
                c.drawText(tr, String.valueOf(left), x + 20, yy + 4, col, false);
            }
            row++;
        }
    }

    // ------------------------------------------------------------------ Napis ZABÓJSTWO
    public static void killTitle(DrawContext c, MinecraftClient mc) {
        if (Fx.killTitleTicks <= 0) return;
        TextRenderer tr = mc.textRenderer;
        float age = 50 - Fx.killTitleTicks;
        float in = Math.min(1f, age / 4f);
        float out = Math.min(1f, Fx.killTitleTicks / 10f);
        float a = Math.min(in, out);
        int al = ((int) (a * 255)) << 24;
        int W = c.getScaledWindowWidth(), H = c.getScaledWindowHeight();
        String t = "ZABÓJSTWO";
        float sc = 2.6f * (1f + (1f - in) * 0.6f);
        c.getMatrices().push();
        c.getMatrices().translate(W / 2f, H * 0.27f, 0);
        c.getMatrices().scale(sc, sc, 1f);
        c.drawText(tr, t, -tr.getWidth(t) / 2, 0, al | 0xFF3B2F, true);
        c.getMatrices().pop();
        if (!Fx.killTitleName.isEmpty()) {
            String sub = Fx.killTitleName + "  +1";
            c.drawText(tr, sub, W / 2 - tr.getWidth(sub) / 2, (int) (H * 0.27f) + 28, al | 0xFFD76A, true);
        }
    }
}
