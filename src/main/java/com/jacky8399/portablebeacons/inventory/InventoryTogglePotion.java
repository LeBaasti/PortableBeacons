package com.jacky8399.portablebeacons.inventory;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.Config;
import com.jacky8399.portablebeacons.PortableBeacons;
import com.jacky8399.portablebeacons.events.Inventories;
import com.jacky8399.portablebeacons.utils.ItemUtils;
import com.jacky8399.portablebeacons.utils.PotionEffectUtils;
import com.jacky8399.portablebeacons.utils.TextUtils;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.util.*;
import java.util.function.Consumer;

public class InventoryTogglePotion implements InventoryProvider {
    private final boolean readOnly;
    private final ItemStack stack;
    private final ItemStack displayStack;
    private final ItemStack expStack;
    private final TreeMap<PotionEffectType, Integer> effects;
    private final HashSet<PotionEffectType> disabledEffects;

    public InventoryTogglePotion(Player player, ItemStack stack, boolean readOnly) {
        this.readOnly = readOnly;
        if (!ItemUtils.isPortableBeacon(stack)) {
            throw new IllegalArgumentException("stack is not beacon");
        }
        this.stack = stack;
        BeaconEffects beaconEffects = ItemUtils.getEffects(stack);
        effects = new TreeMap<>(PotionEffectUtils.POTION_COMPARATOR); // to maintain consistent order
        effects.putAll(beaconEffects.getEffects());
        disabledEffects = new HashSet<>(beaconEffects.getDisabledEffects());

        // display stack
        displayStack = stack.clone();
        expStack = new ItemStack(Material.EXPERIENCE_BOTTLE);

        updateItem(player);
    }

    private void updateItem(Player player) {
        BeaconEffects effects = ItemUtils.getEffects(stack);
        effects.setEffects(this.effects);
        effects.setDisabledEffects(disabledEffects);
        ItemMeta tempMeta = ItemUtils.createMetaCopyItemData(player, effects, Objects.requireNonNull(stack.getItemMeta()));
        stack.setItemMeta(tempMeta);
        // if all effects are disabled
        displayStack.setType(effects.getEffects().size() == disabledEffects.size() ? Material.GLASS : Material.BEACON);
        displayStack.setItemMeta(Bukkit.getItemFactory().asMetaFor(tempMeta, displayStack));

        ItemMeta expMeta = expStack.getItemMeta();
        double expUsage = effects.calcExpPerMinute();
        Map<String, TextUtils.Context> contexts = Map.of(
                "usage", args -> makeFormat(args, "0.#").format(60 / expUsage),
                "player-level", args -> Integer.toString(player.getLevel()),
                "remaining-time", args -> makeFormat(args, "#").format(player.getLevel() * 60 / expUsage)
        );
        String[] expUsageMsg = TextUtils.replacePlaceholders(Config.effectsToggleExpUsageMessage, null, contexts).split("\n");
        expMeta.setDisplayName(expUsageMsg[0]);
        if (expUsageMsg.length > 1) {
            expMeta.setLore(Arrays.asList(Arrays.copyOfRange(expUsageMsg, 1, expUsageMsg.length)));
        }
        expStack.setItemMeta(expMeta);
    }

    @Override
    public String getTitle(Player player) {
        return Config.effectsToggleTitle;
    }

    @Override
    public int getRows() {
        return Math.min(6, effects.size() / 9 + 3);
    }

    private static final String REMOVE_POTION_PERM = "portablebeacons.command.item.remove";
    private static final String BYPASS_MAX_AMPLIFIER_LIMIT_PERM = "portablebeacons.bypass.max.aplifier.limit";

    @Override
    public void populate(Player player, InventoryAccessor inventory) {
        for (int i = 0; i < 9; i++) {
            if (i == 4) {
                inventory.set(4, displayStack, readOnly ? InventoryTogglePotion::playCantEdit : e -> {
                    if (!Config.effectsToggleEnabled)
                        return;
                    // simple check to see if all toggleable effects have been disabled
                    if (effects.keySet().stream()
                            .filter(type -> Config.effectsToggleCanDisableNegativeEffects || !PotionEffectUtils.isNegative(type))
                            .anyMatch(effect -> !disabledEffects.contains(effect))) {
                        // disable all
                        effects.keySet().stream()
                                .filter(type -> checkToggleable(player, type))
                                .forEach(disabledEffects::add);
                    } else {
                        disabledEffects.removeIf(type -> checkToggleable(player, type));
                    }
                    playEdit(e);
                    updateItem(player);
                    inventory.requestRefresh(player);
                });
            } else {
                boolean canRemovePotions = player.hasPermission(REMOVE_POTION_PERM);
                if (i == 0) {
                    // edit potion effects
                    var stack = getPotionManagerDisplay(canRemovePotions);
                    inventory.set(0, stack, addPotionEffects(inventory));
                } else if (i == 8 && Config.nerfExpLevelsPerMinute != 0) {
                    inventory.set(8, expStack);
                } else {
                    inventory.set(i, BORDER);
                }
            }
        }

        Iterator<Map.Entry<PotionEffectType, Integer>> iterator = effects.entrySet().iterator();
        for (int row = 1, rows = getRows(); row < rows; row++) {
            for (int column = 0; column < 9; column++) {
                int slot = row * 9 + column;
                if (!iterator.hasNext()) {
                    inventory.set(slot, null);
                    continue;
                }

                Map.Entry<PotionEffectType, Integer> entry = iterator.next();
                PotionEffectType type = entry.getKey();
                int level = entry.getValue();
                ItemStack stack = getPotionDisplay(type, level);
                inventory.set(slot, stack, togglePotionEffect(inventory, type));
            }
        }
    }

    @NotNull
    private static ItemStack getPotionManagerDisplay(boolean canRemovePotions) {
        var stack = new ItemStack(Material.WRITABLE_BOOK);
        var meta = stack.getItemMeta();
        //changed displayName from "Edit potion effects (Admin)" TO "Edit potion effects"
        meta.setDisplayName(ChatColor.YELLOW + "Edit potion effects");
        var lore = new ArrayList<String>();
        lore.add(ChatColor.GREEN + "Add potion effects by putting potions here");
        if(canRemovePotions) lore.add(ChatColor.RED + "Remove potion effects by pressing the drop key in the UI");
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    @NotNull
    private ItemStack getPotionDisplay(PotionEffectType type, int level) {
        BaseComponent display = PotionEffectUtils.getDisplayName(type, level);
        display.setItalic(false);
        ItemStack stack;
        ItemMeta meta;
        if (disabledEffects.contains(type)) {
            stack = new ItemStack(Material.GLASS_BOTTLE);
            meta = stack.getItemMeta();
            // strip colour and add strikethrough
            display.setColor(ChatColor.GRAY);
            display.setStrikethrough(true);
        } else {
            stack = new ItemStack(Material.POTION);
            meta = stack.getItemMeta();
            PotionMeta potionMeta = (PotionMeta) meta;

            PotionType potionType = PotionEffectUtils.getPotionType(type);
            potionMeta.setBasePotionData(new PotionData(potionType, false, level != 1 && potionType.isUpgradeable()));
            if (potionType == PotionType.WATER) {
                potionMeta.setColor(type.getColor());
            }
            meta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
        }

        ItemUtils.setDisplayName(meta, display);
        stack.setItemMeta(meta);
        stack.setAmount(level);
        return stack;
    }

    private Consumer<InventoryClickEvent> togglePotionEffect(InventoryAccessor inventory, PotionEffectType type) {
        return e -> {
            if (readOnly) {
                playCantEdit(e);
                return;
            }

            Player clicked = (Player) e.getWhoClicked();
            if (e.getClick() != ClickType.DROP && e.getClick() != ClickType.CONTROL_DROP) {
                // check permissions and config if they changed
                if (!checkToggleable(clicked, type)) {
                    playCantEdit(e);
                    return;
                }
                if (!disabledEffects.add(type))
                    disabledEffects.remove(type);
            } else {
                if(!clicked.hasPermission(REMOVE_POTION_PERM)) return;
                effects.remove(type);
                disabledEffects.remove(type);
            }
            playEdit(e);
            updateItem(clicked);
            inventory.requestRefresh(clicked);
        };
    }

    private Consumer<InventoryClickEvent> addPotionEffects(InventoryAccessor inventory) {
        return e -> {
            Player clicked = (Player) e.getWhoClicked();

            // grow inventory if needed
            int oldRows = getRows();

            var cursor = e.getCursor();
            if (cursor == null || !(cursor.getItemMeta() instanceof PotionMeta potionMeta))
                return;
            if(!potionMeta.isUnbreakable()) {
                playCantEdit(e);
                return;
            }
            var basePotion = potionMeta.getBasePotionData();
            if (basePotion.getType().getEffectType() != null) {
                PotionEffectType potion = basePotion.getType() == PotionType.TURTLE_MASTER ?
                        PotionEffectType.DAMAGE_RESISTANCE :
                        basePotion.getType().getEffectType();
                int amplifier = basePotion.isUpgraded() ? 2 : 1;
                effects.merge(potion, amplifier, Integer::sum);
            }
            if (potionMeta.hasCustomEffects()) {
                for (var potionEffect : potionMeta.getCustomEffects()) {
                    if(!clicked.hasPermission(BYPASS_MAX_AMPLIFIER_LIMIT_PERM)) {
                        int maxDefaultAmplifier = PortableBeacons.INSTANCE.getConfig().getInt("effects.default.max-amplifier");
                        int maxPotionAmplifier = PortableBeacons.INSTANCE.getConfig().getInt("effects."+ potionEffect.getType().getName().toLowerCase() +".max-amplifier");
                        int maxAmplifier = Math.max(maxPotionAmplifier, maxDefaultAmplifier);
                        int mergedAmplifier = effects.getOrDefault(potionEffect.getType(), 0) + potionEffect.getAmplifier();
                        if(mergedAmplifier > maxAmplifier) {
                            playCantEdit(e);
                            continue;
                        }
                        potionMeta.removeCustomEffect(potionEffect.getType());
                        cursor.setItemMeta(potionMeta);
                    }
                    effects.merge(potionEffect.getType(), potionEffect.getAmplifier(), Integer::sum);
                }
                if(!potionMeta.hasCustomEffects()) {
                    clicked.setItemOnCursor(new ItemStack(Material.AIR));

                }
            }
            playEdit(e);
            if (getRows() > oldRows) {
                // reopen inventory to resize
                Inventories.openInventory(clicked, this);
            } else {
                updateItem(clicked);
                inventory.requestRefresh(clicked);
            }
        };
    }

    private static final Sound EDIT_SOUND = Sound.BLOCK_NOTE_BLOCK_BASS;
    private static final Sound CANT_EDIT_SOUND = Sound.ENTITY_VILLAGER_NO;

    private static void playCantEdit(InventoryClickEvent e) {
        ((Player) e.getWhoClicked()).playSound(e.getWhoClicked(), CANT_EDIT_SOUND, SoundCategory.PLAYERS, 0.5f, 1);
    }

    private static void playEdit(InventoryClickEvent e) {
        ((Player) e.getWhoClicked()).playSound(e.getWhoClicked(), EDIT_SOUND, SoundCategory.PLAYERS, 0.5f, 1);
    }

    private static boolean checkToggleable(Player player, PotionEffectType type) {
        return Config.effectsToggleEnabled &&
                (Config.effectsToggleCanDisableNegativeEffects || !PotionEffectUtils.isNegative(type)) &&
                (!Config.effectsToggleFineTunePerms ||
                        player.hasPermission("portablebeacons.effect-toggle." + PotionEffectUtils.getName(type)));
    }

    private static final ItemStack BORDER = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);

    static {
        ItemMeta borderMeta = BORDER.getItemMeta();
        borderMeta.setDisplayName(ChatColor.BLACK.toString());
        BORDER.setItemMeta(borderMeta);
    }

    private static DecimalFormat makeFormat(String[] args, String defaultValue) {
        return new DecimalFormat(args.length != 0 ? args[0] : defaultValue);
    }
}
