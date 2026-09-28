package id.menkiplugcore.cdrgates;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CdrGatesPlugin extends JavaPlugin {
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
    private GateStore store;
    private GateCommands commands;
    private NamespacedKey keyTypeKey;
    private NamespacedKey gateNameKey;
    private NamespacedKey secretKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        keyTypeKey = new NamespacedKey(this, "key_type");
        gateNameKey = new NamespacedKey(this, "gate");
        secretKey = new NamespacedKey(this, "secret");

        store = new GateStore(this);
        store.load();
        for (Gate gate : store.all()) gate.busy(false);

        commands = new GateCommands(this, store);
        commands.register();
        Bukkit.getPluginManager().registerEvents(new GateListener(this, store, commands), this);
        startAutoScanner();

        getLogger().info("CdrGates v" + getDescription().getVersion() + " enabled with " + store.all().size() + " gate(s).");
    }

    @Override
    public void onDisable() {
        if (store != null) store.save();
    }

    public Component text(String input) {
        return legacy.deserialize(input == null ? "" : input);
    }

    public void send(CommandSender sender, String message) {
        sender.sendMessage(text(prefix() + message));
    }

    public String prefix() {
        return getConfig().getString("prefix", "&8[&6CdrGates&8] &r");
    }

    public int maxGateSize() {
        return Math.max(1, getConfig().getInt("max-gate-size", 1000));
    }

    public int animationTicks() {
        return Math.max(1, getConfig().getInt("gate-animation-ticks", 14));
    }

    public int autoScanTicks() {
        return Math.max(1, getConfig().getInt("auto-scan-ticks", 10));
    }

    public Material editorTool() {
        return materialConfig("editor-tool", Material.BEDROCK);
    }

    public Material defaultBlock() {
        Material material = materialConfig("default-block", Material.OAK_FENCE);
        return material.isBlock() ? material : Material.OAK_FENCE;
    }

    public Material keyMaterial() {
        return materialConfig("key-material", Material.TRIPWIRE_HOOK);
    }

    public Material masterKeyMaterial() {
        return materialConfig("master-key-material", Material.NETHER_STAR);
    }

    private Material materialConfig(String path, Material fallback) {
        Material found = Material.matchMaterial(getConfig().getString(path, fallback.name()));
        return found == null ? fallback : found;
    }

    public boolean hasAccess(Player player, Gate gate) {
        return switch (gate.accessMode()) {
            case NONE -> true;
            case ITEM -> gate.accessItem() != null && player.getInventory().contains(gate.accessItem());
            case KEY -> hasSecureKey(player, gate);
        };
    }

    private boolean hasSecureKey(Player player, Gate gate) {
        if (gate.accessSecret() == null) return false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir() || !item.hasItemMeta()) continue;
            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            String kind = pdc.get(keyTypeKey, PersistentDataType.STRING);
            if ("master".equals(kind)) {
                String secret = pdc.get(secretKey, PersistentDataType.STRING);
                if (store.masterSecret().equals(secret)) return true;
            } else if ("gate".equals(kind)) {
                String keyGate = pdc.get(gateNameKey, PersistentDataType.STRING);
                String secret = pdc.get(secretKey, PersistentDataType.STRING);
                if (gate.id().equals(GateStore.normalize(keyGate == null ? "" : keyGate))
                        && gate.accessSecret().equals(secret)) return true;
            }
        }
        return false;
    }

    public ItemStack createGateKey(Gate gate, int amount) {
        ItemStack item = new ItemStack(keyMaterial(), amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text("&6Gate Key &8• &e" + gate.name()));
        meta.lore(List.of(
                text("&7Kunci resmi CdrGates"),
                text("&7Gate: &f" + gate.name()),
                text("&8Secure PDC Key")
        ));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyTypeKey, PersistentDataType.STRING, "gate");
        pdc.set(gateNameKey, PersistentDataType.STRING, gate.id());
        pdc.set(secretKey, PersistentDataType.STRING, gate.accessSecret());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createMasterKey() {
        ItemStack item = new ItemStack(masterKeyMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text("&c&lCdrGates Master Key"));
        meta.lore(List.of(
                text("&7Master access seluruh secure gate"),
                text("&8Secure PDC Key")
        ));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyTypeKey, PersistentDataType.STRING, "master");
        pdc.set(secretKey, PersistentDataType.STRING, store.masterSecret());
        item.setItemMeta(meta);
        return item;
    }

    public boolean toggleGate(Gate gate) {
        if (gate == null || gate.busy() || gate.blocks().isEmpty()) return false;
        gate.busy(true);
        boolean opening = !gate.open();

        Set<Integer> ySet = new HashSet<>();
        for (BlockKey key : gate.blocks()) ySet.add(key.y());
        List<Integer> levels = new ArrayList<>(ySet);
        levels.sort(Comparator.naturalOrder());
        if (!opening) levels.sort(Comparator.reverseOrder());

        int step = animationTicks();
        for (int i = 0; i < levels.size(); i++) {
            int y = levels.get(i);
            long delay = (long) i * step;
            Bukkit.getScheduler().runTaskLater(this, () -> applyLayer(gate, y, opening), delay);
        }

        long finishDelay = Math.max(1L, (long) levels.size() * step);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            gate.open(opening);
            gate.busy(false);
            store.save();
        }, finishDelay);
        return true;
    }

    private void applyLayer(Gate gate, int y, boolean opening) {
        for (BlockKey key : gate.blocks()) {
            if (key.y() != y) continue;
            Location location = key.toLocation();
            if (location == null) continue;
            Material material;
            if (opening) {
                material = gate.openPaint().getOrDefault(key, Material.AIR);
            } else {
                material = gate.closedPaint().getOrDefault(key, gate.baseMaterial());
            }
            location.getBlock().setType(material, false);
        }
    }

    public void applyClosedAppearance(Gate gate) {
        for (BlockKey key : gate.blocks()) {
            Location location = key.toLocation();
            if (location == null) continue;
            location.getBlock().setType(gate.closedPaint().getOrDefault(key, gate.baseMaterial()), false);
        }
        gate.open(false);
    }

    private void startAutoScanner() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Gate gate : store.all()) {
                if (gate.sensors().isEmpty() || gate.busy()) continue;
                boolean shouldOpen = false;

                outer:
                for (GateSensor sensor : gate.sensors().values()) {
                    Location center = sensor.toLocation();
                    if (center == null || center.getWorld() == null) continue;
                    double radiusSquared = sensor.radius() * sensor.radius();
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (!player.getWorld().equals(center.getWorld())) continue;
                        if (player.getLocation().distanceSquared(center) <= radiusSquared && hasAccess(player, gate)) {
                            shouldOpen = true;
                            break outer;
                        }
                    }
                }

                if (shouldOpen != gate.open()) toggleGate(gate);
            }
        }, autoScanTicks(), autoScanTicks());
    }
}
