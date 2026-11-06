package com.tacz.guns.resource.serialize;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.tacz.guns.crafting.result.GunSmithTableResult;

/**
 * 工作台配方产物（{@code result} 字段）反序列化器。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/serialize/GunSmithTableResultSerializer.java}，
 * 在本类里做 {@code type} 分支（gun/ammo/attachment/custom）、{@code count}/{@code nbt}/{@code group} 解析，
 * 并通过 {@code CraftingHelper#getItemStack} + {@code ItemStack.EMPTY} 构造 {@link GunSmithTableResult}。
 *
 * <p>
 * <b>1.7.10 差异</b>：{@link GunSmithTableResult} / {@code RawGunTableResult} 属于 {@code crafting/**} 工作流，
 * 其内部依赖 {@code ItemStack.EMPTY}（1.7.10 为 {@code null}）、{@code CraftingHelper}（1.7.10 不存在）、
 * {@code CompoundTag}（1.7.10 为 {@code NBTTagCompound}）都要重写，形态尚未定稿。
 * 因此解析动作整体委托给 {@link GunSmithTableResult#fromJson(JsonElement)}，
 * 本类只保留「注册进 {@link com.tacz.guns.resource.CommonAssetsManager#GSON}」的适配职责
 * （跨包契约见 docs/PORT_STATUS.md）。
 */
public class GunSmithTableResultSerializer implements JsonDeserializer<GunSmithTableResult> {

    @Override
    public GunSmithTableResult deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
        return GunSmithTableResult.fromJson(json);
    }
}
