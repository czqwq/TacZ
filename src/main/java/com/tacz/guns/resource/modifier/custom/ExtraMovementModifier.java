package com.tacz.guns.resource.modifier.custom;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;

import org.jetbrains.annotations.Nullable;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.modifier.CacheValue;
import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.MoveSpeed;

/**
 * 这个字段使用modifier还是太奇怪了，姑且只用于缓存
 */
public class ExtraMovementModifier implements IAttachmentModifier<MoveSpeed, MoveSpeed> {

    public static final String ID = GunProperties.MOVE_SPEED.name();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    @SuppressWarnings("deprecation")
    public JsonProperty<MoveSpeed> readJson(String json) {
        ExtraMovementModifier.Data data = CommonAssetsManager.GSON.fromJson(json, ExtraMovementModifier.Data.class);
        MoveSpeed moveSpeed = data.getMoveSpeed();
        return new ExtraSpeedJsonProperty(moveSpeed);
    }

    @Override
    public CacheValue<MoveSpeed> initCache(ItemStack gunItem, GunData gunData) {
        return new CacheValue<>(gunData.getMoveSpeed());
    }

    @Override
    public void eval(List<MoveSpeed> modifiers, CacheValue<MoveSpeed> cache) {
        cache.setValue(MoveSpeed.of(cache.getValue(), modifiers));
    }

    public static class ExtraSpeedJsonProperty extends JsonProperty<MoveSpeed> {

        public ExtraSpeedJsonProperty(MoveSpeed value) {
            super(value);
        }

        @Override
        public void initComponents() {
            MoveSpeed speed = getValue();
            if (speed == null) return;
            resolveComponent(speed.getBaseMultiplier(), "movement_speed");
            resolveComponent(speed.getAimMultiplier(), "aim_speed");
            resolveComponent(speed.getReloadMultiplier(), "reload_speed");
        }

        private void resolveComponent(float amount, String key) {
            if (amount > 0) {
                components.add(
                    new ChatComponentTranslation(String.format("tooltip.tacz.attachment.%s.increase", key))
                        .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GREEN)));
            } else if (amount < 0) {
                components.add(
                    new ChatComponentTranslation(String.format("tooltip.tacz.attachment.%s.decrease", key))
                        .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));
            }
        }
    }

    public static class Data {

        @SerializedName("movement_speed")
        @Nullable
        private MoveSpeed moveSpeed = null;

        @Nullable
        public MoveSpeed getMoveSpeed() {
            return moveSpeed;
        }
    }
}
