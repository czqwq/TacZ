package com.tacz.guns.client.model.papi;

import java.util.function.Function;

import net.minecraft.item.ItemStack;

import com.tacz.guns.client.resource.pojo.display.gun.RangefinderManager;

public class RangefinderPapi implements Function<ItemStack, String> {

    public static final String NAME = "rangefinder";

    @Override
    public String apply(ItemStack stack) {
        return RangefinderManager.getText(stack);
    }
}
