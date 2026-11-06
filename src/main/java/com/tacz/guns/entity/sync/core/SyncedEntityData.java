package com.tacz.guns.entity.sync.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import com.google.common.collect.ImmutableSet;
import com.tacz.guns.GunMod;
import com.tacz.guns.init.CommonRegistry;
import com.tacz.guns.network.message.handshake.ServerMessageSyncedEntityDataMapping;

/**
 * 搬运自上游 1.20.1 {@code entity/sync/core/SyncedEntityData.java}（MrCrayfish Framework，LGPL；
 * TACZ 版本增加了 handshake 映射与 dirty 收集）。
 *
 * <p>
 * <b>1.7.10 形态（重要设计决定）</b>：上游用 Forge capability（{@code DataHolderCapabilityProvider}）
 * 把 {@link DataHolder} 挂在实体上；1.7.10 没有 capability（也没有 {@code ICapabilityProvider}/{@code LazyOptional}），
 * 因此改为**静态 per-entity 数据管理器**：
 * <ul>
 * <li>{@code WeakHashMap<Entity, DataHolder>} 保存「实体 → 数据」映射：key 是弱引用，实体卸载后可被 GC 回收；
 * 值 {@link DataHolder} 不反向持有实体，因此不会造成泄漏；</li>
 * <li>{@link #getDataHolder(Entity)} 在首次访问时按需创建 holder（等价上游 attach capability 的时机）；</li>
 * <li><b>不使用 DataWatcher</b>：1.7.10 的 DataWatcher 只有 32 个槽位且类型受限
 * （byte/short/int/float/String/ItemStack），装不下 {@code ReloadState} 这类复合值，
 * 也无法表达上游的「key ↔ 内部 id」映射与 dirty 批量同步；</li>
 * <li>NBT 持久化：上游 capability 用 {@code saveToFile}/{@code serializeNBT}；本模组所有 synced key 都是
 * {@code save=false}（见 {@code ModSyncedEntityData}），因此 1.7.10 不需要 capability 序列化；
 * 若未来有 save=true 的 key，按 {@code DataEntry#writeValue/readValue} 自行存进
 * {@code EntityPlayer#getEntityData()} 即可（已在 {@code event/SyncedEntityDataEvent} 注明）。</li>
 * </ul>
 */
public class SyncedEntityData {

    private static final Marker SYNCED_ENTITY_DATA_MARKER = MarkerManager.getMarker("SYNCED_ENTITY_DATA_TAC_COPY");
    private static SyncedEntityData INSTANCE;

    private final Set<SyncedClassKey<?>> registeredClassKeys = new HashSet<>();
    private final Map<ResourceLocation, SyncedClassKey<?>> idToClassKey = new HashMap<>();
    private final Map<String, SyncedClassKey<?>> classNameToClassKey = new HashMap<>();
    private final Map<String, Boolean> clientClassNameCapabilityCache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> serverClassNameCapabilityCache = new ConcurrentHashMap<>();

    private final Set<SyncedDataKey<?, ?>> registeredDataKeys = new HashSet<>();
    private final Map<SyncedClassKey<?>, HashMap<ResourceLocation, SyncedDataKey<?, ?>>> classToKeys = new HashMap<>();
    private final Map<SyncedDataKey<?, ?>, Integer> internalIds = new HashMap<>();
    private final Map<Integer, SyncedDataKey<?, ?>> syncedIdToKey = new HashMap<>();

    /**
     * 1.7.10 替代 capability 的数据管理器：实体 → DataHolder（弱 key，实体回收后自动释放）。
     */
    private final Map<Entity, DataHolder> dataHolders = Collections.synchronizedMap(new WeakHashMap<>());

    private final AtomicInteger nextIdTracker = new AtomicInteger();
    private final List<Entity> dirtyEntities = new ArrayList<>();
    private boolean dirty = false;

    private SyncedEntityData() {}

    public static SyncedEntityData instance() {
        if (INSTANCE == null) {
            INSTANCE = new SyncedEntityData();
        }
        return INSTANCE;
    }

    private <E extends Entity> void registerClassKey(SyncedClassKey<E> classKey) {
        if (!this.registeredClassKeys.contains(classKey)) {
            this.registeredClassKeys.add(classKey);
            this.idToClassKey.put(classKey.id(), classKey);
            this.classNameToClassKey.put(
                classKey.entityClass()
                    .getName(),
                classKey);
        }
    }

    /**
     * 注册一个 synced data key（由 {@code ModSyncedEntityData#init()} 在 init 阶段调用）。
     */
    public synchronized <E extends Entity, T> void registerDataKey(SyncedDataKey<E, T> dataKey) {
        ResourceLocation keyId = dataKey.id();
        SyncedClassKey<E> classKey = dataKey.classKey();
        if (CommonRegistry.isLoadComplete()) {
            throw new IllegalStateException(
                String.format(
                    "Tried to register synced data key %s for %s after game initialization",
                    keyId,
                    classKey.id()));
        }
        if (this.registeredDataKeys.contains(dataKey)) {
            throw new IllegalArgumentException(
                String.format("The synced data key %s for %s is already registered", keyId, classKey.id()));
        }
        this.registerClassKey(dataKey.classKey());
        this.registeredDataKeys.add(dataKey);
        this.classToKeys.computeIfAbsent(classKey, c -> new HashMap<>())
            .put(keyId, dataKey);
        int nextId = this.nextIdTracker.getAndIncrement();
        this.internalIds.put(dataKey, nextId);
        this.syncedIdToKey.put(nextId, dataKey);
        GunMod.LOGGER
            .info(SYNCED_ENTITY_DATA_MARKER, "Registered synced data key {} for {}", dataKey.id(), classKey.id());
    }

    public <E extends Entity, T> void set(E entity, SyncedDataKey<?, ?> key, T value) {
        if (!this.registeredDataKeys.contains(key)) {
            String keys = this.registeredDataKeys.stream()
                .map(
                    k -> k.pairKey()
                        .toString())
                .collect(Collectors.joining(",", "[", "]"));
            GunMod.LOGGER.info(SYNCED_ENTITY_DATA_MARKER, "Registered keys before throwing exception: {}", keys);
            throw new IllegalArgumentException(
                String.format(
                    "The synced data key %s for %s is not registered!",
                    key.id(),
                    key.classKey()
                        .id()));
        }
        DataHolder holder = this.getDataHolder(entity);
        if (holder != null && holder.set(entity, key, value)) {
            if (!entity.worldObj.isRemote) {
                this.dirty = true;
                this.dirtyEntities.add(entity);
            }
        }
    }

    public <E extends Entity, T> T get(E entity, SyncedDataKey<E, T> key) {
        if (!this.registeredDataKeys.contains(key)) {
            String keys = this.registeredDataKeys.stream()
                .map(
                    k -> k.pairKey()
                        .toString())
                .collect(Collectors.joining(",", "[", "]"));
            GunMod.LOGGER.info(SYNCED_ENTITY_DATA_MARKER, "Registered keys before throwing exception: {}", keys);
            throw new IllegalArgumentException(
                String.format(
                    "The synced data key %s for %s is not registered!",
                    key.id(),
                    key.classKey()
                        .id()));
        }
        DataHolder holder = this.getDataHolder(entity);
        return holder != null ? holder.get(key)
            : key.defaultValueSupplier()
                .get();
    }

    public int getInternalId(SyncedDataKey<?, ?> key) {
        return this.internalIds.getOrDefault(key, -1);
    }

    @Nullable
    public SyncedClassKey<?> getClassKey(ResourceLocation id) {
        return idToClassKey.get(id);
    }

    @Nullable
    public SyncedDataKey<?, ?> getKey(int id) {
        return this.syncedIdToKey.get(id);
    }

    @Nullable
    public SyncedDataKey<?, ?> getKey(SyncedClassKey<?> classKey, ResourceLocation dataKey) {
        Map<ResourceLocation, SyncedDataKey<?, ?>> keys = this.classToKeys.get(classKey);
        if (keys == null) {
            return null;
        }
        return keys.get(dataKey);
    }

    public Set<SyncedDataKey<?, ?>> getKeys() {
        return ImmutableSet.copyOf(this.registeredDataKeys);
    }

    /**
     * 1.7.10：capability 替换为静态管理器，按需创建（见类注释）。
     *
     * @return 若实体类型没有注册任何 synced key，返回 null（与上游 capability 缺失一致）
     */
    @Nullable
    public DataHolder getDataHolder(Entity entity) {
        if (entity == null) {
            return null;
        }
        DataHolder holder = this.dataHolders.get(entity);
        if (holder == null) {
            if (!this.hasSyncedDataKey(entity)) {
                return null;
            }
            holder = new DataHolder();
            this.dataHolders.put(entity, holder);
        }
        return holder;
    }

    public boolean hasSyncedDataKey(Entity entity) {
        Class<? extends Entity> entityClass = entity.getClass();
        return this.getClassNameCapabilityCache(entity.worldObj != null && entity.worldObj.isRemote)
            .computeIfAbsent(entityClass.getName(), c -> {
                Class<?> targetClass = entityClass;
                // 沿继承链向上找（Entities 的父类若注册过 key 也算命中）
                while (!targetClass.isAssignableFrom(Entity.class)) {
                    if (this.classNameToClassKey.containsKey(targetClass.getName())) {
                        return true;
                    }
                    targetClass = targetClass.getSuperclass();
                }
                return false;
            });
    }

    private Map<String, Boolean> getClassNameCapabilityCache(boolean client) {
        return client ? this.clientClassNameCapabilityCache : this.serverClassNameCapabilityCache;
    }

    public boolean updateMappings(ServerMessageSyncedEntityDataMapping message) {
        this.syncedIdToKey.clear();

        List<Pair<ResourceLocation, ResourceLocation>> missingKeys = new ArrayList<>();
        message.getKeyMap()
            .forEach((classId, list) -> {
                SyncedClassKey<?> classKey = this.idToClassKey.get(classId);
                if (classKey == null || !this.classToKeys.containsKey(classKey)) {
                    list.forEach(pair -> missingKeys.add(Pair.of(classId, pair.getLeft())));
                    return;
                }

                Map<ResourceLocation, SyncedDataKey<?, ?>> keys = this.classToKeys.get(classKey);
                list.forEach(pair -> {
                    SyncedDataKey<?, ?> syncedDataKey = keys.get(pair.getLeft());
                    if (syncedDataKey == null) {
                        missingKeys.add(Pair.of(classId, pair.getLeft()));
                        return;
                    }
                    this.syncedIdToKey.put(pair.getRight(), syncedDataKey);
                });
            });

        if (!missingKeys.isEmpty()) {
            String keys = missingKeys.stream()
                .map(Object::toString)
                .collect(Collectors.joining(",", "[", "]"));
            GunMod.LOGGER.info(SYNCED_ENTITY_DATA_MARKER, "Received unknown synced keys: {}", keys);
        }

        return missingKeys.isEmpty();
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public List<Entity> getDirtyEntities() {
        return dirtyEntities;
    }
}
