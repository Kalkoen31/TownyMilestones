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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;

/**
 * Logs how many of the loaded advancements count under the current filters, and warns about
 * filter prefixes that match nothing (usually a typo). It also warns when the advancements maximum
 * cannot be reached with the advancements that exist.
 */
final class AdvancementReport {

    private AdvancementReport() { }

    /**
     * Writes the report to the log. Main thread only.
     *
     * @param cfg the advancement settings
     * @param log where to write
     */
    static void log(Settings.Advancements cfg, Logger log) {
        int displayed = 0;
        int counting = 0;
        long totalPoints = 0;
        Map<String, Integer> prefixHits = new HashMap<>();
        for (Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext();) {
            Advancement advancement = it.next();
            if (advancement.getDisplay() == null) {
                continue;
            }
            displayed++;
            String key = advancement.getKey().toString();
            int points = AdvancementRules.pointsFor(cfg, advancement);
            if (points > 0) {
                counting++;
                totalPoints += points;
            }
            for (String prefix : allPrefixes(cfg)) {
                if (key.startsWith(prefix)) {
                    prefixHits.merge(prefix, 1, Integer::sum);
                }
            }
        }
        log.info("Advancements: " + displayed + " displayed, " + counting + " count for towns, worth "
                + totalPoints + " points in total. " + (cfg.pointsPerBlock() * cfg.max())
                + " points give the maximum of +" + cfg.max() + " claims.");
        for (String prefix : allPrefixes(cfg)) {
            if (!prefixHits.containsKey(prefix)) {
                log.warning("The advancement prefix '" + prefix + "' in config.yml matches no loaded advancement.");
            }
        }
        if (cfg.enabled() && (long) cfg.pointsPerBlock() * cfg.max() > totalPoints) {
            log.warning("The advancements maximum of +" + cfg.max() + " cannot be reached: it needs "
                    + (cfg.pointsPerBlock() * cfg.max()) + " points but only " + totalPoints + " exist.");
        }
    }

    private static List<String> allPrefixes(Settings.Advancements cfg) {
        return java.util.stream.Stream.concat(cfg.includePrefixes().stream(), cfg.excludePrefixes().stream()).toList();
    }
}
