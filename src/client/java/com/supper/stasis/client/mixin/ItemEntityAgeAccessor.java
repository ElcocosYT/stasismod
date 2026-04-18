package com.supper.stasis.client.mixin;

import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemEntity.class)
public interface ItemEntityAgeAccessor {
    @Accessor("itemAge")
    int stasis$getItemAge();

    @Accessor("itemAge")
    void stasis$setItemAge(int age);
}
