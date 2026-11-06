package com.tacz.guns.init;

/**
 * 搬运自上游 1.20.1 {@code init/ModSounds.java}。
 *
 * <p>
 * <b>1.7.10 无 SoundEvent 注册表</b>：声音由 {@code sounds.json} + 字符串名寻址，
 * {@code World#playSoundEffect(x,y,z,String,vol,pitch)} / {@code World#playSoundAtEntity} 直接播放。
 * 因此本类只保留上游两个声音名的字符串常量，{@code register()} 为空实现（保留 CommonProxy 调用点顺序）。
 * 声音文件与 {@code sounds.json} 由 assets-port 工作流处理（{@code assets/tacz/sounds.json}）。
 */
public class ModSounds {

    /** 对应上游 {@code SoundEvent gun}（{@code tacz:gun}）。 */
    public static final String GUN = "tacz:gun";
    /** 对应上游 {@code SoundEvent target_block_hit}（{@code tacz:target_block_hit}）。 */
    public static final String TARGET_HIT = "tacz:target_block_hit";

    public static void register() {
        // 1.7.10 无声音注册表；字符串常量 + sounds.json 即等价（见类 javadoc）
    }
}
