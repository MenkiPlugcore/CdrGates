package id.menkiplugcore.cdrgates;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class GateStore {
    private final CdrGatesPlugin plugin;
    private final File file;
    private final Map<String, Gate> gates = new LinkedHashMap<>();
    private String masterSecret;
    private int autoId;

    public GateStore(CdrGatesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        gates.clear();
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder.");
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        masterSecret = yaml.getString("master-secret");
        if (masterSecret == null || masterSecret.isBlank()) masterSecret = newSecret();
        autoId = yaml.getInt("auto-id", 0);

        ConfigurationSection root = yaml.getConfigurationSection("gates");
        if (root == null) return;

        for (String storageKey : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(storageKey);
            if (section == null) continue;

            String name = section.getString("name");
            if (name == null || name.isBlank()) continue;
            String id = normalize(name);
            Material base = material(section.getString("base"), plugin.defaultBlock());
            Gate gate = new Gate(id, name, base);
            gate.open(section.getBoolean("open", false));

            String mode = section.getString("access.mode", "NONE");
            try {
                gate.accessMode(Gate.AccessMode.valueOf(mode.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                gate.accessMode(Gate.AccessMode.NONE);
            }
            gate.accessItem(material(section.getString("access.item"), null));
            gate.accessSecret(section.getString("access.secret"));

            for (String raw : section.getStringList("blocks")) {
                BlockKey key = BlockKey.decode(raw);
                if (key != null) gate.blocks().add(key);
            }
            loadPaint(section.getStringList("paint.closed"), gate.closedPaint());
            loadPaint(section.getStringList("paint.open"), gate.openPaint());

            for (String raw : section.getStringList("clicks")) {
                int cut = raw.lastIndexOf('|');
                if (cut <= 0) continue;
                BlockKey key = BlockKey.decode(raw.substring(0, cut));
                if (key == null) continue;
                try {
                    gate.clicks().put(key, Gate.ClickMode.valueOf(raw.substring(cut + 1).toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    // skip invalid mode
                }
            }

            ConfigurationSection sensors = section.getConfigurationSection("sensors");
            if (sensors != null) {
                for (String sensorKey : sensors.getKeys(false)) {
                    ConfigurationSection s = sensors.getConfigurationSection(sensorKey);
                    if (s == null) continue;
                    try {
                        int sensorId = Integer.parseInt(sensorKey);
                        String world = s.getString("world");
                        if (world == null) continue;
                        GateSensor sensor = new GateSensor(sensorId, world,
                                s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), s.getDouble("radius"));
                        gate.sensors().put(sensorId, sensor);
                        autoId = Math.max(autoId, sensorId);
                    } catch (NumberFormatException ignored) {
                        // skip malformed sensor id
                    }
                }
            }

            gates.put(id, gate);
        }
    }

    private void loadPaint(List<String> list, Map<BlockKey, Material> target) {
        for (String raw : list) {
            int cut = raw.lastIndexOf('|');
            if (cut <= 0) continue;
            BlockKey key = BlockKey.decode(raw.substring(0, cut));
            Material material = material(raw.substring(cut + 1), null);
            if (key != null && material != null && material.isBlock()) target.put(key, material);
        }
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("master-secret", masterSecret);
        yaml.set("auto-id", autoId);
        ConfigurationSection root = yaml.createSection("gates");

        for (Gate gate : gates.values()) {
            ConfigurationSection section = root.createSection(storageKey(gate.id()));
            section.set("name", gate.name());
            section.set("base", gate.baseMaterial().name());
            section.set("open", gate.open());
            section.set("access.mode", gate.accessMode().name());
            if (gate.accessItem() != null) section.set("access.item", gate.accessItem().name());
            if (gate.accessSecret() != null) section.set("access.secret", gate.accessSecret());

            section.set("blocks", gate.blocks().stream().map(BlockKey::encode).toList());
            section.set("paint.closed", savePaint(gate.closedPaint()));
            section.set("paint.open", savePaint(gate.openPaint()));

            List<String> clicks = new ArrayList<>();
            gate.clicks().forEach((key, mode) -> clicks.add(key.encode() + "|" + mode.name()));
            section.set("clicks", clicks);

            ConfigurationSection sensors = section.createSection("sensors");
            gate.sensors().forEach((id, sensor) -> {
                ConfigurationSection s = sensors.createSection(Integer.toString(id));
                s.set("world", sensor.world());
                s.set("x", sensor.x());
                s.set("y", sensor.y());
                s.set("z", sensor.z());
                s.set("radius", sensor.radius());
            });
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save data.yml: " + e.getMessage());
        }
    }

    private List<String> savePaint(Map<BlockKey, Material> map) {
        List<String> out = new ArrayList<>();
        map.forEach((key, material) -> out.add(key.encode() + "|" + material.name()));
        return out;
    }

    public Gate get(String name) {
        return name == null ? null : gates.get(normalize(name));
    }

    public Gate getOrCreate(String name) {
        String id = normalize(name);
        return gates.computeIfAbsent(id, key -> new Gate(key, name, plugin.defaultBlock()));
    }

    public boolean exists(String name) {
        return get(name) != null;
    }

    public void remove(String name) {
        if (name != null) gates.remove(normalize(name));
    }

    public Collection<Gate> all() {
        return gates.values();
    }

    public String masterSecret() {
        return masterSecret;
    }

    public int nextAutoId() {
        return ++autoId;
    }

    public String newSecret() {
        return UUID.randomUUID().toString() + "-" + UUID.randomUUID();
    }

    public static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private String storageKey(String id) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(id.getBytes(StandardCharsets.UTF_8));
    }

    private Material material(String raw, Material fallback) {
        if (raw == null) return fallback;
        Material material = Material.matchMaterial(raw);
        return material == null ? fallback : material;
    }
}
