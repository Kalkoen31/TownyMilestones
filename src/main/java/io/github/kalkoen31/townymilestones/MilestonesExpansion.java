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
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI placeholders with the milestone claim bonus of a player's town. Registered by
 * {@link TownyMilestones} only when PlaceholderAPI is installed.
 *
 * <p>Placeholders: {@code %townymilestones_bonus%} (claims applied, after the cap),
 * {@code %townymilestones_earned%} (before the cap), and the sources
 * {@code %townymilestones_age%}, {@code %townymilestones_activity%},
 * {@code %townymilestones_advancements%} and {@code %townymilestones_diplomacy%}. They give 0 for a
 * player without a town and an empty text for an unknown placeholder or player.
 *
 * <p>PlaceholderAPI may ask for placeholders off the main thread, so this only reads the cache of
 * {@link BonusManager}; a town that is not cached yet reads as 0.
 */
final class MilestonesExpansion extends PlaceholderExpansion {

    private final BonusManager bonus;

    MilestonesExpansion(BonusManager bonus) {
        this.bonus = bonus;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "townymilestones";
    }

    @Override
    public @NotNull String getAuthor() {
        return "kalkoen31";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    /** Registered from this plugin, not from the PlaceholderAPI eCloud, so it must survive /papi reload. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        Resident resident = TownyAPI.getInstance().getResident(player.getUniqueId());
        Town town = resident == null ? null : resident.getTownOrNull();
        Breakdown b = town == null ? Breakdown.NONE : bonus.breakdownOf(town);
        return switch (params.toLowerCase(java.util.Locale.ROOT)) {
            case "bonus" -> String.valueOf(b.total());
            case "earned" -> String.valueOf(b.uncapped());
            case "age" -> String.valueOf(b.age());
            case "activity" -> String.valueOf(b.activity());
            case "advancements" -> String.valueOf(b.advancements());
            case "diplomacy" -> String.valueOf(b.diplomacy());
            default -> "";
        };
    }
}
