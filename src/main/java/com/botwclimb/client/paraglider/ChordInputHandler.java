package com.botwclimb.client.paraglider;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.paraglider.InputChord;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/**
 * Feeds raw GLFW key events (which carry the modifier state at the moment of the press) into an {@link InputChord}.
 * That is what makes the shortcut ordered: Shift then Space counts, Space then Shift does not.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class ChordInputHandler {
    public static final InputChord PARAGLIDE = new InputChord();

    private ChordInputHandler() {}

    private static int modMask(KeyModifier m) {
        return switch (m) {
            case SHIFT -> GLFW.GLFW_MOD_SHIFT;
            case CONTROL -> GLFW.GLFW_MOD_CONTROL;
            case ALT -> GLFW.GLFW_MOD_ALT;
            default -> 0;
        };
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        var key = ParagliderKeys.PARAGLIDE.getKey();
        if (event.getKey() != key.getValue()) return;
        if (event.getAction() == GLFW.GLFW_PRESS) {
            if (Minecraft.getInstance().screen != null) return;
            int need = modMask(ParagliderKeys.PARAGLIDE.getKeyModifier());
            PARAGLIDE.onPress(need != 0 && (event.getModifiers() & need) == need);
        } else if (event.getAction() == GLFW.GLFW_RELEASE) {
            PARAGLIDE.onRelease();
        }
    }

    /** True while the shortcut's main key is physically held. */
    public static boolean keyPhysicallyDown() {
        var key = ParagliderKeys.PARAGLIDE.getKey();
        return key.getType() == InputConstants.Type.KEYSYM
                && InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key.getValue());
    }
}
