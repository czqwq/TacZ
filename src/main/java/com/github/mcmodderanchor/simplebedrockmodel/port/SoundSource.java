// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: (new file - 1.7.10 port support, no upstream counterpart)
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.port;

/**
 * TODO(L2): 1.20 的 {@code com.github.mcmodderanchor.simplebedrockmodel.port.SoundSource} 是声音分类枚举（决定音量滑块）。
 * 1.7.10 的 {@code World#playSound(x,y,z,name,volume,pitch,distanceDelay)} 没有分类参数，
 * 分类只存在于 {@code SoundManager} 的 {@code SoundCategory} 内部，且 1.7.10 原版没有可配置的音量分类。
 * 本枚举保留上游常量名以便调用点原样保留，{@link #name()} 之外不作任何用途。
 */
public enum SoundSource {
    MASTER,
    MUSIC,
    RECORDS,
    WEATHER,
    BLOCKS,
    HOSTILE,
    NEUTRAL,
    PLAYERS,
    AMBIENT,
    VOICE
}
