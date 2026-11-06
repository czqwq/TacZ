package com.tacz.guns.crafting.result;

import java.util.EnumMap;
import java.util.Locale;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.resource.pojo.data.block.TabConfig;
import com.tacz.guns.resource.pojo.data.recipe.GunResult;
import com.tacz.guns.util.DefaultGunAttachments;

/**
 * 配方加载时部分物品的上下文还未完成初始化，等待到实际需要使用配方时再进行初始化。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/crafting/result/RawGunTableResult.java}
 * 字段（{@code type}/{@code count}/{@code id}/{@code extraData}/{@code nbt}）、构造器、
 * {@code setExtraData}/{@code setNbt}、静态 {@code init(RawGunTableResult)} 与三个私有构建方法保持上游结构。
 *
 * <p>
 * <b>1.7.10 差异</b>：
 * <ul>
 * <li>{@code CompoundTag} → {@link NBTTagCompound}；{@code getAllKeys()} → {@code func_150296_c()}；
 * {@code ItemStack#getOrCreateTag()} → 手动 {@code getTagCompound()/setTagCompound()}（可能返回 {@code null}）；</li>
 * <li>{@code ItemStack.EMPTY} → {@code null}；因此 {@link #init} 在物品构建失败时返回「空产物」；</li>
 * <li>{@code ResourceLocation.tryParse} → {@code new ResourceLocation(String)}（1.7.10 不校验）。</li>
 * </ul>
 */
public class RawGunTableResult {

    private final String type;
    private final int count;
    private final ResourceLocation id;
    @Nullable
    private GunResult extraData;
    @Nullable
    private NBTTagCompound nbt;

    public RawGunTableResult(@NotNull String type, @NotNull ResourceLocation id, int count) {
        this.type = type;
        this.id = id;
        this.count = count;
    }

    public void setExtraData(@Nullable GunResult extraData) {
        this.extraData = extraData;
    }

    public void setNbt(@Nullable NBTTagCompound nbt) {
        this.nbt = nbt;
    }

    public static GunSmithTableResult init(RawGunTableResult raw) {
        GunSmithTableResult result;
        switch (raw.type) {
            case GunSmithTableResult.GUN:
                result = raw.getGunStack();
                break;
            case GunSmithTableResult.AMMO:
                result = raw.getAmmoStack();
                break;
            case GunSmithTableResult.ATTACHMENT:
                result = raw.getAttachmentStack();
                break;
            default:
                result = new GunSmithTableResult((ItemStack) null, TabConfig.TAB_EMPTY);
                break;
        }
        if (raw.nbt != null) {
            ItemStack stack = result.getResult();
            if (stack != null && stack.getItem() != null) {
                NBTTagCompound itemTag = stack.getTagCompound();
                if (itemTag == null) {
                    itemTag = new NBTTagCompound();
                    stack.setTagCompound(itemTag);
                }
                for (String key : raw.nbt.func_150296_c()) {
                    // NBTBase 在 1.7.10 是可变 tag，直接引用（与上游 tag.copy 的差异见类注释）
                    itemTag.setTag(key, raw.nbt.getTag(key));
                }
            }
        }
        return result;
    }

    private GunSmithTableResult getGunStack() {
        int ammoCount;
        EnumMap<AttachmentType, ResourceLocation> attachments;
        if (extraData != null) {
            ammoCount = Math.max(0, extraData.getAmmoCount());
            attachments = extraData.getAttachments();
        } else {
            ammoCount = 0;
            attachments = new EnumMap<>(AttachmentType.class);
        }
        return TimelessAPI.getCommonGunIndex(id)
            .map(gunIndex -> {
                ItemStack itemStack = GunItemBuilder.create()
                    .setCount(count)
                    .setId(id)
                    .setAmmoCount(ammoCount)
                    .setAmmoInBarrel(false)
                    .putAllVirtualAttachment(DefaultGunAttachments.defaults(id))
                    .putAllAttachment(attachments)
                    .setFireMode(
                        gunIndex.getGunData()
                            .getFireModeSet()
                            .get(0))
                    .build();
                String rawType = gunIndex.getType();
                if (!rawType.contains(":")) {
                    rawType = GunMod.MOD_ID + ":" + rawType;
                }
                ResourceLocation group = new ResourceLocation(rawType);
                return new GunSmithTableResult(itemStack, group);
            })
            .orElse(new GunSmithTableResult((ItemStack) null, TabConfig.TAB_EMPTY));
    }

    private GunSmithTableResult getAmmoStack() {
        return new GunSmithTableResult(
            AmmoItemBuilder.create()
                .setCount(count)
                .setId(id)
                .build(),
            TabConfig.TAB_AMMO);
    }

    private GunSmithTableResult getAttachmentStack() {
        return TimelessAPI.getCommonAttachmentIndex(id)
            .map(attachmentIndex -> {
                ItemStack itemStack = AttachmentItemBuilder.create()
                    .setCount(count)
                    .setId(id)
                    .build();
                String raw = attachmentIndex.getType()
                    .name()
                    .toLowerCase(Locale.US);
                if (!raw.contains(":")) {
                    raw = GunMod.MOD_ID + ":" + raw;
                }
                ResourceLocation group = new ResourceLocation(raw);
                return new GunSmithTableResult(itemStack, group);
            })
            .orElse(new GunSmithTableResult((ItemStack) null, TabConfig.TAB_EMPTY));
    }
}
