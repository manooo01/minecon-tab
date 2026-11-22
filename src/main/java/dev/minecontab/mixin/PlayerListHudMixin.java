package dev.minecontab.mixin;

import dev.minecontab.CapeDetector;
import dev.minecontab.TabStyler;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void minecontab$decorate(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        CapeDetector.Minecon cape = CapeDetector.detect(entry);
        if (cape != null) {
            cir.setReturnValue(TabStyler.decorate(cir.getReturnValue(), cape));
        }
    }
}
