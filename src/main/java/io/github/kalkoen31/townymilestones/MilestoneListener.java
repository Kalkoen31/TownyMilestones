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
import com.palmergames.bukkit.towny.event.DeleteTownEvent;
import com.palmergames.bukkit.towny.event.TownyLoadedDatabaseEvent;
import com.palmergames.bukkit.towny.event.statusscreen.TownStatusScreenEvent;
import com.palmergames.bukkit.towny.event.town.TownCalculateMaxTownBlocksEvent;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * The events this plugin reacts to:
 * <ul>
 *   <li>Towny asks for a town's claim limit: the bonus is added.</li>
 *   <li>A player earns an advancement, or joins with advancements already completed: they may be
 *       credited to their town.</li>
 *   <li>Towny builds the {@code /town} screen: the bonus line is added.</li>
 *   <li>Towny finishes loading, or a town is deleted: the cache is filled, or the record dropped.</li>
 * </ul>
 */
final class MilestoneListener implements Listener {

    /** Delay before the retroactive scan, in ticks (2 seconds). */
    private static final long SCAN_DELAY_TICKS = 40L;

    private final TownyMilestones plugin;
    private final DataStore store;
    private final BonusManager bonus;

    MilestoneListener(TownyMilestones plugin, DataStore store, BonusManager bonus) {
        this.plugin = plugin;
        this.store = store;
        this.bonus = bonus;
    }

    /**
     * Adds the bonus to the claim limit Towny has calculated so far. Towny applies its own
     * {@code town_block_limit} before firing this event, so the bonus is not held back by it; this
     * plugin's {@code total-cap} and {@code max-percent-of-base} take its place.
     *
     * @param event Towny's calculation event; may be async
     */
    @EventHandler
    public void onCalculateMax(TownCalculateMaxTownBlocksEvent event) {
        int add = bonus.bonusFor(event.getTown());
        if (add <= 0) {
            return;
        }
        int base = event.getTownBlockCount();
        int percent = plugin.settings().maxPercentOfBase();
        if (percent > 0) {
            add = (int) Math.min(add, (long) base * percent / 100);
        }
        event.setTownBlockCount(base + add);
    }

    /**
     * Credits an advancement to the earner's town when it qualifies: see {@link AdvancementRules}, the
     * player must be in a town and have been there long enough, and the town must not have been
     * credited for it yet.
     *
     * @param event Paper's advancement event
     */
    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        Settings.Advancements cfg = plugin.settings().advancements();
        if (!cfg.enabled()) {
            return;
        }
        int points = AdvancementRules.pointsFor(cfg, event.getAdvancement());
        Town town = eligibleTown(event.getPlayer(), cfg);
        if (points <= 0 || town == null) {
            return;
        }
        TownData data = store.town(town.getUUID(), town.getName());
        if (data.credited.putIfAbsent(event.getAdvancement().getKey().toString(), points) == null) {
            store.markDirty();
            bonus.recompute(town);
        }
    }

    /**
     * Once per player ever, credits the advancements a player had already completed to their
     * town. It runs a moment after joining, so Towny and the player's advancement data are loaded.
     * A player who is not yet eligible (no town, or too new in it) is tried again at the next join.
     *
     * @param event Paper's join event
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.settings().advancements().retroactive()) {
            return;
        }
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> scanCompleted(player), SCAN_DELAY_TICKS);
    }

    /**
     * Adds the town's milestone bonus to Towny's {@code /town} screen.
     *
     * @param event Towny's town status screen event
     */
    @EventHandler
    public void onTownStatus(TownStatusScreenEvent event) {
        Breakdown b = bonus.breakdownOf(event.getTown());
        if (b.uncapped() <= 0) {
            return;
        }
        String capped = b.uncapped() > b.total() ? " (earned +" + b.uncapped() + ", limited by the cap)" : "";
        event.addLine(Component.text("Milestone claims: +" + b.total() + capped, NamedTextColor.GREEN));
    }

    /**
     * Recalculates every town once Towny has loaded its database, so the cache is full before the
     * first minutely update. Towny creates this event async when it is fired off the main thread, so
     * the work is moved to the main thread.
     *
     * @param event Towny's database loaded event
     */
    @EventHandler
    public void onTownyLoaded(TownyLoadedDatabaseEvent event) {
        runOnMainThread(bonus::recomputeAll);
    }

    /**
     * Forgets a town's record when Towny deletes the town. Towny creates this event async when it is
     * fired off the main thread, and the records are only touched on the main thread, so the work is
     * moved there.
     *
     * @param event Towny's town deletion event
     */
    @EventHandler
    public void onTownDeleted(DeleteTownEvent event) {
        UUID id = event.getTownUUID();
        runOnMainThread(() -> bonus.remove(id));
    }

    // Runs the task now when already on the main thread, otherwise on the next tick.
    private void runOnMainThread(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }

    // Credits every completed, counting advancement of the player to their town, once per player ever.
    private void scanCompleted(Player player) {
        Settings.Advancements cfg = plugin.settings().advancements();
        if (!player.isOnline() || !cfg.enabled() || !cfg.retroactive()
                || store.scannedPlayers.contains(player.getUniqueId())) {
            return;
        }
        Town town = eligibleTown(player, cfg);
        if (town == null) {
            return;
        }
        TownData data = store.town(town.getUUID(), town.getName());
        long started = System.nanoTime();
        int added = 0;
        for (Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext();) {
            Advancement advancement = it.next();
            int points = AdvancementRules.pointsFor(cfg, advancement);
            if (points > 0 && player.getAdvancementProgress(advancement).isDone()
                    && data.credited.putIfAbsent(advancement.getKey().toString(), points) == null) {
                added++;
            }
        }
        store.scannedPlayers.add(player.getUniqueId());
        store.markDirty();
        bonus.recompute(town);
        plugin.getLogger().info("Retroactive scan of " + player.getName() + ": " + added
                + " advancement(s) newly credited to town " + town.getName() + " ("
                + String.format("%.1f", (System.nanoTime() - started) / 1_000_000.0) + " ms).");
    }

    // The player's town if they are in one and have been there long enough, otherwise null.
    private static Town eligibleTown(Player player, Settings.Advancements cfg) {
        Resident resident = TownyAPI.getInstance().getResident(player);
        Town town = resident == null ? null : resident.getTownOrNull();
        if (town == null) {
            return null;
        }
        long inTown = System.currentTimeMillis() - resident.getJoinedTownAt();
        return inTown < TimeUnit.DAYS.toMillis(cfg.minDaysInTown()) ? null : town;
    }

}
