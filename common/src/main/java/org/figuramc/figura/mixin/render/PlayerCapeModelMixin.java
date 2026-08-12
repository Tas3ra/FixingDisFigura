package org.figuramc.figura.mixin.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.figuramc.figura.ducks.PlayerModelCapeAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;
import java.util.Map;

@Mixin(PlayerCapeModel.class)
public abstract class PlayerCapeModelMixin<T extends HumanoidRenderState> extends HumanoidModel<T> implements PlayerModelCapeAccessor {

    // Fake cape ModelPart which we set rotations of.
    // This is because the internal cape renderer uses the matrix stack,
    // instead of setting rotations like every single other ModelPart they render...
    @Unique
    public ModelPart fakeCloak = new ModelPart(List.of(), Map.of());

    @Final
    @Shadow
    private ModelPart cape;

    public PlayerCapeModelMixin(ModelPart modelPart) {
        super(modelPart);
    }

    @Override
    public ModelPart figura$getCloak() {
        return cape;
    }

    @Override
    public ModelPart figura$getFakeCloak() {
        return fakeCloak;
    }

}
