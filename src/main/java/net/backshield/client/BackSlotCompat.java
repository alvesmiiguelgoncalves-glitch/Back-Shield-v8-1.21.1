package net.backshield.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.SwordItem;
import net.minecraft.registry.Registries;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Integracao OPCIONAL com o BackSlot (Globox_Z) e o BackSlot Addon.
 *
 * Feita a partir do bytecode do backslot-1.3.1 / backslotaddon-1.1.2:
 *  - o slot de costas e o de cintura sao slots EXTRAS do PlayerInventory: indice 41 (costas) e 42 (cintura);
 *  - sem o addon, so o item do indice 41 aparece nas costas (o da cintura fica na cintura);
 *  - com o addon e config double_back_sword = true, quando AMBOS os slots tem item, o item da CINTURA e uma
 *    ESPADA e o das costas e uma espada (ou um escudo), a espada da cintura tambem vai para as costas.
 */
@Environment(EnvType.CLIENT)
final class BackSlotCompat {

    private static final int BACK_INDEX = 41;
    private static final int BELT_INDEX = 42;

    private static boolean initialized;
    private static boolean backSlotLoaded;
    private static boolean addonLoaded;
    private static Field addonConfigField;
    private static Field doubleSwordField;

    private BackSlotCompat() {
    }

    private static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        FabricLoader loader = FabricLoader.getInstance();
        backSlotLoaded = loader.isModLoaded("backslot");
        addonLoaded = loader.isModLoaded("backslotaddon");
        if (addonLoaded) {
            try {
                Class<?> init = Class.forName("net.backslotaddon.config.ConfigInit");
                addonConfigField = init.getField("CONFIG");
                doubleSwordField = Class.forName("net.backslotaddon.config.BackSlotAddonConfig").getField("double_back_sword");
            } catch (Throwable t) {
                addonConfigField = null;
                doubleSwordField = null;
            }
        }
    }

    private static boolean doubleBackSwordEnabled() {
        if (addonConfigField == null || doubleSwordField == null) {
            return true; // nao deu para ler a config: assume o padrao do addon
        }
        try {
            return doubleSwordField.getBoolean(addonConfigField.get(null));
        } catch (Throwable t) {
            return true;
        }
    }

    private static ItemStack stack(PlayerEntity player, int index) {
        try {
            return player.getInventory().getStack(index);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    /** @return 0, 1 ou 2 itens do BackSlot aparecendo nas costas. */
    static int backItems(LivingEntity entity) {
        init();
        if (!backSlotLoaded || !(entity instanceof PlayerEntity player)) {
            return 0;
        }
        ItemStack back = stack(player, BACK_INDEX);
        if (back.isEmpty()) {
            return 0;
        }
        ItemStack belt = stack(player, BELT_INDEX);
        boolean doubled = addonLoaded && doubleBackSwordEnabled()
                && !belt.isEmpty() && belt.getItem() instanceof SwordItem
                && (back.getItem() instanceof SwordItem || back.getItem() instanceof ShieldItem);
        return doubled ? 2 : 1;
    }

    static List<String> describe(LivingEntity entity) {
        init();
        List<String> lines = new ArrayList<>();
        lines.add("backslot instalado=" + backSlotLoaded + ", addon=" + addonLoaded);
        if (backSlotLoaded && entity instanceof PlayerEntity player) {
            lines.add("  costas(41)=" + Registries.ITEM.getId(stack(player, BACK_INDEX).getItem())
                    + ", cintura(42)=" + Registries.ITEM.getId(stack(player, BELT_INDEX).getItem())
                    + " -> itens nas costas=" + backItems(entity));
        }
        return lines;
    }
}
