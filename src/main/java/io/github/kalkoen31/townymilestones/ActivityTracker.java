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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Runs once a minute on the main thread. It counts online minutes per player for the current day
 * and records an "active day" for a player's town once one of its residents reaches the
 * configured minutes, then refreshes the cached bonus of all towns.
 *
 * <p>The once-per-day rule is kept in {@link TownData#lastActiveDate}, which is saved, so a
 * restart cannot count the same day twice. The per-player minute counters are memory only: after a
 * restart they start again at zero for the rest of that day.
 */
final class ActivityTracker implements Runnable {

    // Where and how a player was looking at the previous check; used to spot idle players.
    private record Pose(UUID world, int x, int y, int z, int yaw, int pitch) { }

    private final TownyMilestones plugin;
    private final DataStore store;
    private final BonusManager bonus;

    private final Map<UUID, Integer> minutesToday = new HashMap<>();
    private final Map<UUID, Pose> lastPose = new HashMap<>();
    private LocalDate day;

    ActivityTracker(TownyMilestones plugin, DataStore store, BonusManager bonus) {
        this.plugin = plugin;
        this.store = store;
        this.bonus = bonus;
    }

    @Override
    public void run() {
        Settings.Activity cfg = plugin.settings().activity();
        LocalDate today = LocalDate.now(plugin.settings().zone());
        if (!today.equals(day)) {
            day = today;
            minutesToday.clear();
        }

        TownyAPI towny = TownyAPI.getInstance();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Resident resident = towny.getResident(player);
            Town town = resident == null ? null : resident.getTownOrNull();
            if (town == null) {
                continue;
            }
            // The pose is stored on every check, so "moved" always means "since the last minute".
            boolean moved = updatePose(player);
            if (cfg.requireMovement() && !moved) {
                continue;
            }
            int minutes = minutesToday.merge(player.getUniqueId(), 1, Integer::sum);
            if (minutes >= cfg.minMinutes()) {
                recordActiveDay(town, today);
            }
        }
        bonus.recomputeAll();
    }

    // Counts today for the town unless it was already counted.
    private void recordActiveDay(Town town, LocalDate today) {
        TownData data = store.town(town.getUUID(), town.getName());
        if (today.equals(data.lastActiveDate)) {
            return;
        }
        data.activeDays++;
        data.lastActiveDate = today;
        data.lastActiveMillis = System.currentTimeMillis();
        store.markDirty();
    }

    // Stores the player's current pose. Returns true if it differs from the stored one, and false
    // if it is the same or none was stored yet.
    private boolean updatePose(Player player) {
        Location l = player.getLocation();
        Pose now = new Pose(l.getWorld().getUID(), l.getBlockX(), l.getBlockY(), l.getBlockZ(),
                Math.round(l.getYaw()), Math.round(l.getPitch()));
        Pose before = lastPose.put(player.getUniqueId(), now);
        return before != null && !before.equals(now);
    }
}
