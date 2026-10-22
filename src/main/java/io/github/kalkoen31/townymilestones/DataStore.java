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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The plugin's own data: per-town records and the first time each mutual alliance was seen.
 *
 * <p>Kept in memory and written to {@code data.yml} in the plugin folder. All access happens on the
 * main thread; only the file write itself ({@link #write}) may run elsewhere, on a string made by
 * {@link #serialise()}.
 *
 * <p>Advancement keys are stored as {@code key=points} list entries, because a key may contain a
 * dot and a dot would be read as a path separator if the key were used as a YAML key.
 */
final class DataStore {

    private static final String TOWNS = "towns";
    private static final String ALLIANCES = "alliances";
    private static final String SCANNED = "scanned-players";

    private final Path file;
    private final Logger log;

    /** Town UUID to record. */
    final Map<UUID, TownData> towns = new HashMap<>();

    /** Alliance key (see {@link BonusCalculator#allianceKey}) to epoch milliseconds first seen. */
    final Map<String, Long> alliances = new HashMap<>();

    /** Players whose already completed advancements have been credited once; never scanned again. */
    final Set<UUID> scannedPlayers = new HashSet<>();

    private boolean dirty;

    DataStore(Path file, Logger log) {
        this.file = file;
        this.log = log;
    }

    /** Marks the data as changed so the next periodic save writes it. */
    void markDirty() {
        dirty = true;
    }

    /**
     * Reads and clears the changed flag.
     *
     * @return true once if something changed since the last call
     */
    boolean consumeDirty() {
        boolean was = dirty;
        dirty = false;
        return was;
    }

    /**
     * Returns the record of a town, creating an empty one on first use.
     *
     * @param id the Towny town UUID
     * @param name the current town name, stored for readability
     * @return the record, never null
     */
    TownData town(UUID id, String name) {
        TownData data = towns.computeIfAbsent(id, key -> new TownData());
        if (!name.equals(data.lastKnownName)) {
            data.lastKnownName = name;
            dirty = true;
        }
        return data;
    }

    /**
     * Loads {@code data.yml}; a missing file gives empty data.
     *
     * <p>The file can be edited by hand, so loading never stops the plugin from starting. A copy
     * is kept as {@code data.yml.bak} before anything is read. A file that is not valid YAML is
     * moved aside as {@code data.yml.corrupt-<time>} and the plugin starts empty. A single town
     * entry with a bad value is skipped with a warning, and the whole file is then also kept as
     * {@code data.yml.broken-<time>}, because the next save would otherwise drop that entry.
     */
    void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        copy(file.resolveSibling(file.getFileName() + ".bak"));
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file.toFile());
        } catch (IOException | InvalidConfigurationException e) {
            Path corrupt = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
            try {
                Files.move(file, corrupt);
            } catch (IOException moveError) {
                log.log(Level.WARNING, "Could not move the unreadable data file aside", moveError);
            }
            log.log(Level.SEVERE, "data.yml could not be read and was moved to " + corrupt.getFileName()
                    + ". Starting with empty data; the last good copy is data.yml.bak.", e);
            return;
        }

        int skipped = 0;
        ConfigurationSection townSection = yaml.getConfigurationSection(TOWNS);
        if (townSection != null) {
            for (String key : townSection.getKeys(false)) {
                UUID id = parseUuid(key);
                ConfigurationSection s = townSection.getConfigurationSection(key);
                try {
                    if (s == null || id == null) {
                        throw new IllegalArgumentException("not a town entry");
                    }
                    towns.put(id, readTown(s));
                } catch (IllegalArgumentException | DateTimeParseException e) {
                    // NumberFormatException is an IllegalArgumentException
                    skipped++;
                    log.warning("Skipping town entry '" + key + "' in data.yml: " + e.getMessage());
                }
            }
        }
        for (String entry : yaml.getStringList(SCANNED)) {
            UUID id = parseUuid(entry);
            if (id != null) {
                scannedPlayers.add(id);
            }
        }
        ConfigurationSection allianceSection = yaml.getConfigurationSection(ALLIANCES);
        if (allianceSection != null) {
            for (String key : allianceSection.getKeys(false)) {
                alliances.put(key, allianceSection.getLong(key));
            }
        }
        if (skipped > 0) {
            copy(file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis()));
            log.warning(skipped + " town entries were skipped; the original file was kept as data.yml.broken-*.");
        }
    }

    private static TownData readTown(ConfigurationSection s) {
        TownData data = new TownData();
        data.lastKnownName = s.getString("name", "");
        data.activeDays = s.getInt("active-days");
        String last = s.getString("last-active-date");
        data.lastActiveDate = last == null ? null : LocalDate.parse(last);
        data.lastActiveMillis = s.getLong("last-active-millis");
        data.announcedTotal = s.getInt("announced-total", -1);
        for (String entry : s.getStringList("credited")) {
            int split = entry.lastIndexOf('=');
            if (split > 0) {
                data.credited.put(entry.substring(0, split), Integer.parseInt(entry.substring(split + 1)));
            }
        }
        return data;
    }

    // Copies data.yml next to itself under another name; a failure is only warned about.
    private void copy(Path target) {
        try {
            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.log(Level.WARNING, "Could not copy data.yml to " + target.getFileName(), e);
        }
    }

    /**
     * Converts all data to YAML text.
     *
     * @return the whole data set as YAML text, ready for {@link #write}
     */
    String serialise() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, TownData> e : towns.entrySet()) {
            TownData d = e.getValue();
            String base = TOWNS + "." + e.getKey();
            yaml.set(base + ".name", d.lastKnownName);
            yaml.set(base + ".active-days", d.activeDays);
            yaml.set(base + ".last-active-date", d.lastActiveDate == null ? null : d.lastActiveDate.toString());
            yaml.set(base + ".last-active-millis", d.lastActiveMillis);
            yaml.set(base + ".announced-total", d.announcedTotal);
            List<String> credited = d.credited.entrySet().stream()
                    .map(c -> c.getKey() + "=" + c.getValue()).sorted().toList();
            yaml.set(base + ".credited", credited);
        }
        for (Map.Entry<String, Long> e : alliances.entrySet()) {
            yaml.set(ALLIANCES + "." + e.getKey(), e.getValue());
        }
        yaml.set(SCANNED, scannedPlayers.stream().map(UUID::toString).sorted().toList());
        return yaml.saveToString();
    }

    /**
     * Writes serialised data to {@code data.yml} through a temporary file, so a crash while writing
     * cannot leave a half-written data file. May be called off the main thread; calls are serialised
     * so the shutdown write cannot overlap a periodic one.
     *
     * @param yamlText the text from {@link #serialise()}
     */
    synchronized void write(String yamlText) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, yamlText, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Could not write " + file.getFileName(), e);
        }
    }

    private static UUID parseUuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
