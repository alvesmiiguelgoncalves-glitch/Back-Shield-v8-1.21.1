package net.backshield.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Integracao OPCIONAL com o Backpacked 3.x (MrCrayfish), por reflexao.
 *
 * Feita a partir do bytecode do backpacked-fabric-1.21.1-3.0.5. A mochila equipada NAO fica em Trinkets nem no
 * inventario do jogador: ela mora em dados sincronizados (ModSyncedDataKeys.BACKPACKS). O proprio
 * BackpackLayer (que desenha a mochila) decide so olhando:
 *   1. ModSyncedDataKeys.COSMETIC_PROPERTIES (Optional) - presente = ha mochila equipada;
 *   2. Services.BACKPACK.isBackpackVisible(player);
 *   3. se o peitoral for elytra, so desenha se CosmeticProperties.showWithElytra().
 * Replicamos exatamente essas tres regras, entao "detectado" == "a mochila esta sendo desenhada".
 *
 * Um item do Backpacked solto no inventario NAO conta (antes contava por engano).
 * Se algo disso falhar (versao diferente do Backpacked), cai em BackpackHelper.getFirstBackpackStack(Player).
 */
@Environment(EnvType.CLIENT)
final class BackpackCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("backshield");

    private static boolean initialized;
    private static boolean loaded;

    // Estrategia principal
    private static Object propsKey;
    private static Method propsGetValue;
    private static Object backpackService;
    private static Method isVisibleMethod;

    // Fallback
    private static Method firstBackpackStack;

    private static String lastNote = "-";

    private BackpackCompat() {
    }

    private static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        loaded = FabricLoader.getInstance().isModLoaded("backpacked");
        if (!loaded) {
            return;
        }
        try {
            Class<?> keys = Class.forName("com.mrcrayfish.backpacked.core.ModSyncedDataKeys");
            Field field = keys.getField("COSMETIC_PROPERTIES");
            propsKey = field.get(null);
            propsGetValue = findMethod(field.getType(), "getValue", 1);
        } catch (Throwable t) {
            LOGGER.warn("Backpacked COSMETIC_PROPERTIES not available; using fallback", t);
            propsKey = null;
            propsGetValue = null;
        }
        try {
            Class<?> services = Class.forName("com.mrcrayfish.backpacked.platform.Services");
            Field field = services.getField("BACKPACK");
            backpackService = field.get(null);
            isVisibleMethod = findMethod(field.getType(), "isBackpackVisible", 1);
        } catch (Throwable t) {
            LOGGER.warn("Backpacked isBackpackVisible not available (assuming visible)", t);
        }
        try {
            Class<?> helper = Class.forName("com.mrcrayfish.backpacked.BackpackHelper");
            for (Method m : helper.getMethods()) {
                if (m.getName().equals("getFirstBackpackStack") && m.getParameterCount() == 1) {
                    firstBackpackStack = m;
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("BackpackHelper not available", t);
        }
    }

    private static Method findMethod(Class<?> type, String name, int params) {
        for (Method m : type.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == params) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    static boolean hasBackpack(LivingEntity entity) {
        init();
        if (!loaded || !(entity instanceof PlayerEntity player)) {
            return false;
        }
        if (propsKey != null && propsGetValue != null) {
            try {
                Object value = propsGetValue.invoke(propsKey, player);
                if (!(value instanceof Optional<?> optional) || optional.isEmpty()) {
                    lastNote = "COSMETIC_PROPERTIES vazio";
                    return false;
                }
                if (isVisibleMethod != null && backpackService != null
                        && Boolean.FALSE.equals(isVisibleMethod.invoke(backpackService, player))) {
                    lastNote = "mochila oculta (isBackpackVisible=false)";
                    return false;
                }
                ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
                if (chest.isOf(Items.ELYTRA) && !showWithElytra(optional.get())) {
                    lastNote = "mochila escondida com elytra (showWithElytra=false)";
                    return false;
                }
                lastNote = "COSMETIC_PROPERTIES presente";
                return true;
            } catch (Throwable t) {
                LOGGER.warn("Backpacked props check failed; using fallback", t);
                propsGetValue = null;
            }
        }
        if (firstBackpackStack != null) {
            try {
                Object result = firstBackpackStack.invoke(null, player);
                lastNote = "fallback getFirstBackpackStack";
                return result instanceof ItemStack stack && !stack.isEmpty();
            } catch (Throwable t) {
                LOGGER.warn("getFirstBackpackStack failed", t);
                firstBackpackStack = null;
            }
        }
        return false;
    }

    private static boolean showWithElytra(Object props) {
        try {
            Method m = props.getClass().getMethod("showWithElytra");
            m.setAccessible(true);
            return Boolean.TRUE.equals(m.invoke(props));
        } catch (Throwable t) {
            return true;
        }
    }

    /** Linhas de diagnostico para /backshield status. */
    static List<String> describe(LivingEntity entity) {
        init();
        List<String> lines = new ArrayList<>();
        lines.add("backpacked instalado=" + loaded + ", props=" + (propsGetValue != null)
                + ", visivel=" + (isVisibleMethod != null) + ", fallback=" + (firstBackpackStack != null));
        lines.add("mochila equipada=" + hasBackpack(entity) + " (" + lastNote + ")");
        return lines;
    }
}
