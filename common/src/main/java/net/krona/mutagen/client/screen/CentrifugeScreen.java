package net.krona.mutagen.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.Mutagen;
import net.krona.mutagen.menu.CentrifugeMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

@Environment(EnvType.CLIENT)
public class CentrifugeScreen extends MachineScreen<CentrifugeMenu> {
    private static final ResourceLocation TEXTURE = Mutagen.id("textures/gui/container/centrifuge.png");

    public CentrifugeScreen(CentrifugeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, TEXTURE);
    }
}
