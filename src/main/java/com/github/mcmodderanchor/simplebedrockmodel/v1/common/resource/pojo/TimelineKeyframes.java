// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/common/resource/pojo/TimelineKeyframes.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo;

import java.util.List;

import it.unimi.dsi.fastutil.doubles.Double2ObjectRBTreeMap;

/**
 * 基岩版动画的 {@code timeline} 时间轴事件关键帧。
 * <p>
 * 标准格式为「时间 -&gt; 字符串 或 字符串数组」，每个字符串是一条指令（Molang 表达式 / 斜杠命令 / 实体事件）。
 * 同一时间点可挂多条，因此每个关键帧的值统一存为字符串列表。
 * 
 * <pre>
 * "timeline": {
 *   "0.45": "action.notify('mag_out')",
 *   "3.0": [ "variable.a = 0;", "variable.b = 1;" ]
 * }
 * </pre>
 */
public class TimelineKeyframes {

    private final Double2ObjectRBTreeMap<List<String>> keyframes;

    public TimelineKeyframes(Double2ObjectRBTreeMap<List<String>> keyframes) {
        this.keyframes = keyframes;
    }

    public Double2ObjectRBTreeMap<List<String>> getKeyframes() {
        return keyframes;
    }
}
