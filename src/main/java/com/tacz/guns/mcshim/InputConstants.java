package com.tacz.guns.mcshim;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * 对应 1.20.1 的 {@code com.mojang.blaze3d.platform.InputConstants}。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.20 用 GLFW 的键码（{@code org.lwjgl.glfw.GLFW#GLFW_KEY_A} 等）；
 * 1.7.10 用 LWJGL2 的 {@code org.lwjgl.input.Keyboard} / {@code org.lwjgl.input.Mouse}。
 * <b>好消息</b>：GLFW3 的键码数值就是从 GLFW/LWJGL2 那套继承来的，
 * {@code GLFW_KEY_A == Keyboard.KEY_A == 30}、{@code GLFW_KEY_ESCAPE == Keyboard.KEY_ESCAPE == 256}，
 * 因此<b>键码数值可以直接混用</b>。
 *
 * <p>
 * 本类提供与 1.20 同形的 {@code Type} / {@code Key} / {@link #getKey(int, int)} / {@link #UNKNOWN}。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li>{@link Type#SCANCODE} 在 1.7.10 侧没有对应的「扫描码绑定」机制，
 * {@link Type#getOrCreate(int)} 仍能创建 Key，但下游的 {@code KeyBinding} 不会使用它。</li>
 * <li>{@link Key#getDisplayName()} 用 {@code Keyboard.getKeyName} / {@code Mouse.getButtonName}
 * 反查名字；<b>它们需要 LWJGL2 已初始化</b>（即只能在客户端运行期调用），
 * 本方法内部做了 try/catch 保护，初始化前返回 {@code "key.<value>"}。</li>
 * <li>1.20 的 {@code InputConstants} 还有 {@code isKeyDown}/{@code setupKeyboardCallbacks} 等
 * 回调注册 API，1.7.10 由 {@code KeyBinding} 自己处理，本类不提供。</li>
 * </ul>
 */
public final class InputConstants {

    /** 未知输入。 */
    public static final Key UNKNOWN = Type.KEYSYM.getOrCreate(-1);

    private InputConstants() {}

    /**
     * 对应 1.20 {@code InputConstants#getKey(int keyCode, int scanCode)}：
     * 优先按 KEYSYM 解析；{@code keyCode} 落在鼠标键范围（0..7）时按 MOUSE 解析。
     */
    public static Key getKey(int keyCode, int scanCode) {
        if (keyCode >= 0 && keyCode <= 7 && scanCode == 0) {
            return Type.MOUSE.getOrCreate(keyCode);
        }
        return Type.KEYSYM.getOrCreate(keyCode);
    }

    /** 对应 1.20 {@code InputConstants#getKey(InputConstants.Type, int)}。 */
    public static Key getKey(Type type, int value) {
        return type.getOrCreate(value);
    }

    /** 输入类型，对应 1.20 {@code InputConstants.Type}。 */
    public enum Type {

        /** 键盘符号键码（{@code Keyboard.KEY_*}）。 */
        KEYSYM("key.keyboard"),
        /** 鼠标按键（0=左键，1=右键，2=中键）。 */
        MOUSE("key.mouse"),
        /** 扫描码（1.7.10 未使用）。 */
        SCANCODE("scancode");

        private final String displayId;
        private final Map<Integer, Key> map = new HashMap<>();

        Type(String displayId) {
            this.displayId = displayId;
        }

        /** 对应 1.20 {@code Type#getOrCreate(int)}：同一 (type, value) 复用同一个 Key 实例。 */
        public Key getOrCreate(int value) {
            return map.computeIfAbsent(value, v -> new Key(this, v));
        }

        /** 显示名前缀（翻译键用）。 */
        public String displayId() {
            return displayId;
        }
    }

    /**
     * 对应 1.20 {@code InputConstants.Key}：一个「类型 + 数值」的不可变输入标识。
     */
    public static final class Key implements Comparable<Key> {

        private final Type type;
        private final int value;
        private final String name;

        Key(Type type, int value) {
            this.type = type;
            this.value = value;
            this.name = type.displayId + "." + value;
        }

        /** 输入类型。 */
        public Type getType() {
            return type;
        }

        /** 数值（键码 / 鼠标键号 / 扫描码）。 */
        public int getValue() {
            return value;
        }

        /** 程序化名字，例如 {@code key.keyboard.30}。 */
        public String getName() {
            return name;
        }

        /** 对应 1.20 {@code Key#getDisplayName()}：尽量给出人类可读的名字。 */
        public String getDisplayName() {
            try {
                if (type == Type.MOUSE) {
                    String candidate = Mouse.getButtonName(value);
                    if (candidate != null) {
                        return candidate;
                    }
                } else if (type == Type.KEYSYM) {
                    String candidate = Keyboard.getKeyName(value);
                    if (candidate != null) {
                        return candidate;
                    }
                }
            } catch (Throwable ignored) {
                // LWJGL2 未初始化（服务端 / 早期启动），退回程序化名字
            }
            return name;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key)) {
                return false;
            }
            Key that = (Key) other;
            return value == that.value && type == that.type;
        }

        @Override
        public int hashCode() {
            return type.ordinal() * 31 + value;
        }

        @Override
        public int compareTo(Key other) {
            int byType = type.compareTo(other.type);
            return byType != 0 ? byType : Integer.compare(value, other.value);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
