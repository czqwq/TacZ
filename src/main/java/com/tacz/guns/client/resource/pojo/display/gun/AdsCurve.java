package com.tacz.guns.client.resource.pojo.display.gun;

import java.util.Locale;

import javax.annotation.Nullable;

import com.tacz.guns.mcshim.Mth;
import com.tacz.guns.util.math.Easing;

/** 开镜进度中的一个运动窗口和缓动曲线。 */
public class AdsCurve {

    private float start = 0;
    private float end = 1;
    /**
     * 缓动名，取值同时接受 {@code snake_case}（{@code ease_in_out_sine}）与
     * {@code camelCase}（{@code easeInOutSine}）两种写法。
     * <p>
     * 平台差异：上游 {@code AdsCurve.java:11} 是 {@code private Ease ease}（枚举），靠
     * {@code @SerializedName(value = "ease_in_sine", alternate = "easeInSine")} 接受两种写法；
     * 1.7.10 自带的 gson 2.2.4 没有 {@code SerializedName#alternate}（2.4 才加），
     * 因此存原始字符串、在 {@link #resolveEase()} 里按两种写法解析，接受范围与上游一致。
     */
    private String ease = "linear";
    private boolean clamp = true;

    private transient Ease resolvedEase;
    private transient boolean easeResolved;

    public boolean isValid() {
        return Float.isFinite(start) && Float.isFinite(end)
            && start >= 0
            && end <= 1
            && start < end
            && resolveEase() != null;
    }

    @Nullable
    private Ease resolveEase() {
        if (!easeResolved) {
            resolvedEase = parseEase(ease);
            easeResolved = true;
        }
        return resolvedEase;
    }

    /** 把 snake_case / camelCase 两种写法都映射到枚举常量。 */
    @Nullable
    private static Ease parseEase(@Nullable String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        StringBuilder snake = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    snake.append('_');
                }
                snake.append(Character.toLowerCase(c));
            } else {
                snake.append(c == '-' ? '_' : c);
            }
        }
        try {
            return Ease.valueOf(
                snake.toString()
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public float evaluate(float progress) {
        float t = (progress - start) / (end - start);
        if (clamp) {
            t = Mth.clamp(t, 0, 1);
        }
        Ease ease = resolveEase();
        if (ease == null) {
            // 上游此时会 NPE；这里退化为线性，避免坏配置直接崩渲染
            return t;
        }
        return switch (ease) {
            case LINEAR -> t;
            case EASE_IN_QUAD -> t * t;
            case EASE_OUT_QUAD -> 1 - (1 - t) * (1 - t);
            case EASE_IN_OUT_QUAD -> t < 0.5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;
            case EASE_IN_CUBIC -> t * t * t;
            case EASE_OUT_CUBIC -> 1 - (1 - t) * (1 - t) * (1 - t);
            case EASE_IN_OUT_CUBIC -> t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
            case EASE_IN_QUART -> (float) Math.pow(t, 4);
            case EASE_OUT_QUART -> 1 - (float) Math.pow(1 - t, 4);
            case EASE_IN_OUT_QUART -> t < 0.5f ? 8 * (float) Math.pow(t, 4) : 1 - (float) Math.pow(-2 * t + 2, 4) / 2;
            case EASE_IN_QUINT -> (float) Math.pow(t, 5);
            case EASE_OUT_QUINT -> 1 - (float) Math.pow(1 - t, 5);
            case EASE_IN_OUT_QUINT -> t < 0.5f ? 16 * (float) Math.pow(t, 5) : 1 - (float) Math.pow(-2 * t + 2, 5) / 2;
            case EASE_IN_SINE -> (float) (1 - Math.cos(t * Math.PI / 2));
            case EASE_IN_EXPO -> (float) Easing.easeInExpo(t);
            case EASE_OUT_EXPO -> t <= 0 ? 0 : t >= 1 ? 1 : (float) (1 - Math.pow(2, -10.0 * t));
            case EASE_IN_OUT_EXPO -> t <= 0 ? 0
                : t >= 1 ? 1
                    : t < 0.5f ? (float) Math.pow(2, 20.0 * t - 10) / 2 : (float) (2 - Math.pow(2, -20.0 * t + 10)) / 2;
            case EASE_OUT_SINE -> (float) Math.sin(t * Math.PI / 2);
            case EASE_IN_OUT_SINE -> (float) ((1 - Math.cos(t * Math.PI)) / 2);
            // 圆弧曲线在单位区间外没有实数定义，始终限制其输入。
            case EASE_IN_CIRC -> 1 - (float) Math.sqrt(1 - Math.pow(Mth.clamp(t, 0, 1), 2));
            case EASE_OUT_CIRC -> (float) Math.sqrt(1 - Math.pow(Mth.clamp(t, 0, 1) - 1, 2));
            case EASE_IN_OUT_CIRC -> {
                float u = Mth.clamp(t, 0, 1);
                yield u < 0.5f ? (1 - (float) Math.sqrt(1 - Math.pow(2 * u, 2))) / 2
                    : ((float) Math.sqrt(1 - Math.pow(-2 * u + 2, 2)) + 1) / 2;
            }
            case EASE_IN_BACK -> (float) (2.70158 * t * t * t - 1.70158 * t * t);
            case EASE_OUT_BACK -> (float) (1 + 2.70158 * Math.pow(t - 1, 3) + 1.70158 * Math.pow(t - 1, 2));
            case EASE_IN_OUT_BACK -> t < 0.5f
                ? (float) (Math.pow(2 * t, 2) * ((1.70158 * 1.525 + 1) * 2 * t - 1.70158 * 1.525) / 2)
                : (float) (Math.pow(2 * t - 2, 2) * ((1.70158 * 1.525 + 1) * (2 * t - 2) + 1.70158 * 1.525) / 2 + 1);
            case SMOOTHSTEP -> t * t * (3 - 2 * t);
        };
    }

    private enum Ease {
        LINEAR,
        EASE_IN_SINE,
        EASE_OUT_SINE,
        EASE_IN_OUT_SINE,
        EASE_IN_QUAD,
        EASE_OUT_QUAD,
        EASE_IN_OUT_QUAD,
        EASE_IN_CUBIC,
        EASE_OUT_CUBIC,
        EASE_IN_OUT_CUBIC,
        EASE_IN_QUART,
        EASE_OUT_QUART,
        EASE_IN_OUT_QUART,
        EASE_IN_QUINT,
        EASE_OUT_QUINT,
        EASE_IN_OUT_QUINT,
        EASE_IN_EXPO,
        EASE_OUT_EXPO,
        EASE_IN_OUT_EXPO,
        EASE_IN_CIRC,
        EASE_OUT_CIRC,
        EASE_IN_OUT_CIRC,
        EASE_IN_BACK,
        EASE_OUT_BACK,
        EASE_IN_OUT_BACK,
        SMOOTHSTEP
    }
}
