package id.menkiplugcore.cdrgates;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class Gate {
    public enum AccessMode { NONE, ITEM, KEY }
    public enum ClickMode { RIGHT, LEFT, BOTH }

    private final String id;
    private final String name;
    private Material baseMaterial;
    private boolean open;
    private boolean busy;
    private AccessMode accessMode = AccessMode.NONE;
    private Material accessItem;
    private String accessSecret;

    private final Set<BlockKey> blocks = new LinkedHashSet<>();
    private final Map<BlockKey, Material> closedPaint = new LinkedHashMap<>();
    private final Map<BlockKey, Material> openPaint = new LinkedHashMap<>();
    private final Map<BlockKey, ClickMode> clicks = new LinkedHashMap<>();
    private final Map<Integer, GateSensor> sensors = new LinkedHashMap<>();

    public Gate(String id, String name, Material baseMaterial) {
        this.id = id;
        this.name = name;
        this.baseMaterial = baseMaterial;
    }

    public String id() { return id; }
    public String name() { return name; }
    public Material baseMaterial() { return baseMaterial; }
    public void baseMaterial(Material material) { this.baseMaterial = material; }
    public boolean open() { return open; }
    public void open(boolean value) { this.open = value; }
    public boolean busy() { return busy; }
    public void busy(boolean value) { this.busy = value; }
    public AccessMode accessMode() { return accessMode; }
    public void accessMode(AccessMode mode) { this.accessMode = mode; }
    public Material accessItem() { return accessItem; }
    public void accessItem(Material item) { this.accessItem = item; }
    public String accessSecret() { return accessSecret; }
    public void accessSecret(String secret) { this.accessSecret = secret; }
    public Set<BlockKey> blocks() { return blocks; }
    public Map<BlockKey, Material> closedPaint() { return closedPaint; }
    public Map<BlockKey, Material> openPaint() { return openPaint; }
    public Map<BlockKey, ClickMode> clicks() { return clicks; }
    public Map<Integer, GateSensor> sensors() { return sensors; }
}
