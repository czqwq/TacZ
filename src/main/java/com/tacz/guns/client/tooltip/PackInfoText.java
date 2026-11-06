package com.tacz.guns.client.tooltip;

import javax.annotation.Nullable;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.tacz.guns.client.resource.pojo.PackInfo;

/**
 * {@link PackInfo} 文本的 1.7.10 空安全助手。
 *
 * <h2>为什么需要</h2>
 * {@code client/resource/pojo/PackInfo} 的各字段都有<b>非 null 初始化值</b>，但 gson 读到显式的
 * {@code JsonToken.NULL} 时会 {@code field.set(obj, null)}，<b>覆盖</b>初始化值（只有「键不存在」时默认值
 * 才生效）—— 即第三方枪包的 {@code gunpack_info.json} 写 {@code "name": null} / {@code "license": null}
 * 就会让 getter 返回 null。（已实测确认这个 gson 行为，并指出 2.2.4 与 2.10 一致，
 * 不是移植差异；内置包 {@code custom/tacz_default_gun} 全部 json 扫 {@code :\s*null} = 0 命中，
 * 所以只有第三方包会触发。）
 *
 * <p>
 * 而下列调用点<b>不能</b>容忍 null：
 * <ul>
 * <li>{@link ChatComponentTranslation} 的构造器能接 null，但
 * {@code getFormattedText()} → {@code initializeFromFormat(null)} → {@code Pattern#matcher(null)}
 * 会抛 <b>NPE</b>（{@code ChatComponentTranslation.java:73-94}）——
 * 而 {@code GunPackList} 在构造器里就调 {@code getFormattedText()}，
 * {@code GunSmithTableScreen#renderPackInfo} 更是选中配方后<b>每帧</b>调；</li>
 * <li>{@code "License: " + null} 会打印字面 {@code null}。</li>
 * </ul>
 *
 * <p>
 * 归口到本类，避免 6 个调用点各写一份。调用方：{@code GunPackList}（创造/工作台筛选列表）、
 * {@code GunSmithTableScreen#renderPackInfo}、{@code ClientGunTooltip} /
 * {@code ClientAttachmentItemTooltip} / {@code ClientBlockItemTooltip}（tooltip 的枪包名行）、
 * {@code client/event/TooltipEvent#ammoHoverText}（弹药描述块的枪包名行）。
 *
 * <p>
 * 回落顺序与 {@code CreativePackFilters} 既有的处理保持一致：
 * {@code name} 缺失 / 空白 / 等于占位键 → 用 {@code namespace} → 连 namespace 都没有才用
 * {@link #NO_NAME_KEY}（该键在 20 个语种里都存在，见 {@code assets/tacz/lang/en_US.lang:18}）。
 */
public final class PackInfoText {

    /** {@link PackInfo#getName()} 的默认占位键（第三方包没写 name 时保留的初始化值）。 */
    public static final String NO_NAME_KEY = "custom.tacz.error.no_name";

    private PackInfoText() {}

    /**
     * 枪包显示名。
     *
     * <p>
     * 返回值<b>恒非 null</b>，且其 {@code getFormattedText()} 不会抛异常。
     * 调用方需要颜色/斜体时自行 {@code setChatStyle(...)}（上游是 BLUE + ITALIC）。
     *
     * @param packInfo  枪包信息，可为 {@code null}
     * @param namespace 目标物品/方块的命名空间，用于 name 缺失时回落；可为 {@code null}
     */
    public static IChatComponent name(@Nullable PackInfo packInfo, @Nullable String namespace) {
        String key = packInfo == null ? null : packInfo.getName();
        if (isBlank(key) || NO_NAME_KEY.equals(key)) {
            return isBlank(namespace) ? new ChatComponentTranslation(NO_NAME_KEY) : new ChatComponentText(namespace);
        }
        return new ChatComponentTranslation(key);
    }

    /**
     * 取恒非 null 的字符串字段（null → 空串）。
     *
     * <p>
     * 用于只参与字符串拼接的字段（{@code version} / {@code license} / {@code date}）：
     * 拼 null 会印出字面 {@code null}，用空串至少不会显示垃圾文本。
     */
    public static String safe(@Nullable String value) {
        return value == null ? "" : value;
    }

    /** {@code null} 或全空白。 */
    public static boolean isBlank(@Nullable String value) {
        return value == null || value.trim()
            .isEmpty();
    }
}
