package net.backshield.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * Escolhe QUAL perfil de posicao vale agora. Prioridade:
 *   voando (asas abertas)  >  elytra equipada (planando no ar / em pe)  >  peitoral  >  nada
 * e, em cada caso, mochila > 2 itens nas costas > 1 item nas costas.
 * (Itens do BackSlot so contam sem elytra e sem mochila; com elytra/mochila vale o perfil de elytra/mochila.)
 */
@Environment(EnvType.CLIENT)
public final class ProfileSelector {

    private ProfileSelector() {
    }

    public static String select(LivingEntity entity) {
        boolean backpack = BackpackCompat.hasBackpack(entity);

        if (entity.isFallFlying()) {
            return backpack ? "flying_backpack" : "flying";
        }
        if (EquipmentCompat.hasElytra(entity)) {
            if (isAirborne(entity)) {
                return backpack ? "gliding_backpack" : "gliding";
            }
            return backpack ? "elytra_backpack" : "elytra";
        }

        int items = BackSlotCompat.backItems(entity);
        if (hasChestplate(entity)) {
            if (backpack) {
                return "chest_backpack";
            }
            return items >= 2 ? "chest_item2" : items == 1 ? "chest_item1" : "chest";
        }
        if (backpack) {
            return "backpack";
        }
        return items >= 2 ? "item2" : items == 1 ? "item1" : "normal";
    }

    /** Peitoral de armadura equipado (elytra nao conta: ela tem perfis proprios). */
    private static boolean hasChestplate(LivingEntity entity) {
        ItemStack chest = entity.getEquippedStack(EquipmentSlot.CHEST);
        return !chest.isEmpty() && !EquipmentCompat.isElytra(chest);
    }

    /** No ar, sem estar na agua, escalando, montado nem voando em criativo. */
    private static boolean isAirborne(LivingEntity entity) {
        if (entity.isOnGround() || entity.isTouchingWater() || entity.isClimbing() || entity.hasVehicle()) {
            return false;
        }
        return !(entity instanceof PlayerEntity player && player.getAbilities().flying);
    }

    public static List<String> explain(LivingEntity entity) {
        return List.of(
                "perfil ativo = " + select(entity),
                "voando=" + entity.isFallFlying() + ", elytra=" + EquipmentCompat.hasElytra(entity)
                        + ", noAr=" + isAirborne(entity) + ", peitoral=" + hasChestplate(entity)
                        + ", mochila=" + BackpackCompat.hasBackpack(entity)
                        + ", itensNasCostas=" + BackSlotCompat.backItems(entity));
    }
}
