# Chill Zone Spawn Protection 0.1.7-alpha

Minecraft 26.2 / Fabric server-side spawn safe-zone mod.

## 0.1.7 changes
- **Square is now the default** whenever you start a new region with `/spawn region pos1`.
- Square mode now uses **pos1 + pos2 as opposite block corners**, not center + radius.
- pos1 and pos2 snap to the exact blocks you are standing on, so the protected edge and preview align with block edges.
- Square regions still extend from world bottom to world top; Y is ignored.
- Circle mode is still available with `/spawn region shape circle`; for circles, pos1 is the center and pos2 defines the radius.
- `/spawn region preview on` is now **global**. Everyone in the same dimension can see the flame boundary preview.
- SAFE ZONE / PVP ZONE notices last about **2.5 seconds** instead of 1.5 seconds.
- Boundary sounds now use the Players sound category at full volume for better reliability/audibility.
- Existing protection, `/spawn`, damage prevention, mob removal, explosion protection, persistence, and action-bar formatting remain intact.

## Recommended square setup
Stand on one corner block of the spawn area:

`/spawn region pos1`

Walk to the opposite corner block:

`/spawn region pos2`

Then:

`/spawn region preview on`

The flame outline follows the **actual protected block boundary**. When satisfied:

`/spawn protection on`

Use `/spawn region preview off` whenever you no longer want the outline visible.

## Shapes
- `/spawn region shape square` — pos1 and pos2 are opposite corners. This is the default.
- `/spawn region shape circle` — pos1 is the center, pos2 is a point on the radius.

## Zone feedback
- Safe zone: `🛡 SAFE ZONE 🛡` in green/bold.
- PvP zone: `⚔ PVP ZONE ⚔` in red/bold.
- Messages display only on initial zone detection / boundary changes and clear after about 2.5 seconds so combat action-bar timers can take over.
- Sounds play only when actually crossing the boundary.

## Commands
Player:
- `/spawn`

Admin/OP:
- `/spawn set`
- `/spawn protection on|off`
- `/spawn region pos1`
- `/spawn region pos2`
- `/spawn region shape square|circle`
- `/spawn region preview on|off`
- `/spawn region show`
- `/spawn region clear`
