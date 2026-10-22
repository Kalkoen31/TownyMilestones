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

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Immutable snapshot of {@code config.yml}.
 *
 * <p>{@link #load} reads the values through {@link FileConfiguration}, which falls back to the
 * defaults of the {@code config.yml} bundled in the jar (see {@code JavaPlugin#reloadConfig}).
 * Numbers that would break the maths (a zero divisor) or make no sense (negative) are raised to
 * their smallest valid value.
 *
 * @param zone time zone that decides when a day starts
 * @param totalCap hard limit on the combined bonus
 * @param maxPercentOfBase limit as a percentage of Towny's own claim count, 0 when off
 * @param saveIntervalMinutes minutes between data file writes
 * @param announce whether a town's online residents are told when its bonus grows
 * @param age settings of the town age source
 * @param activity settings of the active days source
 * @param advancements settings of the advancements source
 * @param diplomacy settings of the diplomacy source
 */
public record Settings(
        ZoneId zone,
        int totalCap,
        int maxPercentOfBase,
        int saveIntervalMinutes,
        boolean announce,
        Age age,
        Activity activity,
        Advancements advancements,
        Diplomacy diplomacy) {

    /**
     * Town age source.
     *
     * @param enabled whether the source counts
     * @param periodDays length of one period in days
     * @param blocksPerPeriod claims per full period
     * @param max cap of this source
     * @param minActiveDays active days needed before age counts
     * @param graceDays days without activity after which age stops growing
     */
    public record Age(boolean enabled, int periodDays, int blocksPerPeriod, int max,
                      int minActiveDays, int graceDays) { }

    /**
     * Active days source.
     *
     * @param enabled whether the source counts
     * @param minMinutes minutes one resident must be online in a day
     * @param requireMovement whether only minutes with movement or view changes count
     * @param daysPerBlock active days per claim
     * @param max cap of this source
     */
    public record Activity(boolean enabled, int minMinutes, boolean requireMovement,
                           int daysPerBlock, int max) { }

    /**
     * Advancements source.
     *
     * @param enabled whether the source counts
     * @param minDaysInTown days a resident must be in the town before their advancements count
     * @param taskPoints points of a task-frame advancement
     * @param goalPoints points of a goal-frame advancement
     * @param challengePoints points of a challenge-frame advancement
     * @param pointsPerBlock points per claim
     * @param max cap of this source
     * @param includePrefixes key prefixes that may count; empty means all
     * @param excludePrefixes key prefixes that never count
     * @param retroactive whether a player's already completed advancements are credited once
     */
    public record Advancements(boolean enabled, int minDaysInTown, int taskPoints, int goalPoints,
                               int challengePoints, int pointsPerBlock, int max,
                               List<String> includePrefixes, List<String> excludePrefixes,
                               boolean retroactive) { }

    /**
     * Diplomacy source.
     *
     * @param enabled whether the source counts
     * @param minAllianceDays days an alliance must have lasted
     * @param minAllyResidents residents the allied nation needs
     * @param blocksPerAlly claims per qualifying ally
     * @param max cap of this source
     * @param scanIntervalMinutes minutes between alliance checks
     */
    public record Diplomacy(boolean enabled, int minAllianceDays, int minAllyResidents,
                            int blocksPerAlly, int max, int scanIntervalMinutes) { }

    /**
     * Reads the settings from a loaded configuration.
     *
     * @param c the plugin configuration, with the bundled defaults attached
     * @param log used to warn about an unknown time zone
     * @return the parsed settings
     */
    public static Settings load(FileConfiguration c, Logger log) {
        return new Settings(
                zone(c.getString("timezone"), log),
                atLeast(c.getInt("total-cap"), 0),
                atLeast(c.getInt("max-percent-of-base"), 0),
                atLeast(c.getInt("save-interval-minutes"), 1),
                c.getBoolean("announce"),
                new Age(
                        c.getBoolean("age.enabled"),
                        atLeast(c.getInt("age.period-days"), 1),
                        atLeast(c.getInt("age.blocks-per-period"), 0),
                        atLeast(c.getInt("age.max"), 0),
                        atLeast(c.getInt("age.min-active-days"), 0),
                        atLeast(c.getInt("age.inactivity-grace-days"), 0)),
                new Activity(
                        c.getBoolean("active-days.enabled"),
                        atLeast(c.getInt("active-days.min-minutes"), 1),
                        c.getBoolean("active-days.require-movement"),
                        atLeast(c.getInt("active-days.days-per-block"), 1),
                        atLeast(c.getInt("active-days.max"), 0)),
                new Advancements(
                        c.getBoolean("advancements.enabled"),
                        atLeast(c.getInt("advancements.min-days-in-town"), 0),
                        atLeast(c.getInt("advancements.points.task"), 0),
                        atLeast(c.getInt("advancements.points.goal"), 0),
                        atLeast(c.getInt("advancements.points.challenge"), 0),
                        atLeast(c.getInt("advancements.points-per-block"), 1),
                        atLeast(c.getInt("advancements.max"), 0),
                        List.copyOf(c.getStringList("advancements.include-prefixes")),
                        List.copyOf(c.getStringList("advancements.exclude-prefixes")),
                        c.getBoolean("advancements.retroactive")),
                new Diplomacy(
                        c.getBoolean("diplomacy.enabled"),
                        atLeast(c.getInt("diplomacy.min-alliance-days"), 0),
                        atLeast(c.getInt("diplomacy.min-ally-residents"), 0),
                        atLeast(c.getInt("diplomacy.blocks-per-ally"), 0),
                        atLeast(c.getInt("diplomacy.max"), 0),
                        atLeast(c.getInt("diplomacy.scan-interval-minutes"), 1)));
    }

    private static int atLeast(int value, int minimum) {
        return Math.max(value, minimum);
    }

    // "system" or an unknown name gives the server's zone; an unknown name is also warned about.
    private static ZoneId zone(String name, Logger log) {
        if (name == null || name.equalsIgnoreCase("system")) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(name);
        } catch (DateTimeException e) {
            log.warning("Unknown timezone '" + name + "' in config.yml; using the server's time zone.");
            return ZoneId.systemDefault();
        }
    }
}
