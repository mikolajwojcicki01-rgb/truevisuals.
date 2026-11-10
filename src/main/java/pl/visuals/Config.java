package pl.visuals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Ustawienia moda zapisywane w config/visuals.json. */
public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("visuals.json");

    public static Config I = new Config();

    // --- visualsy ---
    public boolean rings = true;          // wirujące pierścienie wokół gracza
    public boolean groundRing = true;     // świecący okrąg pod graczem
    public boolean magicCircle = true;    // magiczny krąg z napisem
    public boolean playerStars = true;    // gwiazdki unoszące się wokół gracza
    public boolean ambientStars = true;   // gwiazdki w powietrzu
    public boolean potionsHud = true;     // mały HUD z potionami
    public int ringCount = 3;             // 1..6
    public float intensity = 1.0f;        // 0.3..2
    public float speed = 1.0f;            // 0.3..3
    public int theme = 0;                 // indeks w THEMES
    public int target = 2;                // 0 = tylko ja, 1 = inni, 2 = wszyscy
    public String circleText = "VISUALS";

    // --- kolor menu ---
    public int menuColor = 11;            // indeks w PALETTE (11 = jak motyw)
    public float menuHue = 0.6f;          // dla "Własny"

    // --- aura wokół gracza ---
    public boolean auraOn = true;
    public int auraColor = 1;             // indeks w PALETTE
    public float auraHue = 0.75f;         // dla "Własny"
    public float auraSize = 1.0f;
    public float auraIntensity = 1.0f;

    // --- podświetlenie po uderzeniu ---
    public boolean hitFlash = true;
    public int hitFlashColor = 5;         // czerwony
    public float hitFlashHue = 0.0f;
    public float hitFlashTime = 0.4f;     // sekundy

    // --- HUD ---
    public boolean targetHud = true;
    public boolean comboHud = true;
    public boolean cooldownsHud = true;
    public boolean keystrokesHud = false;
    public boolean infoHud = false;      // FPS + ping
    public boolean coordsHud = false;
    public boolean armorHud = false;

    // --- walka ---
    public int hitSound = 1;              // indeks w Fx.HIT_SOUNDS
    public float hitVolume = 1.0f;
    public float hitPitch = 1.0f;
    public boolean hitPitchRandom = true;
    public int hitParticle = 1;           // indeks w Fx.HIT_PARTICLES

    // --- zabicie ---
    public int killEffect = 10;           // indeks w Fx.KILL_EFFECTS (10 = złota eksplozja jak na filmie)
    public boolean killTitle = true;      // napis ZABÓJSTWO
    public int killSound = 10;            // indeks w Fx.KILL_SOUNDS
    public float killVolume = 1.0f;
    public float killPitch = 1.0f;

    // --- fake player ---
    public float fakeDamage = 2.0f;       // obrażenia na cios (HP 20)
    public boolean fakeRespawn = true;

    // --- ragdoll ---
    public boolean ragdoll = true;
    public int ragdollMode = 0;           // 0 = w górę, 1 = odrzut, 2 = rakieta
    public boolean ragdollTrail = true;
    public float ragdollLie = 3.0f;       // ile sekund leży
    public float ragdollPower = 1.0f;     // siła wyrzutu

    public static final String[] THEMES = {"Kosmos", "Neon", "Ogień", "Róż", "Soup", "Tęcza"};
    private static final int[][] THEME_COLORS = {
            {0x8FD8FF, 0xB99BFF},
            {0x3DFFC0, 0x00B4FF},
            {0xFF5A3C, 0xFFC15A},
            {0xFF7AD9, 0x8F7BFF},
            {0x2AFFC8, 0xFF4F6A},
            {0xFF0000, 0x00FFFF}, // tęcza liczona osobno
    };
    public static final String[] PALETTE = {"Biały", "Niebieski", "Zielony", "Fioletowy", "Szmaragd", "Czerwony",
            "Różowy", "Pomarańczowy", "Żółty", "Cyjan", "Tęcza", "Motyw", "Własny"};
    private static final int[] PALETTE_RGB = {0xFFFFFF, 0x2F6BFF, 0x35E05A, 0xA14BFF, 0x10E0A0, 0xFF2B2B,
            0xFF6AD5, 0xFF8A1F, 0xFFE24A, 0x2FE6FF};

    /** Kolor z palety: idx, odcień dla "Własny" (0..1), pozycja s (0..1) dla gradientów, czas. */
    public static int palette(int idx, float hue, float s, float time) {
        idx = Math.floorMod(idx, PALETTE.length);
        if (idx == 10) return hsv((time * 0.2f + s * 0.5f) % 1f, 0.8f, 1f);
        if (idx == 11) return color(s, time);
        if (idx == 12) return hsv(Math.max(0f, Math.min(0.999f, hue)), 0.85f, 1f);
        return PALETTE_RGB[idx];
    }

    public static final String[] TARGETS = {"Tylko ja", "Inni gracze", "Wszyscy"};

    public static int themeA() { return THEME_COLORS[safeTheme()][0]; }
    public static int themeB() { return THEME_COLORS[safeTheme()][1]; }

    private static int safeTheme() {
        return Math.max(0, Math.min(THEMES.length - 1, I.theme));
    }

    /** Kolor RGB (bez alfy) dla pozycji s (0..1) wzdłuż gradientu motywu. */
    public static int color(float s, float time) {
        s = Math.max(0f, Math.min(1f, s));
        if (safeTheme() == 5) {
            return hsv((time * 0.15f + s * 0.45f) % 1f, 0.75f, 1f);
        }
        return lerp(themeA(), themeB(), s);
    }

    public static int themeColor(int index, float s) {
        int[] c = THEME_COLORS[Math.max(0, Math.min(THEMES.length - 1, index))];
        if (index == 5) return hsv((s * 0.8f) % 1f, 0.75f, 1f);
        return lerp(c[0], c[1], s);
    }

    public static int lerp(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    public static int hsv(float h, float s, float v) {
        float r = 0, g = 0, b = 0;
        int i = (int) (h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Config c = GSON.fromJson(Files.readString(FILE), Config.class);
                if (c != null) I = c;
            }
        } catch (Exception e) {
            VisualsMod.LOGGER.warn("Nie udało się wczytać configu, używam domyślnego", e);
        }
        I.ringCount = Math.max(1, Math.min(6, I.ringCount));
        if (I.circleText == null || I.circleText.isBlank()) I.circleText = "VISUALS";
        save();
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(I));
        } catch (Exception e) {
            VisualsMod.LOGGER.warn("Nie udało się zapisać configu", e);
        }
    }
}
