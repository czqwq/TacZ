package com.tacz.guns.entity.sync;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;

import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.entity.sync.core.IDataSerializer;
import com.tacz.guns.mcshim.PacketBuffer;

/**
 * 搬运自上游 1.20.1 {@code entity/sync/ModSerializers.java}。
 *
 * <p>
 * 1.7.10 适配：{@code FriendlyByteBuf} → {@link PacketBuffer}，{@code CompoundTag/Tag} →
 * {@link NBTTagCompound}/{@link NBTBase}（NBT key {@code StateType}/{@code CountDown} 保持上游不变）。
 */
public class ModSerializers {

    public static final IDataSerializer<ReloadState> RELOAD_STATE = new IDataSerializer<>() {

        @Override
        public void write(PacketBuffer buf, ReloadState value) {
            buf.writeInt(
                value.getStateType()
                    .ordinal());
            buf.writeLong(value.getCountDown());
        }

        @Override
        public ReloadState read(PacketBuffer buf) {
            ReloadState reloadState = new ReloadState();
            // 1.7.10 差异：这里不要用 `values()[buf.readInt()]` 裸取 —— 上游 1.20 的实体同步走原版
            // 注册表/类型化 DataWatcher，序号不可能越界；本工程改走自研网络包，畸形/版本不匹配的包
            // 会在服务端 tick 的 NetworkTaskQueue 排空路径上直接 AIOOBE。越界时归一到默认 NOT_RELOADING。
            int ordinal = buf.readInt();
            long countDown = buf.readLong();
            ReloadState.StateType[] values = ReloadState.StateType.values();
            if (ordinal < 0 || ordinal >= values.length) {
                // 注意：countDown 必须在判越界之前读完，否则本 DataEntry 少读 8 字节，
                // 后续 DataEntry 会按错位的字节流解码（整包 DecoderException）。
                return reloadState;
            }
            reloadState.setStateType(values[ordinal]);
            reloadState.setCountDown(countDown);
            return reloadState;
        }

        @Override
        public NBTBase write(ReloadState value) {
            NBTTagCompound compound = new NBTTagCompound();
            compound.setString(
                "StateType",
                value.getStateType()
                    .toString());
            compound.setLong("CountDown", value.getCountDown());
            return compound;
        }

        @Override
        public ReloadState read(NBTBase nbt) {
            NBTTagCompound compound = (NBTTagCompound) nbt;
            try {
                ReloadState.StateType stateType = ReloadState.StateType.valueOf(compound.getString("StateType"));
                long countDown = compound.getLong("CountDown");
                ReloadState reloadState = new ReloadState();
                reloadState.setStateType(stateType);
                reloadState.setCountDown(countDown);
                return reloadState;
            } catch (IllegalArgumentException ignore) {}
            return new ReloadState();
        }
    };
}
