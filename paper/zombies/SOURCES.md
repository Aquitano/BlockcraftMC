# Implementation references

The main reference was [Lama06/Zombies-Plugin](https://github.com/Lama06/Zombies-Plugin) at
[`8eabda58bd337a52080581d63e1c2aa89bd7b43e`](https://github.com/Lama06/Zombies-Plugin/tree/8eabda58bd337a52080581d63e1c2aa89bd7b43e), dated May 5, 2024.

The following source files informed the mechanics:

- `system/weapon/shoot/FireBulletsSystem.java`: ray-based gunfire and weapon spread.
- `system/weapon/ammo/ReloadAmmoSystem.java`: separate magazine and reserve ammunition.
- `system/RepairWindowSystem.java`: sneaking near a barricade to repair it and earn match gold.
- `system/player/revive/TickPlayerCorpseSystem.java`: timed revives and a faster revival perk.
- `system/zombie/SpawnZombiesSystem.java`: paced spawning and wave composition.
- `src/main/resources/templates/dead_end.json`: map-owned positions for doors, windows, and stations.

Blockcraft's Java implementation, arena, icon, and weapon models are original. No reference source files, binaries, or map assets are bundled.
The implementation uses the existing Blockcraft utilities instead of the reference project's entity-component system.
Gun traces stop at solid blocks. Squad size scales the enemy count. Repair rewards have a per-wave cap.

Hypixel's Zombies is the gameplay reference. This mode is not affiliated with Hypixel.
