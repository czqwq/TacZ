package com.tacz.guns.client.resource;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.handler.FirstPersonRenderHandler;
import com.google.common.collect.Maps;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.index.*;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.AmmoIndexPOJO;
import com.tacz.guns.resource.pojo.AttachmentIndexPOJO;
import com.tacz.guns.resource.pojo.BlockIndexPOJO;
import com.tacz.guns.resource.pojo.GunIndexPOJO;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClientIndexManager {

    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final AtomicLong RESOURCE_REVISION = new AtomicLong();
    private static final Set<ResourceLocation> WARNED_MISSING_GUN_DISPLAYS = new HashSet<>();

    public static final Map<ResourceLocation, GunDisplayInstance> GUN_DISPLAY = Maps.newHashMap();
    public static final Map<ResourceLocation, ClientGunIndex> GUN_INDEX = Maps.newHashMap();
    public static final Map<ResourceLocation, ClientAmmoIndex> AMMO_INDEX = Maps.newHashMap();
    public static final Map<ResourceLocation, ClientAttachmentIndex> ATTACHMENT_INDEX = Maps.newHashMap();
    public static final Map<ResourceLocation, ClientAttachmentVariantIndex> ATTACHMENT_VARIANT_INDEX = Maps
        .newHashMap();
    public static final Map<ResourceLocation, ClientSlotAdapterIndex> SLOT_ADAPTER_INDEX = Maps.newHashMap();
    public static final Map<ResourceLocation, ClientBlockIndex> BLOCK_INDEX = Maps.newHashMap();

    public static void clear() {
        ClientAssetLoadDispatcher.invalidate();
        RESOURCE_REVISION.incrementAndGet();
        GUN_DISPLAY.clear();
        WARNED_MISSING_GUN_DISPLAYS.clear();
        GUN_INDEX.clear();
        AMMO_INDEX.clear();
        ATTACHMENT_INDEX.clear();
        ATTACHMENT_VARIANT_INDEX.clear();
        SLOT_ADAPTER_INDEX.clear();
        BLOCK_INDEX.clear();
    }

    public static long resourceRevision() {
        return RESOURCE_REVISION.get();
    }

    public static void reload() {
        clear();
        try {
            loadGunDisplay();
            loadGunIndex();
            loadAmmoIndex();
            loadAttachmentIndex();
            loadAttachmentVariantIndex();
            loadSlotAdapterIndex();
            loadBlockIndex();
            preloadExternalModels();
            warmUpInventoryModels();
        } finally {
            // 使重载过程中暂时解析失败的展示槽位在资源就绪后再次解析。
            RESOURCE_REVISION.incrementAndGet();
        }

        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player != null && IGun.mainHandHoldGun(player)) {
            AttachmentPropertyManager.postChangeEvent(player, player.getHeldItem());

            // 自动切一次枪，以便刷新状态机
            IClientPlayerGunOperator.fromLocalPlayer(player)
                .draw(null);
            // 恢复上游的「重载后清理第一人称渲染缓存」。
            // 上游依据：tmp/TACZ-1.20.1/.../client/resource/ClientIndexManager.java:76-83 ——
            // LocalPlayer player = Minecraft.getInstance().player;
            // if (player != null && IGun.mainHandHoldGun(player)) {
            // ...draw(ItemStack.EMPTY);
            // FirstPersonRenderHandler.reset(); // ← 就在 draw 之后、同一个 if 内
            // }
            //
            // 为什么位置只能在这里（三条理由，均为上游/本仓语义，不是随笔）：
            // (1) 不能放在方法最前面：reset() 要清的是 MAIN_STATE/OFF_STATE 里指向**旧**
            // GunDisplayInstance 动画实例的 activeInstance，而旧 display 是 `clear()`（:63）里
            // 才被丢弃、新 display 是 loadGunDisplay()（:65）才创建 —— 必须在它们之后才谈得上「清旧」。
            // (2) 不能放在 try/finally 之外的无条件位置：上游把它放在
            // `player != null && IGun.mainHandHoldGun(player)` 门内 —— 玩家不在世界 / 主手不是枪时
            // 根本不存在第一人称枪械渲染状态，无条件调用只会无意义地清一次（还会把 PARTICLE_SYSTEM
            // 的 hasPrevCam 打掉，多一次 dt 归零）。
            // (3) 必须在 draw(null) **之后**：draw() 要在新建的 GunDisplayInstance 的状态机上重触发抽枪，
            // 而 reset() 丢弃的是旧 display 的包装实例；顺序反过来会把刚驱动起来的抽枪过渡再清掉。
            // 上游同位置即 draw 之后（见上面的 76-83 行引用）。
            //
            // 物理侧安全（无需额外守卫，理由）：本类 `@SideOnly(Side.CLIENT)`（:29），且 `reload()`
            // 早在 :79 就无条件使用 `Minecraft.getMinecraft()`（服务端没有该类）⇒ 本方法本来就
            // 不可在专服执行；两个调用点也都只在客户端：
            // · client/resource/ClientAssetsManager.java:216（该类 @SideOnly(CLIENT)，:78）
            // · client/ClientProxy.java:446（ClientProxy 只在 CLIENT 侧实例化）
            // 因此这里引用 client 侧的 FirstPersonRenderHandler 不引入任何新的物理侧类加载风险。
            //
            // 清粒子是期望行为：
            // ·
            // FirstPersonRenderHandler.reset()（com/github/.../v1/client/handler/FirstPersonRenderHandler.java:142-151）
            // 会 `PARTICLE_SYSTEM.clear()`（= discardEmitters() + hasPrevCam=false，
            // 见 v1/particle/firstperson/FirstPersonParticleSystem.java:221-224），
            // 丢弃的是**第一人称本机载体发射器**；已投递到原版 ParticleEngine 的世界空间粒子不受影响
            // （同文件 discardEmitters 的 javadoc 明写）。
            // 重载后部落的 ParticleEffectDefinition（ParticleDefinitionLoader 缓存被
            // client/particle/ParticleDefinitionReloader 整表重建）与旧贴图都已失效，
            // 继续保留发射器只会用旧定义/旧纹理继续喷 —— 清掉正是想要的行为。
            // · reset() 还会把 lastParticleTickNanos 归零：F3+T / 换包这类重载耗时可达数秒，
            // 不归零则下一帧 dt 是「秒级」，粒子会按巨大时间步积算出爆量/瞬移。
            // · 同类语义已在断线时使用（同文件 :137-140 onPlayerLoggedOut → reset()），
            // 即「资源/世界上下文失效 → 清第一人称状态」是本仓既有约定。
            FirstPersonRenderHandler.reset();
        }
    }

    public static void loadGunIndex() {
        TimelessAPI.getAllCommonGunIndex()
            .forEach(index -> {
                ResourceLocation id = index.getKey();
                GunIndexPOJO pojo = index.getValue()
                    .getPojo();
                try {
                    GUN_INDEX.put(id, ClientGunIndex.getInstance(pojo));
                } catch (IllegalArgumentException exception) {
                    GunMod.LOGGER.warn("{} index file read fail!", id, exception);
                }
            });
    }

    public static void loadGunDisplay() {
        ClientAssetsManager.INSTANCE.getGunDisplays()
            .forEach(entry -> {
                ResourceLocation displayId = entry.getKey();
                try {
                    GUN_DISPLAY.put(displayId, GunDisplayInstance.create(displayId, entry.getValue()));
                } catch (IllegalArgumentException exception) {
                    GunMod.LOGGER.warn("{} display file read fail!", displayId, exception);
                }
            });
    }

    public static GunDisplayInstance getOrCreateGunDisplay(ResourceLocation displayId) {
        GunDisplayInstance instance = GUN_DISPLAY.get(displayId);
        if (instance != null) {
            return instance;
        }
        if (WARNED_MISSING_GUN_DISPLAYS.add(displayId)) {
            GunMod.LOGGER.warn(
                "Gun display {} is unavailable (assets/{}/display/guns/{}.json);",
                displayId,
                displayId.getResourceDomain(),
                displayId.getResourcePath());
        }
        return null;
    }

    public static void loadAmmoIndex() {
        TimelessAPI.getAllCommonAmmoIndex()
            .forEach(index -> {
                ResourceLocation id = index.getKey();
                AmmoIndexPOJO pojo = index.getValue()
                    .getPojo();
                try {
                    AMMO_INDEX.put(id, ClientAmmoIndex.getInstance(pojo));
                } catch (IllegalArgumentException exception) {
                    GunMod.LOGGER.warn("{} index file read fail!", id, exception);
                }
            });
    }

    public static void loadAttachmentIndex() {
        TimelessAPI.getAllCommonAttachmentIndex()
            .forEach(index -> {
                ResourceLocation id = index.getKey();
                AttachmentIndexPOJO pojo = index.getValue()
                    .getPojo();
                try {
                    ATTACHMENT_INDEX.put(id, ClientAttachmentIndex.getInstance(id, pojo));
                } catch (IllegalArgumentException exception) {
                    GunMod.LOGGER.warn("{} index file read fail!", id, exception);
                }
            });
    }

    public static void loadAttachmentVariantIndex() {
        TimelessAPI.getAllCommonAttachmentVariantIndex()
            .forEach(index -> {
                ResourceLocation id = index.getKey();
                try {
                    ATTACHMENT_VARIANT_INDEX.put(id, ClientAttachmentVariantIndex.getInstance(index.getValue()));
                } catch (IllegalArgumentException exception) {
                    GunMod.LOGGER.warn("{} attachment variant index file read fail!", id, exception);
                }
            });
    }

    public static void loadSlotAdapterIndex() {
        TimelessAPI.getAllCommonSlotAdapterIndex()
            .forEach(index -> {
                ResourceLocation id = index.getKey();
                try {
                    SLOT_ADAPTER_INDEX.put(id, ClientSlotAdapterIndex.getInstance(index.getValue()));
                } catch (IllegalArgumentException exception) {
                    GunMod.LOGGER.warn("{} slot adapter index file read fail!", id, exception);
                }
            });
    }

    public static void loadBlockIndex() {
        TimelessAPI.getAllCommonBlockIndex()
            .forEach(index -> {
                ResourceLocation id = index.getKey();
                BlockIndexPOJO pojo = index.getValue()
                    .getPojo();
                try {
                    BLOCK_INDEX.put(id, ClientBlockIndex.getInstance(pojo));
                } catch (IllegalArgumentException exception) {
                    GunMod.LOGGER.warn("{} index file read fail!", id, exception);
                }
            });
    }

    public static Set<Map.Entry<ResourceLocation, ClientGunIndex>> getAllGuns() {
        return GUN_INDEX.entrySet();
    }

    public static Set<Map.Entry<ResourceLocation, ClientAmmoIndex>> getAllAmmo() {
        return AMMO_INDEX.entrySet();
    }

    public static Set<Map.Entry<ResourceLocation, ClientAttachmentIndex>> getAllAttachments() {
        return ATTACHMENT_INDEX.entrySet();
    }

    public static Set<Map.Entry<ResourceLocation, ClientAttachmentVariantIndex>> getAllAttachmentVariants() {
        return ATTACHMENT_VARIANT_INDEX.entrySet();
    }

    public static Set<Map.Entry<ResourceLocation, ClientSlotAdapterIndex>> getAllSlotAdapters() {
        return SLOT_ADAPTER_INDEX.entrySet();
    }

    public static Set<Map.Entry<ResourceLocation, ClientBlockIndex>> getAllBlocks() {
        return BLOCK_INDEX.entrySet();
    }

    /** 重载时预加载默认命名空间的模型，其他枪包继续按需加载并由背包预热。 */
    private static void preloadExternalModels() {
        List<CompletableFuture<?>> tasks = new ArrayList<>();
        for (GunDisplayInstance display : GUN_DISPLAY.values()) {
            tasks.add(
                display.getExternalModels()
                    .preload());
        }
        for (ClientAttachmentIndex index : ATTACHMENT_INDEX.values()) {
            tasks.add(
                index.getExternalModels()
                    .preload());
        }
        for (ClientAttachmentVariantIndex index : ATTACHMENT_VARIANT_INDEX.values()) {
            tasks.add(
                index.getExternalModels()
                    .preload());
        }
        for (ClientSlotAdapterIndex index : SLOT_ADAPTER_INDEX.values()) {
            tasks.add(
                index.getExternalModels()
                    .preload());
        }

        // 只在资源重载阶段等待；外部模型的渲染入口不会等待后台任务。
        CompletableFuture.allOf(tasks.toArray(CompletableFuture<?>[]::new))
            .join();
    }

    public static void warmUpInventoryModels() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }
        warmUpItemForUse(player.getHeldItem());
        // TODO(port): 1.7.10 没有副手槽位，上游 getOffhandItem() 无对应，此处传 null 跳过副手预热
        warmUpItemForUse(null);
        warmUpHotbarModels(player);
        warmUpBackpackModels(player);
    }

    public static void warmUpEquippedAndHotbarModels() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }
        warmUpItemForUse(player.getHeldItem());
        // TODO(port): 1.7.10 没有副手槽位，上游 getOffhandItem() 无对应，此处传 null 跳过副手预热
        warmUpItemForUse(null);
        warmUpHotbarModels(player);
    }

    public static void warmUpBackpackModels() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }
        warmUpBackpackModels(player);
    }

    private static void warmUpHotbarModels(EntityPlayerSP player) {
        // 1.20: Inventory#items 是 List<ItemStack>；1.7.10: InventoryPlayer#mainInventory 是 ItemStack[]
        ItemStack[] items = player.inventory.mainInventory;
        int hotbarSize = Math.min(HOTBAR_SLOT_COUNT, items.length);
        for (int i = 0; i < hotbarSize; i++) {
            warmUpItemModel(items[i]);
        }
    }

    private static void warmUpBackpackModels(EntityPlayerSP player) {
        ItemStack[] items = player.inventory.mainInventory;
        for (int i = Math.min(HOTBAR_SLOT_COUNT, items.length); i < items.length; i++) {
            warmUpItemModel(items[i]);
        }
    }

    public static void warmUpItem(ItemStack stack) {
        warmUpItemForUse(stack);
    }

    public static void warmUpItemModel(ItemStack stack) {
        // 1.20: ItemStack#isEmpty()；1.7.10: 没有该哨兵，判 null 与 air
        if (stack == null || stack.getItem() == null || stack.stackSize <= 0) {
            return;
        }
        if (stack.getItem() instanceof IGun) {
            TimelessAPI.getGunDisplay(stack)
                .ifPresent(display -> {
                    display.warmUpLod();
                    display.warmUpModel();
                    display.warmUpRuntime();
                });
            return;
        }
        IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
        if (attachment != null) {
            ResourceLocation attachmentId = attachment.getAttachmentId(stack);
            TimelessAPI.getClientAttachmentIndex(attachmentId)
                .ifPresent(ClientAttachmentIndex::warmUp);
            TimelessAPI.getClientAttachmentVariantIndex(stack)
                .ifPresent(ClientAttachmentVariantIndex::warmUp);
            return;
        }
        IAmmo ammo = IAmmo.getIAmmoOrNull(stack);
        if (ammo != null) {
            TimelessAPI.getClientAmmoIndex(ammo.getAmmoId(stack))
                .ifPresent(ClientAmmoIndex::warmUp);
        }
    }

    public static void warmUpItemForUse(ItemStack stack) {
        // 1.20: ItemStack#isEmpty()；1.7.10: 没有该哨兵，判 null 与 air
        if (stack == null || stack.getItem() == null || stack.stackSize <= 0) {
            return;
        }
        if (stack.getItem() instanceof IGun) {
            TimelessAPI.getGunDisplay(stack)
                .ifPresent(display -> {
                    display.warmUpLod();
                    display.warmUpModel();
                    display.warmUpRuntime();
                });
            return;
        }
        warmUpItemModel(stack);
    }
}
