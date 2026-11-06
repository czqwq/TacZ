package com.tacz.guns.mcshim;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.util.ResourceLocation;

/**
 * 对应 1.20.1 的 {@code net.minecraft.sounds.SoundEvent}。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.7.10 <b>没有</b> {@code SoundEvent} 类，声音就是一个<b>字符串名</b>：
 * {@code World#playSound(x, y, z, name, volume, pitch)} /
 * {@code EntityPlayer#playSound(name, volume, pitch)}。
 * 1.20 的 {@code SoundEvent} 只是 {@code ResourceLocation} + 固定/可变范围标记的包装。
 *
 * <p>
 * 本类因此是「{@code ResourceLocation} 的薄包装」，并提供
 * {@link #getName()} 给出 1.7.10 播放接口需要的字符串。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li><b>没有 {@code Holder}/{@code Registry}</b>：1.20 的声音通过
 * {@code BuiltInRegistries.SOUND_EVENT} 注册并由 {@code Holder} 引用；1.7.10 直接按名字播，
 * 不需要注册。</li>
 * <li>{@link #isVariableRange()} 只是记录 1.20 的语义标记，1.7.10 播放时忽略
 * （{@code World#playSound} 本身按距离衰减）。</li>
 * <li>1.20 的 {@code SoundEvent#getRange(float volume)} 未移植（上游未使用）。</li>
 * </ul>
 */
public class SoundEvent {

    private static final Map<ResourceLocation, SoundEvent> CACHE = new ConcurrentHashMap<>();

    private final ResourceLocation location;
    private final boolean variableRange;

    protected SoundEvent(ResourceLocation location, boolean variableRange) {
        this.location = location;
        this.variableRange = variableRange;
    }

    /** 对应 1.20 {@code SoundEvent#create(ResourceLocation)}：固定范围。 */
    public static SoundEvent create(ResourceLocation location) {
        return CACHE.computeIfAbsent(location, loc -> new SoundEvent(loc, false));
    }

    /** 对应 1.20 {@code SoundEvent#createVariableRangeEvent(ResourceLocation)}。 */
    public static SoundEvent createVariableRangeEvent(ResourceLocation location) {
        return CACHE.computeIfAbsent(location, loc -> new SoundEvent(loc, true));
    }

    /** 对应 1.20 {@code SoundEvent#getLocation()}。 */
    public ResourceLocation getLocation() {
        return location;
    }

    /** 对应 1.20 {@code SoundEvent#isVariableRange()}（1.7.10 播放时忽略）。 */
    public boolean isVariableRange() {
        return variableRange;
    }

    /** 1.7.10 播放接口需要的声音名字（{@code namespace:path}）。 */
    public String getName() {
        return location.toString();
    }

    @Override
    public String toString() {
        return getName();
    }
}
