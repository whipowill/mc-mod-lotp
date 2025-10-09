package com.whipowill.beastmaster;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.passive.DonkeyEntity;
import net.minecraft.entity.passive.MuleEntity;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BeastMasterMod implements ModInitializer {
    public static final String MOD_ID = "beastmaster";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static BeastConfig CONFIG;

    // Cooldown tracking
    private static final Map<UUID, Long> playerWhistleCooldowns = new HashMap<>();
    private final Map<UUID, Long> mountBuckCooldowns = new HashMap<>();

    @Override
    public void onInitialize() {
        LOGGER.info("Leader of the Pack mod initialized!");

        // Load configuration
        CONFIG = BeastConfig.load();

        // Register commands
        BeastCommand.register();

        // Register entity tracking on load
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (isSupportedEntity(entity) && isOwned(entity) && getOwnerUuid(entity) != null) {
                try {
                    PackManager manager = PackManager.get((ServerWorld) world);
                    if (!manager.isEntityTracked(entity.getUuid())) {
                        manager.storeEntityNbt(entity);
                        LOGGER.debug("Tracked new entity on load: {}", entity.getUuid());
                    }
                } catch (Exception e) {
                    LOGGER.error("Error tracking entity on load", e);
                }
            }
        });

        // Smart saving: Mounts only, smaller radius, no pets
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            // Mount caching: every 5 seconds, 12 block radius
            if (server.getTicks() % 100 == 0) {
                for (ServerWorld world : server.getWorlds()) {
                    for (ServerPlayerEntity player : world.getPlayers()) {
                        // ONLY cache mounts within 12 blocks
                        for (Entity entity : world.getEntitiesByClass(Entity.class,
                                player.getBoundingBox().expand(12.0),
                                e -> BeastConfig.isSupportedMount(e) &&
                                     isOwnedByPlayer(e, player.getUuid()))) {
                            try {
                                PackManager manager = PackManager.get(world);
                                manager.storeEntityNbt(entity);
                            } catch (Exception e) {
                                LOGGER.error("Error in mount proximity save", e);
                            }
                        }
                    }
                }
            }

            // Apply regeneration effects if enabled
            if (server.getTicks() % 40 == 0) {
                for (ServerWorld world : server.getWorlds()) {
                    for (Entity entity : world.iterateEntities()) {
                        if (isSupportedEntity(entity) && isOwned(entity) && entity instanceof LivingEntity living) {
                            applyRegenEffects(living);
                        }
                    }
                }
            }

            // Clean up old cooldowns periodically
            if (server.getTicks() % 200 == 0) {
                cleanUpCooldowns();
            }
        });

        // Save on ALL interactions with owned entities
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient() && isSupportedEntity(entity)) {
                if (isOwnedByPlayer(entity, player.getUuid())) {
                    world.getServer().execute(() -> {
                        try {
                            PackManager manager = PackManager.get((ServerWorld) world);
                            manager.storeEntityNbt(entity);
                            LOGGER.debug("Updated entity on interaction: {}",
                                entity.getUuid());
                        } catch (Exception e) {
                            LOGGER.error("Error tracking entity on interaction", e);
                        }
                    });
                }
            }
            return ActionResult.PASS;
        });
    }

    private void applyRegenEffects(LivingEntity entity) {
        try {
            boolean isPet = BeastConfig.isSupportedPet(entity);
            boolean isMount = BeastConfig.isSupportedMount(entity);

            if (isPet && CONFIG.petRegen && entity.isAlive()) {
                // Apply regeneration effect to pets
                if (entity.getHealth() < entity.getMaxHealth()) {
                    entity.heal(1.0F);
                }
            }

            if (isMount && CONFIG.mountRegen && entity.isAlive()) {
                // Apply regeneration effect to mounts
                if (entity.getHealth() < entity.getMaxHealth()) {
                    entity.heal(1.0F);
                }
            }

            // Apply combat behavior for pets
            if (isPet && CONFIG.petImmortal) {
                if (entity.getHealth() <= (entity.getMaxHealth() * CONFIG.healthRequiredToFight / 100.0f)) {
                    // Stop attacking when health is low
                    if (entity instanceof WolfEntity wolf) {
                        wolf.setAngryAt(null); // Clear anger target in 1.18.2
                        wolf.setTarget(null);
                    }
                }
            }

            // Apply riding behavior for mounts - BUCK PLAYER OFF when injured!
            if (isMount && CONFIG.mountImmortal) {
                float healthPercent = (entity.getHealth() / entity.getMaxHealth()) * 100;
                if (healthPercent <= CONFIG.healthRequiredToMove) {
                    buckPlayerOff(entity);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error applying regeneration effects", e);
        }
    }

    // New method to buck player off injured mount
    private void buckPlayerOff(Entity entity) {
        try {
            if (entity.hasPassengers()) {
                UUID mountId = entity.getUuid();
                long now = System.currentTimeMillis();

                // Only buck every 2 seconds to avoid spam, but be persistent
                Long lastBuck = mountBuckCooldowns.get(mountId);
                if (lastBuck != null && now - lastBuck < 2000) {
                    return; // Still on cooldown
                }

                // Get all passengers and dismount them
                List<Entity> passengers = entity.getPassengerList();
                for (Entity passenger : passengers) {
                    if (passenger instanceof PlayerEntity) {
                        passenger.stopRiding();
                        mountBuckCooldowns.put(mountId, now); // Set cooldown
                        LOGGER.debug("Bucked player off injured mount: {}", entity.getUuid());

                        // Send message to player (first time or occasionally)
                        if (passenger instanceof ServerPlayerEntity player) {
                            if (lastBuck == null || now - lastBuck > 10000) { // Only message every 10 seconds
                                player.sendMessage(Text.of("§cYour mount is too injured to carry you!"), false);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error bucking player off mount", e);
        }
    }

    // Cooldown management methods
    public static boolean isPlayerOnCooldown(UUID playerUUID) {
        if (CONFIG.whistleCooldownSeconds <= 0) return false;

        Long lastUsed = playerWhistleCooldowns.get(playerUUID);
        if (lastUsed == null) return false;

        long cooldownMillis = CONFIG.whistleCooldownSeconds * 1000L;
        return System.currentTimeMillis() - lastUsed < cooldownMillis;
    }

    public static void setPlayerCooldown(UUID playerUUID) {
        if (CONFIG.whistleCooldownSeconds > 0) {
            playerWhistleCooldowns.put(playerUUID, System.currentTimeMillis());
        }
    }

    public static long getCooldownRemaining(UUID playerUUID) {
        if (CONFIG.whistleCooldownSeconds <= 0) return 0;

        Long lastUsed = playerWhistleCooldowns.get(playerUUID);
        if (lastUsed == null) return 0;

        long elapsed = System.currentTimeMillis() - lastUsed;
        long cooldownMillis = CONFIG.whistleCooldownSeconds * 1000L;
        return Math.max(0, cooldownMillis - elapsed);
    }

    private void cleanUpCooldowns() {
        long now = System.currentTimeMillis();
        long cooldownMillis = CONFIG.whistleCooldownSeconds * 1000L;

        playerWhistleCooldowns.entrySet().removeIf(entry ->
            now - entry.getValue() > cooldownMillis + 60000);

        // Also clean up mount buck cooldowns (keep for 30 seconds after last buck)
        mountBuckCooldowns.entrySet().removeIf(entry ->
            now - entry.getValue() > 30000);
    }

    // Helper method to check if entity is supported
    public static boolean isSupportedEntity(Entity entity) {
        return BeastConfig.isSupportedPet(entity) || BeastConfig.isSupportedMount(entity);
    }

    // Helper method to check if entity is owned
    public static boolean isOwned(Entity entity) {
        if (entity instanceof Tameable) {
            Tameable tameable = (Tameable) entity;
            return tameable.getOwnerUuid() != null; // If it has an owner, it's tamed
        }
        // For mounts, check both tamed and owner
        if (entity instanceof HorseEntity horse) {
            return horse.isTame() && horse.getOwnerUuid() != null;
        }
        if (entity instanceof DonkeyEntity donkey) {
            return donkey.isTame() && donkey.getOwnerUuid() != null;
        }
        if (entity instanceof MuleEntity mule) {
            return mule.isTame() && mule.getOwnerUuid() != null;
        }
        if (entity instanceof LlamaEntity llama) {
            return llama.isTame() && llama.getOwnerUuid() != null;
        }
        // For pigs with saddles
        if (entity instanceof PigEntity pig && pig.isSaddled() && pig.getFirstPassenger() instanceof PlayerEntity) {
            return true;
        }
        return false;
    }

    // Helper method to check if entity is owned by specific player
    public static boolean isOwnedByPlayer(Entity entity, UUID playerUUID) {
        if (entity instanceof Tameable) {
            Tameable tameable = (Tameable) entity;
            return tameable.getOwnerUuid() != null && tameable.getOwnerUuid().equals(playerUUID);
        }
        // Use the same logic as the working mount mod:
        if (entity instanceof HorseEntity horse) {
            return horse.isTame() && horse.getOwnerUuid() != null && horse.getOwnerUuid().equals(playerUUID);
        }
        if (entity instanceof DonkeyEntity donkey) {
            return donkey.isTame() && donkey.getOwnerUuid() != null && donkey.getOwnerUuid().equals(playerUUID);
        }
        if (entity instanceof MuleEntity mule) {
            return mule.isTame() && mule.getOwnerUuid() != null && mule.getOwnerUuid().equals(playerUUID);
        }
        if (entity instanceof LlamaEntity llama) {
            return llama.isTame() && llama.getOwnerUuid() != null && llama.getOwnerUuid().equals(playerUUID);
        }
        // For pigs with saddles
        if (entity instanceof PigEntity pig && pig.isSaddled() && pig.getFirstPassenger() instanceof PlayerEntity player) {
            return player.getUuid().equals(playerUUID);
        }
        return false;
    }

    // Helper method to get owner UUID
    public static UUID getOwnerUuid(Entity entity) {
        if (entity instanceof Tameable) {
            Tameable tameable = (Tameable) entity;
            return tameable.getOwnerUuid();
        }
        // For mounts
        if (entity instanceof HorseEntity horse) {
            return horse.getOwnerUuid();
        }
        if (entity instanceof DonkeyEntity donkey) {
            return donkey.getOwnerUuid();
        }
        if (entity instanceof MuleEntity mule) {
            return mule.getOwnerUuid();
        }
        if (entity instanceof LlamaEntity llama) {
            return llama.getOwnerUuid();
        }
        // For pigs, use the rider as owner
        if (entity instanceof PigEntity pig && pig.isSaddled() && pig.getFirstPassenger() instanceof PlayerEntity player) {
            return player.getUuid();
        }
        return null;
    }
}