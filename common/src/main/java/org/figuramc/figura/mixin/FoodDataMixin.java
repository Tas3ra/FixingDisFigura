package org.figuramc.figura.mixin;

import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FoodData.class)
public interface FoodDataMixin {
    @Accessor("exhaustionLevel")
    float getExhaustionLevel();
}
