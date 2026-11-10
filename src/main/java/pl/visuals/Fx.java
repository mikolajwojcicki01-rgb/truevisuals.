package pl.visuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Dźwięki, cząsteczki, pioruny, combo i dane celu. */
public final class Fx {
    private Fx() {}

    private static final Random RNG = new Random();

    // ---------- tabele wyboru (nazwa, id dźwięku) ----------
    public static final String[][] HIT_SOUNDS = {
            {"Brak", ""},
            {"Crit", "entity.player.attack.crit"},
            {"Mocny cios", "entity.player.attack.strong"},
            {"Machnięcie", "entity.player.attack.sweep"},
            {"Odrzut", "entity.player.attack.knockback"},
            {"Słaby cios", "entity.player.attack.weak"},
            {"Ding (łuk)", "entity.arrow.hit_player"},
            {"Orb XP", "entity.experience_orb.pickup"},
            {"Dzwonek", "block.note_block.bell"},
            {"Pling", "block.note_block.pling"},
            {"Harfa", "block.note_block.harp"},
            {"Bęben", "block.note_block.basedrum"},
            {"Kowadło", "block.anvil.land"},
            {"Szkło", "block.glass.break"},
            {"Totem", "item.totem.use"},
            {"Piorun", "entity.lightning_bolt.impact"},
            {"Trójząb", "item.trident.hit"},
            {"Smok", "entity.ender_dragon.hurt"},
            {"Wither", "entity.wither.hurt"},
            {"Golem", "entity.iron_golem.attack"},
            {"Oof", "entity.player.hurt"},
            {"Pop", "entity.item.pickup"},
            {"Level up", "entity.player.levelup"},
            {"Kot", "entity.cat.hurt"},
    };

    public static final String[][] KILL_SOUNDS = {
            {"Brak", ""},
            {"Piorun", "entity.lightning_bolt.thunder"},
            {"Uderzenie pioruna", "entity.lightning_bolt.impact"},
            {"Totem", "item.totem.use"},
            {"Wybuch", "entity.generic.explode"},
            {"Ryk smoka", "entity.ender_dragon.growl"},
            {"Śmierć smoka", "entity.ender_dragon.death"},
            {"Śmierć withera", "entity.wither.death"},
            {"Spawn withera", "entity.wither.spawn"},
            {"Level up", "entity.player.levelup"},
            {"Fajerwerk", "entity.firework_rocket.blast"},
            {"Duży fajerwerk", "entity.firework_rocket.large_blast"},
            {"Migotanie fajerwerku", "entity.firework_rocket.twinkle"},
            {"Grzmot tridenta", "item.trident.thunder"},
            {"Beacon off", "block.beacon.deactivate"},
            {"Kowadło", "block.anvil.land"},
            {"Dzwon", "block.bell.use"},
            {"Warden: boom", "entity.warden.sonic_boom"},
            {"Warden: ryk", "entity.warden.roar"},
            {"Klątwa strażnika", "entity.elder_guardian.curse"},
            {"Krzyk endermana", "entity.enderman.scream"},
            {"Krzyk ghasta", "entity.ghast.scream"},
            {"Ryk ravagera", "entity.ravager.roar"},
            {"Portal endu", "block.end_portal.spawn"},
            {"Respawn anchor", "block.respawn_anchor.charge"},
            {"Conduit", "block.conduit.activate"},
            {"Osiągnięcie", "ui.toast.challenge_complete"},
            {"Przywołanie", "entity.evoker.prepare_summon"},
            {"Losowy", "*"},
    };

    public static final String[] KILL_EFFECTS = {
            "Brak", "Piorun", "Wybuch", "Dusze", "Serca", "Totem", "Konfetti", "Płomienie", "Kosmos", "Iskry", "Złota eksplozja"
    };

    public static final String[] HIT_PARTICLES = {
            "Brak", "Crit", "Magiczny crit", "Serca", "Płomień", "Iskry", "Dusze", "Totem"
    };

    public static final String[] RAGDOLL_MODES = {"W górę", "Odrzut", "Rakieta"};

    public static String[] names(String[][] t) {
        String[] n = new String[t.length];
        for (int i = 0; i < t.length; i++) n[i] = t[i][0];
        return n;
    }

    // ---------- stan combo / celu ----------
    public static int killTitleTicks;
    public static String killTitleName = "";

    public static void showKillTitle(String name) {
        if (!Config.I.killTitle) return;
        killTitleName = name == null ? "" : name;
        killTitleTicks = 50;
    }

    public static int combo;
    public static int comboPop;
    private static int comboTicks;
    private static boolean prevAttack, prevUse;
    private static final java.util.ArrayDeque<Long> LCLICKS = new java.util.ArrayDeque<>();
    private static final java.util.ArrayDeque<Long> RCLICKS = new java.util.ArrayDeque<>();

    private static int cps(java.util.ArrayDeque<Long> q) {
        long now = System.currentTimeMillis();
        while (!q.isEmpty() && now - q.peekFirst() > 1000) q.pollFirst();
        return q.size();
    }

    public static int leftCps() { return cps(LCLICKS); }

    public static int rightCps() { return cps(RCLICKS); }
    private static int prevHurt;
    private static boolean prevAtkClick;

    public static String targetName = "";
    public static net.minecraft.entity.player.SkinTextures targetSkin;
    public static float targetHealth, targetMax = 20f, targetAnim = 20f;
    public static long targetTime = -1000;

    // ---------- podświetlenie trafionego celu ----------
    public static final class Flash {
        public final int entityId;
        public final boolean fake;
        public int life;
        public final int max;

        Flash(int entityId, boolean fake, int life) {
            this.entityId = entityId;
            this.fake = fake;
            this.life = life;
            this.max = life;
        }
    }

    public static final List<Flash> FLASHES = new ArrayList<>();

    public static void addFlash(int entityId, boolean fake) {
        if (!Config.I.hitFlash) return;
        FLASHES.removeIf(f -> f.fake == fake && f.entityId == entityId);
        FLASHES.add(new Flash(entityId, fake, Math.max(3, Math.round(Config.I.hitFlashTime * 20f))));
    }

    // ---------- pioruny ----------
    public static final class Bolt {
        public final double x, y, z;
        public int life;
        public final int maxLife;
        public final int type; // 0 = piorun, 1 = złota eksplozja
        public final long seed = RNG.nextLong();

        Bolt(double x, double y, double z) {
            this(x, y, z, 0);
        }

        Bolt(double x, double y, double z, int type) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.maxLife = type == 1 ? 26 : 16;
            this.life = maxLife;
        }
    }

    public static final List<Bolt> BOLTS = new ArrayList<>();

    // ---------- dźwięki ----------
    public static void play(String id, float volume, float pitch) {
        if (id == null || id.isEmpty()) return;
        try {
            SoundEvent ev = SoundEvent.of(Identifier.of("minecraft", id));
            MinecraftClient.getInstance().getSoundManager()
                    .play(PositionedSoundInstance.master(ev, pitch, Math.max(0f, volume)));
        } catch (Exception e) {
            VisualsMod.LOGGER.warn("Nie można odtworzyć dźwięku {}", id);
        }
    }

    public static void playHitSound() {
        Config c = Config.I;
        int i = Math.floorMod(c.hitSound, HIT_SOUNDS.length);
        float pitch = c.hitPitch;
        if (c.hitPitchRandom) pitch *= 0.92f + RNG.nextFloat() * 0.16f;
        play(HIT_SOUNDS[i][1], c.hitVolume, pitch);
    }

    public static void playKillSound() {
        Config c = Config.I;
        int i = Math.floorMod(c.killSound, KILL_SOUNDS.length);
        String id = KILL_SOUNDS[i][1];
        if (id.equals("*")) {
            // losowy dźwięk (pomijamy "Brak" i samego siebie)
            int r = 1 + RNG.nextInt(KILL_SOUNDS.length - 2);
            id = KILL_SOUNDS[r][1];
        }
        play(id, c.killVolume, c.killPitch);
    }

    // ---------- cząsteczki ----------
    private static void burst(ParticleEffect p, double x, double y, double z, int n, double spread, double speed) {
        var w = MinecraftClient.getInstance().world;
        if (w == null) return;
        for (int i = 0; i < n; i++) {
            w.addParticle(p,
                    x + (RNG.nextDouble() - 0.5) * spread, y + (RNG.nextDouble() - 0.5) * spread,
                    z + (RNG.nextDouble() - 0.5) * spread,
                    (RNG.nextDouble() - 0.5) * speed, (RNG.nextDouble() - 0.3) * speed, (RNG.nextDouble() - 0.5) * speed);
        }
    }

    public static void hitParticles(double x, double y, double z) {
        switch (Math.floorMod(Config.I.hitParticle, HIT_PARTICLES.length)) {
            case 1 -> burst(ParticleTypes.CRIT, x, y, z, 10, 0.6, 0.6);
            case 2 -> burst(ParticleTypes.ENCHANTED_HIT, x, y, z, 12, 0.6, 0.6);
            case 3 -> burst(ParticleTypes.HEART, x, y, z, 4, 0.6, 0.1);
            case 4 -> burst(ParticleTypes.FLAME, x, y, z, 8, 0.5, 0.15);
            case 5 -> burst(ParticleTypes.ELECTRIC_SPARK, x, y, z, 10, 0.6, 0.5);
            case 6 -> burst(ParticleTypes.SOUL, x, y, z, 5, 0.5, 0.1);
            case 7 -> burst(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 10, 0.6, 0.5);
            default -> { }
        }
    }

    /** Efekt zabicia w punkcie (x,y,z) = stopy ofiary. */
    public static void killEffect(double x, double y, double z) {
        int e = Math.floorMod(Config.I.killEffect, KILL_EFFECTS.length);
        double cy = y + 1.0;
        switch (e) {
            case 1 -> BOLTS.add(new Bolt(x, y, z));
            case 2 -> {
                burst(ParticleTypes.EXPLOSION, x, cy, z, 4, 1.2, 0.0);
                burst(ParticleTypes.FLAME, x, cy, z, 30, 1.0, 0.5);
                burst(ParticleTypes.LARGE_SMOKE, x, cy, z, 14, 1.0, 0.2);
            }
            case 3 -> {
                burst(ParticleTypes.SOUL, x, cy, z, 18, 0.8, 0.12);
                burst(ParticleTypes.SOUL_FIRE_FLAME, x, cy, z, 18, 0.8, 0.2);
            }
            case 4 -> burst(ParticleTypes.HEART, x, cy, z, 18, 1.2, 0.2);
            case 5 -> burst(ParticleTypes.TOTEM_OF_UNDYING, x, cy, z, 50, 0.8, 0.9);
            case 6 -> burst(ParticleTypes.FIREWORK, x, cy, z, 40, 0.6, 0.9);
            case 7 -> {
                burst(ParticleTypes.FLAME, x, cy, z, 40, 1.0, 0.3);
                burst(ParticleTypes.LAVA, x, cy, z, 12, 1.0, 0.1);
            }
            case 8 -> {
                burst(ParticleTypes.END_ROD, x, cy, z, 50, 0.8, 0.7);
                BOLTS.add(new Bolt(x, y, z));
            }
            case 9 -> burst(ParticleTypes.ELECTRIC_SPARK, x, cy, z, 50, 1.0, 1.0);
            case 10 -> {
                BOLTS.add(new Bolt(x, y, z, 1));
                burst(ParticleTypes.FLASH, x, cy, z, 1, 0.0, 0.0);
                burst(ParticleTypes.EXPLOSION_EMITTER, x, cy, z, 1, 0.0, 0.0);
                burst(ParticleTypes.FIREWORK, x, cy, z, 70, 0.9, 1.0);
                burst(ParticleTypes.FLAME, x, cy, z, 45, 1.0, 0.45);
                burst(ParticleTypes.END_ROD, x, cy, z, 30, 0.8, 0.8);
            }
            default -> { }
        }
        playKillSound();
    }

    // ---------- tick: wykrywanie uderzeń, combo, cel ----------
    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        // pioruny
        BOLTS.removeIf(b -> --b.life <= 0);
        FLASHES.removeIf(f -> --f.life <= 0);
        if (comboPop > 0) comboPop--;
        if (killTitleTicks > 0) killTitleTicks--;

        // trafienie w nas = koniec combo
        int ht = mc.player.hurtTime;
        if (ht > 0 && prevHurt == 0) combo = 0;
        prevHurt = ht;
        if (combo > 0 && --comboTicks <= 0) combo = 0;

        // cel pod celownikiem
        if (mc.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le
                && le != mc.player) {
            setTarget(mc, le);
        } else if (FakePlayer.I != null && FakePlayer.I.rayHit(mc) > 0) {
            FakePlayer.I.fillTarget(mc);
        }
        targetAnim += (targetHealth - targetAnim) * 0.2f;

        // uderzenie (zbocze klawisza ataku)
        boolean atk = mc.options.attackKey.isPressed();
        if (atk && !prevAttack && mc.currentScreen == null) onSwing(mc);
        prevAttack = atk;

        boolean use = mc.options.useKey.isPressed();
        if (atk && !prevAtkClick) LCLICKS.addLast(System.currentTimeMillis());
        if (use && !prevUse) RCLICKS.addLast(System.currentTimeMillis());
        prevAtkClick = atk;
        prevUse = use;
    }

    private static void setTarget(MinecraftClient mc, LivingEntity le) {
        targetName = le.getName().getString();
        targetHealth = le.getHealth();
        targetMax = Math.max(1f, le.getMaxHealth());
        if (le instanceof AbstractClientPlayerEntity p) targetSkin = p.getSkinTextures();
        else targetSkin = null;
        targetTime = mc.world.getTime();
    }

    private static void onSwing(MinecraftClient mc) {
        if (mc.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le
                && le != mc.player) {
            Vec3d p = le.getPos();
            addFlash(le.getId(), false);
            registerHit(p.x, p.y + le.getHeight() * 0.6, p.z);
            return;
        }
        FakePlayer fp = FakePlayer.I;
        if (fp != null && fp.alive() && fp.rayHit(mc) > 0) {
            fp.hit(mc);
        }
    }

    /** Wspólne dla prawdziwych i fake graczy: dźwięk, cząsteczki, combo. */
    public static void registerHit(double x, double y, double z) {
        combo++;
        comboPop = 8;
        comboTicks = 60;
        playHitSound();
        hitParticles(x, y, z);
    }
}
