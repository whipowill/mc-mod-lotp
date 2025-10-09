package com.whipowill.beastmaster.mixins;

import com.whipowill.beastmaster.BeastMasterMod;
import com.whipowill.beastmaster.BeastConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(LivingEntity.class)
public abstract class GenericPetMountMixin {

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity livingEntity = (LivingEntity)(Object)this;
        Entity entity = (Entity)(Object)this;

        // Only process if it's one of our supported entities
        if (!BeastConfig.isSupportedPet(entity) && !BeastConfig.isSupportedMount(entity)) {
            return;
        }

        if (BeastMasterMod.isOwned(entity)) {
            // Check if friendly fire is disabled and damage is from owner
            if (BeastMasterMod.CONFIG != null &&
                BeastMasterMod.CONFIG.disableFriendlyFire &&
                isDamageFromOwner(source, entity)) {

                //LOGGER.debug("Blocked friendly fire damage to {} from owner", entity.getUuid());
                cir.setReturnValue(false); // Cancel the damage
                return;
            }

            boolean isPet = BeastMasterMod.CONFIG != null &&
                           BeastMasterMod.CONFIG.petImmortal &&
                           BeastConfig.isSupportedPet(entity);

            boolean isMount = BeastMasterMod.CONFIG != null &&
                             BeastMasterMod.CONFIG.mountImmortal &&
                             BeastConfig.isSupportedMount(entity);

            // If immortal and health would drop below 1, cancel the damage
            if ((isPet || isMount) && livingEntity.getHealth() - amount <= 0) {
                livingEntity.setHealth(1.0F);

                // For wolves, clear anger when health is low
                if (BeastMasterMod.CONFIG != null && entity instanceof WolfEntity wolf) {
                    float healthPercent = (livingEntity.getHealth() / livingEntity.getMaxHealth()) * 100;
                    if (healthPercent <= BeastMasterMod.CONFIG.healthRequiredToFight) {
                        wolf.setAngryAt(null);
                        wolf.setTarget(null);
                    }
                }

                cir.setReturnValue(false);
            }
        }
    }

    private boolean isDamageFromOwner(DamageSource source, Entity entity) {
        // Check if damage source is the owner
        if (source.getAttacker() instanceof PlayerEntity attacker) {
            UUID ownerUuid = BeastMasterMod.getOwnerUuid(entity);
            return attacker.getUuid().equals(ownerUuid);
        }
        return false;
    }
}