package com.whipowill.beastmaster;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class PackManager extends PersistentState {
    private static final Logger LOGGER = LoggerFactory.getLogger("PackManager");
    private static final String ENTITIES_KEY = "beastmaster_entities";

    public static class EntityData {
        public final UUID entityUuid;
        public final UUID ownerUuid;
        public final RegistryKey<World> dimension;
        public final double x, y, z;
        public final long timestamp;
        public NbtCompound entityNbt;
        public String customName;
        public boolean isPet;

        public EntityData(UUID entityUuid, UUID ownerUuid, RegistryKey<World> dimension, double x, double y, double z, boolean isPet) {
            this.entityUuid = entityUuid;
            this.ownerUuid = ownerUuid;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.timestamp = System.currentTimeMillis();
            this.isPet = isPet;
        }
    }

    private final Map<UUID, EntityData> entityDataMap = new HashMap<>();

    public static PackManager get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
            PackManager::fromNbt,
            PackManager::new,
            "beastmaster_data" // Use consistent name, don't append world path
        );
    }

    public static PackManager fromNbt(NbtCompound nbt) {
        PackManager manager = new PackManager();
        try {
            NbtList entitiesList = nbt.getList(ENTITIES_KEY, 10);

            for (int i = 0; i < entitiesList.size(); i++) {
                NbtCompound entry = entitiesList.getCompound(i);

                // Check if required fields exist
                if (!entry.containsUuid("entityUUID") || !entry.containsUuid("ownerUUID")) {
                    LOGGER.warn("Skipping invalid entity entry: missing UUIDs");
                    continue;
                }

                UUID entityUuid = entry.getUuid("entityUUID");
                UUID ownerUuid = entry.getUuid("ownerUUID");
                String dimensionStr = entry.getString("dimension");
                double x = entry.getDouble("x");
                double y = entry.getDouble("y");
                double z = entry.getDouble("z");
                boolean isPet = entry.getBoolean("isPet");

                RegistryKey<World> dimension = RegistryKey.of(net.minecraft.util.registry.Registry.WORLD_KEY,
                    new net.minecraft.util.Identifier(dimensionStr));

                EntityData entityData = new EntityData(entityUuid, ownerUuid, dimension, x, y, z, isPet);

                if (entry.contains("entityNbt", 10)) { // 10 = COMPOUND type
                    entityData.entityNbt = entry.getCompound("entityNbt");
                }

                if (entry.contains("customName", 8)) { // 8 = STRING type
                    entityData.customName = entry.getString("customName");
                }

                manager.entityDataMap.put(entityUuid, entityData);
            }
            LOGGER.info("Loaded {} entity registrations from storage", manager.entityDataMap.size());
        } catch (Exception e) {
            LOGGER.error("Error loading PackManager from NBT", e);
        }
        return manager;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        try {
            NbtList entitiesList = new NbtList();

            for (EntityData entityData : entityDataMap.values()) {
                NbtCompound entry = new NbtCompound();
                entry.putUuid("entityUUID", entityData.entityUuid);
                entry.putUuid("ownerUUID", entityData.ownerUuid);
                entry.putString("dimension", entityData.dimension.getValue().toString());
                entry.putDouble("x", entityData.x);
                entry.putDouble("y", entityData.y);
                entry.putDouble("z", entityData.z);
                entry.putBoolean("isPet", entityData.isPet);

                if (entityData.entityNbt != null) {
                    entry.put("entityNbt", entityData.entityNbt);
                }

                if (entityData.customName != null) {
                    entry.putString("customName", entityData.customName);
                }

                entitiesList.add(entry);
            }

            nbt.put(ENTITIES_KEY, entitiesList);
            LOGGER.debug("Saved {} entities to NBT", entitiesList.size());
        } catch (Exception e) {
            LOGGER.error("Error saving PackManager to NBT", e);
        }
        return nbt;
    }

    public void storeEntityNbt(Entity entity) {
        try {
            UUID entityUuid = entity.getUuid();
            UUID ownerUuid = BeastMasterMod.getOwnerUuid(entity);

            if (ownerUuid != null && entity.isAlive()) {
                RegistryKey<World> dimension = entity.getWorld().getRegistryKey();
                Vec3d pos = entity.getPos();

                boolean isPet = BeastConfig.isSupportedPet(entity);

                // Save entity to NBT
                NbtCompound entityNbt = new NbtCompound();
                entity.saveNbt(entityNbt);

                // Ensure the UUID is preserved in NBT
                if (!entityNbt.containsUuid("UUID")) {
                    entityNbt.putUuid("UUID", entityUuid);
                }

                EntityData newData = new EntityData(entityUuid, ownerUuid, dimension, pos.x, pos.y, pos.z, isPet);
                newData.entityNbt = entityNbt;

                if (entity.hasCustomName()) {
                    newData.customName = entity.getCustomName().getString();
                } else {
                    newData.customName = null;
                }

                EntityData oldData = entityDataMap.put(entityUuid, newData);
                markDirty(); // This is crucial!

                if (oldData != null) {
                    LOGGER.debug("Updated entity NBT: {} in {}", entityUuid, dimension.getValue());
                } else {
                    LOGGER.info("Registered {} with NBT: {} in {} for owner {}",
                        isPet ? "pet" : "mount", entityUuid, dimension.getValue(), ownerUuid);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error storing entity NBT for {}", entity.getUuid(), e);
        }
    }

    public void untrackEntity(UUID entityUuid) {
        try {
            if (entityDataMap.remove(entityUuid) != null) {
                markDirty();
                LOGGER.info("Untracked entity: {}", entityUuid);
            }
        } catch (Exception e) {
            LOGGER.error("Error untracking entity", e);
        }
    }

    public List<EntityData> getEntitiesByOwner(UUID ownerUuid) {
        List<EntityData> result = new ArrayList<>();
        for (EntityData data : entityDataMap.values()) {
            if (data.ownerUuid.equals(ownerUuid)) {
                result.add(data);
            }
        }
        return result;
    }

    public List<EntityData> getPetsByOwner(UUID ownerUuid) {
        List<EntityData> result = new ArrayList<>();
        for (EntityData data : entityDataMap.values()) {
            if (data.ownerUuid.equals(ownerUuid) && data.isPet) {
                result.add(data);
            }
        }
        return result;
    }

    public List<EntityData> getMountsByOwner(UUID ownerUuid) {
        List<EntityData> result = new ArrayList<>();
        for (EntityData data : entityDataMap.values()) {
            if (data.ownerUuid.equals(ownerUuid) && !data.isPet) {
                result.add(data);
            }
        }
        return result;
    }

    public boolean isEntityTracked(UUID entityUuid) {
        return entityDataMap.containsKey(entityUuid);
    }

    public Optional<EntityData> getEntityData(UUID entityUuid) {
        return Optional.ofNullable(entityDataMap.get(entityUuid));
    }

    public List<EntityData> getAllEntities() {
        return new ArrayList<>(entityDataMap.values());
    }

    // Add a method to clear all data (for debugging)
    public void clearAllData() {
        entityDataMap.clear();
        markDirty();
        LOGGER.info("Cleared all entity data");
    }
}