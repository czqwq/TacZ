package com.tacz.guns.init;

import net.minecraft.entity.Entity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.GunMod;

/**
 * 移植说明：子弹伤害来源。
 *
 * <p>
 * <b>上游形态</b>（{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/init/ModDamageTypes.java}）：
 * 1.20 走数据驱动的伤害类型注册表 —— {@code ResourceKey<DamageType>}（BULLET / BULLET_IGNORE_ARMOR /
 * BULLET_VOID / BULLET_VOID_IGNORE_ARMOR）+ {@code TagKey<DamageType> BULLETS_TAG}，使用时从
 * {@code RegistryAccess} 里取 {@code Holder.Reference<DamageType>} 构造 {@code DamageSource}。
 *
 * <p>
 * <b>1.7.10 形态</b>：没有伤害类型注册表、没有 {@code Holder}/{@code RegistryAccess}、
 * 也没有 {@code DamageType} 记录；{@link DamageSource} 是普通类，语义靠<b>子类 + 标志位</b>表达：
 * <ul>
 * <li>{@code BULLET} → {@code Sources#bullet(Entity, Entity)}：{@link EntityDamageSourceIndirect}（子弹 → 射手）</li>
 * <li>{@code BULLET_IGNORE_ARMOR} → {@code setDamageBypassesArmor()}（对应上游
 * {@code minecraft:bypasses_armor} tag）</li>
 * <li>{@code BULLET_VOID} → 上游只有 {@code minecraft:bypasses_invulnerability} tag；1.7.10 无该标志，
 * 退化为 {@code setDamageIsAbsolute()} + {@code setMagicDamage()}（不可达：默认数据里
 * {@code use_void_damage_on} 是空标签，见 {@code EntityKineticBullet#createDamageSources}）</li>
 * <li>四个 json 的 {@code exhaustion=0.05} → {@link BulletDamageSource#getHungerDamage()} 覆写</li>
 * <li>{@code BULLETS_TAG} → 1.7.10 无伤害类型 tag，**放弃 tag 形态**；上游用它做「是否子弹伤害」判定，
 * 1.7.10 改为 {@link #isBulletDamage(DamageSource)}（{@code instanceof} 判定本类的
 * {@link BulletDamageSource}），语义等价、无需数据包。</li>
 * </ul>
 *
 * <p>
 * 四个 {@code ResourceLocation} 常量保留下来（同名路径 {@code tacz:bullet} 等），用于伤害日志/成就匹配，
 * 语义与上游的 {@code ResourceKey} 一一对应（少了注册表这一层）。
 *
 * <p>
 * <b>死亡消息</b>：1.20 里四个 damage_type 的 json（{@code data/tacz/damage_type/*.json}）都把
 * {@code message_id} 写成 {@code tacz.bullet}，所以语言文件只有 {@code death.attack.tacz.bullet} 与
 * {@code death.attack.tacz.bullet.item} 两个 key。1.7.10 的死亡消息 key 直接由
 * {@code DamageSource#damageType} 拼成 {@code death.attack.<damageType>}（见
 * {@code EntityDamageSourceIndirect#func_151519_b}），因此这里把 {@code damageType} 统一为
 * {@value #DEATH_MESSAGE_ID}，而不是各自的路径名 —— 否则穿甲/虚空三种伤害会显示未翻译的 key。
 * 真正的伤害类型 id 仍保存在 {@link BulletDamageSource#getTaczDamageType()} 里。
 */
public class ModDamageTypes {

    public static final ResourceLocation BULLET = new ResourceLocation(GunMod.MOD_ID, "bullet");
    public static final ResourceLocation BULLET_IGNORE_ARMOR = new ResourceLocation(
        GunMod.MOD_ID,
        "bullet_ignore_armor");
    public static final ResourceLocation BULLET_VOID = new ResourceLocation(GunMod.MOD_ID, "bullet_void");
    public static final ResourceLocation BULLET_VOID_IGNORE_ARMOR = new ResourceLocation(
        GunMod.MOD_ID,
        "bullet_void_ignore_armor");

    /** 四个上游 damage_type 共用的 {@code message_id}，对应语言文件 {@code death.attack.tacz.bullet[.item]} */
    private static final String DEATH_MESSAGE_ID = "tacz.bullet";

    private ModDamageTypes() {}

    /**
     * 1.7.10 版 {@code TagKey<DamageType> BULLETS_TAG} 的替代判定：该伤害是否由 TACZ 子弹造成。
     *
     * <p>
     * 上游写法是 {@code event.getSource().is(ModDamageTypes.BULLETS_TAG)}
     * （tag 内容见 {@code data/tacz/tags/damage_type/bullets.json}，正是四个伤害类型）。
     * 1.7.10 没有伤害类型注册表/tag，因此用 {@code instanceof} 判定，覆盖集合与上游 tag 完全一致。
     *
     * @param source 待判定的伤害来源，可为 {@code null}
     * @return 是否 TACZ 子弹伤害
     */
    public static boolean isBulletDamage(DamageSource source) {
        return source instanceof BulletDamageSource;
    }

    /**
     * 子弹伤害来源的工厂（上游 {@code ModDamageTypes.Sources} 的等价物）。
     *
     * <p>
     * 与上游签名的差异：上游带 {@code RegistryAccess access} 首参（用于查伤害类型注册表），
     * 1.7.10 没有注册表 → 该参数删除；{@code Entity} → 1.7.10 同名类。
     */
    public static class Sources {

        public static DamageSource bullet(Entity bullet, Entity shooter) {
            return bullet(bullet, shooter, false);
        }

        public static DamageSource bullet(Entity bullet, Entity shooter, boolean ignoreArmor) {
            return new BulletDamageSource(
                ignoreArmor ? BULLET_IGNORE_ARMOR : BULLET,
                bullet,
                shooter,
                ignoreArmor,
                false);
        }

        public static DamageSource bulletVoid(Entity bullet, Entity shooter) {
            return bulletVoid(bullet, shooter, false);
        }

        public static DamageSource bulletVoid(Entity bullet, Entity shooter, boolean ignoreArmor) {
            return new BulletDamageSource(
                ignoreArmor ? BULLET_VOID_IGNORE_ARMOR : BULLET_VOID,
                bullet,
                shooter,
                ignoreArmor,
                true);
        }
    }

    /**
     * 1.7.10 的子弹伤害来源：在 {@link EntityDamageSourceIndirect} 基础上加「无视护甲」标志。
     * <p>
     * 上游用四种 {@code DamageType} + 两个原版 tag 表达：
     * <ul>
     * <li>{@code minecraft:bypasses_armor} → {@link #setDamageBypassesArmor()}（与上游四个 json 中的
     * {@code bullet_ignore_armor} / {@code bullet_void_ignore_armor} 对应）</li>
     * <li>{@code minecraft:bypasses_invulnerability}（{@code bullet_void*}）→ 1.7.10 的
     * {@code DamageSource} 没有「穿透无敌」标志（{@code EntityLivingBase#attackEntityFrom} 一进来就
     * {@code isEntityInvulnerable()} 返回 false），只能退化为 {@code absolute}+{@code magic}
     * （见 {@code BulletDamageSource(..., absolute=true)} 的注释）</li>
     * <li>四个 json 的 {@code exhaustion=0.05} → {@link #getHungerDamage()} 覆写为 0.05
     * （1.7.10 默认 0.3，只有 {@code EntityPlayer#damageEntity} 消费该值）</li>
     * <li>上游的 damage_type 不在 {@code is_projectile} tag 里 ⇒ 1.7.10 <b>不</b>调 {@code setProjectile()}
     * （否则保护类附魔的类型 4 会对子弹额外生效，上游不会）</li>
     * </ul>
     */
    public static class BulletDamageSource extends EntityDamageSourceIndirect {

        /** 上游 {@code data/tacz/damage_type/*.json} 的 {@code exhaustion} 值。 */
        private static final float EXHAUSTION = 0.05F;

        private final ResourceLocation damageType;

        public BulletDamageSource(ResourceLocation damageType, Entity bullet, Entity shooter, boolean ignoreArmor,
            boolean absolute) {
            // 死亡消息 key 用共用 message_id，见类注释「死亡消息」
            super(DEATH_MESSAGE_ID, bullet, shooter);
            this.damageType = damageType;
            if (ignoreArmor) {
                setDamageBypassesArmor();
            }
            if (absolute) {
                // 上游 BULLET_VOID* 在 bypasses_invulnerability tag 里；1.7.10 无该标志，退化为
                // 「无视药水/附魔减免 + 魔法伤害」（仅第三方 use_void_damage_on 标签会走到，默认数据为空标签）
                setDamageIsAbsolute();
                setMagicDamage();
            }
        }

        /**
         * 1.20 damage_type json 的 {@code exhaustion}：每次命中让玩家增加 0.05 饥饿消耗。
         * 1.7.10 只有 {@code EntityPlayer#damageEntity} 用这个值（{@code getHungerDamage()}），
         * 默认 0.3F；与上游四份 json 对齐需要显式覆盖。
         */
        @Override
        public float getHungerDamage() {
            return EXHAUSTION;
        }

        /** 1.7.10 版「damage_type 注册键」：{@code tacz:bullet_ignore_armor} 等，供日志/成就匹配使用 */
        public ResourceLocation getTaczDamageType() {
            return damageType;
        }
    }
}
