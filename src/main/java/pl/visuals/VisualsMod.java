package pl.visuals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VisualsMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("visuals");
    private static KeyBinding menuKey;

    @Override
    public void onInitializeClient() {
        Config.load();

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.visuals.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.visuals"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.wasPressed()) {
                if (client.world != null && client.currentScreen == null) {
                    client.setScreen(new VisualsScreen());
                }
            }
            RagdollManager.tick(client);
            Fx.tick(client);
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(VisualsRenderer::render);

        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden || mc.currentScreen instanceof VisualsScreen) return;
            Config c = Config.I;
            float td = tickCounter.getTickDelta(false);
            if (c.potionsHud) PotionsHud.render(context, mc);
            if (c.targetHud) Huds.target(context, mc);
            if (c.comboHud) Huds.combo(context, mc);
            if (c.cooldownsHud) Huds.cooldowns(context, mc, td);
            Huds.killTitle(context, mc);
            if (c.keystrokesHud) Huds.keystrokes(context, mc);
            if (c.infoHud || c.coordsHud) Huds.info(context, mc, c.infoHud, c.coordsHud);
            if (c.armorHud) Huds.armor(context, mc);
        });

        LOGGER.info("Truevisuals załadowany – prawy Shift otwiera menu.");
    }
}
