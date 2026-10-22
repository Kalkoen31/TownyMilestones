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

import io.papermc.paper.advancement.AdvancementDisplay;
import org.bukkit.advancement.Advancement;

/** Decides whether an advancement counts for towns and how many points it is worth. */
final class AdvancementRules {

    private AdvancementRules() { }

    /**
     * Points an advancement is worth.
     *
     * @param cfg the advancement settings
     * @param advancement the advancement
     * @return its points, or 0 when it does not count
     */
    static int pointsFor(Settings.Advancements cfg, Advancement advancement) {
        AdvancementDisplay display = advancement.getDisplay();
        return points(cfg, advancement.getKey().toString(), display == null ? null : display.frame());
    }

    /**
     * Points for an advancement key and frame. An advancement counts when it is shown in the
     * advancements screen (recipes and other undisplayed advancements have no display), passes the
     * prefix filters, and its frame is worth points.
     *
     * @param cfg the advancement settings
     * @param key the full advancement key, {@code namespace:path}
     * @param frame the frame, or null for an advancement without a display
     * @return its points, or 0 when it does not count
     */
    static int points(Settings.Advancements cfg, String key, AdvancementDisplay.Frame frame) {
        if (frame == null || !passesFilters(cfg, key)) {
            return 0;
        }
        return switch (frame) {
            case TASK -> cfg.taskPoints();
            case GOAL -> cfg.goalPoints();
            case CHALLENGE -> cfg.challengePoints();
        };
    }

    /**
     * Applies the include and exclude prefix filters.
     *
     * @param cfg the advancement settings
     * @param key the full advancement key
     * @return true if the key may count
     */
    static boolean passesFilters(Settings.Advancements cfg, String key) {
        for (String prefix : cfg.excludePrefixes()) {
            if (key.startsWith(prefix)) {
                return false;
            }
        }
        if (cfg.includePrefixes().isEmpty()) {
            return true;
        }
        for (String prefix : cfg.includePrefixes()) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
