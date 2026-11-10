package pl.visuals;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Menu moda: tło void z gwiazdami, animacja otwierania/zamykania, zakładki, przełączniki i suwaki.
 */
public class VisualsScreen extends Screen {
    private static final int PW = 480, PH = 346;
    private static final String[] TABS = {"Visuals", "Kolory", "Moduły", "Walka", "Zabicie", "Target i Fake", "Ragdoll", "Aura & Hit", "Info"};

    // ---- gwiazdy ----
    private static final int STARS = 260;
    private final float[] sx = new float[STARS], sy = new float[STARS], sz = new float[STARS], sph = new float[STARS];
    private final int[] scol = new int[STARS];

    // ---- stan ----
    private final long openMs = Util.getMeasuringTimeMs();
    private long lastMs = openMs;
    private boolean closing;
    private long closeMs;
    private int tab;
    private float tabInd;
    private float curScale = 1f, curCx, curCy;
    private int panelX, panelY;
    private final List<Row> rows = new ArrayList<>();
    private Row dragging;

    public VisualsScreen() {
        super(Text.literal("Visuals"));
        Random r = new Random(1337);
        int[] pal = {0xFFFFFF, 0xFFFFFF, 0xBFE8FF, 0xD9C8FF, 0xFFD9F2, 0x9FFFEF};
        for (int i = 0; i < STARS; i++) {
            sx[i] = r.nextFloat();
            sy[i] = r.nextFloat();
            sz[i] = 0.15f + r.nextFloat() * 0.85f;
            sph[i] = r.nextFloat() * 6.28f;
            scol[i] = pal[r.nextInt(pal.length)];
        }
        buildRows();
    }

    // =====================================================================
    //  Narzędzia
    // =====================================================================

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float easeOutBack(float p) {
        float c1 = 1.70158f, c3 = c1 + 1f;
        float q = p - 1f;
        return 1f + c3 * q * q * q + c1 * q * q;
    }

    private static float easeOutCubic(float p) {
        float q = 1f - p;
        return 1f - q * q * q;
    }

    /** rgb + alfa (0..1) -> ARGB. */
    private static int ca(int rgb, float a) {
        int ai = (int) (clamp(a, 0f, 1f) * 255f);
        return (ai << 24) | (rgb & 0xFFFFFF);
    }

    private static void rr(DrawContext c, int x1, int y1, int x2, int y2, int r, int col) {
        r = Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        if (r <= 0) {
            c.fill(x1, y1, x2, y2, col);
            return;
        }
        c.fill(x1, y1 + r, x2, y2 - r, col);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            c.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, col);
            c.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, col);
        }
    }

    /** Miękka poświata (kółko z wieloma warstwami). */
    private static void glow(DrawContext c, int cx, int cy, int radius, int rgb, float maxA) {
        int layers = 10;
        float a = maxA / layers;
        for (int k = 0; k < layers; k++) {
            float r = radius * (1f - k / (float) layers);
            int col = ca(rgb, a);
            int ri = (int) r;
            for (int dy = -ri; dy < ri; dy += 2) {
                int half = (int) Math.sqrt(Math.max(0, ri * ri - dy * dy));
                c.fill(cx - half, cy + dy, cx + half, cy + dy + 2, col);
            }
        }
    }

    private void txt(DrawContext c, String s, int x, int y, int rgb, float a, boolean shadow) {
        if (a < 0.04f) return;
        c.drawText(textRenderer, s, x, y, ca(rgb, a), shadow);
    }

    private void txtC(DrawContext c, String s, int cx, int y, int rgb, float a) {
        txt(c, s, cx - textRenderer.getWidth(s) / 2, y, rgb, a, true);
    }

    // =====================================================================
    //  Wiersze menu
    // =====================================================================

    private abstract static class Row {
        int x, y, w, h = 22;
        float hover;

        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        abstract void draw(VisualsScreen s, DrawContext c, int mx, int my, float a, float dt, int accA, int accB);

        boolean click(VisualsScreen s, double mx, double my, int button) {
            return false;
        }

        void drag(double mx) {}

        void updateHover(boolean over, float dt) {
            hover += ((over ? 1f : 0f) - hover) * clamp(dt * 14f, 0f, 1f);
        }
    }

    private static final class ToggleRow extends Row {
        final String label;
        final BooleanSupplier get;
        final Consumer<Boolean> set;
        float on = -1;

        ToggleRow(String label, BooleanSupplier get, Consumer<Boolean> set) {
            this.label = label;
            this.get = get;
            this.set = set;
        }

        @Override
        void draw(VisualsScreen s, DrawContext c, int mx, int my, float a, float dt, int accA, int accB) {
            updateHover(hit(mx, my), dt);
            float target = get.getAsBoolean() ? 1f : 0f;
            if (on < 0) on = target;
            on += (target - on) * clamp(dt * 14f, 0f, 1f);
            rr(c, x, y, x + w, y + h, 6, ca(0xFFFFFF, (0.03f + 0.06f * hover) * a));
            s.txt(c, label, x + 9, y + 7, 0xE8E8F5, a, false);
            int sw = 30, sh = 14;
            int sx0 = x + w - sw - 9, sy0 = y + (h - sh) / 2;
            int bg = Config.lerp(0x2A2A3C, accA, on);
            rr(c, sx0, sy0, sx0 + sw, sy0 + sh, 7, ca(bg, a));
            int kx = sx0 + 2 + (int) ((sw - sh) * on);
            rr(c, kx, sy0 + 2, kx + sh - 4, sy0 + sh - 2, 5, ca(0xFFFFFF, a));
        }

        @Override
        boolean click(VisualsScreen s, double mx, double my, int button) {
            set.accept(!get.getAsBoolean());
            Config.save();
            return true;
        }
    }

    private static final class SliderRow extends Row {
        final String label;
        final float min, max, step;
        final DoubleSupplier get;
        final DoubleConsumer set;
        final String fmt;
        int tx, tw;

        SliderRow(String label, float min, float max, float step, String fmt, DoubleSupplier get, DoubleConsumer set) {
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.fmt = fmt;
            this.get = get;
            this.set = set;
        }

        @Override
        void draw(VisualsScreen s, DrawContext c, int mx, int my, float a, float dt, int accA, int accB) {
            updateHover(hit(mx, my), dt);
            rr(c, x, y, x + w, y + h, 6, ca(0xFFFFFF, (0.03f + 0.06f * hover) * a));
            s.txt(c, label, x + 9, y + 7, 0xE8E8F5, a, false);
            String val = String.format(fmt, (float) get.getAsDouble());
            int vw = s.textRenderer.getWidth(val);
            s.txt(c, val, x + w - 9 - vw, y + 7, 0xFFFFFF, a * 0.9f, false);
            tx = x + (int) (w * 0.40f);
            tw = w - (int) (w * 0.40f) - vw - 22;
            int ty = y + h / 2;
            rr(c, tx, ty - 2, tx + tw, ty + 2, 2, ca(0x2A2A3C, a));
            float f = clamp((float) ((get.getAsDouble() - min) / (max - min)), 0f, 1f);
            int fw = (int) (tw * f);
            for (int i = 0; i < fw; i += 2) {
                c.fill(tx + i, ty - 2, tx + Math.min(fw, i + 2), ty + 2,
                        ca(Config.lerp(accA, accB, i / (float) Math.max(1, tw)), a));
            }
            int kx = tx + fw;
            glowDot(c, kx, ty, accA, a * (0.25f + 0.2f * hover));
            rr(c, kx - 4, ty - 4, kx + 4, ty + 4, 4, ca(0xFFFFFF, a));
        }

        private static void glowDot(DrawContext c, int cx, int cy, int rgb, float a) {
            rr(c, cx - 8, cy - 8, cx + 8, cy + 8, 8, ca(rgb, a * 0.5f));
        }

        private void setFromMouse(double mx) {
            float f = clamp((float) ((mx - tx) / tw), 0f, 1f);
            float v = min + f * (max - min);
            v = Math.round(v / step) * step;
            set.accept(clamp(v, min, max));
        }

        @Override
        boolean click(VisualsScreen s, double mx, double my, int button) {
            setFromMouse(mx);
            return true;
        }

        @Override
        void drag(double mx) {
            setFromMouse(mx);
        }
    }

    private static final class CycleRow extends Row {
        final String label;
        final String[] options;
        final IntSupplier get;
        final IntConsumer set;

        CycleRow(String label, String[] options, IntSupplier get, IntConsumer set) {
            this.label = label;
            this.options = options;
            this.get = get;
            this.set = set;
        }

        @Override
        void draw(VisualsScreen s, DrawContext c, int mx, int my, float a, float dt, int accA, int accB) {
            updateHover(hit(mx, my), dt);
            rr(c, x, y, x + w, y + h, 6, ca(0xFFFFFF, (0.03f + 0.06f * hover) * a));
            s.txt(c, label, x + 9, y + 7, 0xE8E8F5, a, false);
            String val = "<  " + options[Math.floorMod(get.getAsInt(), options.length)] + "  >";
            int vw = s.textRenderer.getWidth(val);
            rr(c, x + w - vw - 18, y + 3, x + w - 6, y + h - 3, 5, ca(accA, 0.18f * a + 0.1f * hover));
            s.txt(c, val, x + w - vw - 12, y + 7, 0xFFFFFF, a, false);
        }

        @Override
        boolean click(VisualsScreen s, double mx, double my, int button) {
            int d = button == 1 ? -1 : 1;
            set.accept(Math.floorMod(get.getAsInt() + d, options.length));
            Config.save();
            return true;
        }
    }

    private static final class ButtonRow extends Row {
        final String label;
        final Runnable action;

        ButtonRow(String label, Runnable action) {
            this.label = label;
            this.action = action;
            this.h = 26;
        }

        @Override
        void draw(VisualsScreen s, DrawContext c, int mx, int my, float a, float dt, int accA, int accB) {
            updateHover(hit(mx, my), dt);
            rr(c, x, y, x + w, y + h, 7, ca(accA, (0.20f + 0.25f * hover) * a));
            rr(c, x + 1, y + 1, x + w - 1, y + h - 1, 6, ca(0x0B0B16, 0.55f * a));
            rr(c, x + 1, y + h / 2, x + w - 1, y + h - 1, 6, ca(accB, (0.10f + 0.18f * hover) * a));
            s.txtC(c, label, x + w / 2, y + 9, 0xFFFFFF, a);
        }

        @Override
        boolean click(VisualsScreen s, double mx, double my, int button) {
            action.run();
            return true;
        }
    }

    private static final class ThemeRow extends Row {
        final float[] sel = new float[Config.THEMES.length];

        ThemeRow() {
            this.h = 84;
        }

        private int cellW() {
            return (w - 16) / 3;
        }

        @Override
        void draw(VisualsScreen s, DrawContext c, int mx, int my, float a, float dt, int accA, int accB) {
            s.txt(c, "Motyw kolorów", x + 9, y + 2, 0xE8E8F5, a, false);
            int cw = cellW();
            for (int i = 0; i < Config.THEMES.length; i++) {
                int cx0 = x + (i % 3) * (cw + 8);
                int cy0 = y + 16 + (i / 3) * 34;
                boolean over = mx >= cx0 && mx < cx0 + cw && my >= cy0 && my < cy0 + 30;
                float target = (Config.I.theme == i) ? 1f : 0f;
                sel[i] += (target - sel[i]) * clamp(dt * 14f, 0f, 1f);
                rr(c, cx0 - 1, cy0 - 1, cx0 + cw + 1, cy0 + 31, 7,
                        ca(0xFFFFFF, (0.10f + 0.75f * sel[i] + (over ? 0.2f : 0f)) * a));
                rr(c, cx0, cy0, cx0 + cw, cy0 + 30, 6, ca(0x0B0B16, a));
                for (int k = 0; k < cw - 8; k += 2) {
                    float f = k / (float) (cw - 8);
                    int col = Config.themeColor(i, f);
                    c.fill(cx0 + 4 + k, cy0 + 4, cx0 + 4 + Math.min(cw - 8, k + 2), cy0 + 14, ca(col, 0.95f * a));
                }
                s.txtC(c, Config.THEMES[i], cx0 + cw / 2, cy0 + 18, 0xFFFFFF, a);
            }
        }

        @Override
        boolean click(VisualsScreen s, double mx, double my, int button) {
            int cw = cellW();
            for (int i = 0; i < Config.THEMES.length; i++) {
                int cx0 = x + (i % 3) * (cw + 8);
                int cy0 = y + 16 + (i / 3) * 34;
                if (mx >= cx0 && mx < cx0 + cw && my >= cy0 && my < cy0 + 30) {
                    Config.I.theme = i;
                    Config.save();
                    return true;
                }
            }
            return false;
        }
    }

    private static final class TextRow extends Row {
        final String text;

        TextRow(String text) {
            this.text = text;
            this.h = 14;
        }

        @Override
        void draw(VisualsScreen s, DrawContext c, int mx, int my, float a, float dt, int accA, int accB) {
            s.txt(c, text, x + 9, y + 2, 0xB9B9D0, a, false);
        }
    }

    // =====================================================================
    //  Budowanie zakładek
    // =====================================================================

    private void buildRows() {
        rows.clear();
        Config c = Config.I;
        switch (tab) {
            case 0 -> {
                rows.add(new ToggleRow("Wirujące pierścienie", () -> c.rings, v -> c.rings = v));
                rows.add(new ToggleRow("Okrąg pod graczem", () -> c.groundRing, v -> c.groundRing = v));
                rows.add(new ToggleRow("Magiczny krąg z napisem", () -> c.magicCircle, v -> c.magicCircle = v));
                rows.add(new ToggleRow("Gwiazdki wokół gracza", () -> c.playerStars, v -> c.playerStars = v));
                rows.add(new ToggleRow("Gwiazdki w powietrzu", () -> c.ambientStars, v -> c.ambientStars = v));
                rows.add(new SliderRow("Liczba pierścieni", 1, 6, 1, "%.0f",
                        () -> c.ringCount, v -> { c.ringCount = (int) Math.round(v); Config.save(); }));
                rows.add(new SliderRow("Intensywność", 0.3f, 1.5f, 0.05f, "%.2f",
                        () -> c.intensity, v -> { c.intensity = (float) v; Config.save(); }));
                rows.add(new SliderRow("Prędkość", 0.3f, 3f, 0.05f, "%.2f",
                        () -> c.speed, v -> { c.speed = (float) v; Config.save(); }));
            }
            case 1 -> {
                rows.add(new ThemeRow());
                rows.add(new CycleRow("Pokazuj na", Config.TARGETS, () -> c.target, v -> c.target = v));
                rows.add(new CycleRow("Kolor menu", Config.PALETTE, () -> c.menuColor, v -> c.menuColor = v));
                rows.add(new SliderRow("Odcień menu (dla \"Własny\")", 0f, 1f, 0.01f, "%.2f",
                        () -> c.menuHue, v -> { c.menuHue = (float) v; Config.save(); }));
            }
            case 2 -> {
                rows.add(new ToggleRow("Target Render", () -> c.targetHud, v -> c.targetHud = v));
                rows.add(new ToggleRow("Combo counter", () -> c.comboHud, v -> c.comboHud = v));
                rows.add(new ToggleRow("Cooldowns", () -> c.cooldownsHud, v -> c.cooldownsHud = v));
                rows.add(new ToggleRow("Potions", () -> c.potionsHud, v -> c.potionsHud = v));
                rows.add(new ToggleRow("Keystrokes + CPS", () -> c.keystrokesHud, v -> c.keystrokesHud = v));
                rows.add(new ToggleRow("FPS i ping", () -> c.infoHud, v -> c.infoHud = v));
                rows.add(new ToggleRow("Koordynaty", () -> c.coordsHud, v -> c.coordsHud = v));
                rows.add(new ToggleRow("Armor HUD", () -> c.armorHud, v -> c.armorHud = v));
                rows.add(new ButtonRow("Fake player: postaw (zamyka menu)", () -> {
                    FakePlayer.spawn(client);
                    close();
                }));
                rows.add(new ButtonRow("Fake player: usuń", FakePlayer::remove));
            }
            case 3 -> {
                rows.add(new CycleRow("Dźwięk uderzenia", Fx.names(Fx.HIT_SOUNDS), () -> c.hitSound, v -> c.hitSound = v));
                rows.add(new SliderRow("Głośność", 0f, 2f, 0.05f, "%.2f",
                        () -> c.hitVolume, v -> { c.hitVolume = (float) v; Config.save(); }));
                rows.add(new SliderRow("Wysokość dźwięku", 0.5f, 2f, 0.05f, "%.2f",
                        () -> c.hitPitch, v -> { c.hitPitch = (float) v; Config.save(); }));
                rows.add(new ToggleRow("Losowa wysokość", () -> c.hitPitchRandom, v -> c.hitPitchRandom = v));
                rows.add(new CycleRow("Cząsteczki uderzenia", Fx.HIT_PARTICLES, () -> c.hitParticle, v -> c.hitParticle = v));
                rows.add(new ButtonRow("Odtwórz dźwięk uderzenia", Fx::playHitSound));
                rows.add(new TextRow("Klik lewym = następny, prawym = poprzedni"));
            }
            case 4 -> {
                rows.add(new CycleRow("Efekt zabicia", Fx.KILL_EFFECTS, () -> c.killEffect, v -> c.killEffect = v));
                rows.add(new CycleRow("Dźwięk zabicia", Fx.names(Fx.KILL_SOUNDS), () -> c.killSound, v -> c.killSound = v));
                rows.add(new SliderRow("Głośność", 0f, 2f, 0.05f, "%.2f",
                        () -> c.killVolume, v -> { c.killVolume = (float) v; Config.save(); }));
                rows.add(new SliderRow("Wysokość dźwięku", 0.5f, 2f, 0.05f, "%.2f",
                        () -> c.killPitch, v -> { c.killPitch = (float) v; Config.save(); }));
                rows.add(new ButtonRow("Odtwórz dźwięk zabicia", Fx::playKillSound));
                rows.add(new ToggleRow("Napis ZABÓJSTWO", () -> c.killTitle, v -> c.killTitle = v));
                rows.add(new ButtonRow("Test efektu zabicia (zamyka menu)", () -> {
                    close();
                    RagdollManager.testKillEffect(client);
                }));
                rows.add(new TextRow("Efekt odpala się po zabiciu gracza w pobliżu."));
            }
            case 5 -> {
                rows.add(new ToggleRow("Target Render (karta celu)", () -> c.targetHud, v -> c.targetHud = v));
                rows.add(new ButtonRow("Fake player: postaw (zamyka menu)", () -> {
                    FakePlayer.spawn(client);
                    close();
                }));
                rows.add(new ButtonRow("Fake player: usuń", FakePlayer::remove));
                rows.add(new SliderRow("Obrażenia na cios", 1f, 10f, 0.5f, "%.1f",
                        () -> c.fakeDamage, v -> { c.fakeDamage = (float) v; Config.save(); }));
                rows.add(new ToggleRow("Odnawia się po zabiciu", () -> c.fakeRespawn, v -> c.fakeRespawn = v));
                rows.add(new TextRow("Najedź celownikiem na gracza lub fake playera:"));
                rows.add(new TextRow("pod celownikiem pokaże się jego skin, nick i HP."));
                rows.add(new TextRow("Bij fake playera LPM: dźwięk, combo, efekt zabicia"));
                rows.add(new TextRow("i ragdoll po zabiciu."));
            }
            case 6 -> {
                rows.add(new ToggleRow("Ragdoll po zabiciu gracza", () -> c.ragdoll, v -> c.ragdoll = v));
                rows.add(new CycleRow("Tryb lotu", Fx.RAGDOLL_MODES, () -> c.ragdollMode, v -> c.ragdollMode = v));
                rows.add(new ToggleRow("Błyszczący ślad w locie", () -> c.ragdollTrail, v -> c.ragdollTrail = v));
                rows.add(new SliderRow("Leży (sekundy)", 1f, 6f, 0.5f, "%.1f",
                        () -> c.ragdollLie, v -> { c.ragdollLie = (float) v; Config.save(); }));
                rows.add(new SliderRow("Siła wyrzutu", 0.5f, 2f, 0.05f, "%.2f",
                        () -> c.ragdollPower, v -> { c.ragdollPower = (float) v; Config.save(); }));
                rows.add(new ButtonRow("Test ragdolla (zamyka menu)", () -> {
                    RagdollManager.spawnTest(client);
                    close();
                }));
            }
            case 7 -> {
                rows.add(new ToggleRow("Aura wokół gracza", () -> c.auraOn, v -> c.auraOn = v));
                rows.add(new CycleRow("Kolor aury", Config.PALETTE, () -> c.auraColor, v -> c.auraColor = v));
                rows.add(new SliderRow("Odcień (dla \"Własny\")", 0f, 1f, 0.01f, "%.2f",
                        () -> c.auraHue, v -> { c.auraHue = (float) v; Config.save(); }));
                rows.add(new SliderRow("Rozmiar aury", 0.6f, 1.6f, 0.05f, "%.2f",
                        () -> c.auraSize, v -> { c.auraSize = (float) v; Config.save(); }));
                rows.add(new SliderRow("Jasność aury", 0.3f, 1.5f, 0.05f, "%.2f",
                        () -> c.auraIntensity, v -> { c.auraIntensity = (float) v; Config.save(); }));
                rows.add(new ToggleRow("Podświetlenie po uderzeniu", () -> c.hitFlash, v -> c.hitFlash = v));
                rows.add(new CycleRow("Kolor podświetlenia", Config.PALETTE, () -> c.hitFlashColor, v -> c.hitFlashColor = v));
                rows.add(new SliderRow("Odcień (dla \"Własny\")", 0f, 1f, 0.01f, "%.2f",
                        () -> c.hitFlashHue, v -> { c.hitFlashHue = (float) v; Config.save(); }));
                rows.add(new SliderRow("Czas podświetlenia (s)", 0.1f, 1.0f, 0.05f, "%.2f",
                        () -> c.hitFlashTime, v -> { c.hitFlashTime = (float) v; Config.save(); }));
            }
            default -> {
                rows.add(new TextRow("Prawy Shift – otwórz / zamknij menu"));
                rows.add(new TextRow("Zakładki przełączasz myszką"));
                rows.add(new TextRow(" "));
                rows.add(new TextRow("Po zabiciu gracza jego postać wylatuje w górę,"));
                rows.add(new TextRow("kręci się, spada, leży kilka sekund i znika."));
                rows.add(new TextRow(" "));
                rows.add(new TextRow("Ustawienia: config/visuals.json"));
            }
        }
        dragging = null;
    }

    private void setTab(int t) {
        if (t == tab) return;
        tab = t;
        buildRows();
    }

    // =====================================================================
    //  Rysowanie
    // =====================================================================

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // własne tło rysujemy w render()
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (!closing) {
            closing = true;
            closeMs = Util.getMeasuringTimeMs();
        }
    }

    @Override
    public void removed() {
        Config.save();
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        long now = Util.getMeasuringTimeMs();
        float dt = clamp((now - lastMs) / 1000f, 0f, 0.1f);
        lastMs = now;
        float t = (now % 1_000_000L) / 1000f;

        float openP = clamp((now - openMs) / 520f, 0f, 1f);
        float closeP = closing ? clamp((now - closeMs) / 220f, 0f, 1f) : 0f;
        if (closing && closeP >= 1f) {
            client.setScreen(null);
            return;
        }
        float vis = easeOutCubic(openP) * (1f - closeP);
        float warp = 1f + (1f - easeOutCubic(openP)) * 0.7f + closeP * 0.25f;

        int W = width, H = height;
        int accA = Config.palette(Config.I.menuColor, Config.I.menuHue, 0.0f, t);
        int accB = Config.palette(Config.I.menuColor, Config.I.menuHue, 1.0f, t);
        if (accB == accA) accB = Config.lerp(accA, 0xFFFFFF, 0.35f);

        drawVoid(c, W, H, t, vis, warp, mouseX, mouseY, accA, accB);

        // ---- panel ----
        float base = Math.min(1f, Math.min((W - 16f) / PW, (H - 16f) / PH));
        float s = base * (0.84f + 0.16f * easeOutBack(openP)) * (1f - 0.06f * closeP);
        curScale = s;
        curCx = W / 2f;
        curCy = H / 2f;
        panelX = (int) (curCx - PW / 2f);
        panelY = (int) (curCy - PH / 2f);
        float a = vis;

        double lmx = (mouseX - curCx) / s + curCx;
        double lmy = (mouseY - curCy) / s + curCy;

        c.getMatrices().push();
        c.getMatrices().translate(curCx, curCy, 0);
        c.getMatrices().scale(s, s, 1f);
        c.getMatrices().translate(-curCx, -curCy, 0);

        int x = panelX, y = panelY;
        // poświata za panelem
        glow(c, x + PW / 2, y + PH / 2, 230, accA, 0.16f * a);
        glow(c, x + PW - 40, y + 30, 140, accB, 0.10f * a);
        // obramowanie + tło
        rr(c, x - 1, y - 1, x + PW + 1, y + PH + 1, 13, ca(accA, 0.55f * a));
        rr(c, x, y, x + PW, y + PH, 12, ca(0x07070F, 0.93f * a));
        for (int i = 0; i < 40; i++) {
            c.fill(x + 12, y + 1 + i, x + PW - 12, y + 2 + i, ca(accA, 0.05f * a * (1f - i / 40f)));
        }

        // pasek boczny
        rr(c, x + 6, y + 6, x + 124, y + PH - 6, 9, ca(0x000000, 0.35f * a));
        drawTitle(c, x + 65, y + 16, t, a);

        float indTarget = tab * 28f;
        tabInd += (indTarget - tabInd) * clamp(dt * 16f, 0f, 1f);
        int ty0 = y + 56;
        rr(c, x + 12, ty0 + (int) tabInd, x + 118, ty0 + (int) tabInd + 24, 7, ca(accA, 0.20f * a));
        rr(c, x + 12, ty0 + (int) tabInd + 5, x + 15, ty0 + (int) tabInd + 19, 1, ca(accA, a));
        for (int i = 0; i < TABS.length; i++) {
            int ty = ty0 + i * 28;
            boolean over = lmx >= x + 12 && lmx < x + 118 && lmy >= ty && lmy < ty + 24;
            int col = i == tab ? 0xFFFFFF : (over ? 0xDDDDF0 : 0x9A9AB5);
            txt(c, TABS[i], x + 24, ty + 8, col, a, false);
        }
        txt(c, "RShift – zamknij", x + 14, y + PH - 22, 0x6F6F8C, a, false);

        // zawartość
        int cx0 = x + 138, cw = PW - 138 - 14;
        txt(c, TABS[tab], cx0 + 2, y + 16, 0xFFFFFF, a, true);
        for (int i = 0; i < 70; i += 2) {
            c.fill(cx0 + 2 + i, y + 30, cx0 + 4 + i, y + 31, ca(Config.lerp(accA, accB, i / 70f), 0.9f * a));
        }
        int ry = y + 40;
        for (Row r : rows) {
            r.x = cx0;
            r.y = ry;
            r.w = cw;
            r.draw(this, c, (int) lmx, (int) lmy, a, dt, accA, accB);
            ry += r.h + 2;
        }

        c.getMatrices().pop();
    }

    private void drawTitle(DrawContext c, int cx, int y, float t, float a) {
        String s = "Truevisuals";
        float sc = 1.5f;
        int total = (int) (textRenderer.getWidth(s) * sc);
        float x = cx - total / 2f;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            int col = Config.color(((float) i / s.length() + t * 0.2f) % 1f, t);
            c.getMatrices().push();
            c.getMatrices().translate(x, y, 0);
            c.getMatrices().scale(sc, sc, 1f);
            txt(c, ch, 0, 0, col, a, true);
            c.getMatrices().pop();
            x += textRenderer.getWidth(ch) * sc;
        }
    }

    private void drawVoid(DrawContext c, int W, int H, float t, float vis, float warp,
                          int mx, int my, int accA, int accB) {
        int bgA = (int) (clamp(vis, 0f, 1f) * 247f);
        c.fillGradient(0, 0, W, H, (bgA << 24) | 0x010106, (bgA << 24) | 0x0B0420);

        // mgławice
        glow(c, (int) (W * (0.25 + 0.05 * Math.sin(t * 0.17))), (int) (H * (0.30 + 0.05 * Math.cos(t * 0.13))),
                (int) (Math.min(W, H) * 0.55), Config.lerp(0x6A2CFF, accA, 0.6f), 0.20f * vis);
        glow(c, (int) (W * (0.78 + 0.04 * Math.cos(t * 0.15))), (int) (H * (0.70 + 0.05 * Math.sin(t * 0.11))),
                (int) (Math.min(W, H) * 0.50), Config.lerp(0x00A8FF, accB, 0.6f), 0.15f * vis);
        glow(c, (int) (W * 0.55), (int) (H * (0.15 + 0.04 * Math.sin(t * 0.2))),
                (int) (Math.min(W, H) * 0.35), Config.lerp(0xC83CFF, accA, 0.6f), 0.10f * vis);

        // gwiazdy
        float cxm = W / 2f, cym = H / 2f;
        for (int i = 0; i < STARS; i++) {
            float z = sz[i];
            float fx = ((sx[i] - t * 0.006f * z) % 1f + 1f) % 1f;
            float px = fx * W + (mx - cxm) * z * -0.04f;
            float py = sy[i] * H + (my - cym) * z * -0.04f;
            px = cxm + (px - cxm) * warp;
            py = cym + (py - cym) * warp;
            float tw = 0.55f + 0.45f * (float) Math.sin(t * (1.0f + z * 2.2f) + sph[i]);
            float al = tw * vis * (0.25f + 0.75f * z);
            int size = z > 0.78f ? 2 : 1;
            int ix = (int) px, iy = (int) py;
            if (ix < 0 || iy < 0 || ix >= W || iy >= H) continue;
            c.fill(ix, iy, ix + size, iy + size, ca(scol[i], al));
            if (z > 0.93f && i % 3 == 0) {
                int len = 3 + (int) (2 * tw);
                int col = ca(scol[i], al * 0.55f);
                c.fill(ix - len, iy, ix + size + len, iy + 1, col);
                c.fill(ix, iy - len, ix + 1, iy + size + len, col);
                glow(c, ix, iy, 7, scol[i], al * 0.22f);
            }
        }

        // spadające gwiazdy
        for (int k = 0; k < 2; k++) {
            float period = 3.4f + k * 1.9f;
            float tt = t + k * 1.7f;
            int slot = (int) (tt / period);
            float p = (tt % period) / period;
            if (p < 0.3f) {
                Random r = new Random(slot * 31L + k * 977L);
                float x0 = r.nextFloat() * W * 0.8f + W * 0.1f;
                float y0 = r.nextFloat() * H * 0.4f;
                float ang = 0.45f + r.nextFloat() * 0.35f;
                float q = p / 0.3f;
                float dist = q * W * 0.35f;
                for (int i = 0; i < 18; i++) {
                    float d = dist - i * 4f;
                    if (d < 0) break;
                    int sx2 = (int) (x0 + Math.cos(ang) * d);
                    int sy2 = (int) (y0 + Math.sin(ang) * d);
                    float fa = (1f - i / 18f) * (1f - q * q) * vis;
                    c.fill(sx2, sy2, sx2 + 2, sy2 + 2, ca(i < 2 ? 0xFFFFFF : accA, fa));
                }
            }
        }

        // winieta
        c.fillGradient(0, 0, W, H / 4, ca(0x000000, 0.55f * vis), ca(0x000000, 0f));
        c.fillGradient(0, H - H / 4, W, H, ca(0x000000, 0f), ca(0x000000, 0.65f * vis));
    }

    // =====================================================================
    //  Sterowanie
    // =====================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing) return true;
        double lx = (mouseX - curCx) / curScale + curCx;
        double ly = (mouseY - curCy) / curScale + curCy;
        int ty0 = panelY + 56;
        for (int i = 0; i < TABS.length; i++) {
            int ty = ty0 + i * 28;
            if (lx >= panelX + 12 && lx < panelX + 118 && ly >= ty && ly < ty + 24) {
                setTab(i);
                return true;
            }
        }
        for (Row r : rows) {
            if (r.hit(lx, ly) || (r instanceof ThemeRow && lx >= r.x && lx < r.x + r.w && ly >= r.y && ly < r.y + r.h)) {
                if (r.click(this, lx, ly, button)) {
                    if (r instanceof SliderRow) dragging = r;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging != null) {
            double lx = (mouseX - curCx) / curScale + curCx;
            dragging.drag(lx);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging != null) {
            dragging = null;
            Config.save();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 /* ESC */ || keyCode == 344 /* prawy shift */) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
