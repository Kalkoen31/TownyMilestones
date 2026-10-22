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

import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects the plain values {@link BonusMath} needs from Towny objects. It reads Towny objects but
 * changes nothing, and it never calls {@code Town#getMaxTownBlocks}, because that fires the event
 * this bonus is added in. Must run on the main thread (it reads Towny objects).
 */
final class BonusCalculator {

    private BonusCalculator() { }

    /**
     * Calculates the bonus of a town.
     *
     * @param s current settings
     * @param data the town's record, or null if the town has none yet (then the bonus is zero)
     * @param town the Towny town
     * @param store source of alliance start times
     * @param now current time in epoch milliseconds
     * @return the breakdown, never null
     */
    static Breakdown compute(Settings s, TownData data, Town town, DataStore store, long now) {
        if (data == null) {
            return Breakdown.NONE;
        }
        return BonusMath.compute(s, data, town.getRegistered(), mutualAllies(town.getNationOrNull()),
                store.alliances, now);
    }

    /**
     * Lists the nations that are allied with a nation in both directions.
     *
     * @param nation the nation, or null
     * @return its mutual allies; empty for null
     */
    static List<BonusMath.Ally> mutualAllies(Nation nation) {
        List<BonusMath.Ally> result = new ArrayList<>();
        if (nation == null) {
            return result;
        }
        for (Nation ally : nation.getAllies()) {
            if (nation.hasMutualAlly(ally)) {
                result.add(new BonusMath.Ally(allianceKey(nation, ally), ally.getNumResidents()));
            }
        }
        return result;
    }

    /**
     * Key of a pair of nations that does not depend on their order: both UUIDs, the smaller first.
     *
     * @param a one nation
     * @param b the other nation
     * @return the key used in {@link DataStore#alliances}
     */
    static String allianceKey(Nation a, Nation b) {
        String x = a.getUUID().toString();
        String y = b.getUUID().toString();
        return x.compareTo(y) <= 0 ? x + "_" + y : y + "_" + x;
    }
}
