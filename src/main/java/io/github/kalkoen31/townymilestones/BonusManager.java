/*
 * Towny Milestones
 * Copyright (C) 2026 kalkoen31
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.kalkoen31.townymilestones;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Keeps the computed bonus of every town with a record in a cache, so that the Towny event handler
 * can answer without doing work.
 *
 * <p>Towny creates {@code TownCalculateMaxTownBlocksEvent} with the async flag when it is not on the
 * main thread, so the handler may run on any thread. Therefore the cache is a concurrent map, and an
 * async caller that finds nothing cached gets 0 instead of touching Towny objects.
 */
final class BonusManager {

    private final TownyMilestones plugin;
    private final DataStore store;
    private final Map<UUID, Breakdown> cache = new ConcurrentHashMap<>();

    BonusManager(TownyMilestones plugin, DataStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    /**
     * Recalculates one town and updates the cache. Main thread only.
     *
     * @param town the Towny town
     * @return the fresh breakdown
     */
    Breakdown recompute(Town town) {
        TownData data = store.towns.get(town.getUUID());
        Breakdown b = BonusCalculator.compute(
                plugin.settings(), data, town, store, System.currentTimeMillis());
        cache.put(town.getUUID(), b);
        if (data != null) {
            announceIfGrown(town, data, b.total());
        }
        return b;
    }

    // Tells the town's online residents when the bonus grew. The last shown total is saved, so a
    // restart does not repeat a message; the first calculation, and a drop, are accepted silently.
    private void announceIfGrown(Town town, TownData data, int total) {
        int before = data.announcedTotal;
        if (before == total) {
            return;
        }
        data.announcedTotal = total;
        store.markDirty();
        if (before < 0 || total < before || !plugin.settings().announce()) {
            return;
        }
        Component message = Component.text("Your town " + town.getName() + " earned extra claims: bonus is now +"
                + total + " (was +" + before + "). See /townymilestones info.", NamedTextColor.GREEN);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Resident resident = TownyAPI.getInstance().getResident(player);
            Town playerTown = resident == null ? null : resident.getTownOrNull();
            if (playerTown != null && playerTown.getUUID().equals(town.getUUID())) {
                player.sendMessage(message);
            }
        }
    }

    /** Recalculates every town that has a record; towns Towny no longer knows leave the cache. */
    void recomputeAll() {
        TownyAPI towny = TownyAPI.getInstance();
        for (UUID id : store.towns.keySet()) {
            Town town = towny.getTown(id);
            if (town == null) {
                cache.remove(id);
            } else {
                recompute(town);
            }
        }
    }

    /**
     * The bonus to add to a town's claim limit, before the optional percentage limit.
     *
     * @param town the Towny town
     * @return the bonus; 0 if it is not cached and the caller is not on the main thread
     */
    int bonusFor(Town town) {
        return breakdownOf(town).total();
    }

    /**
     * The cached breakdown of a town.
     *
     * @param town the Towny town
     * @return the breakdown; if it is not cached and the caller is not on the main thread, none
     */
    Breakdown breakdownOf(Town town) {
        Breakdown b = cache.get(town.getUUID());
        if (b == null) {
            if (!Bukkit.isPrimaryThread()) {
                return Breakdown.NONE;
            }
            b = recompute(town);
        }
        return b;
    }

    /**
     * Forgets a deleted town.
     *
     * @param id the town UUID
     */
    void remove(UUID id) {
        cache.remove(id);
        if (store.towns.remove(id) != null) {
            store.markDirty();
        }
    }
}
