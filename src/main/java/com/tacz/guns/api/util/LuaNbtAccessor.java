package com.tacz.guns.api.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.ApiStatus;

import com.tacz.guns.api.item.nbt.ItemStackNbtHelper;

/**
 * 一个简单的NBT包装，用于在Lua中访问NBT数据。<br/>
 * 暂时只支持基本数据类型的读写，不支持数组等复杂数据类型。
 * <p>
 * 1.7.10 移植说明：{@code CompoundTag→NBTTagCompound}、{@code Tag.TAG_*}→{@link Constants.NBT}，
 * 读写方法换成 1.7.10 的 {@code getInteger/setInteger/...}。对外暴露给 Lua 的方法名<b>保持不变</b>
 * （脚本 API 契约，不能因平台替换而改名）。
 */
@SuppressWarnings("unused")
public record LuaNbtAccessor(NBTTagCompound nbt) {

    public static LuaNbtAccessor from(ItemStack stack) {
        return new LuaNbtAccessor(ItemStackNbtHelper.getTag(stack));
    }

    public static LuaNbtAccessor from(NBTTagCompound nbt) {
        return new LuaNbtAccessor(nbt);
    }

    public boolean contains(String key) {
        return nbt.hasKey(key);
    }

    public boolean contains(String key, int type) {
        return nbt.hasKey(key, type);
    }

    public LuaNbtAccessor newCompoundTag() {
        return new LuaNbtAccessor(new NBTTagCompound());
    }

    public int getInt(String key) {
        return nbt.getInteger(key);
    }

    public double getDouble(String key) {
        return nbt.getDouble(key);
    }

    public float getFloat(String key) {
        return nbt.getFloat(key);
    }

    public long getLong(String key) {
        return nbt.getLong(key);
    }

    public String getString(String key) {
        return nbt.getString(key);
    }

    public boolean getBoolean(NBTTagCompound nbt, String key) {
        return nbt.getBoolean(key);
    }

    public LuaNbtAccessor getCompound(String key) {
        if (!nbt.hasKey(key, Constants.NBT.TAG_COMPOUND)) {
            return null;
        }
        return from(nbt.getCompoundTag(key));
    }

    public void putInt(String key, int value) {
        nbt.setInteger(key, value);
    }

    public void putDouble(String key, double value) {
        nbt.setDouble(key, value);
    }

    public void putFloat(String key, float value) {
        nbt.setFloat(key, value);
    }

    public void putLong(String key, long value) {
        nbt.setLong(key, value);
    }

    public void putString(String key, String value) {
        nbt.setString(key, value);
    }

    public void putBoolean(String key, boolean value) {
        nbt.setBoolean(key, value);
    }

    /**
     * 向当前的NbtCompound中添加一个新的Compound
     *
     * @param key   键
     * @param value 在脚本中请使用{@link LuaNbtAccessor#newCompoundTag()}创建一个新的LuaNbtAccessor对象
     */
    public void putCompound(String key, LuaNbtAccessor value) {
        if (value != null) {
            nbt.setTag(key, value.nbt());
        }
    }

    @Override
    @ApiStatus.Internal
    public NBTTagCompound nbt() {
        return nbt;
    }
}
