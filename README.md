# Verity: a two-player Minecraft raid encounter

A Fabric mod for **Minecraft 1.21.1** that turns the Verity encounter from Destiny 2's *Salvation's Edge* raid into a two-player co-op fight. One player is trapped in the **Shadow Realm** and reads the callouts. The other player stays **outside** and dissects 3D shapes. Get the Truth right, then take down **The Witness** before the Final Shape is complete.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft **1.21.1**.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) (for 1.21.1) in your `mods` folder.
3. Download `verity-1.0.0.jar`:
   - open the repo's **Actions** tab on GitHub
   - pick the latest green **build** run
   - download the **verity-mod-jar** artifact and unzip it
4. Put the jar in your `mods` folder.

To build it yourself instead, run `./gradlew build`; the jar lands in `build/libs/`.

**Playing together:** the person hosting (in single-player "Open to LAN" or on a Fabric server) needs the mod. The mod adds no new blocks, items or mobs; everything is built from vanilla pieces. A friend should be able to join with plain Fabric, but installing the mod on both sides is the safest setup.

When you open to LAN, turn **Allow Cheats: ON**. The `/verity` commands need operator permission. Set the difficulty to **Easy or higher**, because Peaceful removes the monsters.

## Quick start

```
/verity build     # stand on open ground; builds the arena around you (~70 x 35 blocks)
/verity kit       # optional: iron armor, sword, Infinity bow, food
/verity start     # starts with the other online player
```

Use `/verity solo` to practice alone. Use `/verity stop` to end a run and `/verity clear` to remove the arena.

## How to play

### The shapes

| 2D shape | Essence item |
|---|---|
| ● Circle | Heart of the Sea |
| ▲ Triangle | Amethyst Shard |
| ■ Square | Brick |

Two 2D shapes make a 3D shape:

| 3D shape | Made of |
|---|---|
| Sphere | ● ● |
| Pyramid | ▲ ▲ |
| Cube | ■ ■ |
| Cone | ● ▲ |
| Cylinder | ● ■ |
| Prism | ▲ ■ |

### Each round

At the start of each round, one player is pulled into the **Shadow Realm**, a sealed room 48 blocks east. The other player stays **outside** with three giant statues. Roles swap every round.

**Inside player (the Shadow):**
- The three statues on the far wall show the **callouts**, one 2D shape each for LEFT, MIDDLE and RIGHT. Tell your partner what they are.
- One statue is marked **(YOU)**. That shape is your own.
- Behind you are two **offerings**. Kill Shadow Knights for essences, then right-click an offering to dunk an essence into it. Both offerings must hold the **two shapes that are not yours**.
- Dunking your own shape causes **Dissonance**.

**Outside player (the Dissector):**
- Each statue holds a 3D shape. It needs to end up holding the **two shapes that are not its callout**. For example, if LEFT's callout is ●, the LEFT statue must become a Prism (▲ ■).
- Kill Shadow Knights to get essences. Dunk one essence into a statue, then dunk a **different** essence into a **different** statue. The two shapes **swap** between those statues.
- Dunking a shape the statue doesn't contain causes **Dissonance**.

Once every statue and both offerings are correct, **the Truth is revealed**. The Shadow is freed and **The Witness is exposed for 25 seconds**. Hit it with everything you have. If it survives, the next round begins with the roles swapped.

### Winning and wiping

- **Win:** kill The Witness during a damage phase. Each of you gets *Verity's Brow* and a netherite ingot.
- **Wipe:** any of these ends the run:
  - 3 Dissonances in one round
  - the 3-minute dissection timer runs out
  - someone dies
  - The Witness survives all 3 rounds

### Tips

- Don't wait for your partner. Call out LEFT / MIDDLE / RIGHT the moment you arrive, then work on your offerings while they dissect.
- Knights usually drop a shape that helps, but not always. Hold on to spare essences.
- Plan swaps before you dunk. A statue that should be ▲ ■ but is ● ■ needs its ● swapped out for a ▲ from somewhere else.
- The Witness only fights back while it is exposed. Bows are great for chasing it.

## Commands

| Command | What it does |
|---|---|
| `/verity build` | Build the arena at your feet and remember its location |
| `/verity start [partner]` | Start a two-player run |
| `/verity solo` | Practice alone; callouts are written on the statues |
| `/verity kit [players]` | Hand out gear |
| `/verity stop` | End the current run |
| `/verity clear` | Remove the arena blocks |
