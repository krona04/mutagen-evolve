# Mutagen

**Become the mob. Pay what it costs.**

<center><img src="https://i.imgur.com/QNd2ZcX.png" alt="Mutagen Banner" width="500px"></center>

> **EARLY ACCESS**
>
> This is **0.1 «First Dose»**, the first public release. The core loop is complete and playable. Strains live in
> datapacks, the lab needs research, and from stage III the creature grows on your body.
>
> * **Loaders:** Fabric & NeoForge
> * **Minecraft:** 1.21.1
> * **Java:** 21
> * **Strains in this build:** zombie, skeleton, creeper

> ### Required
> * **[Architectury API](https://modrinth.com/mod/architectury-api)**
> * **[Fabric API](https://modrinth.com/mod/fabric-api)** *(Fabric only)*

> ### Recommended
> * **[Pehkui](https://modrinth.com/mod/pehkui)**: with it, the body changes size through Pehkui
>   (hitbox, eyes, reach and inertia). Without it, the mod does the same with vanilla scaling.

---

## About

Mutagen is not a morph mod. No key turns you into a wolf.

To become something, you hunt it and take a tissue sample with a syringe. You purify the sample in a
centrifuge and synthesize a serum. Then you keep dosing yourself for in-game days while your body
slowly rearranges itself. A dose does not raise your progress. It fills an **absorption reserve**, and
the body works through that reserve at 1% every 30 seconds. If you stop dosing, the transformation
**falls back** on its own, because human nature takes its own back. The full form is the one exception.
Once the body has turned completely, no humanity is left to pull it back, and only a reversal serum can.

At the end of that path the game no longer renders you as a player. You may also burn in daylight, panic
near cats or be unable to keep armour on. **Every ability in this mod ships with its price in the same
version.**

---

## The loop

```
hunt the mob  →  syringe  →  sequencer (read the species)
                          →  centrifuge  →  synthesizer  →  inject  →  live with it
```

1. **Syringe.** Right-click a mob. It takes damage and turns on you, and it will not give a second
   sample for ten minutes. A weakened, restrained or adult target gives better material. A mob from a
   spawner gives poor material. Golden and netherite syringes last longer and take cleaner samples.
   Sample quality decides how strong your dose is.
2. **Sequencer.** It spends samples and redstone to read the species' genome. You cannot synthesize what
   you have not read. At 25% research you can make a weak serum capped at 40%. At 50% you can make a full
   serum capped at 70%. At 75% the serum has no cap. At 100% the strain holds without a stabilizer.
3. **Centrifuge.** It burns ordinary fuel and turns a raw sample into a purified genome in 10 seconds.
4. **Synthesizer.** Genome + mutagen base + catalyst gives a serum. You make the base with a bottle o'
   enchanting, so transformation costs experience. Harder species need more catalyst and more time.
   A genome of 85% purity gives two ampoules.
5. **Inject.** Right-click once. The empty syringe stays in your hand. You reuse the needle, and the
   reagents are the cost.
6. **Live with it.** Dose again, stop and let it fade, or roll it back.

You make everything the lab needs at the **Bio Workbench**. It has its own recipes, listed next to the
grid. Click one and the workbench lays out its ingredients from your inventory.

### The gene tree

Press **G** for a catalogue of every species you have sampled. It shows the creature, its research, what
each threshold unlocks and its genes, with abilities in green and weaknesses in red. The genes stay
hidden until you have read a quarter of the genome.

---

## The five stages

| Stage | Progress | What happens |
|---|---|---|
| **I. Infection** | 1–20% | Nothing visible. Particles, and the first passive trait |
| **II. Manifestation** | 21–40% | Your skin takes the strain's colour. First ability, first weakness |
| **III. Mutation** | 41–70% | Glowing eyes, the creature's head grows over yours, the body takes its size |
| **IV. Domination** | 71–99% | More of the creature on your skeleton (arms, bones, body). Your skin still shows through |
| **V. Full Form** | 100% | **You are rendered as the creature itself.** This stage does not decay |

Every transition is an event: a sound, a screen flash, a line in chat and a short withdrawal while the
body rearranges itself.

### Flesh and bone

Nothing is laid over you. **Your own model turns into the creature.** From stage II the infection
spreads from where the needle went in, bone by bone. Every bone it reaches slowly takes the shape of its
counterpart in the creature's body. A skeleton's limbs thin down to bones. A creeper's legs shrink to
stubs as the body sinks onto them, its arms wither away and its hind legs sprout from under you. The skin
changes with the shape. Patches of the creature's flesh appear with an inflamed edge, in a pattern that
is yours alone, and they close into the creature's hide as each bone finishes. Every new stage wrenches
the body with convulsions, and from stage III it twitches on its own. By 100% you already have the
creature's shape, so the full form is the last step and not a swap. First person follows all of it.

The body size changes too. Hitbox width, height and eye height move towards the creature's own values.
At full form a creeper is 1.7 blocks tall. Reach also grows or shrinks: a zombie's long arms reach half
a block further, and a creeper has nothing to reach with.

### Full form

At 100% the game replaces the player model with the **real model of the creature**, drawn through the
vanilla mob renderer. It keeps the creature's own animations and render layers, and it wears your armour
and holds your item. Your walk cycle, head turn, crouch, swim, sprint, hurt flashes and burning all carry
across. On a server your name still floats above you. In first person you get the creature's own arm, or
no arm at all if it never had one. If it clashes with another mod, turn it off with `fullFormRender`.

---

## Strains in this build

| Strain | Gives | Takes |
|---|---|---|
| **Zombie** | Undead strength, regeneration in darkness, immunity to Hunger, +4 health | Burns in sunlight, moves 15% slower. No sleep from III. From IV only meat and rot, and no fine work |
| **Skeleton** | Arrows hit 1.5× harder, poison and hunger do nothing | −4 health, blasts hurt 1.5× more, burns in sunlight. No sleep from III. From IV cannot eat and never heals naturally |
| **Creeper** | Immune to blasts, +12% speed | Panics near cats, −1 attack damage, 1.5× fire damage, sheds armour at stage V. Loses its hands: no off-hand from III. From IV only five belt slots, no tools, weapons or crafting. At V three slots and no building |

---

## Control

- **Stabilizer.** It locks your current level so it stops decaying. Use it again to release the lock.
- **Reversal serum.** It rolls you back 15% per dose, with nausea, weakness and damage. **Milk does not
  cure a strain.** This is on purpose.
- **`/mutagen`.** Subcommands: `status`, `list`, `dose`, `set`, `lock`, `clear`, `research`. It needs
  permission level 2 and takes both `mutagen:zombie` and the short `zombie`. The honest path to stage V
  takes in-game days, so testing needs a shortcut. For the same reason, `dose` ignores research limits.

## Config

`config/mutagen.json`:

| Key | Default | Meaning |
|---|---|---|
| `transformationSpeed` | `1.0` | Multiplier for absorption and decay |
| `doseStrength` | `20.0` | Reserve added by one 100%-purity dose |
| `humanityPenalties` | `true` | The body refusing armour, and the rest of the price |
| `sunlightBurn` | `true` | Undead strains burning in daylight |
| `hud` | `true` | The mutation indicator |
| `fullFormRender` | `true` | Replacing the player model at stage V |
| `maxGenes` | `1` | Genes the body can hold. Chimeras arrive in `0.5` |
| `knowledgeScope` | `world` | `world`: research is shared. `player`: each player keeps their own |
| `researchSpeed` | `1.0` | Multiplier for research gained per sequencer run |
| `doubleSerumPurity` | `85.0` | Genome purity from which a synthesis gives two ampoules. `101` turns it off |
| `hybridRender` | `true` | The creature's parts on your body at stages III–IV |
| `bodyMorph` | `true` | Proportions and posture change with the infection |
| `bodyMotion` | `true` | Convulsions between stages and twitches from stage III |
| `pehkuiIntegration` | `true` | Use Pehkui for body size when it is installed |

---

## Localization

| Language  |Code  |
| --------- |----- |
| English   |<code>en_us</code> |
| Russian   |<code>ru_ru</code> |
| Ukrainian |<code>uk_ua</code> |
| Polish    |<code>pl_pl</code> |
| German    |<code>de_de</code> |
| French    |<code>fr_fr</code> |
| Spanish   |<code>es_es</code> |

---

## License

Licensed under the **MIT License**.

- **Modpacks:** Free to include in any modpack.
- **Source code:** Free to view, modify and distribute, provided you keep the copyright notice and license text.

---

## Bug reports and suggestions

Found a bug or have a feature idea?  
Please open an issue on the **[GitHub Issues Page](https://github.com/krona04/mutagen-evolve/issues)**.
