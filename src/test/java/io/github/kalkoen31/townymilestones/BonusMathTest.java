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
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Tests of the claim bonus calculation with the default numbers of config.yml (except total-cap, 64 here). */
class BonusMathTest {

    private static final long DAY = TimeUnit.DAYS.toMillis(1);
    private static final long NOW = 1_800_000_000_000L;

    private static Settings settings(int totalCap) {
        return new Settings(ZoneId.of("UTC"), totalCap, 0, 5, true,
                new Settings.Age(true, 7, 1, 16, 7, 14),
                new Settings.Activity(true, 15, true, 5, 24),
                new Settings.Advancements(true, 3, 1, 2, 4, 6, 24, List.of(), List.of(), true),
                new Settings.Diplomacy(true, 14, 3, 2, 8, 10));
    }

    private static TownData data(int activeDays, long lastActiveMillis) {
        TownData d = new TownData();
        d.activeDays = activeDays;
        d.lastActiveMillis = lastActiveMillis;
        return d;
    }

    private static Breakdown compute(Settings s, TownData d, long registeredDaysAgo, List<BonusMath.Ally> allies,
                                     Map<String, Long> since) {
        return BonusMath.compute(s, d, NOW - registeredDaysAgo * DAY, allies, since, NOW);
    }

    @Test
    void ageAndActiveDaysAddUp() {
        Breakdown b = compute(settings(64), data(30, NOW), 100, List.of(), Map.of());
        assertEquals(14, b.age());
        assertEquals(6, b.activity());
        assertEquals(20, b.total());
    }

    @Test
    void ageStopsAtItsMaximum() {
        assertEquals(16, compute(settings(64), data(30, NOW), 200, List.of(), Map.of()).age());
    }

    @Test
    void quietTownStopsGainingAge() {
        // Last active 60 days ago, grace 14 days: age counts from day 100 back to day 54 only.
        Breakdown b = compute(settings(64), data(40, NOW - 60 * DAY), 100, List.of(), Map.of());
        assertEquals(54L, b.ageDays());
        assertEquals(7, b.age());
    }

    @Test
    void ageNeedsEnoughActiveDays() {
        Breakdown b = compute(settings(64), data(6, NOW), 100, List.of(), Map.of());
        assertEquals(0, b.age());
        assertEquals(1, b.activity());
    }

    @Test
    void townWithoutRecordGetsNothing() {
        assertSame(Breakdown.NONE, compute(settings(64), null, 100, List.of(), Map.of()));
    }

    @Test
    void advancementPointsBecomeClaims() {
        TownData d = data(0, NOW);
        d.credited.put("a:one", 1);
        d.credited.put("a:two", 4);
        d.credited.put("a:three", 2);
        for (int i = 0; i < 6; i++) {
            d.credited.put("a:k" + i, 5);
        }
        Breakdown b = compute(settings(64), d, 1, List.of(), Map.of());
        assertEquals(37, b.points());
        assertEquals(6, b.advancements());
    }

    @Test
    void onlyOldEnoughAlliesWithEnoughResidentsCount() {
        List<BonusMath.Ally> allies = List.of(
                new BonusMath.Ally("good", 5),
                new BonusMath.Ally("young", 5),
                new BonusMath.Ally("small", 2),
                new BonusMath.Ally("unknown", 5));
        Map<String, Long> since = Map.of(
                "good", NOW - 20 * DAY,
                "young", NOW - 5 * DAY,
                "small", NOW - 30 * DAY);
        Breakdown b = compute(settings(64), data(0, NOW), 1, allies, since);
        assertEquals(1, b.allies());
        assertEquals(2, b.diplomacy());
    }

    @Test
    void everySourceAndTheTotalHaveTheirOwnCap() {
        TownData d = data(1000, NOW);
        for (int i = 0; i < 500; i++) {
            d.credited.put("a:k" + i, 4);
        }
        Breakdown b = compute(settings(64), d, 1000, List.of(), Map.of());
        assertEquals(16, b.age());
        assertEquals(24, b.activity());
        assertEquals(24, b.advancements());
        assertEquals(64, b.total());
    }

    @Test
    void capOfZeroGrantsNothingButStillShowsWhatWasEarned() {
        Breakdown b = compute(settings(0), data(30, NOW), 100, List.of(), Map.of());
        assertEquals(0, b.total());
        assertEquals(20, b.uncapped());
    }

    @Test
    void disabledSourceAddsNothing() {
        Settings s = settings(64);
        Settings off = new Settings(s.zone(), 64, 0, 5, true, s.age(),
                new Settings.Activity(false, 15, true, 5, 24), s.advancements(), s.diplomacy());
        assertEquals(0, compute(off, data(30, NOW), 1, List.of(), Map.of()).activity());
    }
}
