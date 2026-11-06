package com.tacz.guns.mcshim;

import java.util.Collections;
import java.util.Set;

import org.joml.Matrix4f;

/**
 * 对应 {@code com.maydaymemory.mae.basic.DummyPose}：一个永远为空的 {@link AnimationPose} 单例。
 *
 * <h2>对应 1.20 的什么</h2>
 * 上游 {@code AnimateGeoItemRenderer#getPose()} / {@code getCachedPose()} 在「没有动画数据」时
 * 返回 {@code DummyPose.INSTANCE}，让调用方不必判空。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 见 {@link AnimationPose}。本类把「空姿态」固化成一个不可变单例。
 *
 * <h2>行为差异 / 降级点</h2>
 * {@link #setBoneMatrix(String, org.joml.Matrix4f)} / {@link #clear()} 在单例上是
 * <b>no-op</b>（抛不了 UnsupportedOperationException 会给上游带来不必要的 try/catch，
 * 而静默忽略更符合「Dummy」的语义）。
 */
public final class DummyPose extends AnimationPose {

    /** 全局唯一实例，对应 MAE {@code DummyPose.INSTANCE}。 */
    public static final DummyPose INSTANCE = new DummyPose();

    private DummyPose() {}

    @Override
    public Matrix4f getBoneMatrix(String boneName) {
        return null;
    }

    @Override
    public Set<String> getBones() {
        return Collections.emptySet();
    }

    @Override
    public void setBoneMatrix(String boneName, Matrix4f matrix) {
        // DummyPose 不可变
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public void clear() {
        // DummyPose 不可变
    }

    @Override
    public String toString() {
        return "DummyPose";
    }
}
