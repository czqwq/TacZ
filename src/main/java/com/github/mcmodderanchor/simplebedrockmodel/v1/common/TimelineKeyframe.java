// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/common/TimelineKeyframe.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.common;

import com.maydaymemory.mae.basic.BaseKeyframe;

/**
 * 时间轴事件关键帧：某一时刻要执行的编译后事件 {@link TimelineEvent}。
 */
public class TimelineKeyframe extends BaseKeyframe<TimelineEvent> {

    private final TimelineEvent event;

    public TimelineKeyframe(float timeS, TimelineEvent event) {
        super(timeS);
        this.event = event;
    }

    @Override
    public TimelineEvent getValue() {
        return event;
    }
}
