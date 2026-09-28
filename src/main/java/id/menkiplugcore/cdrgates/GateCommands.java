package id.menkiplugcore.cdrgates;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class GateCommands implements CommandExecutor, TabCompleter {
    private static final List<String> COMMANDS = List.of(
            "gate", "togglegate", "deletegate", "gatetype", "gates", "gateviewall",
            "gateaccess", "gatekey", "gateaccessinfo", "clickgate", "clickremover",
            "gateauto", "gatesauto", "delgateauto", "gatehelp"
    );

    private final CdrGatesPlugin plugin;
    private final GateStore store;
    private final Map<UUID, String> editors = new HashMap<>();

    public GateCommands(CdrGatesPlugin plugin, GateStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public void register() {
        for (String name : COMMANDS) {
            PluginCommand command = plugin.getCommand(name);
            if (command == null) continue;
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    public String editorGate(UUID uuid) {
        return editors.get(uuid);
    }

    public void clearEditor(UUID uuid) {
        editors.remove(uuid);
    }

    public void clearEditorsForGate(String gateName) {
        String id = GateStore.normalize(gateName);
        editors.entrySet().removeIf(entry -> GateStore.normalize(entry.getValue()).equals(id));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "gate" -> gate(sender, args);
            case "togglegate" -> toggle(sender, args);
            case "deletegate" -> delete(sender, args);
            case "gatetype" -> type(sender, args);
            case "gates" -> list(sender);
            case "gateviewall" -> viewAll(sender);
            case "gateaccess" -> access(sender, args);
            case "gatekey" -> key(sender, args);
            case "gateaccessinfo" -> accessInfo(sender, args);
            case "clickgate" -> click(sender, args);
            case "clickremover" -> clickRemove(sender, args);
            case "gateauto" -> auto(sender, args);
            case "gatesauto" -> autoList(sender);
            case "delgateauto" -> autoDelete(sender, args);
            case "gatehelp" -> help(sender);
            default -> false;
        };
    }

    private boolean gate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "&cCommand ini hanya untuk player.");
            return true;
        }
        if (args.length == 0) {
            String previous = editors.remove(player.getUniqueId());
            if (previous == null) {
                plugin.send(player, "&cKamu sedang tidak mengedit gate. Gunakan &e/gate <nama>&c.");
            } else {
                plugin.send(player, "&7Editor gate &e" + previous + " &7dinonaktifkan.");
            }
            return true;
        }

        String gate = args[0];
        editors.put(player.getUniqueId(), gate);
        plugin.send(player, "&7Editor gate &e" + gate + " &aaktif&7.");
        plugin.send(player, "&7Pasang &e" + plugin.editorTool().name() + " &7untuk menambah block gate.");
        plugin.send(player, "&7Hancurkan block gate sambil memegang &e" + plugin.editorTool().name() + " &7untuk menghapusnya.");
        plugin.send(player, "&7Gunakan &e/gate &7untuk keluar dari editor.");
        return true;
    }

    private boolean toggle(CommandSender sender, String[] args) {
        if (args.length < 1) return false;
        Gate gate = store.get(args[0]);
        if (gate == null) {
            plugin.send(sender, "&cGate &e" + args[0] + " &ctidak ditemukan.");
            return true;
        }
        if (gate.blocks().isEmpty()) {
            plugin.send(sender, "&cGate &e" + gate.name() + " &ctidak memiliki block.");
            return true;
        }
        plugin.toggleGate(gate);
        return true;
    }

    private boolean delete(CommandSender sender, String[] args) {
        if (args.length < 1) return false;
        Gate gate = store.get(args[0]);
        if (gate == null) {
            plugin.send(sender, "&cGate &e" + args[0] + " &ctidak ditemukan.");
            return true;
        }
        store.remove(gate.id());
        clearEditorsForGate(gate.id());
        store.save();
        plugin.send(sender, "&aGate &e" + gate.name() + " &aberhasil dihapus.");
        return true;
    }

    private boolean type(CommandSender sender, String[] args) {
        if (args.length < 2) return false;
        Gate gate = store.get(args[0]);
        if (gate == null) {
            plugin.send(sender, "&cGate &e" + args[0] + " &ctidak ditemukan.");
            return true;
        }
        Material material = parseMaterial(args, 1);
        if (material == null || !material.isBlock()) {
            plugin.send(sender, "&cMaterial block tidak valid.");
            return true;
        }
        gate.baseMaterial(material);
        gate.closedPaint().clear();
        gate.openPaint().clear();
        if (!gate.open()) plugin.applyClosedAppearance(gate);
        store.save();
        plugin.send(sender, "&7Material dasar gate &e" + gate.name() + " &7diubah menjadi &a" + material.name() + "&7.");
        plugin.send(sender, "&8Paint per-block gate tersebut telah direset.");
        return true;
    }

    private boolean list(CommandSender sender) {
        if (store.all().isEmpty()) {
            plugin.send(sender, "&cBelum ada gate.");
            return true;
        }
        sender.sendMessage(plugin.text("&e&m----------&r &6CdrGates &e&m----------"));
        for (Gate gate : store.all()) {
            String state = gate.open() ? "&aOPEN" : "&cCLOSED";
            sender.sendMessage(plugin.text("&8- &e" + gate.name() + " &7| " + state + " &7| " + gate.blocks().size() + " block"));
        }
        return true;
    }

    private boolean viewAll(CommandSender sender) {
        list(sender);
        autoList(sender);
        return true;
    }

    private boolean access(CommandSender sender, String[] args) {
        if (args.length < 2) return false;
        Gate gate = store.get(args[0]);
        if (gate == null) {
            plugin.send(sender, "&cGate &e" + args[0] + " &ctidak ditemukan.");
            return true;
        }
        String mode = args[1].toLowerCase(Locale.ROOT);
        switch (mode) {
            case "none" -> {
                gate.accessMode(Gate.AccessMode.NONE);
                gate.accessItem(null);
                store.save();
                plugin.send(sender, "&aAccess gate &e" + gate.name() + " &adiubah ke &fNONE&a.");
            }
            case "item" -> {
                if (args.length < 3) {
                    plugin.send(sender, "&cMode ITEM membutuhkan item. Contoh: &e/gateaccess " + gate.name() + " item iron_ingot");
                    return true;
                }
                Material material = parseMaterial(args, 2);
                if (material == null || material.isAir()) {
                    plugin.send(sender, "&cItem tidak valid.");
                    return true;
                }
                gate.accessMode(Gate.AccessMode.ITEM);
                gate.accessItem(material);
                store.save();
                plugin.send(sender, "&aAccess gate &e" + gate.name() + " &adiubah ke &fITEM&a: &e" + material.name() + "&a.");
            }
            case "key" -> {
                gate.accessMode(Gate.AccessMode.KEY);
                if (gate.accessSecret() == null || gate.accessSecret().isBlank()) gate.accessSecret(store.newSecret());
                store.save();
                plugin.send(sender, "&aAccess gate &e" + gate.name() + " &adiubah ke &6SECURE KEY&a.");
                plugin.send(sender, "&7Buat key dengan &e/gatekey give <player> " + gate.name() + " [jumlah]&7.");
            }
            default -> plugin.send(sender, "&cMode valid: &enone&c, &eitem&c, atau &ekey&c.");
        }
        return true;
    }

    private boolean key(CommandSender sender, String[] args) {
        if (args.length < 1) return false;
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "give" -> {
                if (args.length < 3) {
                    plugin.send(sender, "&cGunakan: &e/gatekey give <player> <gate> [jumlah]");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    plugin.send(sender, "&cPlayer &e" + args[1] + " &ctidak online / tidak ditemukan.");
                    return true;
                }
                Gate gate = store.get(args[2]);
                if (gate == null) {
                    plugin.send(sender, "&cGate &e" + args[2] + " &ctidak ditemukan.");
                    return true;
                }
                if (gate.accessSecret() == null || gate.accessSecret().isBlank()) gate.accessSecret(store.newSecret());
                int amount = 1;
                if (args.length >= 4) {
                    try { amount = Integer.parseInt(args[3]); } catch (NumberFormatException ignored) { }
                }
                amount = Math.max(1, Math.min(64, amount));
                target.getInventory().addItem(plugin.createGateKey(gate, amount));
                store.save();
                plugin.send(sender, "&aMemberikan &e" + amount + "x &6Gate Key &7(&e" + gate.name() + "&7) kepada &f" + target.getName() + "&a.");
            }
            case "master" -> {
                if (args.length < 2) {
                    plugin.send(sender, "&cGunakan: &e/gatekey master <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    plugin.send(sender, "&cPlayer &e" + args[1] + " &ctidak online / tidak ditemukan.");
                    return true;
                }
                target.getInventory().addItem(plugin.createMasterKey());
                plugin.send(sender, "&aMaster Key diberikan kepada &f" + target.getName() + "&a.");
            }
            case "revoke" -> {
                if (args.length < 2) {
                    plugin.send(sender, "&cGunakan: &e/gatekey revoke <gate>");
                    return true;
                }
                Gate gate = store.get(args[1]);
                if (gate == null) {
                    plugin.send(sender, "&cGate &e" + args[1] + " &ctidak ditemukan.");
                    return true;
                }
                gate.accessSecret(store.newSecret());
                store.save();
                plugin.send(sender, "&aSemua Secure Key lama untuk gate &e" + gate.name() + " &asekarang INVALID.");
            }
            default -> plugin.send(sender, "&cAction valid: &egive&c, &emaster&c, atau &erevoke&c.");
        }
        return true;
    }

    private boolean accessInfo(CommandSender sender, String[] args) {
        if (args.length < 1) return false;
        Gate gate = store.get(args[0]);
        if (gate == null) {
            plugin.send(sender, "&cGate &e" + args[0] + " &ctidak ditemukan.");
            return true;
        }
        sender.sendMessage(plugin.text("&e&m--------&r &6CdrGates Access &e&m--------"));
        sender.sendMessage(plugin.text("&7Gate: &e" + gate.name()));
        sender.sendMessage(plugin.text("&7Mode: &f" + gate.accessMode().name().toLowerCase(Locale.ROOT)));
        if (gate.accessMode() == Gate.AccessMode.ITEM && gate.accessItem() != null)
            sender.sendMessage(plugin.text("&7Required item: &f" + gate.accessItem().name()));
        if (gate.accessMode() == Gate.AccessMode.KEY)
            sender.sendMessage(plugin.text("&7Secure Key: &aACTIVE"));
        return true;
    }

    private boolean click(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "&cCommand ini hanya untuk player.");
            return true;
        }
        if (args.length < 2) return false;
        Gate.ClickMode mode;
        try {
            mode = Gate.ClickMode.valueOf(args[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.send(sender, "&cMode harus &eright&c, &eleft&c, atau &eboth&c.");
            return true;
        }
        Gate gate = store.get(args[1]);
        if (gate == null) {
            plugin.send(sender, "&cGate &e" + args[1] + " &ctidak ditemukan.");
            return true;
        }
        var targeted = player.getTargetBlockExact(6);
        if (targeted == null || targeted.getType().isAir()) {
            plugin.send(player, "&cArahkan crosshair ke block pemicu terlebih dahulu.");
            return true;
        }
        BlockKey key = BlockKey.from(targeted.getLocation());
        gate.clicks().put(key, mode);
        store.save();
        plugin.send(player, "&aTrigger click dibuat &7untuk gate &e" + gate.name() + "&7. Mode: &f" + mode.name().toLowerCase(Locale.ROOT) + "&7.");
        return true;
    }

    private boolean clickRemove(CommandSender sender, String[] args) {
        if (args.length == 0) {
            boolean any = store.all().stream().anyMatch(g -> !g.clicks().isEmpty());
            if (!any) {
                plugin.send(sender, "&cTidak ada trigger click.");
                return true;
            }
            store.all().forEach(g -> g.clicks().clear());
            store.save();
            plugin.send(sender, "&aSemua trigger click gate berhasil dihapus.");
            return true;
        }
        Gate gate = store.get(args[0]);
        if (gate == null) {
            plugin.send(sender, "&cGate &e" + args[0] + " &ctidak ditemukan.");
            return true;
        }
        if (gate.clicks().isEmpty()) {
            plugin.send(sender, "&cGate &e" + gate.name() + " &ctidak memiliki trigger click.");
            return true;
        }
        gate.clicks().clear();
        store.save();
        plugin.send(sender, "&aSemua trigger click untuk gate &e" + gate.name() + " &aberhasil dihapus.");
        return true;
    }

    private boolean auto(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "&cCommand ini hanya untuk player.");
            return true;
        }
        if (args.length < 2) return false;
        Gate gate = store.get(args[0]);
        if (gate == null) {
            plugin.send(sender, "&cGate &e" + args[0] + " &ctidak ditemukan.");
            return true;
        }
        double radius;
        try { radius = Double.parseDouble(args[1]); }
        catch (NumberFormatException ex) {
            plugin.send(sender, "&cRadius tidak valid.");
            return true;
        }
        if (radius <= 0) {
            plugin.send(sender, "&cRadius harus lebih besar dari 0.");
            return true;
        }
        int id = store.nextAutoId();
        Location loc = player.getLocation();
        GateSensor sensor = new GateSensor(id, player.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), radius);
        gate.sensors().put(id, sensor);
        store.save();
        plugin.send(sender, "&aSensor otomatis dibuat &7untuk gate &e" + gate.name() + "&7.");
        plugin.send(sender, "&7ID: &f" + id + " &8| &7Radius: &f" + radius + " &8| &7Lokasi: &f" + player.getWorld().getName() + " " + round(loc.getX()) + " " + round(loc.getY()) + " " + round(loc.getZ()));
        return true;
    }

    private boolean autoList(CommandSender sender) {
        boolean any = store.all().stream().anyMatch(g -> !g.sensors().isEmpty());
        if (!any) {
            plugin.send(sender, "&cBelum ada sensor gate otomatis.");
            return true;
        }
        sender.sendMessage(plugin.text("&e&m--------&r &6CdrGates Auto &e&m--------"));
        for (Gate gate : store.all()) {
            for (GateSensor sensor : gate.sensors().values()) {
                sender.sendMessage(plugin.text("&8- &e" + gate.name() + " &7| ID &f" + sensor.id() + " &7| R &f" + sensor.radius() + " &7| &f" + sensor.world() + " " + round(sensor.x()) + " " + round(sensor.y()) + " " + round(sensor.z())));
            }
        }
        return true;
    }

    private boolean autoDelete(CommandSender sender, String[] args) {
        if (args.length == 0) {
            boolean any = store.all().stream().anyMatch(g -> !g.sensors().isEmpty());
            if (!any) {
                plugin.send(sender, "&cTidak ada sensor gate otomatis.");
                return true;
            }
            store.all().forEach(g -> g.sensors().clear());
            store.save();
            plugin.send(sender, "&aSemua sensor gate otomatis berhasil dihapus.");
            return true;
        }
        Gate gate = store.get(args[0]);
        if (gate == null) {
            plugin.send(sender, "&cGate &e" + args[0] + " &ctidak ditemukan.");
            return true;
        }
        if (gate.sensors().isEmpty()) {
            plugin.send(sender, "&cGate &e" + gate.name() + " &ctidak memiliki sensor otomatis.");
            return true;
        }
        gate.sensors().clear();
        store.save();
        plugin.send(sender, "&aSemua sensor otomatis gate &e" + gate.name() + " &aberhasil dihapus.");
        return true;
    }

    private boolean help(CommandSender sender) {
        sender.sendMessage(plugin.text("&e&m------------&r &6CdrGates Help &e&m------------"));
        List<String> lines = List.of(
                "&e/gate <nama> &7- Masuk editor gate",
                "&e/gate &7- Keluar dari editor",
                "&e/togglegate <nama> &7- Buka/tutup gate",
                "&e/gatetype <nama> <block> &7- Ganti material dasar gate",
                "&e/gates &7- Lihat semua gate",
                "&e/deletegate <nama> &7- Hapus gate",
                "&e/clickgate <right/left/both> <gate> &7- Buat trigger block",
                "&e/clickremover [gate] &7- Hapus trigger click",
                "&e/autogate <gate> <radius> &7- Buat sensor otomatis di posisi kamu",
                "&e/autogates &7- Lihat sensor otomatis",
                "&e/delautogate [gate] &7- Hapus sensor otomatis",
                "&e/gateviewall &7- Lihat gate + sensor otomatis",
                "&e/gateaccess <gate> <none/item/key> [item] &7- Atur akses",
                "&e/gatekey give <player> <gate> [jumlah] &7- Buat secure key",
                "&e/gatekey master <player> &7- Beri master key",
                "&e/gatekey revoke <gate> &7- Invalidasi semua key lama",
                "&e/gateaccessinfo <gate> &7- Cek mode akses",
                "&8Editor paint: klik kanan = CLOSED, klik kiri = OPEN."
        );
        lines.forEach(line -> sender.sendMessage(plugin.text(line)));
        return true;
    }

    private Material parseMaterial(String[] args, int start) {
        String raw = String.join("_", Arrays.copyOfRange(args, start, args.length));
        return Material.matchMaterial(raw);
    }

    private String round(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            switch (name) {
                case "gate" -> { return List.of(); }
                case "gatekey" -> suggestions.addAll(List.of("give", "master", "revoke"));
                case "clickgate" -> suggestions.addAll(List.of("right", "left", "both"));
                case "gateaccess" -> suggestions.addAll(gateNames());
                default -> suggestions.addAll(gateNames());
            }
        } else if (name.equals("gateaccess") && args.length == 2) {
            suggestions.addAll(List.of("none", "item", "key"));
        } else if (name.equals("gatekey") && args.length == 2) {
            if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("master")) {
                suggestions.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            } else if (args[0].equalsIgnoreCase("revoke")) suggestions.addAll(gateNames());
        } else if (name.equals("gatekey") && args.length == 3 && args[0].equalsIgnoreCase("give")) {
            suggestions.addAll(gateNames());
        } else if (name.equals("clickgate") && args.length == 2) {
            suggestions.addAll(gateNames());
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return suggestions.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().collect(Collectors.toList());
    }

    private List<String> gateNames() {
        return store.all().stream().map(Gate::name).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
