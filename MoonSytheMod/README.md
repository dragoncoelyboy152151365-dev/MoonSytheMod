# Moon Scythe (Fabric 1.21.11, server-side)

Server-only mod: players do **not** need to install anything.

## Build (GitHub Codespaces)
```
chmod +x gradlew
./gradlew build
```
The jar is `build/libs/moon-scythe-1.0.0.jar`. Put it in the server's `mods/` folder (the server also needs Fabric API).

## Get the weapon
`/moonscythe` (gives it to you) or `/moonscythe <players>`. Needs op level 2.

## What it does
- Built on a vanilla **mace**, so smash attacks, fall damage and mace sounds are native.
- 12 attack damage, axe swing cooldown (1.0 attack speed), unbreakable.
- Strength II while it is in your main hand (removed when you stop holding it). Strength adds +6 on top of the 12.
- Always Breach III; enchantments are reset every tick so anvils/grindstones can't change it.
- 3 crits in a row (no being hit in between, and each within 10s of the last) triggers the Moon Beam: white particle lasers that only damage your opponent.

## Tweaking
- `MoonScytheMod.java`: crits needed, timeout, strength level.
- `MoonBeam.java`: duration, damage, laser count.
- `MoonScytheItem.java`: damage and attack speed.

## Textures later
Add a server resource pack and set `DataComponents.ITEM_MODEL` in `MoonScytheItem.create`. Don't set it without a pack: vanilla clients would show a missing-texture model.
