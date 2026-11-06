package com.tacz.guns.util;

import java.util.EnumMap;

import net.minecraft.util.ResourceLocation;

import com.google.common.collect.Maps;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.attachment.AttachmentType;

/**
 * 读取枪械索引里配置的「默认配件」（built_in_attachments 的 default 字段）。
 *
 * <p>
 * <b>移植说明</b>：
 * <ul>
 * <li>{@code net.minecraft.resources.ResourceLocation} → {@link ResourceLocation}（1.7.10 同名类）。</li>
 * <li>{@link TimelessAPI#getCommonGunIndex(ResourceLocation)} 在 1.7.10 侧保持与上游相同的
 * {@code Optional} 语义（内部由 {@code CommonAssetsManager} 提供），故 {@code map/orElseGet} 无需改动。</li>
 * <li>这是 {@code api/**} 的编译期契约类，<b>公开类名与方法签名保持不变</b>。</li>
 * </ul>
 */
public final class DefaultGunAttachments {

    private DefaultGunAttachments() {}

    public static EnumMap<AttachmentType, ResourceLocation> defaults(ResourceLocation gunId) {
        return TimelessAPI.getCommonGunIndex(gunId)
            .map(index -> {
                EnumMap<AttachmentType, ResourceLocation> defaults = Maps.newEnumMap(AttachmentType.class);
                index.getGunData()
                    .getBuiltInAttachments()
                    .forEach((type, builtInAttachment) -> {
                        ResourceLocation defaultAttachment = builtInAttachment.getDefaultAttachment();
                        if (defaultAttachment != null) {
                            defaults.put(type, defaultAttachment);
                        }
                    });
                return defaults;
            })
            .orElseGet(() -> Maps.newEnumMap(AttachmentType.class));
    }
}
