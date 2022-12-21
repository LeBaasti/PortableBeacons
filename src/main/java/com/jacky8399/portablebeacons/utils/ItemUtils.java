package com.jacky8399.portablebeacons.utils;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.Config;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.regex.Pattern;

public class ItemUtils {
    public static boolean isPortableBeacon(ItemStack stack) {
        return stack != null && stack.getType() == Material.BEACON && stack.hasItemMeta() &&
                stack.getItemMeta().getPersistentDataContainer().has(BeaconEffects.BeaconEffectsDataType.STORAGE_KEY, BeaconEffects.BeaconEffectsDataType.STORAGE_TYPE);
    }

    public static BeaconEffects getEffects(ItemStack stack) {
        return stack.getItemMeta().getPersistentDataContainer().get(BeaconEffects.BeaconEffectsDataType.STORAGE_KEY, BeaconEffects.BeaconEffectsDataType.STORAGE_TYPE);
    }

    public static void setEffects(ItemStack stack, BeaconEffects effects) {
        ItemMeta meta = stack.getItemMeta();
        setEffects(meta, effects);
        stack.setItemMeta(meta);
    }

    public static void setEffects(ItemMeta meta, BeaconEffects effects) {
        meta.getPersistentDataContainer().set(BeaconEffects.BeaconEffectsDataType.STORAGE_KEY, BeaconEffects.BeaconEffectsDataType.STORAGE_TYPE, effects);
    }

    public static boolean isPyramid(ItemStack stack) {
        return stack != null && stack.getType() == Material.BEACON && stack.hasItemMeta() &&
                stack.getItemMeta().getPersistentDataContainer().has(BeaconPyramid.BeaconPyramidDataType.STORAGE_KEY, BeaconPyramid.BeaconPyramidDataType.STORAGE_TYPE);
    }

    public static BeaconPyramid getPyramid(ItemStack stack) {
        return stack.getItemMeta().getPersistentDataContainer().get(BeaconPyramid.BeaconPyramidDataType.STORAGE_KEY, BeaconPyramid.BeaconPyramidDataType.STORAGE_TYPE);
    }

    public static void setPyramid(ItemStack stack, BeaconPyramid pyramid) {
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(BeaconPyramid.BeaconPyramidDataType.STORAGE_KEY, BeaconPyramid.BeaconPyramidDataType.STORAGE_TYPE, pyramid);
        stack.setItemMeta(meta);
    }

    public static ItemStack createStack(BeaconEffects effects) {
        return createStackCopyItemData(effects, new ItemStack(Material.BEACON));
    }

    // preserves item name and things
    public static ItemStack createStackCopyItemData(BeaconEffects effects, ItemStack stack) {
        ItemMeta meta = Objects.requireNonNull(stack.getItemMeta());
        boolean hideEffects = !meta.getItemFlags().contains(ItemFlag.HIDE_POTION_EFFECTS); // can't use HIDE_ENCHANTS

        // copy lore, enchants and set effects
        if (!meta.hasDisplayName() && !Config.itemName.isEmpty()) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + Config.itemName);
        }
        if (Config.itemCustomModelData > -1) {
            meta.setCustomModelData(Config.itemCustomModelData);
        }

        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        List<String> effectsLore = effects.toLore(hideEffects, hideEffects);
        if (Config.itemLore.size() != 0) {
            List<String> lore = new ArrayList<>(effectsLore.size() + Config.itemLore.size() + 1);
            lore.addAll(effectsLore);
            lore.add("");
            lore.addAll(Config.itemLore);
            meta.setLore(lore);
        } else {
            meta.setLore(effectsLore);
        }
        setEffects(meta, effects);

        ItemStack newIs = new ItemStack(Material.BEACON);
        newIs.setItemMeta(meta);
        newIs.setAmount(stack.getAmount());
        return newIs;
    }

    private static int getCost(BeaconEffects effects) {
        return effects.getEffects().values().stream().mapToInt(level -> 1 << level).sum();
    }

    public static int calculateCombinationCost(ItemStack is1, ItemStack is2) {
        if (!isPortableBeacon(is1))
            return 0;
        if (isPortableBeacon(is2)) {
            BeaconEffects e1 = getEffects(is1), e2 = getEffects(is2);
            return getCost(e1) + getCost(e2);
        } else if (is2 != null && is2.getType() == Material.ENCHANTED_BOOK &&
                is2.getItemMeta() instanceof EnchantmentStorageMeta storageMeta) {
            BeaconEffects effects = getEffects(is1);
            Map<Enchantment, Integer> enchants = storageMeta.getStoredEnchants();
            int numOfEnchantsApplicable = 0;

            Integer enchLevel;
            if (Config.enchSoulboundEnabled && Config.enchSoulboundEnchantment != null &&
                    (enchLevel = enchants.get(Config.enchSoulboundEnchantment)) != null &&
                    enchLevel >= Config.enchSoulboundEnchantmentLevel)
                numOfEnchantsApplicable++;
            if (Config.enchExpReductionEnabled && Config.enchExpReductionEnchantment != null &&
                    (enchLevel = enchants.get(Config.enchExpReductionEnchantment)) != null &&
                    enchLevel >= Config.enchExpReductionEnchantmentLevel)
                numOfEnchantsApplicable++;

            return getCost(effects) * numOfEnchantsApplicable;
        }
        return 0;
    }

    @Nullable
    public static ItemStack showFailure(String message, boolean show) {
        if (!show)
            return null;
        ItemStack messageStack = new ItemStack(Material.BARRIER);
        ItemMeta meta = messageStack.getItemMeta();
        meta.setDisplayName(ChatColor.RED + message);
        messageStack.setItemMeta(meta);
        return messageStack;
    }

    public static ItemStack combineStack(Player player, ItemStack is1, ItemStack is2, boolean shouldShowFailure) {
        if (!isPortableBeacon(is1) || is2 == null)
            return null;
        BeaconEffects original = getEffects(is1);
        // soulbound owner check
        if (Config.enchSoulboundOwnerUsageOnly && !original.isOwner(player)) {
            return showFailure("You do not own the portable beacon", shouldShowFailure);
        }

        if (isPortableBeacon(is2)) {
            BeaconEffects e2 = getEffects(is2);
            // owner check for other beacon
            if (Config.enchSoulboundOwnerUsageOnly && !e2.isOwner(player)) {
                return showFailure("You do not own the portable beacon", shouldShowFailure);
            }
            HashMap<PotionEffectType, Integer> potions = new HashMap<>(original.getEffects());
            BinaryOperator<Integer> algorithm = Config.anvilCombinationCombineEffectsAdditively ?
                    Integer::sum : ItemUtils::anvilAlgorithm;
            for (var entry : e2.getEffects().entrySet()) {
                var potionType = entry.getKey();
                int potionLevel = entry.getValue();
                int maxPotionLevel = Config.getInfo(potionType).getMaxAmplifier();
                int newLevel = potions.merge(potionType, potionLevel, algorithm);
                if (newLevel > maxPotionLevel) {
                    return showFailure("Overpowered " + PotionEffectUtils.getName(potionType), shouldShowFailure);
                }
            }

            // check max effects count / overpowered effects
            if (potions.size() > Config.anvilCombinationMaxEffects) {
                return showFailure("Too many effects", shouldShowFailure);
            }

            BeaconEffects newEffects = original.clone();
            newEffects.setEffects(potions);
            return createStack(newEffects);
        } else if (is2.getType() == Material.ENCHANTED_BOOK &&
                is2.getItemMeta() instanceof EnchantmentStorageMeta storageMeta) {
            Map<Enchantment, Integer> enchants = storageMeta.getStoredEnchants();
            boolean hasCombination = false;

            Integer enchLevel;
            if (Config.enchSoulboundEnabled && Config.enchSoulboundEnchantment != null &&
                    (enchLevel = enchants.get(Config.enchSoulboundEnchantment)) != null &&
                    enchLevel >= Config.enchSoulboundEnchantmentLevel) {
                hasCombination = true;
                if (++original.soulboundLevel > Config.enchSoulboundMaxLevel) // level check
                    return showFailure("Overpowered Soulbound", shouldShowFailure);
                if (original.soulboundOwner == null || original.soulboundOwner.equals(player.getUniqueId()))
                    original.soulboundOwner = player.getUniqueId();
            }
            if (Config.enchExpReductionEnabled && Config.enchExpReductionEnchantment != null &&
                    (enchLevel = enchants.get(Config.enchExpReductionEnchantment)) != null &&
                    enchLevel >= Config.enchExpReductionEnchantmentLevel) {
                hasCombination = true;
                if (++original.expReductionLevel > Config.enchExpReductionMaxLevel) // level check
                    return showFailure("Overpowered Experience Efficiency", shouldShowFailure);
            }

            return hasCombination ?
                    createStackCopyItemData(original, is1) :
                    showFailure("Incompatible enchantments", shouldShowFailure);
        }
        return showFailure("Invalid combination", shouldShowFailure);
    }

    private static int anvilAlgorithm(int s1, int s2) {
        if (s1 == s2) {
            return s1 + 1;
        } else {
            return Math.max(s1, s2);
        }
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("(\\s)?\\{(.+?)}");
    public static String replacePlaceholders(@Nullable Player player, String input, ContextLevel level, Map<String, Context> contexts) {
        if (input.indexOf('{') == -1) {
            return input + level.doReplacement();
        }
        return PLACEHOLDER.matcher(input).replaceAll(match -> {
            String[] args = match.group(2).split("\\|");
            String contextName = args[0];
            Context context = contextName.equals("level") ? level : contexts.get(contextName);
            if (context == null)
                return "[Unknown placeholder " + contextName + "]";
            args = Arrays.copyOfRange(args, 1, args.length);
            boolean prependSpace = match.group(1) != null && !context.shouldRemovePrecedingSpace(args);
            String replacement = context.doReplacement(args);
            if (replacement == null) {
                // ignore
                return match.group();
            }
            return (prependSpace ? " " : "") + replacement;
        });
    }

    public static String replacePlaceholders(@Nullable Player player, String input, ContextLevel level) {
        return replacePlaceholders(player, input, level, Collections.emptyMap());
    }

    public static String replacePlaceholders(@Nullable Player player, String input, int level) {
        return replacePlaceholders(player, input, new ContextLevel(level), Collections.emptyMap());
    }

    public static abstract class Context {
        public boolean shouldRemovePrecedingSpace(String... args) {
            return false;
        }

        public abstract String doReplacement(String... args);
    }

    public static class ContextLevel extends Context {
        public final int level;
        public ContextLevel(int level) {
            this.level = level;
        }

        @Override
        public boolean shouldRemovePrecedingSpace(String... args) {
            return args.length == 0; // {level}
        }

        @Override
        public String doReplacement(String... args) {
            return args.length == 1 && "number".equals(args[0]) ?
                    Integer.toString(level) :
                    PotionEffectUtils.toRomanNumeral(level);
        }
    }

    /*
        {...}
        {...|<reduction/multiplier>}
        {...|<reduction/multiplier>|<number/percentage>}
     */
    public static class ContextExpReduction extends Context {
        public final double expMultiplier;
        public ContextExpReduction(double expMultiplier) {
            this.expMultiplier = expMultiplier;
        }

        public ContextExpReduction(int level) {
            this(Math.max(0, 1 - level * Config.enchExpReductionReductionPerLevel));
        }

        @Override
        public String doReplacement(String... args) {
            boolean isMultiplier = false;
            boolean isPercentage = true;

            if (args.length >= 1) {
                isMultiplier = "multiplier".equals(args[0]);
            }
            if (args.length == 2) {
                isPercentage = "percentage".equals(args[1]);
            }

            double actualMultiplier = isMultiplier ? expMultiplier : 1 - expMultiplier;
            return isPercentage ? String.format("%.2f%%", actualMultiplier * 100) : String.format("%.2f", actualMultiplier);
        }
    }

    /*
        {...}
        {...|<name/uuid>}
        {...|name|<fallback>}
     */
    public static class ContextUUID extends Context {
        @Nullable
        UUID uuid;
        @Nullable
        String playerName;
        String fallback;
        public ContextUUID(@Nullable UUID uuid, @Nullable String playerName, String fallback) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.fallback = fallback;
        }

        public ContextUUID(UUID uuid, String fallback) {
            this(uuid, uuid != null ? Bukkit.getOfflinePlayer(uuid).getName() : null, fallback);
        }

        @Override
        public String doReplacement(String... args) {
            String fallback = args.length == 2 && "name".equals(args[0]) ? args[1] : this.fallback;
            if (uuid == null)
                return fallback;
            if (args.length == 0 || "name".equals(args[0])) {
                return playerName != null ? playerName : fallback;
            } else if (args.length == 1 && "uuid".equals(args[0])) {
                return uuid.toString();
            }
            return playerName;
        }
    }
}
