package id.menkiplugcore.cdrgates;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class GateListener implements Listener {
    private final CdrGatesPlugin plugin;
    private final GateStore store;
    private final GateCommands commands;

    public GateListener(CdrGatesPlugin plugin, GateStore store, GateCommands commands) {
        this.plugin = plugin;
        this.store = store;
        this.commands = commands;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        commands.clearEditor(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        String selected = commands.editorGate(player.getUniqueId());
        if (selected == null) return;
        if (event.getBlockPlaced().getType() != plugin.editorTool()) return;

        if (player.getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
            plugin.send(player, "&cEditor gate hanya dapat digunakan dalam Creative Mode.");
            return;
        }

        Gate gate = store.getOrCreate(selected);
        BlockKey key = BlockKey.from(event.getBlockPlaced().getLocation());
        if (gate.blocks().contains(key)) {
            event.setCancelled(true);
            plugin.send(player, "&eLokasi itu sudah menjadi bagian dari gate &6" + gate.name() + "&e.");
            return;
        }
        if (gate.blocks().size() >= plugin.maxGateSize()) {
            event.setCancelled(true);
            plugin.send(player, "&cGate &e" + gate.name() + " &csudah mencapai batas " + plugin.maxGateSize() + " block.");
            return;
        }

        gate.blocks().add(key);
        gate.open(false);
        event.getBlockPlaced().setType(gate.baseMaterial(), false);
        plugin.applyClosedAppearance(gate);
        store.save();
        plugin.send(player, "&aBlock ditambahkan &7ke gate &e" + gate.name() + "&7 di &f" + key.encode() + "&7.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String selected = commands.editorGate(player.getUniqueId());
        if (selected == null || player.getGameMode() != GameMode.CREATIVE) return;
        if (player.getInventory().getItemInMainHand().getType() != plugin.editorTool()) return;

        Gate gate = store.get(selected);
        if (gate == null) return;
        BlockKey key = BlockKey.from(event.getBlock().getLocation());
        if (!gate.blocks().remove(key)) return;

        gate.closedPaint().remove(key);
        gate.openPaint().remove(key);
        plugin.send(player, "&cBlock dihapus &7dari gate &e" + gate.name() + "&7.");

        if (gate.blocks().isEmpty()) {
            store.remove(gate.id());
            commands.clearEditorsForGate(gate.id());
            plugin.send(player, "&7Gate &e" + gate.name() + " &7dihapus karena sudah tidak memiliki block.");
        }
        store.save();
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        String selected = commands.editorGate(player.getUniqueId());
        if (selected != null) {
            handleEditorPaint(event, selected);
            return;
        }

        BlockKey clicked = BlockKey.from(event.getClickedBlock().getLocation());
        List<Gate> matches = new ArrayList<>();
        for (Gate gate : store.all()) {
            Gate.ClickMode mode = gate.clicks().get(clicked);
            if (mode == null) continue;
            if (matches(action, mode)) matches.add(gate);
        }

        for (Gate gate : matches) {
            String permission = permissionFor(gate.clicks().get(clicked));
            if (!player.hasPermission(permission)) continue;
            if (!plugin.hasAccess(player, gate)) {
                plugin.send(player, "&cKamu tidak memiliki akses untuk membuka gate &e" + gate.name() + "&c.");
                continue;
            }
            plugin.toggleGate(gate);
        }
    }

    private void handleEditorPaint(PlayerInteractEvent event, String selected) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.CREATIVE) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() == plugin.editorTool()) return;

        Gate gate = store.get(selected);
        if (gate == null) return;
        BlockKey key = BlockKey.from(event.getClickedBlock().getLocation());
        if (!gate.blocks().contains(key)) return;

        if (held.getType().isAir() || !held.getType().isBlock()) {
            String state = event.getAction() == Action.RIGHT_CLICK_BLOCK ? "CLOSED" : "OPEN";
            plugin.send(player, "&cPegang block yang ingin dipakai sebagai tampilan " + state + ".");
            event.setCancelled(true);
            return;
        }

        Material material = held.getType();
        event.setCancelled(true);
        Location location = event.getClickedBlock().getLocation();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            gate.closedPaint().put(key, material);
            if (!gate.open()) location.getBlock().setType(material, false);
            plugin.send(player, "&7Material CLOSED block ini diubah menjadi &e" + material.name() + "&7.");
        } else {
            gate.openPaint().put(key, material);
            if (gate.open()) location.getBlock().setType(material, false);
            plugin.send(player, "&7Material OPEN block ini diubah menjadi &e" + material.name() + "&7.");
        }
        store.save();
    }

    private boolean matches(Action action, Gate.ClickMode mode) {
        return switch (mode) {
            case BOTH -> true;
            case RIGHT -> action == Action.RIGHT_CLICK_BLOCK;
            case LEFT -> action == Action.LEFT_CLICK_BLOCK;
        };
    }

    private String permissionFor(Gate.ClickMode mode) {
        return switch (mode) {
            case RIGHT -> "gate.toggle.click.right";
            case LEFT -> "gate.toggle.click.left";
            case BOTH -> "gate.toggle.click.both";
        };
    }
}
