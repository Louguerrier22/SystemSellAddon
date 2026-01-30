# DSellSystem

Advanced selling plugin for Paper servers. Turn your shop chest into a slick GUI, sell by category, and level up multipliers while your economy sings.

## Features
- GUI-based selling with category progress and multiplier upgrades
- Worth/price browser with sorting and filtering
- Per-category multipliers and progress tracking
- History system with optional itemized sell logs (SQLite)
- Optional worth-lore overlay when ProtocolLib is installed
- API events for integrations

## Requirements
- Paper 1.21.x (api-version: 1.21)
- Java 21
- Vault (required) + any Vault economy provider (EssentialsX Economy, EzEconomy, etc.)

## Optional Integrations
- ProtocolLib: enables live worth-lore overlays in inventories
- MayorSystem: receives sell payout callbacks

## MayorSystem Bonus Support
DSellSystem is pre-wired for MayorSystem’s `/sell` bonus flow. If MayorSystem is installed and enabled, DSellSystem will send sell payout snapshots automatically so MayorSystem can apply bonuses on top of the base payout.

Why it’s nice:
- No extra hooks or config needed
- Bonuses can be applied after DSellSystem’s multipliers
- Works for category breakdowns and total payouts

## Install
1. Drop the jar into `plugins/`.
2. Install Vault and an economy provider.
3. Start the server to generate configs.
4. Customize `config.yml`, `gui.yml`, and `prices.yml`.
5. Reload with `/dsellsystem reload` or the Settings menu.

## Commands
Player commands:
- `/sell` - Open the Sell GUI.
- `/sell history` - Open your sell history (if enabled).
- `/worth` - Open the Worth (price list) GUI.
- `/worth <item>` - Check the price for an item (material name).

Admin/staff commands:
- `/sell history <player>` - View another player's history.
- `/setworth <item> <price>` - Set a price for a material (must exist in `prices.yml`).
- `/dsellsystem` - Show plugin info.
- `/dsellsystem settings` - Open the Settings GUI.
- `/dsellsystem reload` - Reload configs and prices.

## Permissions
- `dsellsystem.reload` - Reload plugin configuration (default: op)
- `dsellsystem.history.other` - View other players' histories (default: op)
- `dsellsystem.setworth` - Edit prices with `/setworth` (default: op)
- `dsellsystem.settings` - Open Settings menu (default: op)

## Menus
- Sell GUI: main sell interface. Put items in slots 0-35 and click category icons to view progress.
- Progress GUI: per-category progress and next multiplier milestones.
- Worth GUI: paginated price list with sort and filter controls.
- Settings GUI: toggle plugin, toggle history, reload config, ProtocolLib status.
- History GUI: paginated list of sold items with timestamps and worth.

## Configuration
All files live in `plugins/DSellSystem/`:

`config.yml`
- `settings.enabled` - Toggle all selling
- `settings.base-multiplier`, `multiplier-increment`, `max-multiplier`
- `settings.worth-lore.enabled` and `settings.worth-lore.format`
- `level-prices` - Money required per multiplier level
- `history.enabled`, `history.page_size`, `history.breakdown_max`
- Optional: `history.retention_days` (default 14)
- Optional: `message-settings.show-item-breakdown`

`gui.yml`
- Inventory titles, slots, icons, progress bar visuals
- Worth GUI layout (sort/filter/refresh/navigation)

`prices.yml`
- Prices are grouped by category. Category keys must match:
  `CROPS`, `ORES`, `MOBS`, `NATURAL`, `ARMOR_AND_TOOLS`, `FISH`, `BOOK`, `POTIONS`, `BLOCKS`.

## Data Storage
- SQLite database stored at `plugins/DSellSystem/dsellsystem.db`
- Transaction history and itemized history (if enabled)

## API / Events
Integrations should listen to:
- `DSellPayoutSummaryEvent` (totals only)
- `DSellPayoutEvent` (totals + per-category breakdown)

Services:
- `DSellSystemApi` is registered with Bukkit's ServicesManager.

Note: DSellSystem payouts already include the internal multiplier. Apply any external bonuses after the event.

## Build
```bash
./gradlew build
```
The shaded jar is produced in `build/libs/`.

## Support
Need help or want a feature? Open an issue in this repo with:
- Your server version
- Plugin version
- Steps to reproduce
- Relevant config sections

Have fun selling. May your multipliers be ever in your favor.
