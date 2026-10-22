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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.advancement.AdvancementDisplay.Frame;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests of which advancements count and what they are worth. */
class AdvancementRulesTest {

    private static Settings.Advancements cfg(List<String> include, List<String> exclude) {
        return new Settings.Advancements(true, 3, 1, 2, 4, 6, 24, include, exclude, true);
    }

    @Test
    void pointsDependOnTheFrame() {
        Settings.Advancements c = cfg(List.of(), List.of());
        assertEquals(1, AdvancementRules.points(c, "minecraft:story/a", Frame.TASK));
        assertEquals(2, AdvancementRules.points(c, "minecraft:story/a", Frame.GOAL));
        assertEquals(4, AdvancementRules.points(c, "minecraft:story/a", Frame.CHALLENGE));
    }

    @Test
    void anAdvancementWithoutDisplayNeverCounts() {
        assertEquals(0, AdvancementRules.points(cfg(List.of(), List.of()), "minecraft:recipes/x", null));
    }

    @Test
    void excludedPrefixesNeverCount() {
        Settings.Advancements c = cfg(List.of(), List.of("blazeandcave:statistics/"));
        assertEquals(0, AdvancementRules.points(c, "blazeandcave:statistics/kills", Frame.CHALLENGE));
        assertEquals(4, AdvancementRules.points(c, "blazeandcave:mining/x", Frame.CHALLENGE));
    }

    @Test
    void aPrefixMustMatchFromTheStart() {
        Settings.Advancements c = cfg(List.of(), List.of("blazeandcave:statistics/"));
        assertTrue(AdvancementRules.passesFilters(c, "blazeandcave:statistics_other/x"));
        assertTrue(AdvancementRules.passesFilters(c, "other:blazeandcave:statistics/x"));
    }

    @Test
    void includePrefixesRestrictWhatCounts() {
        Settings.Advancements c = cfg(List.of("minecraft:"), List.of());
        assertTrue(AdvancementRules.passesFilters(c, "minecraft:story/a"));
        assertFalse(AdvancementRules.passesFilters(c, "blazeandcave:mining/x"));
    }

    @Test
    void excludeWinsOverInclude() {
        Settings.Advancements c = cfg(List.of("minecraft:"), List.of("minecraft:story/"));
        assertFalse(AdvancementRules.passesFilters(c, "minecraft:story/a"));
        assertTrue(AdvancementRules.passesFilters(c, "minecraft:nether/a"));
    }

    @Test
    void aFrameWorthZeroPointsDoesNotCount() {
        Settings.Advancements c = new Settings.Advancements(true, 3, 0, 2, 4, 6, 24, List.of(), List.of(), true);
        assertEquals(0, AdvancementRules.points(c, "minecraft:story/a", Frame.TASK));
    }
}
