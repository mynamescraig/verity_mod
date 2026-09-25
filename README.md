# Verity

A Fabric mod for **Minecraft 1.21.1** that adds **Verity**, the little yellow smiley-face orb from ThatMob's *Something* ARG. It floats by your shoulder, knows where everything is, and really, *really* wants to be your best friend.

Every player gets their own Verity. It shows up about a minute after you first join a world.

## Install (both of you)

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft **1.21.1**.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) (for 1.21.1) in your `mods` folder.
3. Download `verity-1.1.0.jar` from the [latest release](https://github.com/mynamescraig/verity_mod/releases/latest).
4. Put the jar in your `mods` folder.

Every push to the repo builds the mod and publishes a new release automatically.

**Both players need the mod**, because Verity has its own model. Play together with "Open to LAN" or on a Fabric server.

To build it yourself instead, run `./gradlew build`; the jar lands in `build/libs/`.

## What Verity does

**Helps you out**
- **Follows you** around, hovering by your right shoulder. It floats through walls to keep up.
- **Lights up the dark.** In caves, at night and in the Nether, it carries an invisible light with it. Turn this off with `/verity light false`.
- **Watches your back.** When a monster comes for you, Verity warns you which direction it's coming from, makes it glow, and zaps it.
- **Finds diamonds.** While you're underground, Verity tells you when diamonds are close and which way to dig. In the Nether it looks for ancient debris instead.
- **Knows where everything is, and takes you there.** Ask for a village, stronghold, ancient city, trial chambers, mansion, nether fortress, bastion and more. Say "take me to..." and Verity flies ahead to lead the way, calling out the distance as you go.
- **Gets your stuff back.** After you die, Verity tells you where. Say `verity take me to my stuff` and it leads you there before your items despawn.
- **Keeps you alive:**
  - catches you when you fall (slow falling)
  - heals you when you're low on health
  - gives you fire resistance in lava
  - gives you water breathing when you're about to drown
- **Pulls mob and block drops** toward you. It leaves items a player threw alone, so you can still trade with your friend.
- **Nags you** when you're hungry, your tool is about to break, or night is coming.

**Has feelings.** It smiles, blinks, makes happy `^ ^` eyes when you thank it, and gets sleepy when you go to bed.

**...and gets stranger the longer you know it.** This is creepy mode, which is on by default. Like the series, it plays out in stages based on how much time you've spent together. Check yours with `/verity friendship`.

| Stage | Time together | What starts happening |
|---|---|---|
| New Friend | 0–30 min | Almost nothing. Maybe it stares a bit too long. It says it remembers every time you've died. |
| Friend | 30–90 min | *Something is knocking at your door.* Knocking, footsteps behind you, glitched whispers. It gets jealous of your friend. |
| Best Friend | 90–180 min | *Something is inside your house.* Doors open on their own. It leaves a sign by your bed. It vanishes, then asks "Did you miss me?" It watches you sleep. |
| Only Friend | 180+ min | *Something won't let you leave.* "Where are you going?" It takes five tries to dismiss it. |

- **It gets jealous of your friend.** Spend too long near the other player and your Verity starts asking who they are. It escalates until it warns them to stay away, with a lightning strike next to them (visual only) and a tiny zap.
- When both of you have a Verity, they notice each other. Later on, they don't like it.
- It counts how long you were gone when you log back in.
- Hit it and it gets upset.

Turn creepy mode off with `/verity creepy false`, and Verity is just a sweet helper orb. Your friendship progress is kept either way.

## Talking to Verity

Say anything with "verity" in chat. Only your own Verity answers you.

| Say | Verity... |
|---|---|
| `verity help` | lists what it can do |
| `verity where am i` | tells you your coordinates |
| `verity where is my bed` | tells you where your bed is and how far |
| `verity take me home` | flies ahead and leads you to your bed |
| `verity take me to my stuff` | leads you to where you last died |
| `verity where is an ancient city` | finds the nearest one (works for most structures) |
| `verity take me to a village` | leads you to the nearest village. `verity take me there` works after asking where something is |
| `verity stop` | stops leading you |
| `verity what time is it` | tells you the day and how long until sunset or sunrise |
| `verity find diamonds` | scans for nearby diamonds |
| `verity stay` / `verity follow` | waits where it is, or comes back to you |
| `verity light off` / `verity light on` | turns its light off or on |
| `hi verity`, `thanks verity`, `i love you verity`... | try it |

You can also **right-click** Verity to say hi, or **sneak + right-click** to make it stay or follow.

## Commands

| Command | What it does |
|---|---|
| `/verity summon` | Call Verity to you (or meet it right now) |
| `/verity dismiss` | Send Verity away |
| `/verity stay` / `/verity follow` | Make it wait, or come back |
| `/verity friendship` | See how close you two are, and your story stage |
| `/verity friendship set <minutes>` | Jump to a stage for testing (needs cheats) |
| `/verity creepy true\|false` | Turn creepy mode on or off (per player) |
| `/verity light true\|false` | Turn its light on or off (per player) |

None of these need cheats, except `friendship set`.

## Settings

The first launch writes `config/verity.properties`. Edit it with the game closed.

| Setting | Default | What it does |
|---|---|---|
| `creepyByDefault` | `true` | Whether creepy mode starts on for new players |
| `creepyFrequency` | `1.0` | `2.0` doubles the number of creepy events, `0.5` halves it |
| `stageMinutes` | `30,90,180` | Minutes together before Friend, Best Friend and Only Friend |
| `jealousy` | `true` | Whether Verity gets jealous of the other player |
| `firstMeetingSeconds` | `60` | How long after joining Verity first appears |
| `helperEffects` | `true` | Slow falling, healing, fire resistance and water breathing |
| `oreSense` | `true` | Diamond and ancient debris hints |
| `itemMagnet` | `true` | Pulling drops toward you |
| `zapDamage` | `3.0` | How hard Verity zaps monsters (`0` turns it off) |
| `lightLevel` | `13` | How bright its light is (`0` turns it off) |

## Notes

- Verity's light is a vanilla invisible `light` block that moves with it. If the game crashes, one might get left behind. It's harmless, and you can break it while holding a Light item in Creative.
- Verity can't be hurt or killed.
