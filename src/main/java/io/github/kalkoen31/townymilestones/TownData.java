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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Everything this plugin remembers about one town. Only touched on the server's main thread.
 *
 * <p>The claim bonus itself is never stored: it is recomputed from these facts and the live Towny
 * state by {@link BonusCalculator}, so a config change applies immediately and nothing can drift.
 */
final class TownData {

    /** Town name when last seen; only there to make the data file readable. */
    String lastKnownName = "";

    /** Number of days on which at least one resident played enough (see {@code active-days}). */
    int activeDays;

    /** The most recent day counted as active, in the configured time zone; null before the first. */
    @Nullable LocalDate lastActiveDate;

    /** Epoch milliseconds when the last active day was recorded; 0 before the first. */
    long lastActiveMillis;

    /**
     * The total bonus last shown to the town (or silently accepted), so a restart does not repeat an
     * announcement; -1 until the first calculation.
     */
    int announcedTotal = -1;

    /** Advancement key mapped to the points it was credited with at the time. */
    final Map<String, Integer> credited = new HashMap<>();

    /**
     * Adds up the credited points.
     *
     * @return the sum of the points of all credited advancements
     */
    int creditedPoints() {
        int sum = 0;
        for (int points : credited.values()) {
            sum += points;
        }
        return sum;
    }
}
