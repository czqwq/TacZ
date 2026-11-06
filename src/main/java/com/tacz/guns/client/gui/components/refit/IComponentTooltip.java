package com.tacz.guns.client.gui.components.refit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

/**
 * 1.20.1 的 {@code List<Component>} 文本提示在 1.7.10 对应 {@code List<IChatComponent>}。
 *
 * <p>
 * 上游 {@code ItemStack#getTooltipLines(Player, TooltipFlag)} 在 1.7.10 是
 * {@link ItemStack#getTooltip(net.minecraft.entity.player.EntityPlayer, boolean)}，它返回的是
 * <b>已经带 § 颜色码的 {@code List<String>}</b>；这里把它们逐一包成
 * {@link ChatComponentText}（{@code getFormattedText()} 原样返回，不会丢格式），
 * 使上游「先取 tooltip、再 append 额外行」的写法仍然成立。
 */
public interface IComponentTooltip {

    /**
     * 获取物品的文本提示（对应 1.20 {@code ItemStack#getTooltipLines}）。
     */
    static List<IChatComponent> getTooltipFromItem(ItemStack stack) {
        List<IChatComponent> result = new ArrayList<>();
        if (stack == null || stack.getItem() == null) {
            return result;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        boolean advanced = minecraft.gameSettings != null && minecraft.gameSettings.advancedItemTooltips;
        List<?> raw = stack.getTooltip(minecraft.thePlayer, advanced);
        for (Object line : raw) {
            result.add(new ChatComponentText(String.valueOf(line)));
        }
        return result;
    }

    /**
     * 添加此接口，会调用此渲染文本提示
     *
     * @param consumer 需要渲染的文本提示
     */
    void renderTooltip(Consumer<List<IChatComponent>> consumer);
}
