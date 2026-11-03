package io.github.dioramaaddons.clientmap.compat.diorama;

import dev.diorama.MapMenu;
import dev.diorama.waypoint.Waypoint;
import io.github.dioramaaddons.clientmap.client.MapSettingsScreen;
import io.github.dioramaaddons.clientmap.compat.claims.ClaimsCompat;
import io.github.dioramaaddons.clientmap.network.CmPayloads;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Lines added to Diorama's right-click menus. */
public final class DioramaMenus {
    private DioramaMenus() {}

    public static void addSpotItems(List<MapMenu.Item> items, String dimension, BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            return;
        }
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        boolean here = mc.player.level().dimension().location().toString().equals(dimension);
        if (here && ClaimsCompat.available()) {
            var claim = DioramaBridge.claimAt(dimension, cx, cz);
            if (claim == null) {
                items.add(MapMenu.Item.action(Component.translatable("dioaddons_clientmap.menu.claim_chunk"), true,
                        () -> ClaimsCompat.send(true, cx, cz)));
            } else if (claim.owner().equals(mc.player.getUUID())) {
                items.add(MapMenu.Item.info(Component.translatable("dioaddons_clientmap.menu.your_claim", claim.name())));
                items.add(MapMenu.Item.confirmed(Component.translatable("dioaddons_clientmap.menu.release_chunk"),
                        Component.translatable("dioaddons_clientmap.menu.confirm"), () -> ClaimsCompat.send(false, cx, cz)));
            } else {
                items.add(MapMenu.Item.info(Component.translatable("dioaddons_clientmap.menu.claimed_by", claim.name(),
                        claim.ownerName())));
            }
        }
        items.add(MapMenu.Item.action(Component.translatable("dioaddons_clientmap.menu.settings"), true,
                () -> mc.setScreen(new MapSettingsScreen(mc.screen))));
    }

    /** Returns true when something was added. */
    public static boolean addWaypointItems(List<MapMenu.Item> items, Waypoint waypoint) {
        CmPayloads.WaypointEntry shared = DioramaBridge.shared(waypoint.id);
        if (shared == null) {
            String mine = DioramaBridge.shareOf(waypoint.id);
            if (mine.isEmpty() || !waypoint.editable() || items.isEmpty()) {
                return false;
            }
            items.add(1, MapMenu.Item.info(Component.translatable("dioaddons_clientmap.menu.shared_as",
                    DioramaBridge.shareLabel(mine))));
            return true;
        }
        items.add(1, MapMenu.Item.info(Component.translatable("dioaddons_clientmap.menu.shared_by", shared.ownerName())));
        if (shared.canModify()) {
            items.add(MapMenu.Item.confirmed(Component.translatable("dioaddons_clientmap.menu.delete_shared"),
                    Component.translatable("dioaddons_clientmap.menu.confirm"),
                    () -> PacketDistributor.sendToServer(new CmPayloads.DeleteWaypoint(shared.id()))));
        }
        return true;
    }
}
