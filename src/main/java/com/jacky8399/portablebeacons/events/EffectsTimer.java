package com.jacky8399.portablebeacons.events;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.Config;
import com.jacky8399.portablebeacons.PortableBeacons;
import com.jacky8399.portablebeacons.utils.ItemUtils;
import com.jacky8399.portablebeacons.utils.WorldGuardHelper;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ListIterator;

public class EffectsTimer extends BukkitRunnable {
    public static final double CYCLE_TIME_MULTIPLIER = 0.5;

    public void register() {
        this.runTaskTimer(PortableBeacons.INSTANCE, 0, (int) (150 * CYCLE_TIME_MULTIPLIER));
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyEffects(player);
        }
    }

    public void applyEffects(Player player) {
        boolean doWorldGuard = Config.worldGuard && PortableBeacons.INSTANCE.worldGuardInstalled;
        // world check
        if (Config.nerfDisabledWorlds.contains(player.getWorld().getName()))
            return;

        if (doWorldGuard && !player.hasPermission("portablebeacons.bypass.world-guard-limit") &&
                !WorldGuardHelper.canUseBeacons(player))
            return;

        ListIterator<ItemStack> iterator = player.getInventory().iterator();
        while (iterator.hasNext()) {
            int nextIdx = iterator.nextIndex();
            ItemStack is = iterator.next(); // lol
            if (nextIdx > 8 && Config.nerfOnlyApplyInHotbar) {
                continue; // out of hotbar
            }
            if (!ItemUtils.isPortableBeacon(is))
                continue;
            BeaconEffects beaconEffects = ItemUtils.getEffects(is);
            // effects for calculation purposes
            BeaconEffects actualEffects = beaconEffects;
            // owner check
            if (Config.enchSoulboundEnabled && Config.enchSoulboundOwnerUsageOnly &&
                    beaconEffects.soulboundOwner != null && !player.getUniqueId().equals(beaconEffects.soulboundOwner)) {
                continue;
            }

            // filter effects
            if (doWorldGuard)
                actualEffects = WorldGuardHelper.filterBeaconEffects(player, beaconEffects);

            // check levels
            if (!tryDeductExp(player, actualEffects))
                continue;

            player.addPotionEffects(actualEffects.toEffects());

            boolean needsUpdate = beaconEffects.shouldUpdate();
            if (needsUpdate) {
                BeaconEffects newEffects;
                // force downgrade
                if (Config.nerfForceDowngrade)
                    newEffects = beaconEffects.fixOpEffects();
                else
                    newEffects = beaconEffects.clone();
                newEffects.customDataVersion = Config.itemCustomVersion; // actually update custom data version
                iterator.set(ItemUtils.createStackCopyItemData(newEffects, is));
                if (Config.debug)
                    PortableBeacons.INSTANCE.logger.info("Updated obsolete beacon item in " + player.getName() + "'s inventory.");
            }
        }
    }

    boolean tryDeductExp(Player player, BeaconEffects effects) {
        // don't deduct xp from creative players
        if (player.getGameMode() == GameMode.CREATIVE)
            return true;

        double expPerCycle = effects.calcExpPerMinute() * CYCLE_TIME_MULTIPLIER * (1/16d);
        if (expPerCycle != 0) {
            double xp = player.getExp() - expPerCycle;
            if (xp < 0) { // deduct levels
                int required = Math.abs((int) Math.floor(xp));
                int newLevel = player.getLevel() - required;
                if (newLevel < 0)
                    return false;
                xp += required;
                player.setLevel(newLevel);
            }
            player.setExp((float) xp);
        }
        return true;
    }
}
