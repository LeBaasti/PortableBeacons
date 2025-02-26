package com.jacky8399.portablebeacons;

import com.jacky8399.portablebeacons.recipes.CombinationRecipe;
import com.jacky8399.portablebeacons.recipes.ExpCostCalculator;
import com.jacky8399.portablebeacons.recipes.RecipeManager;
import com.jacky8399.portablebeacons.recipes.SimpleRecipe;
import com.jacky8399.portablebeacons.utils.BeaconModification;
import com.jacky8399.portablebeacons.utils.PotionEffectUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Logger;

public class Config {
    public static final Map<String, Field> TOGGLES;
    static {
        Class<Config> clazz = Config.class;
        try {
            TOGGLES = Map.of("ritual", clazz.getField("ritualEnabled"),
                    "toggle-gui", clazz.getField("effectsToggleEnabled"),
                    "creation-reminder", clazz.getField("creationReminder"),
                    "world-placement", clazz.getField("placementEnabled"),
                    "world-pickup", clazz.getField("pickupEnabled"));
        } catch (ReflectiveOperationException e) {
            throw new Error("Can't find toggleable field", e);
        }
    }

    public static final int CONFIG_VERSION = 2;

    public static void saveConfig() {
        FileConfiguration config = PortableBeacons.INSTANCE.getConfig();
        // things that can be changed by commands
        config.set("ritual.item", Config.ritualItem);
        // toggles
        config.set("ritual.enabled", Config.ritualEnabled);
        config.set("beacon-item.effects-toggle.enabled", Config.effectsToggleEnabled);
        config.set("beacon-item.creation-reminder.enabled", Config.creationReminder);
        config.set("world-interactions.placement-enabled", Config.placementEnabled);
        config.set("world-interactions.pickup-enabled", Config.pickupEnabled);
        // warning message
        config.set("ritual.__", "ritual.item is saved by the plugin! If it is empty, the default (32x nether_star) is used.");
        config.setComments("ritual.item", List.of("Saved by the plugin", "To change this value in-game, use /portablebeacons setritualitem"));
        if (Config.itemCustomVersion != null)
            config.set("item-custom-version-do-not-edit", Config.itemCustomVersion);
        config.set("config-version", CONFIG_VERSION);
        config.setComments("config-version", List.of("The configuration version. Shouldn't be modified."));
        config.options().copyDefaults(true).setHeader(List.of("Documentation:",
                "https://github.com/jacky8399/PortableBeacons/blob/master/src/main/resources/config.yml"));
    }

    public static void doMigrations(FileConfiguration config) {
        Logger logger = PortableBeacons.INSTANCE.logger;
        int configVersion = config.getInt("config-version", 0);

        List<String> migrated = new ArrayList<>();
        List<String> needsAttention = new ArrayList<>();

        ConfigMigrations.MigrationLogger migrationLogger = new ConfigMigrations.MigrationLogger(logger, migrated, needsAttention);

        if (configVersion == 0) {
            // ritual.item
            ItemStack legacy = config.getItemStack("item-used", config.getItemStack("item_used"));
            if (legacy != null && !config.contains("ritual.item", true)) {
                config.set("ritual.item", legacy);
                config.set("item-used", null);
                config.set("item_used", null);
                migrated.add("item-used/item_used -> ritual.item");
            }

            // beacon-item.nerfs.exp-percentage-per-cycle
            if (config.contains("beacon-item.nerfs.exp-percentage-per-cycle") &&
                    !config.contains("beacon-item.nerfs.exp-levels-per-minute", true)) {
                double expPerCycle = config.getDouble("beacon-item.nerfs.exp-percentage-per-cycle");
                config.set("beacon-item.nerfs.exp-levels-per-minute", expPerCycle * 8);
                config.set("beacon-item.nerfs.exp-percentage-per-cycle", null);
                migrated.add("beacon-item.nerfs: exp-percentage-per-cycle -> exp-levels-per-minute");
            }

            // anvil-combination.max-effect-amplifier
            if (config.contains("anvil-combination.max-effect-amplifier", true)) {
                int maxAmplifier = config.getInt("anvil-combination.max-effect-amplifier");
                config.set("effects.default.max-amplifier", maxAmplifier);
                config.set("anvil-combination.max-effect-amplifier", null);
                migrated.add("anvil-combination.max-effect-amplifier -> effects.default.max-amplifier");
            }

            // legacy effects
            if (migrateEffectsLegacy(config)) {
                migrated.add("beacon-item.effects -> effects");
            }

            // anvil recipes
            doAnvilMigrations(config, migrated);
        } else if (configVersion <= 1) {
            ConfigMigrations.V1.migrateExpUsageMessage(migrationLogger, config);
            ConfigMigrations.V1.migrateEffectNames(migrationLogger, config);
        }

        if (!migrated.isEmpty()) {
            logger.warning("The following legacy config values have been migrated (in memory):");
            migrated.forEach(message -> logger.warning(" - " + message));
            logger.severe("Confirm that the features still work correctly, then run " +
                    "'/portablebeacons saveconfig' to save these changes to disk.");
            logger.severe("Consult documentation and migrate manually if necessary.");
        }
        if (!needsAttention.isEmpty()) {
            logger.severe("Additionally, the following config values need your attention:");
            needsAttention.forEach(message -> logger.severe(" - " + message));
        }
    }

    /**
     * Copies comments and inline comments from {@code source.path} to {@code destination.path}
     */
    static void copyComments(@Nullable YamlConfiguration source, YamlConfiguration destination, String path, boolean deep) {
        if (source == null)
            return;
        var srcSection = source.getConfigurationSection(path);
        var destSection = destination.getConfigurationSection(path);
        if (srcSection == null || destSection == null)
            return;
        for (String key : destSection.getKeys(deep)) {
            List<String> comments = srcSection.getComments(key);
            if (!comments.isEmpty()) {
                destSection.setComments(key, comments);
            }
            comments = srcSection.getInlineComments(key);
            if (!comments.isEmpty()) {
                destSection.setInlineComments(key, comments);
            }
        }
    }

    static void doAnvilMigrations(FileConfiguration config, List<String> migrated) {
        Logger logger = PortableBeacons.INSTANCE.logger;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(RecipeManager.RECIPES_FILE);
        YamlConfiguration defaultYaml;
        try (var reader = new InputStreamReader(Objects.requireNonNull(PortableBeacons.INSTANCE.getResource("recipes.yml")))) {
            defaultYaml = YamlConfiguration.loadConfiguration(reader);
        } catch (IOException ignored) {
            defaultYaml = null;
        }
        boolean changed = false;
        ConfigurationSection section;
        if ((section = config.getConfigurationSection("anvil-combination")) != null) {
            try {
                var values = CombinationRecipe.load("anvil-combination", section.getValues(false)).save();

                yaml.createSection("recipes.anvil-combination", values);
                yaml.setComments("recipes.anvil-combination", """
                        This recipe was created by automatic config migration.
                        To avoid having your changes be overwritten, you must either:
                         - Delete 'anvil-combination' in config.yml, OR
                         - Save the migrated config.yml using /pb saveconfig""".lines().toList());
                copyComments(defaultYaml, yaml, "recipes.anvil-combination", true);
                changed = true;
                config.set("anvil-combination", null);
                migrated.add("anvil-combination -> recipes.yml");
            } catch (Exception ex) {
                logger.severe("Failed to migrate anvil-combination");
                ex.printStackTrace();
            }
        }

        for (String customEnch : new String[]{"exp-reduction", "soulbound"}) {
            String oldPath = "beacon-item.custom-enchantments." + customEnch + ".enchantment";
            String enchStr;
            if ((enchStr = config.getString(oldPath, null)) == null || enchStr.isEmpty())
                continue;
            // enchantment-level was never released
            try {
                Enchantment ench = Objects.requireNonNull(Enchantment.getByKey(NamespacedKey.fromString(enchStr.toLowerCase(Locale.ENGLISH))),
                        "Invalid enchantment " + enchStr);
                var fakeStack = new ItemStack(Material.ENCHANTED_BOOK);
                var meta = (EnchantmentStorageMeta) fakeStack.getItemMeta();
                meta.addStoredEnchant(ench, 1, false);
                fakeStack.setItemMeta(meta);

                // add one level of enchantment
                var virtualEffects = BeaconEffects.load(Map.of(customEnch, 1), true);
                var recipe = new SimpleRecipe(
                        customEnch, InventoryType.ANVIL, fakeStack,
                        List.of(new BeaconModification(BeaconModification.Type.ADD, virtualEffects)),
                        ExpCostCalculator.Dynamic.VANILLA,
                        customEnch.equals("soulbound") ?
                                EnumSet.of(SimpleRecipe.SpecialOps.SET_SOULBOUND_OWNER) :
                                EnumSet.noneOf(SimpleRecipe.SpecialOps.class)
                );
                yaml.createSection("recipes." + customEnch, recipe.save());
                yaml.setComments("recipes." + customEnch, """
                        This recipe was created by automatic config migration.
                        To avoid having your changes be overwritten, you must either:
                         - Delete '%s' in config.yml, OR
                         - Save the migrated config.yml using /pb saveconfig""".formatted(oldPath).lines().toList());
                copyComments(defaultYaml, yaml, "recipes." + customEnch, true);
                config.set(oldPath, null);
                changed = true;
                migrated.add(oldPath + " -> recipes.yml");
            } catch (Exception ex) {
                logger.severe("Failed to migrate " + oldPath);
                ex.printStackTrace();
            }
        }

        if (changed) {
            try {
                yaml.save(RecipeManager.RECIPES_FILE);
            } catch (IOException ex) {
                logger.severe("An error occurred while migrating anvil-combination");
                ex.printStackTrace();
            }
        }
    }

    public static void loadConfig() {
        FileConfiguration config = PortableBeacons.INSTANCE.getConfig();

        doMigrations(config);

        // debug
        Logger logger = PortableBeacons.INSTANCE.logger;
        debug = config.getBoolean("debug");

        // Ritual item
        ritualEnabled = config.getBoolean("ritual.enabled");
        ritualItem = config.getItemStack("ritual.item");
        if (ritualItem == null) {
            ritualItem = new ItemStack(Material.NETHER_STAR, 32);
        }

        // World interactions
        placementEnabled = config.getBoolean("world-interactions.placement-enabled");
        pickupEnabled = config.getBoolean("world-interactions.pickup-enabled");
        pickupRequireSilkTouch = config.getBoolean("world-interactions.pickup-requires-silk-touch");


        // Beacon item properties

        itemCustomVersion = config.getString("item-custom-version-do-not-edit");

        itemName = translateColor(config.getString("beacon-item.name"));
        itemLore = config.getStringList("beacon-item.lore");

        itemCustomModelData = config.getInt("beacon-item.custom-model-data");

        // Creation reminder

        creationReminder = config.getBoolean("beacon-item.creation-reminder.enabled");
        creationReminderMessage = translateColor(config.getString("beacon-item.creation-reminder.message"));
        creationReminderRadius = getAndCheckDouble(0, config, "beacon-item.creation-reminder.radius");
        creationReminderDisableIfOwned = config.getBoolean("beacon-item.creation-reminder.disable-if-already-own-beacon-item");

        // Effects Toggle GUI
        effectsToggleEnabled = config.getBoolean("beacon-item.effects-toggle.enabled");
        effectsToggleTitle = translateColor(config.getString("beacon-item.effects-toggle.title"));
        effectsToggleExpUsageMessage = translateColor(config.getString("beacon-item.effects-toggle.exp-usage-message"));
        effectsToggleCanDisableNegativeEffects = config.getBoolean("beacon-item.effects-toggle.allow-disabling-negative-effects");
        effectsToggleFineTunePerms = config.getBoolean("beacon-item.effects-toggle.require-permission");

        // Custom enchantments

        // Exp-reduction

        enchExpReductionEnabled = config.getBoolean("beacon-item.custom-enchantments.exp-reduction.enabled");
        enchExpReductionMaxLevel = getAndCheckInt(1, config, "beacon-item.custom-enchantments.exp-reduction.max-level");
        enchExpReductionName = translateColor(config.getString("beacon-item.custom-enchantments.exp-reduction.name"));
        enchExpReductionReductionPerLevel = config.getDouble("beacon-item.custom-enchantments.exp-reduction.reduction-per-level");

        // Soulbound

        enchSoulboundEnabled = config.getBoolean("beacon-item.custom-enchantments.soulbound.enabled");
        enchSoulboundMaxLevel = getAndCheckInt(1, config, "beacon-item.custom-enchantments.soulbound.max-level");
        enchSoulboundName = translateColor(config.getString("beacon-item.custom-enchantments.soulbound.name"));
        enchSoulboundOwnerUsageOnly = config.getBoolean("beacon-item.custom-enchantments.soulbound.owner-usage-only");
        enchSoulboundConsumeLevelOnDeath = config.getBoolean("beacon-item.custom-enchantments.soulbound.consume-level-on-death");
        enchSoulboundCurseOfBinding = config.getBoolean("beacon-item.custom-enchantments.soulbound.just-for-fun-curse-of-binding");

        // Beaconator

        var enchBeaconator = config.getConfigurationSection("beacon-item.custom-enchantments.beaconator");
        enchBeaconatorEnabled = enchBeaconator.getBoolean("enabled");
        enchBeaconatorName = getAndColorizeString(enchBeaconator, "name");
        enchBeaconatorLevels = enchBeaconator.getMapList("levels").stream()
                .map(map -> new BeaconatorLevel(((Number) map.get("radius")).intValue(), ExpCostCalculator.deserialize(map.get("exp-cost"))))
                .toList();
        if (enchBeaconatorLevels.isEmpty())
            throw new IllegalArgumentException("beacon-item.custom-enchantments.beaconator.levels cannot be empty");

        // Nerfs

        nerfExpLevelsPerMinute = getAndCheckDouble(0, config, "beacon-item.nerfs.exp-levels-per-minute");
        nerfOnlyApplyInHotbar = config.getBoolean("beacon-item.nerfs.only-apply-in-hotbar");
        nerfDisabledWorlds = new HashSet<>(config.getStringList("beacon-item.nerfs.disabled-worlds"));
        nerfForceDowngrade = config.getBoolean("beacon-item.nerfs.force-downgrade");

        readEffects(config);

        worldGuard = config.getBoolean("world-guard");

        placeholderApi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

        logger.info("Config loaded");
    }

    @SuppressWarnings("ConstantConditions")
    public static void readEffects(FileConfiguration config) {
        effects.clear();
        // of course getValues doesn't work
        ConfigurationSection effectsSection = config.getConfigurationSection("effects");
        Set<String> keys = new LinkedHashSet<>(config.getDefaults().getConfigurationSection("effects").getKeys(false));
        // add keys from actual config later
        // so that user-defined values override defaults
        keys.addAll(effectsSection.getKeys(false));
        for (String key : keys) {
            ConfigurationSection yaml = config.getConfigurationSection("effects." + key);

            try {
                String nameOverride = translateColor(yaml.getString("name-override"));
                String formatOverride = translateColor(yaml.getString("format-override"));
                Integer maxAmplifier = getAndCheckInteger(0, 255, yaml, "max-amplifier");
                Integer duration = getAndCheckInteger(0, Integer.MAX_VALUE, yaml, "duration");
                Boolean hideParticles = (Boolean) yaml.get("hide-particles");

                if (key.equals("default")) {
                    effectsDefaultMaxAmplifier = Objects.requireNonNull(maxAmplifier, "'max-amplifier' in default cannot be null");
                    effectsDefaultDuration = Objects.requireNonNull(duration, "'duration' in default cannot be null");
                    effectsDefaultHideParticles = Objects.requireNonNull(hideParticles, "'hide-particles' in default cannot be null");

                    if (nameOverride != null || formatOverride != null)
                        throw new IllegalArgumentException("The default section cannot have name or format overrides.");
                } else {
                    PotionEffectType type = Objects.requireNonNull(PotionEffectUtils.parsePotion(key, true),
                            key + " is not a valid potion effect");
                    effects.put(type, new PotionEffectInfo(nameOverride, EffectFormatter.parse(formatOverride), duration, maxAmplifier, hideParticles));
                }
            } catch (Exception e) {
                PortableBeacons.INSTANCE.logger.severe("Skipping erroneous config 'effects." + key + "': " + e.getMessage());
            }
        }
    }

    public static boolean migrateEffectsLegacy(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("beacon-item.effects");
        if (section != null) {
            section.getValues(false).forEach((effect, name) -> {
                try {
                    PotionEffectType type = PotionEffectUtils.parsePotion(effect, true);
                    if (type == null)
                        throw new IllegalArgumentException(effect + " is not a valid potion effect");
//                    String newName = Config.translateColor((String) name);
                    // override PotionEffectInfo
//                    Config.PotionEffectInfo info = Config.effects.get(type);
//                    Config.PotionEffectInfo newInfo = new Config.PotionEffectInfo(newName, info != null ? info.durationInTicks : null, info != null ? info.maxAmplifier : null, info != null ? info.hideParticles : null);
//                    Config.effects.put(type, newInfo);
                    config.set("effects." + effect + ".name", name);
                } catch (IllegalArgumentException ignored) {}
            });
            // delete section
            config.set("beacon-item.effects", null);
            return true;
        }
        return false;
    }

    public static String translateColor(String raw) {
        if (raw == null) return null;
        if (raw.indexOf('&') == -1) return raw;
        // replace RGB codes first
        // use EssentialsX &#RRGGBB format
        StringBuilder builder = new StringBuilder(raw);
        int idx;
        while ((idx = builder.indexOf("&#")) != -1) {
            try {
                String colorStr = builder.substring(idx + 1, idx + 8);
                ChatColor color = ChatColor.of(colorStr);
                builder.replace(idx, idx + 8, color.toString());
            } catch (StringIndexOutOfBoundsException | IllegalArgumentException e) {
                throw new IllegalArgumentException("Malformed RGB color around index=" + idx + ", string=" + raw, e);
            }
        }
        raw = builder.toString();
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    static String getFullPath(ConfigurationSection config, String path) {
        String rootPath = config.getCurrentPath();
        if (rootPath == null || rootPath.isEmpty())
            return path;
        else
            return rootPath + "." + path;
    }

    static String getAndColorizeString(ConfigurationSection config, String path) {
        return translateColor(config.getString(path));
    }

    @Nullable
    static Integer getAndCheckInteger(int min, int max, ConfigurationSection config, String path) {
        Object obj = config.get(path);
        if (obj == null)
            return null;
        if (!(obj instanceof Integer value)) {
            PortableBeacons.INSTANCE.logger.severe("Config \"" + getFullPath(config, path) + "\" is not an integer");
            return min;
        }
        if (value > max) {
            PortableBeacons.INSTANCE.logger.severe("Config \"%s\" cannot be larger than %d, got %d."
                    .formatted(getFullPath(config, path), max, value));
            return max;
        } else if (value < min) {
            PortableBeacons.INSTANCE.logger.severe("Config \"%s\" cannot be smaller than %d, got %d."
                    .formatted(getFullPath(config, path), min, value));
            return min;
        } else {
            return value;
        }
    }


    static int getAndCheckInt(int min, int max, ConfigurationSection config, String path) {
        Integer integer = getAndCheckInteger(min, max, config, path);
        if (integer == null) {
            PortableBeacons.INSTANCE.logger.severe("Config \"" + getFullPath(config, path) + "\" cannot be null");
            return min;
        }
        return integer;
    }

    static int getAndCheckInt(int min, ConfigurationSection config, String path) {
        return getAndCheckInt(min, Integer.MAX_VALUE, config, path);
    }
    static double getAndCheckDouble(double min, double max, ConfigurationSection config, String path) {
        double value = config.getDouble(path);
        if (value > max) {
            PortableBeacons.INSTANCE.logger.severe("Config \"%s\" cannot be larger than %f, got %f."
                    .formatted(getFullPath(config, path), max, value));
            return max;
        } else if (value < min) {
            PortableBeacons.INSTANCE.logger.severe("Config \"%s\" cannot be smaller than %f, got %f."
                    .formatted(getFullPath(config, path), min, value));
            return min;
        } else {
            return value;
        }
    }

    static double getAndCheckDouble(double min, ConfigurationSection config, String path) {
        return getAndCheckDouble(min, Double.MAX_VALUE, config, path);
    }
    // Configuration values

    public static boolean debug;

    public static boolean ritualEnabled;
    public static ItemStack ritualItem;

    public static String itemName;
    public static List<String> itemLore;
    public static int itemCustomModelData;
    public static String itemCustomVersion = null;

    // World interactions
    public static boolean placementEnabled;
    public static boolean pickupEnabled;
    public static boolean pickupRequireSilkTouch;

    // Reminder
    public static boolean creationReminder;
    public static String creationReminderMessage;
    public static double creationReminderRadius;
    public static boolean creationReminderDisableIfOwned;

    // Effects toggle GUI
    public static boolean effectsToggleEnabled;
    public static String effectsToggleTitle;
    public static String effectsToggleExpUsageMessage;
    public static boolean effectsToggleCanDisableNegativeEffects;
    public static boolean effectsToggleFineTunePerms;

    // Custom Enchantments
    public static boolean enchExpReductionEnabled;
    public static double enchExpReductionReductionPerLevel;
    public static int enchExpReductionMaxLevel;
    public static String enchExpReductionName;

    public static boolean enchSoulboundEnabled;
    public static boolean enchSoulboundOwnerUsageOnly;
    public static boolean enchSoulboundConsumeLevelOnDeath;
    public static boolean enchSoulboundCurseOfBinding;
    public static int enchSoulboundMaxLevel;
    public static String enchSoulboundName;

    public static boolean enchBeaconatorEnabled;
    public static String enchBeaconatorName;
    public record BeaconatorLevel(double radius, @Nullable ExpCostCalculator expCost) {}
    public static List<BeaconatorLevel> enchBeaconatorLevels;

    // Nerfs
    public static double nerfExpLevelsPerMinute;
    public static boolean nerfOnlyApplyInHotbar;
    public static Set<String> nerfDisabledWorlds;
    public static boolean nerfForceDowngrade;

    public static boolean worldGuard;

    public static boolean placeholderApi;


    public static int effectsDefaultDuration;

    public static int effectsDefaultMaxAmplifier;

    public static boolean effectsDefaultHideParticles;

    private static final HashMap<PotionEffectType, PotionEffectInfo> effects = new HashMap<>();
    private static final PotionEffectInfo EMPTY_INFO = new PotionEffectInfo(null, null, null, null, null);
    @NotNull
    public static PotionEffectInfo getInfo(PotionEffectType potion) {
        return effects.getOrDefault(potion, EMPTY_INFO);
    }

    public interface EffectFormatter {
        String format(int level);

        EffectFormatter DEFAULT = PotionEffectUtils::toRomanNumeral;

        EffectFormatter NUMBER = Integer::toString;

        @Nullable
        static EffectFormatter parse(@Nullable String input) throws IllegalArgumentException {
            if (input == null)
                return null;
            if (input.equals("number")) {
                return NUMBER;
            }
            throw new IllegalArgumentException("Unknown format " + input);
        }
    }

    public record PotionEffectInfo(@Nullable String nameOverride,
                                   @Nullable EffectFormatter formatOverride,
                                   @Nullable Integer durationInTicks,
                                   @Nullable Integer maxAmplifier,
                                   @Nullable Boolean hideParticles) {

        public int getDuration() {
            return durationInTicks != null ? durationInTicks : effectsDefaultDuration;
        }

        public int getMaxAmplifier() {
            return maxAmplifier != null ? maxAmplifier : effectsDefaultMaxAmplifier;
        }

        public boolean isHideParticles() {
            return hideParticles != null ? hideParticles : effectsDefaultHideParticles;
        }
    }

    public static BeaconatorLevel getBeaconatorLevel(int level, int selectedLevel) {
        if (selectedLevel == 0) // unselected
            selectedLevel = level;
        else
            selectedLevel = Math.min(selectedLevel, level);
        if (selectedLevel - 1 >= enchBeaconatorLevels.size())
            return enchBeaconatorLevels.get(enchBeaconatorLevels.size() - 1);
        return enchBeaconatorLevels.get(selectedLevel - 1);
    }
}
