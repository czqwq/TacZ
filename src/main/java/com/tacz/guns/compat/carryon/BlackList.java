package com.tacz.guns.compat.carryon;

import net.minecraft.util.ResourceLocation;

import com.tacz.guns.GunMod;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.registry.GameData;

/**
 * Carry On（{@code carryon}）兼容：把 TACZ 自己的方块加进对方的搬运黑名单。
 *
 * <p>
 * <b>上游完整实现</b>：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/compat/carryon/BlackList.java}
 * —— 遍历 {@code ForgeRegistries.BLOCKS} 里 namespace 为 {@code tacz} 的方块，
 * 逐个 {@code InterModComms.sendTo("carryon", "blacklistBlock", id::toString)}。
 *
 * <p>
 * <b>1.7.10 映射</b>（这里是真的接上了，不是空壳）：
 * <ul>
 * <li>{@code ForgeRegistries.BLOCKS.getKeys()} → {@link GameData#getBlockRegistry()}{@code .getKeys()}
 * （1.7.10 的方块注册表是 {@code FMLControlledNamespacedRegistry}，key 同样是 {@link ResourceLocation}）。</li>
 * <li>{@code id.getNamespace()} → {@code id.getResourceDomain()}（1.7.10 命名）。</li>
 * <li>{@code InterModComms.sendTo(modId, key, value)} → {@link FMLInterModComms#sendMessage(String, String, String)}
 * （1.7.10 的 IMC 是 {@code FMLInterModComms}，字符串消息这条重载语义完全一致）。</li>
 * </ul>
 *
 * <p>
 * <b>差异</b>：1.7.10 的 Carry On 搬运黑名单 key 与 1.20 版未做实测对照（1.7.10 上该模组生态很小）。
 * 语义保守：只有对方模组在 ({@link Loader#isModLoaded(String)}) 且注册了对应 IMC 监听时才会生效，
 * 否则 {@code sendMessage} 的返回值会被丢弃，不会有副作用。
 */
public final class BlackList {

    private static final String CARRY_ON_ID = "carryon";
    private static final String MESSAGE_KEY = "blacklistBlock";

    private BlackList() {}

    /** 对应上游 {@code BlackList#addBlackList()}（由 {@code init/CompatRegistry#enqueueCompat()} 反射调用）。 */
    public static void addBlackList() {
        if (!Loader.isModLoaded(CARRY_ON_ID)) {
            return;
        }
        int count = 0;
        // 1.7.10 的 getKeys() 是 raw Set，元素实际是 ResourceLocation（FMLControlledNamespacedRegistry 的 key 类型）
        for (Object key : GameData.getBlockRegistry()
            .getKeys()) {
            if (!(key instanceof ResourceLocation id)) {
                continue;
            }
            if (!GunMod.MOD_ID.equals(id.getResourceDomain())) {
                continue;
            }
            FMLInterModComms.sendMessage(CARRY_ON_ID, MESSAGE_KEY, id.toString());
            count++;
        }
        GunMod.LOGGER.info("Sent {} block id(s) to Carry On's carry blacklist", count);
    }
}
