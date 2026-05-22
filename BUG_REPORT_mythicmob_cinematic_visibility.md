# Bug Report: MythicMob Cinematic Entry Not Hidden From Other Players

## Summary
When spawning a MythicMob during a cinematic using the `mythicmob_cinematic` entry, the mob is not reliably hidden from other players. Approximately 30% of the time, the mob remains fully visible to players outside the cinematic.

## Steps to Reproduce
1. Create a cinematic with a `mythicmob_cinematic` entry
2. Have multiple players online
3. Start the cinematic for one player
4. Other players nearby will sometimes see the mob (not just briefly, but permanently)

## Expected Behavior
The MythicMob should only be visible to the player in the cinematic.

## Actual Behavior
The mob is inconsistently hidden - sometimes works correctly, sometimes the mob stays visible for other players.

## Root Cause
In `MythicMobCinematicEntry.kt`, the code uses MythicMobs' "hide" mechanic which appears to be unreliable or has timing issues.

## Suggested Fix
Use Bukkit's `Player.hideEntity()` API directly instead of MythicMobs' hide mechanic:

```kotlin
val mob = MythicBukkit.inst().mobManager.spawnMob(...)
val entity = mob.entity.bukkitEntity ?: return@switchContext

server.onlinePlayers
    .filter { it.uniqueId != player.uniqueId }
    .forEach { it.hideEntity(plugin, entity) }
```

This directly sends entity destroy packets to other players and is more reliable.

## Environment
- Typewriter: 0.9.0-beta-172
- MythicMobs: 5.11.2
- Paper: 1.21.11
