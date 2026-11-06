package com.tacz.guns.client.model.papi;

import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;

public class PlayerNamePapi implements Function<ItemStack, String> {

    public static final String NAME = "player_name";

    @Override
    public String apply(ItemStack stack) {
        // 移植说明：上游 PlayerNamePapi.java:14 用 Minecraft.getMinecraft().thePlayer 与
        // player.getName().getString()（Component）。1.7.10 对应 thePlayer 与 getCommandSenderName()，
        // 后者返回的就是 GameProfile 里的玩家名（不含队伍前缀），与 getName() 等价。
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player != null) {
            return player.getCommandSenderName();
        }
        return "";
    }
}
