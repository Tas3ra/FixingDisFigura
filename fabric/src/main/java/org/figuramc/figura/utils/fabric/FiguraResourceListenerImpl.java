package org.figuramc.figura.utils.fabric;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import org.figuramc.figura.utils.FiguraIdentifier;
import org.figuramc.figura.utils.FiguraResourceListener;

import java.util.function.Consumer;

public class FiguraResourceListenerImpl extends FiguraResourceListener implements ResourceManagerReloadListener {
    public FiguraResourceListenerImpl(String id, Consumer<ResourceManager> reloadConsumer) {
        super(id, reloadConsumer);
    }

    public static FiguraResourceListener createResourceListener(String id, Consumer<ResourceManager> reloadConsumer) {
        return new FiguraResourceListenerImpl(id, reloadConsumer);
    }

    public Identifier getFabricId() {
        return new FiguraIdentifier(this.id());
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        reloadConsumer().accept(manager);
    }
}
