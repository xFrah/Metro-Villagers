# Metro Villagers

A high-performance, lightweight mod that revolutionizes villager pathfinding and job acquisition by introducing a **Gossip Network**. Instead of every villager selfishly querying the server's chunk database, villagers now share workstation coordinates with each other through word-of-mouth!

## Features

- **The Gossip Network:** Jobless villagers will actively ask nearby villagers for directions to workstations. This creates a peer-to-peer distributed cache, massively reducing server load in large trading halls.
- **Smart Memory Validation:** Villagers remember job sites and keep their memory perfectly synced with the world. If a job site is destroyed, they instantly delete it from their memory.
- **Unreachability Blacklist:** Fixes the vanilla bug where villagers obsess over unreachable blocks. If a villager realizes a workstation is blocked behind a wall, they will place it on a temporary 60-second blacklist and smoothly move on to the next closest site!
- **True Gossip Chaining:** Villagers can chain knowledge! If Villager A sees a block at 48 blocks and tells Villager B, and Villager B tells Villager C, Villager C can find a job 144 blocks away purely through word-of-mouth chaining!

## Performance Improvements

Vanilla Minecraft destroys server TPS in large trading halls because every jobless villager constantly runs a massive 48-block chunk database query. 

Metro Villagers fixes this by:
1. Hijacking the vanilla AI and feeding it cached coordinates, entirely bypassing the laggy vanilla chunk scan.
2. Replacing heavy chunk database queries with instant, O(1) memory lookups whenever jobless villagers ask their friends.
3. Completely eliminating the infinite pathfinding loop that occurs when a workstation is blocked.

## Visuals
When a jobless villager successfully receives a job coordinate from a friend via gossip, a small burst of "angry" storm cloud particles will appear above their head, making it incredibly easy to see the gossip network functioning in real-time!
