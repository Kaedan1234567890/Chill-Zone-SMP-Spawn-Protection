# Chill Zone Spawn Protection 0.1.2-alpha

Minecraft 26.2 compile hotfix for the spawn-protection mod.

Changes from 0.1.1-alpha:
- Fixed the 26.2 `ExplosionParticleInfo` package import.
- Replaced removed `ServerPlayer.displayClientMessage(...)` calls with the 26.2 action-bar API `ServerPlayer.sendOverlayMessage(...)`.
- Keeps the SAFE ZONE / PVP ZONE action bar behavior unchanged.
- No intended changes to region geometry, protection rules, saved config, or `/spawn` commands.

# Chill Zone Spawn Protection 0.1.0-alpha

Minecraft 26.2 / Fabric server-side spawn and safe-zone mod.

## Region shape
`/spawn region pos1` sets the **center** of the safe zone.
`/spawn region pos2` sets a point on the **edge**. The horizontal distance becomes the radius.
Y is deliberately ignored, so the region is a **vertical cylinder from the bottom of the world to the top**.

## Commands
Player command:
- `/spawn` - teleports to the exact saved spawn point and clears fall momentum/distance.

OP-only on a dedicated server (available to the host in single-player testing):
- `/spawn set`
- `/spawn protection on`
- `/spawn protection off`
- `/spawn region pos1`
- `/spawn region pos2`
- `/spawn region show`
- `/spawn region clear`

## Protection while ON
- Blocks in the region are frozen at the world-setBlock layer. This is intentionally broad: player breaking/placing, fire changes, leaf decay, fluids, piston block-state changes, and explosion block changes are blocked.
- Players inside the safe zone cannot take living-entity damage through Fabric's server damage event.
- Explosions whose blast radius touches the cylinder are cancelled.
- Mobs that enter or spawn inside are removed within 5 ticks.
- Enter/leave messages use the action bar.

### Editing the build
Because block-state changes are frozen while protection is ON, turn protection OFF before intentionally editing spawn, then turn it back ON afterward.

## Persistence
Saved to `config/chillzone-spawn-protection.json`: spawn point, facing direction, safe-zone center/radius, dimension, and protection state.

## Later follow-up (not part of this mod)
Combat restrictions for `/home` and fall-damage cleanup after home teleport should be handled in the homes/combat system so the spawn-protection mod does not take ownership of unrelated commands.


## 0.1.1-alpha - Zone action bar
- While spawn protection is enabled, players continuously see their current zone above the hotbar.
- Inside the protected cylinder: **🛡 SAFE ZONE** in green/bold.
- Outside the protected cylinder: **⚔ PVP ZONE** in red/bold.
- The display follows the actual configured spawn-region boundary and does not spam normal chat.


## 0.1.6-alpha zone feedback
- Action bar now shows `🛡 SAFE ZONE 🛡` inside spawn and `⚔ PVP ZONE ⚔` outside.
- Crossing from PvP into the safe zone plays a short positive chime.
- Crossing from the safe zone into PvP plays a short lower warning tone.
- Sounds play only on an actual boundary crossing, not every tick.
- Joining/rejoining remembers the initial zone silently so the sound does not fire just because the player logged in.


## 0.1.6 zone message timing
- SAFE ZONE / PVP ZONE action-bar notices are now event-based instead of being resent every server tick.
- The zone notice is cleared after about 30 ticks (1.5 seconds), allowing combat countdown/action-bar messages to remain visible.
- Entry/exit sounds remain boundary-triggered only.
