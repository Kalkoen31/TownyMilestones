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
import io.papermc.paper.advancement.AdvancementDisplay;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * The {@code /townymilestones} command (alias {@code /tmilestones}).
 *
 * <ul>
 *   <li>{@code info} - the bonus breakdown of your own town (permission {@code townymilestones.info}).</li>
 *   <li>{@code info <town>} - the same for any town (admin).</li>
 *   <li>{@code advancements} - the advancement key prefixes on this server with counts per frame,
 *       to help fill in {@code include-prefixes} and {@code exclude-prefixes} (admin).</li>
 *   <li>{@code reload} - reloads {@code config.yml} (admin).</li>
 * </ul>
 * Without arguments it behaves like {@code info}.
 */
final class MilestonesCommand implements BasicCommand {

    private static final String PERM_INFO = "townymilestones.info";
    private static final String PERM_ADMIN = "townymilestones.admin";

    /** Longest list printed by {@code advancements}, to keep chat readable. */
    private static final int MAX_PREFIX_LINES = 40;

    private final TownyMilestones plugin;
    private final BonusManager bonus;

    MilestonesCommand(TownyMilestones plugin, BonusManager bonus) {
        this.plugin = plugin;
        this.bonus = bonus;
    }

    @Override
    public String permission() {
        return PERM_INFO;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info" -> info(sender, args.length > 1 ? args[1] : null);
            case "advancements" -> {
                if (requireAdmin(sender)) {
                    advancements(sender);
                }
            }
            case "reload" -> {
                if (requireAdmin(sender)) {
                    plugin.reloadSettings();
                    bonus.recomputeAll();
                    line(sender, "Config reloaded.", NamedTextColor.GREEN);
                }
            }
            default -> line(sender, "Usage: /townymilestones [info [town] | advancements | reload]",
                    NamedTextColor.RED);
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        boolean admin = sender.hasPermission(PERM_ADMIN);
        if (args.length <= 1) {
            List<String> subs = new ArrayList<>(List.of("info"));
            if (admin) {
                subs.addAll(List.of("advancements", "reload"));
            }
            String typed = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return subs.stream().filter(s -> s.startsWith(typed)).toList();
        }
        if (admin && args[0].equalsIgnoreCase("info") && args.length == 2) {
            String typed = args[1].toLowerCase(Locale.ROOT);
            return TownyAPI.getInstance().getTowns().stream().map(Town::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
        }
        return List.of();
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission(PERM_ADMIN)) {
            return true;
        }
        line(sender, "You do not have permission for that.", NamedTextColor.RED);
        return false;
    }

    private void info(CommandSender sender, String townName) {
        Town town;
        if (townName != null) {
            if (!requireAdmin(sender)) {
                return;
            }
            town = TownyAPI.getInstance().getTown(townName);
            if (town == null) {
                line(sender, "No town named '" + townName + "'.", NamedTextColor.RED);
                return;
            }
        } else {
            Resident resident = sender instanceof Player p ? TownyAPI.getInstance().getResident(p) : null;
            town = resident == null ? null : resident.getTownOrNull();
            if (town == null) {
                line(sender, "You are not in a town. Admins can use /townymilestones info <town>.",
                        NamedTextColor.RED);
                return;
            }
        }

        Settings s = plugin.settings();
        Breakdown b = bonus.recompute(town);
        line(sender, "Claim bonus of " + town.getName(), NamedTextColor.GOLD);
        line(sender, "Age: +" + b.age() + " (max " + s.age().max() + ") - " + b.ageDays() + " days counted"
                + (s.age().enabled() ? "" : " [off]"), NamedTextColor.YELLOW);
        line(sender, "Active days: +" + b.activity() + " (max " + s.activity().max() + ") - "
                + b.activeDays() + " days" + (s.activity().enabled() ? "" : " [off]"), NamedTextColor.YELLOW);
        line(sender, "Advancements: +" + b.advancements() + " (max " + s.advancements().max() + ") - "
                + b.advancementCount() + " advancements, " + b.points() + " points"
                + (s.advancements().enabled() ? "" : " [off]"), NamedTextColor.YELLOW);
        line(sender, "Diplomacy: +" + b.diplomacy() + " (max " + s.diplomacy().max() + ") - "
                + b.allies() + " qualifying allies" + (s.diplomacy().enabled() ? "" : " [off]"),
                NamedTextColor.YELLOW);
        line(sender, "Earned so far: +" + b.uncapped() + " (before the cap)", NamedTextColor.YELLOW);
        line(sender, "Total bonus applied: +" + b.total() + " (cap " + s.totalCap() + ")", NamedTextColor.GREEN);
        line(sender, "Claims: " + town.getNumTownBlocks() + " used of " + town.getMaxTownBlocksAsAString()
                + " allowed", NamedTextColor.GRAY);
    }

    // Prints "namespace:first-path-segment" groups of all loaded advancements with frame counts.
    private void advancements(CommandSender sender) {
        Map<String, int[]> groups = new TreeMap<>();
        Iterator<Advancement> it = Bukkit.advancementIterator();
        int hidden = 0;
        while (it.hasNext()) {
            Advancement adv = it.next();
            AdvancementDisplay display = adv.getDisplay();
            if (display == null) {
                hidden++;
                continue;
            }
            String key = adv.getKey().getNamespace() + ":" + adv.getKey().getKey().split("/", 2)[0];
            int[] counts = groups.computeIfAbsent(key, k -> new int[3]);
            switch (display.frame()) {
                case TASK -> counts[0]++;
                case GOAL -> counts[1]++;
                case CHALLENGE -> counts[2]++;
            }
        }
        line(sender, "Advancement prefixes (task / goal / challenge). " + hidden
                + " without a display are never counted.", NamedTextColor.GOLD);
        int printed = 0;
        for (Map.Entry<String, int[]> e : groups.entrySet()) {
            if (printed++ == MAX_PREFIX_LINES) {
                line(sender, "... and " + (groups.size() - MAX_PREFIX_LINES) + " more prefixes.",
                        NamedTextColor.GRAY);
                break;
            }
            int[] c = e.getValue();
            line(sender, e.getKey() + "  " + c[0] + " / " + c[1] + " / " + c[2], NamedTextColor.YELLOW);
        }
    }

    private static void line(CommandSender sender, String text, NamedTextColor color) {
        sender.sendMessage(Component.text(text, color));
    }
}
