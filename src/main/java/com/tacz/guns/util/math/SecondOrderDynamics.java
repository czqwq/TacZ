package com.tacz.guns.util.math;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.tacz.guns.util.time.GunTime;

/**
 * 二阶动力学（弹簧）平滑器。
 *
 * <p>
 * <b>移植说明（1.20.1 -> 1.7.10）</b>：上游 {@code update()} 里的判断是
 * {@code Minecraft.getInstance() == null || GunTime.shouldTick()}。
 * 1.7.10 的 {@code Minecraft.getInstance()} 叫 {@code Minecraft.getMinecraft()}，
 * 且 {@code net.minecraft.client.Minecraft} 是<b>客户端专用类</b>，
 * 在本类（{@code util/math}，服务端也会加载）里引用它会导致服务端 {@code NoClassDefFoundError}。
 * 由于 {@code getInstance()} 在 1.20 里启动后<b>永不返回 null</b>，该条件实际等价于
 * {@code GunTime.shouldTick()}，故此处只保留后半段 —— 行为等价，且保证服务端安全。
 */
public class SecondOrderDynamics {

    public static final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(15, Thread::new);

    static {
        for (int i = 0; i < 15; i++) {
            executorService.execute(() -> {});
        }
    }

    private final float k1;
    private final float k2;
    private final float k3;

    private float py;
    private float pyd;
    private float px;

    private float target;

    private boolean stop = false;

    /**
     * @param f  Natural frequency
     * @param z  Damping coefficient
     * @param r  Initial velocity
     * @param x0 Initial position
     */
    public SecondOrderDynamics(float f, float z, float r, float x0) {
        k1 = (float) (z / (Math.PI * f));
        k2 = (float) (1 / ((2 * Math.PI * f) * (2 * Math.PI * f)));
        k3 = (float) (r * z / (2 * Math.PI * f));

        py = px = x0;
        pyd = 0;

        target = x0;

        executorService.execute(this::update);
    }

    /**
     * @return processed y value
     */
    public float update(float x) {
        target = x;
        return get();
    }

    public float get() {
        // 修正罕见的 NAN 错误
        if (Float.isNaN(py)) {
            py = 0;
        }
        if (Float.isNaN(pyd)) {
            pyd = 0;
        }
        return py + 0.05f * pyd;
    }

    public void stop() {
        this.stop = true;
    }

    private void update() {
        while (!stop) {
            if (GunTime.shouldTick()) {
                // 修正罕见的 NAN 错误
                if (Float.isNaN(py)) {
                    py = 0;
                }
                if (Float.isNaN(pyd)) {
                    pyd = 0;
                }

                float t = 0.05f;
                float xd = (target - px) / t;
                float y = py + t * pyd;

                pyd = pyd + t * (px + k3 * xd - py - k1 * pyd) / k2;
                px = target;
                py = y;
            }

            try {
                Thread.sleep(6);
            } catch (InterruptedException e) {
                Thread.currentThread()
                    .interrupt();
            }
        }
    }
}
