package com.tacz.guns.client.model.papi;

import java.util.Map;
import java.util.function.Function;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.google.common.collect.Maps;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class PapiManager {

    private static final Map<String, Function<ItemStack, String>> PAPI = Maps.newHashMap();

    // 注册，不知道放哪里，先放这
    static {
        addPapi(PlayerNamePapi.NAME, new PlayerNamePapi());
        addPapi(AmmoCountPapi.NAME, new AmmoCountPapi());
        addPapi(RangefinderPapi.NAME, new RangefinderPapi());
    }

    public static void addPapi(String textKey, Function<ItemStack, String> function) {
        textKey = "%" + textKey + "%";
        PAPI.put(textKey, function);
    }

    public static String getTextShow(String textKey, ItemStack stack) {
        // 移植说明：上游 PapiManager.java:29 用 I18n.language.getOrDefault(textKey)，即
        // net.minecraft.locale.Language#getOrDefault(String)：返回「未格式化的原文翻译」，缺 key 时返回 key 本身。
        // 1.7.10 与之等价的是 StatCollector#translateToLocal（内部 StringTranslate#translateKey），
        // 同样不做 String.format 格式化、缺 key 时返回 key。不要用 translateToLocalFormatted（会格式化 %）。
        String text = StatCollector.translateToLocal(textKey);
        for (var entry : PAPI.entrySet()) {
            String placeholder = entry.getKey();
            if (!text.contains(placeholder)) {
                continue;
            }
            String data = entry.getValue()
                .apply(stack);
            text = text.replace(placeholder, data);
        }
        return text;
    }
}
