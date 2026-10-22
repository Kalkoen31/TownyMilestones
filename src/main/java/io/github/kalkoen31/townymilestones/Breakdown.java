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

/**
 * The claim bonus of one town, split by source. Every source is already limited by its own max,
 * and {@code total} by the total cap.
 *
 * @param age claims from town age
 * @param activity claims from active days
 * @param advancements claims from advancements
 * @param diplomacy claims from alliances
 * @param total the combined bonus after the total cap
 * @param uncapped the combined bonus before the total cap, shown so admins can test with a cap of 0
 * @param ageDays days of age that count (after the inactivity freeze)
 * @param activeDays active days recorded
 * @param points advancement points credited
 * @param advancementCount advancements credited
 * @param allies qualifying allied nations
 */
record Breakdown(int age, int activity, int advancements, int diplomacy, int total, int uncapped,
                 long ageDays, int activeDays, int points, int advancementCount, int allies) {

    /** The breakdown of a town this plugin has no record of. */
    static final Breakdown NONE = new Breakdown(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
}
