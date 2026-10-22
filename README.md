# Towny Milestones

## AI disclosure

The code and documentation in this repository were generated with Claude Sonnet 5.5 (Anthropic). The repository owner has tested the plugin on a local test server and on their own server. The PlaceholderAPI placeholders have not been tested in game.

Earn extra Towny town claims through town age, active days, advancements and alliances.

Towny gives a town its claims from its town level and nation level. This plugin adds a bonus on top, earned by what the town does. Every source counts per town, not per resident, so a bigger town does not earn faster. Each source has its own maximum and a total cap limits all of them together. All numbers are in `plugins/TownyMilestones/config.yml`.

## How the bonus is earned

| Source | What counts |
|---|---|
| Town age | Full weeks since the town was created. Needs a minimum number of active days, and stops growing while the town is inactive. |
| Active days | Days on which at least one resident played a configurable number of minutes (by default only minutes with movement or a view change). One day counts once per town. |
| Advancements | Each advancement counts once per town, the first time a resident who has been in the town long enough earns it. Points depend on the advancement frame (task, goal, challenge). Advancements without a display (such as recipes) never count, and prefix filters can include or exclude more. The default config is tuned for BlazeandCave's Advancements Pack 1.21 and excludes its milestone, hidden cheater and statistics advancements. |
| Diplomacy | Mutual alliances of the town's nation that have lasted a configurable number of days with a nation of a minimum size. This source is live and drops when an alliance ends. |

The bonus is added to Towny's claim limit through Towny's `TownCalculateMaxTownBlocksEvent`, so it shows up wherever Towny uses the town's claim limit. Towny applies its own `town_block_limit` before that event, so it does not hold this bonus back; use `total-cap` and `max-percent-of-base` here instead.

A town starts earning after the first day one of its residents is active while the plugin runs.

Advancements a player had already completed are credited once, about two seconds after the player joins, once they are in a town and have been there long enough (`advancements.retroactive`). A player who later moves to another town does not bring them along.

`total-cap` is 0 by default: progress is tracked and shown, but no extra claims are granted until you raise it and run `/townymilestones reload`. The breakdown shows what was earned before the cap. The bonus also appears as a line on Towny's `/town` screen, and a town's online residents get a chat message when it grows (`announce`).

## PlaceholderAPI

If PlaceholderAPI is installed, these placeholders are registered for the town of the player: `%townymilestones_bonus%` (claims applied, after the cap), `%townymilestones_earned%` (before the cap), and the sources `%townymilestones_age%`, `%townymilestones_activity%`, `%townymilestones_advancements%` and `%townymilestones_diplomacy%`. They read 0 for a player without a town.

## Data safety

The plugin keeps a copy of `data.yml` as `data.yml.bak` on every start. If the file cannot be read it is moved to `data.yml.corrupt-<time>` and the plugin starts with empty data. A town entry with a bad value is skipped with a warning, and the whole file is then kept as `data.yml.broken-<time>`. On start and on `/townymilestones reload` the log shows how many advancements count, and warns about filter prefixes that match nothing.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/townymilestones` or `/townymilestones info` (alias `/tmilestones`) | `townymilestones.info` (default: everyone) | Bonus breakdown of your own town |
| `/townymilestones info <town>` | `townymilestones.admin` (default: op) | Breakdown of any town |
| `/townymilestones advancements` | `townymilestones.admin` | Advancement key prefixes on the server with task, goal and challenge counts, to help fill in the prefix filters |
| `/townymilestones reload` | `townymilestones.admin` | Reload `config.yml`. `save-interval-minutes` and `diplomacy.scan-interval-minutes` need a restart. |

## Requirements

- A Paper server (developed for Paper 26.2, build 129)
- Towny (developed against 0.103.2.0)
- PlaceholderAPI is optional
- Java 25 or newer

## Compatibility

Built against Paper `26.2.build.129-stable`, Towny `0.103.2.0` and PlaceholderAPI `2.12.3`. Tested by the author on Paper 26.2 (builds 129 and 134) with Towny 0.103.2.0. Other versions are untested.

## Installation

1. Put the jar in the server's `plugins/` folder.
2. Restart the server.
3. Edit `plugins/TownyMilestones/config.yml` and run `/townymilestones reload`.

The plugin stores its own data in `plugins/TownyMilestones/data.yml`.

## Building

You need JDK 25. No separate Gradle installation is needed: the included wrapper downloads Gradle 9.8.1 on the first run. Run `./gradlew build` (on Windows PowerShell: `.\gradlew build`). The jar is created in `build/libs/`.

## Unit tests

`.\gradlew test` runs the unit tests of the bonus calculation and the advancement rules. They need no server. They do not cover the server, Towny or the data file; test those on a real server.

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE), or (at your option) any later version.
