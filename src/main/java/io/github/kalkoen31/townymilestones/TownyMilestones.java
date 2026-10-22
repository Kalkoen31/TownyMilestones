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

import java.nio.file.Path;
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Towny Milestones: earn extra Towny town claims through town age, active days, advancements and
 * alliances.
 *
 * <p>Registered in {@code plugin.yml}; it requires Towny. The bonus is added to Towny's claim limit
 * through {@code TownCalculateMaxTownBlocksEvent} (see {@link MilestoneListener}); it is calculated
 * from remembered facts ({@link TownData}) and the live Towny state ({@link BonusCalculator}).
 *
 * <p>Command: {@code /townymilestones} (alias {@code /tmilestones}), see {@link MilestonesCommand}.
 */
public final class TownyMilestones extends JavaPlugin {

    private static final long TICKS_PER_MINUTE = 20L * 60L;

    /** First alliance scan runs this many ticks after startup, so Towny has loaded its data. */
    private static final long FIRST_ALLIANCE_SCAN_TICKS = 20L * 30L;

    /** Preparing the data file for saving is reported when it takes at least this long (milliseconds). */
    private static final long SLOW_SERIALISE_MILLIS = 50L;

    /** Replaced on reload and read by async Towny event calls, hence volatile. */
    private volatile Settings settings;

    private DataStore store;

    /** Null when PlaceholderAPI is not installed. */
    private MilestonesExpansion expansion;

    /** Called when the plugin is enabled. */
    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.load(getConfig(), getLogger());

        store = new DataStore(Path.of(getDataFolder().getPath(), "data.yml"), getLogger());
        store.load();
        BonusManager bonus = new BonusManager(this, store);

        getServer().getPluginManager().registerEvents(new MilestoneListener(this, store, bonus), this);
        // Fill the cache right after startup. Towny's own "database loaded" event does it again when it fires.
        getServer().getScheduler().runTask(this, () -> {
            bonus.recomputeAll();
            AdvancementReport.log(settings.advancements(), getLogger());
        });
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            expansion = new MilestonesExpansion(bonus);
            expansion.register();
            getLogger().info("PlaceholderAPI found: placeholders registered.");
        }
        registerCommand("townymilestones", "Show the claim bonus earned by your town.",
                List.of("tmilestones"), new MilestonesCommand(this, bonus));

        getServer().getScheduler().runTaskTimer(this, new ActivityTracker(this, store, bonus),
                TICKS_PER_MINUTE, TICKS_PER_MINUTE);
        long scanTicks = settings.diplomacy().scanIntervalMinutes() * TICKS_PER_MINUTE;
        getServer().getScheduler().runTaskTimer(this, new AllianceTracker(store),
                FIRST_ALLIANCE_SCAN_TICKS, scanTicks);
        long saveTicks = settings.saveIntervalMinutes() * TICKS_PER_MINUTE;
        getServer().getScheduler().runTaskTimer(this, this::saveAsync, saveTicks, saveTicks);

        getLogger().info("Towny Milestones enabled.");
    }

    /** Called when the plugin is disabled: writes the data file before the server stops. */
    @Override
    public void onDisable() {
        if (expansion != null) {
            expansion.unregister();
        }
        if (store != null) {
            store.write(store.serialise());
        }
        getLogger().info("Towny Milestones disabled.");
    }

    /**
     * Gives the settings currently in force.
     *
     * @return the current settings; never null while the plugin is enabled
     */
    Settings settings() {
        return settings;
    }

    /** Re-reads {@code config.yml}. Intervals of the timers only change after a restart. */
    void reloadSettings() {
        reloadConfig();
        settings = Settings.load(getConfig(), getLogger());
        AdvancementReport.log(settings.advancements(), getLogger());
    }

    /** Writes the data file if something changed; the file write itself runs off the main thread. */
    private void saveAsync() {
        if (store.consumeDirty()) {
            long started = System.nanoTime();
            String text = store.serialise();
            long millis = (System.nanoTime() - started) / 1_000_000L;
            if (millis >= SLOW_SERIALISE_MILLIS) {
                getLogger().warning("Preparing data.yml for saving took " + millis
                        + " ms on the main thread; this will grow with the number of towns.");
            }
            getServer().getScheduler().runTaskAsynchronously(this, () -> store.write(text));
        }
    }
}
