package io.github.dioramaaddons.clientmap.compat.diorama.mixin;

import dev.diorama.waypoint.Waypoint;
import dev.diorama.waypoint.WaypointEditScreen;
import io.github.dioramaaddons.clientmap.compat.diorama.DioramaBridge;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A "Share: Private / Public / Group" row in Diorama's waypoint screen, under "Show on maps"; Save and Cancel move
 * down to make room, or on short screens the button goes between Save and Cancel. Previously:
 * down to make room. The choice is sent when the waypoint is saved. Same layout in every supported Diorama version:
 * left = width/2 - 146, top = height/2 - 92, Save and Cancel at top + 192.
 */
@Mixin(value = WaypointEditScreen.class, remap = false)
public abstract class WaypointEditScreenMixin extends Screen {
    @Shadow
    @Final
    private Waypoint waypoint;

    @Unique
    private static final int ROW = 26;

    @Unique
    private String dioaddons$scope;

    @Unique
    private int dioaddons$width = 200;

    protected WaypointEditScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void dioaddons$init(CallbackInfo ci) {
        if (!DioramaBridge.canShare(waypoint)) {
            return;
        }
        if (dioaddons$scope == null) {
            dioaddons$scope = DioramaBridge.shareOf(waypoint.id);
        }
        int left = this.width / 2 - 146;
        int top = this.height / 2 - 92;
        List<AbstractWidget> bottom = new java.util.ArrayList<>();
        for (GuiEventListener child : this.children()) {
            if (child instanceof AbstractWidget w && w.getY() >= top + 190) {
                bottom.add(w);
            }
        }
        int x = left;
        int y = top + 184;
        int w = 200;
        if (top + 192 + ROW + 20 <= this.height - 2) {
            // Room below: a row of its own, Save and Cancel move down.
            bottom.forEach(b -> b.setY(b.getY() + ROW));
        } else {
            // Short screen: Save, Share and Cancel share the bottom row.
            bottom.sort(java.util.Comparator.comparingInt(AbstractWidget::getX));
            int third = (292 - 4) / 3;
            for (int i = 0; i < bottom.size() && i < 2; i++) {
                bottom.get(i).setWidth(third);
                bottom.get(i).setX(i == 0 ? left : left + 2 * (third + 2));
            }
            x = left + third + 2;
            y = top + 192;
            w = third;
        }
        dioaddons$width = w;
        this.addRenderableWidget(Button.builder(dioaddons$label(), b -> {
                    List<String> choices = DioramaBridge.shareChoices();
                    int i = choices.indexOf(dioaddons$scope);
                    dioaddons$scope = choices.get((i + 1) % choices.size());
                    b.setMessage(dioaddons$label());
                })
                .bounds(x, y, w, 20)
                .tooltip(Tooltip.create(Component.translatable("dioaddons_clientmap.share.tooltip")))
                .build());
    }

    @Unique
    private Component dioaddons$label() {
        // On the compact bottom row only the choice is shown ("Public", "Group: x"); the tooltip says what it is.
        String text = dioaddons$width < 200 ? DioramaBridge.shareLabel(dioaddons$scope).getString()
                : Component.translatable("dioaddons_clientmap.share.button", DioramaBridge.shareLabel(dioaddons$scope)).getString();
        int max = dioaddons$width - 12;
        if (this.font.width(text) > max) {
            text = this.font.plainSubstrByWidth(text, max - this.font.width("…")) + "…";
        }
        return Component.literal(text);
    }

    @Inject(method = "save", at = {
            @At(value = "INVOKE", target = "Ldev/diorama/waypoint/WaypointStore;add(Ldev/diorama/waypoint/Waypoint;)V", shift = At.Shift.AFTER),
            @At(value = "INVOKE", target = "Ldev/diorama/waypoint/WaypointStore;changed()V", shift = At.Shift.AFTER)})
    private void dioaddons$saved(CallbackInfo ci) {
        if (dioaddons$scope != null) {
            DioramaBridge.applyShare(waypoint, dioaddons$scope);
        }
    }
}
