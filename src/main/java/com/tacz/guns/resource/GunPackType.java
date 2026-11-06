package com.tacz.guns.resource;

import cpw.mods.fml.common.FMLCommonHandler;

/**
 * 枪包的作用侧，用于替代上游 1.20.1 的 {@code net.minecraft.server.packs.PackType}。
 *
 * <p>
 * 上游在 {@code GunMod} 构造器里按 {@code FMLLoader.getDist()} 决定
 * {@code GunPackLoader.INSTANCE.packType = PackType.CLIENT_RESOURCES | SERVER_DATA}
 * （参考 {@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/GunMod.java:38}），
 * 这个字段随后被 {@code GunPackLoader#discoverExtensions} 传给
 * {@code Pack.readMetaAndCreate(..., packType, ...)}，作用是「把这个枪包注册进哪一种原版资源仓库」。
 *
 * <p>
 * 1.7.10 既没有 {@code PackType}，也没有把枪包塞进原版资源仓库的机制
 * （枪包内容由 {@link GunPackLoader} 自己扫、由 {@code CommonAssetsManager} / {@code ClientAssetsManager} 自己读），
 * 因此这里保留同名概念仅用于<b>记录当前实际作用侧</b>与日志，加载行为本身两侧一致。
 */
public enum GunPackType {

    /** 客户端：读枪包里的 {@code assets/<namespace>/**}。 */
    CLIENT_RESOURCES,
    /** 服务端：读枪包里的 {@code data/<namespace>/**}。 */
    SERVER_DATA;

    /** 1.7.10 的当前作用侧。{@code FMLCommonHandler#getSide()} 取代上游的 {@code FMLLoader#getDist()}。 */
    public static GunPackType current() {
        return FMLCommonHandler.instance()
            .getSide()
            .isClient() ? CLIENT_RESOURCES : SERVER_DATA;
    }
}
