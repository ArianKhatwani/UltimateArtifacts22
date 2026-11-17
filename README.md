# AnimatedPrefixes 1.0.2 — Paper 1.21.11

Animated gradient prefixes displayed in TAB only. Assignments persist across restarts.

## Included prefixes
- `owner` — premium multi-colour spectrum.
- `pre_owner` — softer gold/rose/violet shimmer.
- `admin` — warm animated wave.
- `admin_starter` — static blue/violet gradient.
- `tech_admin` — cyan/blue/violet scan.
- `builder` — green/gold nature wave.
- `horn_owner`, `legend`, `void`, `thunder`, `celestial`, `inferno`, `frost`, `royal`, `blood`, `nature`.

All preset labels include square brackets. The default animation update interval is 3 ticks (slower than the previous 2-tick setting).

## Animation modes
`royal_spectrum`, `shimmer`, `wave`, `static`, `aurora`, `tech_scan`, `ember`, `ice`, `bloodwave`, `nature`, `pulse`, `rainbow`.

## Commands
- `/prefix list`
- `/prefix give <player> <id>`
- `/prefix set <player> <id>`
- `/prefix remove <player>`
- `/prefix create <id> <text>`
- `/prefix reload`

Permission: `animatedprefixes.admin` (default OP).

## Build on GitHub
Upload the contents of this project to the repository root. Ensure `build.gradle` and `.github/workflows/build.yml` are at the root. Open Actions and download `AnimatedPrefixes-JAR`.

## Install
Replace the old plugin JAR in `plugins/` with the new JAR and restart Paper 1.21.11. Back up `plugins/AnimatedPrefixes/players.yml` and `config.yml` first if you have custom settings. The new default config is used for a fresh installation; on an existing installation, update the config manually or remove/rename the old config after backing it up.
