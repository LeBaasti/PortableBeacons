package com.jacky8399.main;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Config {

    public static void loadConfig() {
        FileConfiguration config = PortableBeacons.INSTANCE.getConfig();

        // debug
        Logger logger = PortableBeacons.INSTANCE.logger;
        if ((debug = config.getBoolean("debug"))) {
            logger.info("Debug enabled");
        }

        // what an unintelligible mess

        // Ritual item
        ritualItem = config.getItemStack("item-used", config.getItemStack("item_used",
                new ItemStack(Material.NETHER_STAR, 32)));

        // Beacon item properties

        itemCustomVersion = config.getString("item-custom-version-do-not-edit");

        itemName = translateColor(config.getString("beacon-item.name", ""));
        itemLore = config.getStringList("beacon-item.lore").stream().map(Config::translateColor).collect(Collectors.toList());

        itemCustomModelData = config.getInt("beacon-item.custom-model-data");

        // Creation reminder

        creationReminder = config.getBoolean("beacon-item.creation-reminder.enabled");
        creationReminderMessage = translateColor(config.getString("beacon-item.creation-reminder.message"));
        creationReminderRadius = config.getDouble("beacon-item.creation-reminder.radius");
        creationReminderDisableIfOwned = config.getBoolean("beacon-item.creation-reminder.disable-if-already-own-beacon-item");

        // Custom enchantments

        // Exp-reduction

        customEnchantExpReductionEnabled = config.getBoolean("beacon-item.custom-enchantments.exp-reduction.enabled");
        customEnchantExpReductionEnchantment = Enchantment.getByKey(NamespacedKey.minecraft(config.getString("beacon-item.custom-enchantments.exp-reduction.enchantment").toLowerCase(Locale.ROOT)));
        customEnchantExpReductionMaxLevel = config.getInt("beacon-item.custom-enchantments.exp-reduction.max-level");
        customEnchantExpReductionName = translateColor(config.getString("beacon-item.custom-enchantments.exp-reduction.name"));
        customEnchantExpReductionReductionPerLevel = config.getDouble("beacon-item.custom-enchantments.exp-reduction.reduction-per-level");

        // Soulbound

        customEnchantSoulboundEnabled = config.getBoolean("beacon-item.custom-enchantments.soulbound.enabled");
        customEnchantSoulboundEnchantment = Enchantment.getByKey(NamespacedKey.minecraft(config.getString("beacon-item.custom-enchantments.soulbound.enchantment").toLowerCase(Locale.ROOT)));
        customEnchantSoulboundMaxLevel = config.getInt("beacon-item.custom-enchantments.soulbound.max-level");
        customEnchantSoulboundName = translateColor(config.getString("beacon-item.custom-enchantments.soulbound.name"));
        customEnchantSoulboundOwnerUsageOnly = config.getBoolean("beacon-item.custom-enchantments.soulbound.owner-usage-only");
        customEnchantSoulboundConsumeLevelOnDeath = config.getBoolean("beacon-item.custom-enchantments.soulbound.consume-level-on-death");

        // Nerfs

        itemNerfsExpPercentagePerCycle = Math.max(0, config.getDouble("beacon-item.nerfs.exp-percentage-per-cycle"));
        itemNerfsOnlyApplyInHotbar = config.getBoolean("beacon-item.nerfs.only-apply-in-hotbar");
        itemNerfsDisabledWorlds = Sets.newHashSet(config.getStringList("beacon-item.nerfs.disabled-worlds"));
        itemNerfsForceDowngrade = config.getBoolean("beacon-item.nerfs.force-downgrade");

        readEffects(config);

        // Anvil combination

        anvilCombinationEnabled = config.getBoolean("anvil-combination.enabled");
        anvilCombinationMaxEffects = config.getInt("anvil-combination.max-effects");
        if (config.contains("anvil-combination.max-effect-amplifier", true)) {
            int maxAmplifier = config.getInt("anvil-combination.max-effect-amplifier");
            Config.effectsDefault = new PotionEffectInfo(null, Config.effectsDefault.durationInTicks, maxAmplifier, Config.effectsDefault.hideParticles);
            config.set("effects.default.max-amplifier", maxAmplifier);
            config.set("anvil-combination.max-effect-amplifier", null);
            PortableBeacons.INSTANCE.logger.info("Old config (anvil-combination.max-effect-amplifier) has been migrated automatically. Use '/pb saveconfig' to save to file.");
        }
        anvilCombinationCombineEffectsAdditively = config.getBoolean("anvil-combination.combine-effects-additively");
        anvilCombinationEnforceVanillaExpLimit = config.getBoolean("anvil-combination.enforce-vanilla-exp-limit");

        worldGuard = config.getBoolean("world-guard");
        if (debug) {
            logger.info(() -> "Ritual item: " + ritualItem);
            logger.info(() -> "Item custom version: " + itemCustomVersion);
            logger.info(() -> "Beacon item/name: " + itemName);
            logger.info(() -> "Beacon item/lore: " + String.join("\\n", itemLore));
            logger.info(() -> "Beacon item/custom model data: " + itemCustomModelData);
            logger.info(() -> "Creation reminder/enabled: " + creationReminder);
            logger.info(() -> "Creation reminder/message: " + creationReminderMessage);
            logger.info(() -> "Creation reminder/radius: " + creationReminderRadius);
            logger.info(() -> "Creation reminder/disable if owned: " + creationReminderDisableIfOwned);
            logger.info(() -> "Enchant/Exp-reduction/enabled: " + customEnchantExpReductionEnabled);
            logger.info(() -> "Enchant/Exp-reduction/enchantment: " + customEnchantExpReductionEnchantment);
            logger.info(() -> "Enchant/Exp-reduction/max level: " + customEnchantExpReductionMaxLevel);
            logger.info(() -> "Enchant/Exp-reduction/name: " + customEnchantExpReductionName);
            logger.info(() -> "Enchant/Exp-reduction/reduction per level: " + customEnchantExpReductionReductionPerLevel);
            logger.info(() -> "Enchant/Soulbound/enabled: " + customEnchantSoulboundEnabled);
            logger.info(() -> "Enchant/Soulbound/enchantment: " + customEnchantSoulboundEnchantment);
            logger.info(() -> "Enchant/Soulbound/max level: " + customEnchantSoulboundMaxLevel);
            logger.info(() -> "Enchant/Soulbound/name: " + customEnchantSoulboundName);
            logger.info(() -> "Enchant/Soulbound/owner usage only: " + customEnchantSoulboundOwnerUsageOnly);
            logger.info(() -> "Enchant/Soulbound/consume level on death: " + customEnchantSoulboundConsumeLevelOnDeath);
            logger.info(() -> "Nerfs/exp % per cycle: " + itemNerfsExpPercentagePerCycle);
            logger.info(() -> "Nerfs/only apply when in hotbar: " + itemNerfsOnlyApplyInHotbar);
            logger.info(() -> "Nerfs/disabled worlds: " + String.join(", ", itemNerfsDisabledWorlds));
            logger.info(() -> "Nerfs/force downgrade: " + itemNerfsForceDowngrade);
            logger.info(() -> "Anvil combination/enabled: " + anvilCombinationEnabled);
            logger.info(() -> "Anvil combination/max effects: " + anvilCombinationMaxEffects);
            logger.info(() -> "Anvil combination/combine effects additively: " + anvilCombinationCombineEffectsAdditively);
            logger.info(() -> "Anvil combination/enforce vanilla exp limit: " + anvilCombinationEnforceVanillaExpLimit);
            logger.info(() -> "World guard: " + worldGuard);
        }

        logger.info("Config loaded");
    }

    @SuppressWarnings("ConstantConditions")
    public static void readEffects(FileConfiguration config) {
        effects = new HashMap<>();
        // of course getValues doesn't work
        ConfigurationSection effectsSection = config.getConfigurationSection("effects");
        Set<String> keys = new LinkedHashSet<>(config.getDefaults().getConfigurationSection("effects").getKeys(false));
        // add keys from actual config later
        // so that user-defined values override defaults
        keys.addAll(effectsSection.getKeys(false));
        for (String key : keys) {
            ConfigurationSection yaml = config.getConfigurationSection("effects." + key);

            try {
                String displayName = translateColor(yaml.getString("name"));
                Integer maxAmplifier = (Integer) yaml.get("max-amplifier");
                Integer duration = (Integer) yaml.get("duration");
                Boolean hideParticles = (Boolean) yaml.get("hide-particles");
                if (key.equals("default")) {
                    Preconditions.checkNotNull(maxAmplifier, "'max-amplifier' in default cannot be null");
                    Preconditions.checkNotNull(duration, "'duration' in default cannot be null");
                    Preconditions.checkNotNull(hideParticles, "'hide-particles' in default cannot be null");

                    effectsDefault = new PotionEffectInfo(null, duration, maxAmplifier, hideParticles);
                } else {
                    PotionEffectType type = PotionEffectUtils.parsePotion(key, true)
                            .orElseThrow(()->new IllegalArgumentException(key + " is not a valid potion effect"));

                    effects.put(type, new PotionEffectInfo(displayName, duration, maxAmplifier, hideParticles));
                }
            } catch (Exception e) {
                PortableBeacons.INSTANCE.logger.severe(String.format("Error while reading config 'effects.%s' (%s), skipping!", key, e.getMessage()));
            }
        }

        Preconditions.checkNotNull(effectsDefault, "default must be provided");
    }

    public static void loadConfigLegacy(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("beacon-item.effects");
        if (section != null) {
            section.getValues(false).forEach((effect, name) -> {
                try {
                    PotionEffectType type = PotionEffectUtils.parsePotion(effect, true)
                            .orElseThrow(IllegalArgumentException::new); // for vanilla names
                    String newName = Config.translateColor((String) name);
                    // override PotionEffectInfo
                    Config.PotionEffectInfo info = Config.effects.get(type);
                    Config.PotionEffectInfo newInfo = new Config.PotionEffectInfo(newName, info != null ? info.durationInTicks : null, info != null ? info.maxAmplifier : null, info != null ? info.hideParticles : null);
                    Config.effects.put(type, newInfo);
                    config.set("effects." + effect + ".name", name);
                } catch (IllegalArgumentException ignored) {}
            });
            // delete section
            config.set("beacon-item.effects", null);
            PortableBeacons.INSTANCE.logger.info("Old config (beacon-item.effects) has been migrated automatically. Use '/pb saveconfig' to save to file.");
        }
    }

    private static final boolean canUseRGB;

    static {
        boolean canUseRGB1;
        try {
            ChatColor.class.getMethod("of", String.class);
            canUseRGB1 = true;
        } catch (NoSuchMethodException e) {
            canUseRGB1 = false;
        }
        canUseRGB = canUseRGB1;
    }

    @Nullable
    public static String translateColor(@Nullable String raw) {
        if (raw == null) return null;
        // replace RGB codes first
        // use EssentialsX &#RRGGBB format
        if (canUseRGB) {
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
        }

        raw = ChatColor.translateAlternateColorCodes('&', raw);
        return raw;
    }

    public static boolean debug;

    public static ItemStack ritualItem;

    public static String itemName;
    public static List<String> itemLore;
    public static int itemCustomModelData;
    public static String itemCustomVersion = null;

    // Reminder
    public static boolean creationReminder;
    public static String creationReminderMessage;
    public static double creationReminderRadius;
    public static boolean creationReminderDisableIfOwned;

    // Custom Enchantments
    public static boolean customEnchantExpReductionEnabled;
    public static double customEnchantExpReductionReductionPerLevel;
    public static int customEnchantExpReductionMaxLevel;
    public static String customEnchantExpReductionName;
    public static Enchantment customEnchantExpReductionEnchantment;

    public static boolean customEnchantSoulboundEnabled;
    public static boolean customEnchantSoulboundOwnerUsageOnly;
    public static boolean customEnchantSoulboundConsumeLevelOnDeath;
    public static int customEnchantSoulboundMaxLevel;
    public static String customEnchantSoulboundName;
    public static Enchantment customEnchantSoulboundEnchantment;

    // Nerfs
    public static double itemNerfsExpPercentagePerCycle;
    public static boolean itemNerfsOnlyApplyInHotbar;
    public static Set<String> itemNerfsDisabledWorlds;
    public static boolean itemNerfsForceDowngrade;

    // Anvil crafting
    public static boolean anvilCombinationEnabled;
    public static int anvilCombinationMaxEffects;
    public static boolean anvilCombinationCombineEffectsAdditively;
    public static boolean anvilCombinationEnforceVanillaExpLimit;

    public static boolean worldGuard;

    @SuppressWarnings("NotNullFieldNotInitialized")
    @NotNull
    public static PotionEffectInfo effectsDefault;
    public static HashMap<PotionEffectType, PotionEffectInfo> effects;

    public static class PotionEffectInfo {
        @Nullable
        public final String displayName;
        @Nullable
        public final Integer durationInTicks;
        @Nullable
        public final Integer maxAmplifier;
        @Nullable
        public final Boolean hideParticles;

        public PotionEffectInfo(@Nullable String displayName, @Nullable Integer duration, @Nullable Integer maxAmplifier, @Nullable Boolean hideParticles) {
            this.displayName = displayName;
            this.durationInTicks = duration;
            this.maxAmplifier = maxAmplifier;
            this.hideParticles = hideParticles;
        }

        @SuppressWarnings("ConstantConditions")
        public int getDuration() {
            return durationInTicks != null ? durationInTicks : effectsDefault.durationInTicks;
        }

        @SuppressWarnings("ConstantConditions")
        public int getMaxAmplifier() {
            return maxAmplifier != null ? maxAmplifier : effectsDefault.maxAmplifier;
        }

        @SuppressWarnings("ConstantConditions")
        public boolean isHideParticles() {
            return hideParticles != null ? hideParticles : effectsDefault.hideParticles;
        }
    }
}
