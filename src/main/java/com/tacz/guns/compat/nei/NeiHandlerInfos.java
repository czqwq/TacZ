package com.tacz.guns.compat.nei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonGunIndex;

import codechicken.nei.event.NEIRegisterHandlerInfosEvent;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 给本模组的 NEI 配方页注册 {@link HandlerInfo}：页签图标、mod 名、页面尺寸。
 *
 * <p>
 * <b>为什么必须补这一步</b>：NEI 的配方页签信息来自 {@code GuiRecipeTab.handlerMap}（默认由
 * {@code assets/nei/csv/handlers.csv} 或 config 里的 handlers.csv 填充）。没登记的 handler 会落到
 * {@code DEFAULT_HANDLER_INFO}（{@code modName = "Unknown"}）⇒ 页签 tooltip 显示 mod 为 Unknown、
 * 页签没有物品图标（退回「页名前 2 个字符」的文字兜底）。
 *
 * <p>
 * <b>时序（实机踩过的坑）</b>：
 * <ol>
 * <li>NEI 在 {@code FMLLoadCompleteEvent}（{@code NEIModContainer#loadComplete}）里调用
 * {@code GuiRecipeTab.loadHandlerInfo()} 并 post {@link NEIRegisterHandlerInfosEvent}；</li>
 * <li>但本模组的 NEI 插件要等<b>进世界</b>后由 NEI 的后台线程实例化
 * （{@code NEIClientConfig#bootNEI → PluginLoader.load → loadConfig}），此时事件早就发过了；</li>
 * <li>而 {@code handlerMap} 的读写都在客户端线程，不能从 NEI 插件线程直接改。</li>
 * </ol>
 * 因此这里的做法是：在 {@code loadConfig()}（插件线程）把注册动作投递到客户端主线程
 * （{@code Minecraft#func_152344_a} = {@code addScheduledTask}，见 {@code Minecraft.java:2978-3015}），
 * 在主线程直接写入 {@code handlerMap}（等价于 {@code event.registerHandlerInfo(info)}）；
 * 事件监听器则由 {@code ClientProxy#init} 通过 {@link #registerListener()} <b>提前</b>挂到 Forge 总线
 * （见下一条），NEI 之后若重载 handler 信息（Shift+左键 options 按钮等）也能重新登记。
 *
 * <p>
 * <b>为什么监听器必须在 mod init 阶段注册</b>：① NEI 的 {@code NEIRegisterHandlerInfosEvent} 在
 * FML LoadComplete 就发出，晚于它的注册收不到；② Forge 1.7.10 的 {@code EventBus#register} 会读取
 * {@code Loader#activeModContainer()}，在 mod 加载期之外（如 NEI 后台线程/世界加载时）调用会刷一条
 * "Unable to determine registrant mod" 的 ERROR。
 *
 * <p>
 * <b>为什么本类必须 public</b>：Forge 1.7.10 的 {@code EventBus} 用 ASM 生成回调类，生成代码直接
 * {@code INVOKEVIRTUAL} 调用监听方法（{@code ASMEventHandler#createWrapper}）；监听器类是 private
 * 嵌套类时，JVM 访问检查会抛 {@code IllegalAccessError}。因此监听器放在 public 顶层类里
 * （GT5U 也是把 public 的 {@code NEIGTConfig} 实例挂到 Forge 总线）。
 *
 * <p>
 * <b>图标</b>：页签图标用「一个代表性物品栈」（上游 JEI 的 category icon 同样取创造栏/物品图标）。
 * 枪/弹药/配件图标要枪包索引数据，索引未加载时退回 null（页签显示文字兜底，不影响功能）；
 * 工作台图标可以直接从 {@link GameRegistry} 取方块物品，索引未加载也能显示。
 */
public final class NeiHandlerInfos {

    private static final String DEFAULT_MOD_NAME = "Timeless and Classics Guns: Zero";
    private static final String MOD_ID = GunMod.MOD_ID;
    private static final String AMMO_STORAGE_PATH = "ammo_storage";
    private static final NeiHandlerInfos INSTANCE = new NeiHandlerInfos();

    /** handler 信息监听器是否已挂到 Forge 总线（幂等）。 */
    private static boolean listenerRegistered;

    private NeiHandlerInfos() {}

    /**
     * 由 {@code ClientProxy#init}（mod 加载主线程，NEI 已安装时）反射调用：提前挂事件监听器。
     *
     * <p>
     * 必须在 mod 加载阶段调用（{@code EventBus#register} 依赖 {@code Loader#activeModContainer()}）。
     */
    public static void registerListener() {
        if (listenerRegistered) {
            return;
        }
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        listenerRegistered = true;
    }

    /**
     * 由 {@link NEITaczConfig#loadConfig()} 调用（NEI 插件线程）。
     *
     * <p>
     * 只做「投递」：真正的写 {@code handlerMap} 在客户端主线程执行。
     */
    static void register() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        minecraft.func_152344_a(NeiHandlerInfos::applyOnClientThread);
    }

    private static void applyOnClientThread() {
        List<HandlerInfo> infos = buildInfos();
        for (HandlerInfo info : infos) {
            // 与 NEIRegisterHandlerInfosEvent#registerHandlerInfo 的实现一致（该方法只做这一件事）。
            GuiRecipeTab.handlerMap.put(info.getHandlerName(), info);
        }
        GunMod.LOGGER.info("[TACZ] NEI handler infos applied: {} (reload listener: {})", infos.size(),
            listenerRegistered ? "on" : "off");
    }

    /** NEI 每次 {@code GuiRecipeTab.loadHandlerInfo()} 都会清空 handlerMap 后 post 本事件。 */
    @SubscribeEvent
    public void onRegisterHandlerInfos(NEIRegisterHandlerInfosEvent event) {
        List<HandlerInfo> infos = buildInfos();
        for (HandlerInfo info : infos) {
            event.registerHandlerInfo(info);
        }
        GunMod.LOGGER.info("[TACZ] NEI handler infos re-registered: {}", infos.size());
    }

    private static List<HandlerInfo> buildInfos() {
        String modName = modName();
        List<HandlerInfo> infos = new ArrayList<>();
        for (ResourceLocation tableId : GunSmithTableRecipeHandler.tableBlockIds()) {
            addInfo(infos, modName, GunSmithTableRecipeHandler.handlerIdFor(tableId),
                GunSmithTableRecipeHandler.tableIconFor(tableId));
        }
        addInfo(infos, modName, GunAmmoQueryHandler.HANDLER_ID, firstGunStack());
        addInfo(infos, modName, AmmoQueryHandler.HANDLER_ID, firstAmmoStack());
        addInfo(infos, modName, AttachmentQueryHandler.HANDLER_ID, firstAttachmentStack());
        addInfo(infos, modName, GunAttachmentQueryHandler.HANDLER_ID, firstGunStack());
        addInfo(infos, modName, AmmoStorageUpgradeHandler.HANDLER_ID, ammoStorageStack());
        return infos;
    }

    private static void addInfo(List<HandlerInfo> infos, String modName, String handlerId, @Nullable ItemStack icon) {
        HandlerInfo.Builder builder = new HandlerInfo.Builder(handlerId, modName, MOD_ID)
            // 我们的页面是「一个配方一页」的自绘 166x65 面板（上游 JEI 同样一次只显示一个配方），
            // 不启用 NEI 的多 widget 堆叠；overlay/favorites 按钮会画在面板右下角压住格子，且本移植
            // 没有实现 overlay 转移，故一并隐藏（详见 docs/review/r8/nei.md §5）。
            .setMultipleWidgetsAllowed(false)
            .setShowOverlayButton(false)
            .setShowFavoritesButton(false);
        if (icon != null) {
            builder.setDisplayStack(icon);
        }
        infos.add(builder.build());
    }

    /** mod 显示名取 FML 里的实际 mod 名（{@code @Mod(name=...)}），避免和 mcmod.info 不一致。 */
    private static String modName() {
        ModContainer container = Loader.instance()
            .getIndexedModList()
            .get(MOD_ID);
        return container == null || container.getName() == null ? DEFAULT_MOD_NAME : container.getName();
    }

    /** 取一个代表性枪械物品栈（枪包索引未加载时返回 null）。 */
    @Nullable
    private static ItemStack firstGunStack() {
        for (Map.Entry<ResourceLocation, CommonGunIndex> entry : TimelessAPI.getAllCommonGunIndex()) {
            ItemStack stack = NeiStacks.buildGun(entry.getKey());
            if (stack != null) {
                return stack;
            }
        }
        return null;
    }

    /** 取一个代表性弹药物品栈（枪包索引未加载时返回 null）。 */
    @Nullable
    private static ItemStack firstAmmoStack() {
        for (Map.Entry<ResourceLocation, CommonAmmoIndex> entry : TimelessAPI.getAllCommonAmmoIndex()) {
            ItemStack stack = NeiStacks.buildAmmo(entry.getKey());
            if (stack != null) {
                return stack;
            }
        }
        return null;
    }

    /** 取一个代表性配件物品栈（跳过 hidden 配件，与查询页口径一致）。 */
    @Nullable
    private static ItemStack firstAttachmentStack() {
        for (Map.Entry<ResourceLocation, CommonAttachmentIndex> entry : TimelessAPI.getAllCommonAttachmentIndex()) {
            if (entry.getValue()
                .getPojo()
                .isHidden()) {
                continue;
            }
            ItemStack stack = NeiStacks.buildAttachment(entry.getKey());
            if (stack != null) {
                return stack;
            }
        }
        return null;
    }

    /** 弹药库方块物品（方块物品在 preInit 已注册，随时可构造）。 */
    @Nullable
    private static ItemStack ammoStorageStack() {
        Item item = GameRegistry.findItem(MOD_ID, AMMO_STORAGE_PATH);
        return item == null ? null : new ItemStack(item);
    }
}
