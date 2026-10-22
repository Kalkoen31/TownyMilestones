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

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The claim bonus calculation, with no dependency on Towny or the server: it only needs plain
 * values. {@link BonusCalculator} collects those values from Towny; the unit tests call this class
 * directly.
 */
final class BonusMath {

    private static final long DAY_MILLIS = TimeUnit.DAYS.toMillis(1);

    private BonusMath() { }

    /**
     * A nation allied with the town's nation, in both directions.
     *
     * @param key the alliance key (see {@link BonusCalculator#allianceKey})
     * @param residents residents of the allied nation
     */
    record Ally(String key, int residents) { }

    /**
     * Calculates the bonus.
     *
     * @param s current settings
     * @param data the town's record, or null if the town has none yet (then the bonus is zero)
     * @param registeredMillis when the town was created, in epoch milliseconds
     * @param mutualAllies the town's nation's mutual allies; empty for a town without a nation
     * @param allianceSince when each alliance was first seen, by alliance key
     * @param now current time in epoch milliseconds
     * @return the breakdown, never null
     */
    static Breakdown compute(Settings s, TownData data, long registeredMillis, List<Ally> mutualAllies,
                             Map<String, Long> allianceSince, long now) {
        if (data == null) {
            return Breakdown.NONE;
        }

        long ageDays = 0;
        int age = 0;
        if (s.age().enabled() && data.activeDays >= s.age().minActiveDays()) {
            // Age counts up to the end of the grace period after the last active day, so a
            // town that went quiet stops gaining age instead of gaining it forever.
            long graceEnd = data.lastActiveMillis + s.age().graceDays() * DAY_MILLIS;
            long end = Math.min(now, graceEnd);
            ageDays = Math.max(0, (end - registeredMillis) / DAY_MILLIS);
            long periods = ageDays / s.age().periodDays();
            age = (int) Math.min(s.age().max(), periods * s.age().blocksPerPeriod());
        }

        int activity = 0;
        if (s.activity().enabled()) {
            activity = Math.min(s.activity().max(), data.activeDays / s.activity().daysPerBlock());
        }

        int points = data.creditedPoints();
        int advancements = 0;
        if (s.advancements().enabled()) {
            advancements = Math.min(s.advancements().max(), points / s.advancements().pointsPerBlock());
        }

        int allies = 0;
        int diplomacy = 0;
        if (s.diplomacy().enabled()) {
            allies = qualifyingAllies(s.diplomacy(), mutualAllies, allianceSince, now);
            diplomacy = Math.min(s.diplomacy().max(), allies * s.diplomacy().blocksPerAlly());
        }

        int uncapped = age + activity + advancements + diplomacy;
        int total = Math.min(s.totalCap(), uncapped);
        return new Breakdown(age, activity, advancements, diplomacy, total, uncapped,
                ageDays, data.activeDays, points, data.credited.size(), allies);
    }

    // Counts allies whose alliance is old enough and whose nation is big enough.
    private static int qualifyingAllies(Settings.Diplomacy d, List<Ally> mutualAllies,
                                        Map<String, Long> allianceSince, long now) {
        int count = 0;
        for (Ally ally : mutualAllies) {
            Long since = allianceSince.get(ally.key());
            if (since == null || now - since < d.minAllianceDays() * DAY_MILLIS) {
                continue;
            }
            if (ally.residents() >= d.minAllyResidents()) {
                count++;
            }
        }
        return count;
    }
}
