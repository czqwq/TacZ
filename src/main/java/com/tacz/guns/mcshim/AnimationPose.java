package com.tacz.guns.mcshim;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix4f;

/**
 * 对应 {@code com.maydaymemory.mae.basic.Pose}（第三方动画库 MAE 的关节姿态）。
 *
 * <h2>对应 1.20 的什么</h2>
 * 上游 {@code AnimateGeoItemRenderer#getPose()} / {@code getCachedPose()} 的返回类型是
 * {@code com.maydaymemory.mae.basic.Pose}，它是一个「骨头名 → 4x4 关节矩阵」的只读视图，
 * 主要被 {@code playeranimator} 兼容与 GeckoLib/Bedrock 动画桥接用。
 *
 * <h2>1.7.10 用什么实现</h2>
 * MAE 库没有 1.7.10 版本，而且 TACZ 只用了它的 {@code Pose} 容器语义（没有用它的动画求值器）。
 * 本类因此是<b>最小自实现</b>：一个 {@code 骨头名 → Matrix4f} 的 {@link LinkedHashMap}。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li>MAE 的 {@code Pose} 是接口，还有 {@code getPose(String)} 返回 {@code Optional<Matrix4f>}、
 * {@code getBones()} 返回 {@code Set<String>}、以及大量「插值/{@code blend}」操作。
 * 本类只提供读取与写入，<b>不提供</b>插值与组合运算。</li>
 * <li>MAE 的矩阵是<b>可变引用</b>并允许共享；本类在 {@link #getBoneMatrix(String)} 里
 * 返回内部实例（不复制），与 MAE 一致；需要快照请自行 {@code new Matrix4f(...)}。</li>
 * <li>上游用到的 {@link DummyPose#INSTANCE} 是本类的<b>空实现单例</b>（永远没有骨头），
 * 对应 MAE 的 {@code DummyPose}。</li>
 * </ul>
 */
public class AnimationPose {

    private final Map<String, Matrix4f> bones = new LinkedHashMap<>();

    public AnimationPose() {}

    /**
     * 对应 MAE {@code Pose#getPose(String)}：取骨头矩阵。
     *
     * @return 找不到返回 {@code null}（MAE 返回 {@code Optional.empty()}；本 shim 为减少空 Optional 开销改返回 null）
     */
    public Matrix4f getBoneMatrix(String boneName) {
        return bones.get(boneName);
    }

    /** 对应 MAE {@code Pose#getBones()}。 */
    public Set<String> getBones() {
        return bones.keySet();
    }

    /** 设置骨头矩阵。 */
    public void setBoneMatrix(String boneName, Matrix4f matrix) {
        bones.put(boneName, matrix);
    }

    /** 是否没有任何骨头。 */
    public boolean isEmpty() {
        return bones.isEmpty();
    }

    /** 骨头数量。 */
    public int size() {
        return bones.size();
    }

    /** 清空。 */
    public void clear() {
        bones.clear();
    }

    @Override
    public String toString() {
        return "AnimationPose" + bones.keySet();
    }
}
