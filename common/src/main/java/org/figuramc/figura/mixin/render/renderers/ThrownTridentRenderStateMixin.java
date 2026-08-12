package org.figuramc.figura.mixin.render.renderers;

import net.minecraft.client.renderer.entity.state.ThrownTridentRenderState;
import net.minecraft.world.entity.projectile.Projectile;
import org.figuramc.figura.ducks.FiguraProjectileRenderStateExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ThrownTridentRenderState.class)
public class ThrownTridentRenderStateMixin implements FiguraProjectileRenderStateExtension {
    @Unique
    float figura$delta;

    @Override
    public float figura$getTickDelta() {
        return figura$delta;
    }

    @Override
    public void figura$setTickDelta(float tickDelta) {
        this.figura$delta = tickDelta;
    }

}
