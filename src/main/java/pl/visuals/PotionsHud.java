package pl.visuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.List;

/** Mały HUD "Potions" w lewym górnym rogu (jak w filmie). */
public final class PotionsHud {
    private PotionsHud() {}

    public static void render(DrawContext ctx, MinecraftClient mc) {
        List<StatusEffectInstance> effects = new ArrayList<>(mc.player.getStatusEffects());
        if (effects.isEmpty()) return;

        TextRenderer tr = mc.textRenderer;
        float t = VisualsRenderer.time();
        int accA = Config.color(0f, t);
        int accB = Config.color(1f, t);

        List<String> names = new ArrayList<>();
        List<String> times = new ArrayList<>();
        int maxW = tr.getWidth("Potions");
        for (StatusEffectInstance e : effects) {
            String name = e.getEffectType().value().getName().getString() + " " + (e.getAmplifier() + 1);
            String time;
            if (e.isInfinite()) {
                time = "∞";
            } else {
                int sec = e.getDuration() / 20;
                time = (sec / 60) + ":" + String.format("%02d", sec % 60);
            }
            names.add(name);
            times.add(time);
            maxW = Math.max(maxW, tr.getWidth(name) + 10 + tr.getWidth(time));
        }

        int x = 6, y = 6;
        int w = maxW + 14;
        int h = 18 + effects.size() * 11 + 4;
        ctx.fill(x, y, x + w, y + h, 0xB0090912);
        ctx.fill(x, y, x + 2, y + h, 0xFF000000 | accA);
        ctx.fill(x + 2, y + 15, x + w, y + 16, 0x40000000 | accB);
        ctx.drawText(tr, "Potions", x + 7, y + 4, 0xFFFFFFFF, true);
        for (int i = 0; i < names.size(); i++) {
            int ly = y + 20 + i * 11;
            ctx.drawText(tr, names.get(i), x + 7, ly, 0xFF000000 | accA, false);
            ctx.drawText(tr, times.get(i), x + w - 7 - tr.getWidth(times.get(i)), ly, 0xFFC8C8DC, false);
        }
    }
}
