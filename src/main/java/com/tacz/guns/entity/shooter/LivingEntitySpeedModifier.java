package com.tacz.guns.entity.shooter;

import java.util.UUID;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.item.ItemStack;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.custom.ExtraMovementModifier;
import com.tacz.guns.resource.modifier.custom.WeightModifier;
import com.tacz.guns.resource.pojo.data.gun.MoveSpeed;

/**
 * 持枪移动速度修正（重量惩罚 + 配件移动速度乘区）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/entity/shooter/LivingEntitySpeedModifier.java}。
 * 构造器 {@code (EntityLivingBase, ShooterDataHolder)} 与 {@code updateSpeedModifier()} 被
 * {@code LivingEntityMixin} 每 tick 调用。
 *
 * <p>
 * 平台替换（相对上游）：
 * <ul>
 * <li>{@code LivingEntity → EntityLivingBase}、{@code getMainHandItem() → getHeldItem()}、{@code isAlive() → isEntityAlive()}。</li>
 * <li>{@code shooter.getAttributes().getInstance(Attributes.MOVEMENT_SPEED)} →
 * {@code shooter.getEntityAttribute(SharedMonsterAttributes.movementSpeed)}（1.7.10 只有
 * {@code IAttributeInstance}，没有 AttributeMap 的 {@code getInstance}）。</li>
 * <li>{@code AttributeModifier(uuid, name, amount, Operation)} →
 * {@code new AttributeModifier(uuid, name, amount, int operation)}；
 * 1.7.10 的 operation 常量与 1.20 数值一致：0=ADDITION、1=MULTIPLY_BASE、2=MULTIPLY_TOTAL
 * （见 {@code ModifiableAttributeInstance#computeValue}）。</li>
 * <li>{@code addTransientModifier(m)}（不写存档）→ {@code m.setSaved(false); applyModifier(m)}。</li>
 * <li>{@code removeModifier(UUID)} → 1.7.10 的 {@code IAttributeInstance#removeModifier(AttributeModifier)}
 * 收的是修饰器对象，先 {@code getModifier(uuid)} 取回再移除。</li>
 * </ul>
 */
public class LivingEntitySpeedModifier {

    private static final UUID EXTRA_SPEED_MODIFIER_UUID = UUID.fromString("4D5696AE-A7C5-C59C-80E9-2A2DC8373C46");
    private static final UUID WEIGHT_SPEED_MODIFIER_UUID = UUID.fromString("2CB6F5AD-C6D2-9D29-4E84-0856ACD47CDB");
    /**
     * 1.7.10 的 AttributeModifier 用 int 表达运算方式，数值与 1.20 的 Operation 枚举 ordinal 一致。
     */
    private static final int OPERATION_MULTIPLY_BASE = 1;
    private static final int OPERATION_MULTIPLY_TOTAL = 2;

    private final EntityLivingBase shooter;
    private final ShooterDataHolder dataHolder;

    public LivingEntitySpeedModifier(EntityLivingBase shooter, ShooterDataHolder dataHolder) {
        this.shooter = shooter;
        this.dataHolder = dataHolder;
    }

    public void updateSpeedModifier() {
        if (!shooter.isEntityAlive()) {
            return;
        }

        ItemStack stack = shooter.getHeldItem();
        IAttributeInstance speedModifier = shooter.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
        if (speedModifier == null) {
            return;
        }

        if (stack != null && stack.getItem() instanceof AbstractGunItem) {
            // 处理重量带来的修正
            AttachmentCacheProperty cacheProperty = IGunOperator.fromLivingEntity(shooter)
                .getCacheProperty();
            if (cacheProperty != null) {
                double weightFactor = SyncConfig.WEIGHT_SPEED_MULTIPLIER.get();
                if (weightFactor > 0) {
                    float targetSpeed = cacheProperty.getCache(WeightModifier.ID);
                    targetSpeed *= (float) -weightFactor;
                    AttributeModifier currentModifier = speedModifier.getModifier(WEIGHT_SPEED_MODIFIER_UUID);
                    if (currentModifier == null || currentModifier.getAmount() != targetSpeed) {
                        removeModifier(speedModifier, WEIGHT_SPEED_MODIFIER_UUID);
                        speedModifier.applyModifier(
                            new AttributeModifier(
                                WEIGHT_SPEED_MODIFIER_UUID,
                                "Gun Speed Modifier",
                                targetSpeed,
                                OPERATION_MULTIPLY_BASE).setSaved(false));
                    }
                }

                MoveSpeed speed = cacheProperty.getCache(ExtraMovementModifier.ID);
                if (speed != null) {
                    double targetSpeed = getTargetSpeed(speed);
                    AttributeModifier currentModifier = speedModifier.getModifier(EXTRA_SPEED_MODIFIER_UUID);
                    if (currentModifier == null || currentModifier.getAmount() != targetSpeed) {
                        removeModifier(speedModifier, EXTRA_SPEED_MODIFIER_UUID);
                        speedModifier.applyModifier(
                            new AttributeModifier(
                                EXTRA_SPEED_MODIFIER_UUID,
                                "Extra Gun Speed Modifier",
                                targetSpeed,
                                OPERATION_MULTIPLY_TOTAL).setSaved(false));
                    }
                }
            }
        } else {
            removeModifier(speedModifier, WEIGHT_SPEED_MODIFIER_UUID);
            removeModifier(speedModifier, EXTRA_SPEED_MODIFIER_UUID);
        }
    }

    private static void removeModifier(IAttributeInstance attributeInstance, UUID modifierId) {
        AttributeModifier modifier = attributeInstance.getModifier(modifierId);
        if (modifier != null) {
            attributeInstance.removeModifier(modifier);
        }
    }

    private double getTargetSpeed(MoveSpeed moveSpeed) {
        if (dataHolder.reloadStateType.isReloading()) {
            return moveSpeed.getReloadMultiplier();
        }
        if (dataHolder.isAiming) {
            return moveSpeed.getAimMultiplier();
        }
        return moveSpeed.getBaseMultiplier();
    }

}
