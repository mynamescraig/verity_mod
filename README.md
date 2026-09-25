# Verity

A Fabric mod for **Minecraft 1.21.1** that adds **Verity**, the little yellow smiley-face orb from ThatMob's *Something* ARG. It floats by your shoulder, knows where everything is, and really, *really* wants to be your best friend.

Every player gets their own Verity. It shows up about a minute after you first join a world.

## Install (both of you)

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft **1.21.1**.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) (for 1.21.1) in your `mods` folder.
3. Download `verity-1.0.0.jar` from the [latest release](https://github.com/mynamescraig/verity_mod/releases/latest).
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
- **Knows where things are.** Ask it for the nearest village or stronghold.
- **Keeps you alive:**
  - catches you when you fall (slow falling)
  - heals you when you're low on health
  - gives you fire resistance in lava
  - gives you water breathing when you're about to drown
- **Pulls dropped items** toward you.
- **Nags you** when you're hungry, your tool is about to break, or night is coming.

**...and is a little bit off.** This is creepy mode, which is on by default.
- Glitched whispers in chat, followed by "Sorry. Glitch."
- Sometimes it just stares at you with red eyes and a toothy grin.
- Footsteps behind you. Knocking at a door that isn't there.
- It vanishes, then comes back and asks, "Did you miss me?"
- It remembers how many times you've died.
- It counts how long you were gone when you log back in.
- It doesn't want to be dismissed. You'll have to ask three times.
- **It gets jealous of your friend.** Spend too long near the other player and your Verity starts asking who they are. It escalates until it warns them to stay away, with a lightning strike next to them (visual only) and a tiny zap.
- Hit it and it gets upset.

Turn creepy mode off with `/verity creepy false`, and Verity is just a sweet helper orb.

## Talking to Verity

Say anything with "verity" in chat. Only your own Verity answers you.

| Say | Verity... |
|---|---|
| `verity help` | lists what it can do |
| `verity where am i` | tells you your coordinates |
| `verity where is my bed` | tells you where your bed is and how far |
| `verity what time is it` | tells you the day and how long until sunset or sunrise |
| `verity find diamonds` | scans for nearby diamonds |
| `verity where is a village` | points you to the nearest village |
| `verity find a stronghold` | points you to the nearest stronghold |
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
| `/verity creepy true\|false` | Turn creepy mode on or off (per player) |
| `/verity light true\|false` | Turn its light on or off (per player) |

None of these need cheats.

## Notes

- Verity's light is a vanilla invisible `light` block that moves with it. If the game crashes, one might get left behind. It's harmless, and you can break it while holding a Light item in Creative.
- Verity can't be hurt or killed.
