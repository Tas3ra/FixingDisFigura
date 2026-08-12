package org.figuramc.figura.ducks;

import net.minecraft.resources.Identifier;

// Mojang no longer ships the effects field in GameRenderer, instead statically calling the shaders themselves
public interface GameEffects {

    static Identifier[] getEffects() {
        return EFFECTS;
    }

    Identifier[] EFFECTS = new Identifier[]{Identifier.parse("shaders/post/blur.json"), Identifier.parse("shaders/post/entity_outline.json"), Identifier.parse("shaders/post/invert.json"), Identifier.parse("shaders/post/blur.json"), Identifier.parse("shaders/post/creeper.json"), Identifier.parse("shaders/post/spider.json")};
}