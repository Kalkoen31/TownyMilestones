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
import com.palmergames.bukkit.towny.object.Nation;
import java.util.HashSet;
import java.util.Set;

/**
 * Runs on the main thread at a fixed interval and remembers when each mutual alliance was first
 * seen. Towny does not record when an alliance began, so this is the only way to require an
 * alliance to have lasted some days. An alliance that is no longer mutual is forgotten, so a
 * renewed alliance starts counting again from zero.
 *
 * <p>An alliance that already existed when the plugin was first installed counts from the first
 * scan, not from its real start.
 */
final class AllianceTracker implements Runnable {

    private final DataStore store;

    AllianceTracker(DataStore store) {
        this.store = store;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        Set<String> current = new HashSet<>();
        for (Nation nation : TownyAPI.getInstance().getNations()) {
            for (Nation ally : nation.getAllies()) {
                if (nation.hasMutualAlly(ally)) {
                    current.add(BonusCalculator.allianceKey(nation, ally));
                }
            }
        }
        boolean changed = store.alliances.keySet().retainAll(current);
        for (String key : current) {
            if (store.alliances.putIfAbsent(key, now) == null) {
                changed = true;
            }
        }
        if (changed) {
            store.markDirty();
        }
    }
}
