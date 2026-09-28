# 🧬 Mutagen

**Become the mob. Pay what it costs.**

> ⚠️ **EARLY ACCESS**
>
> This is **0.1 «First Dose»**, the first public release. The core loop is complete and playable, strains live in
> datapacks, the lab needs research, and from stage III the creature grows on your body. Content is
> still small and saves may break until `0.6`.
>
> * **Loaders:** Fabric & NeoForge
> * **Minecraft:** 1.21.1
> * **Java:** 21
> * **Strains in this build:** zombie, skeleton, creeper

> ### Required
> * **[Architectury API](https://modrinth.com/mod/architectury-api)**
> * **[Fabric API](https://modrinth.com/mod/fabric-api)** *(Fabric only)*

> ### Recommended
> * **[Pehkui](https://modrinth.com/mod/pehkui)** — with it the body changes size through Pehkui:
>   hitbox, eyes, reach and inertia. Without it the mod does the same with vanilla scaling.

---

## 📖 About

Mutagen is not a morph mod. There is no key that turns you into a wolf.

To become something, you hunt it, take a tissue sample with a syringe, purify it in a centrifuge,
synthesize a serum — and then keep dosing yourself for in-game days while your body slowly rearranges
itself. A dose does not raise your progress. It fills an **absorption reserve**, and the body works
through that reserve at 1% every 30 seconds. Stop dosing and the transformation **falls back** on its
own: human nature takes its own back. The one exception is the full form — once the body has turned
completely, there is no humanity left to pull it back, and only a reversal serum will.

By the end of that path you are no longer rendered as a player at all. You are also on fire in daylight,
panicking near cats, or unable to keep armour on. **Every ability in this mod ships with the price it
costs, in the same version.**

---

## 🔬 The loop

```
hunt the mob  →  syringe  →  sequencer (read the species)
                          →  centrifuge  →  synthesizer  →  inject  →  live with it
```

1. **Syringe** — right-click a mob. It takes damage, it turns on you, and it will not give a second
   sample for ten minutes. A weakened, restrained or adult target gives better material; a mob from a
   spawner gives poor material. Golden and netherite syringes last and take cleaner samples. Quality
   carries all the way through to how strong your dose is.
2. **Sequencer** — spends samples and redstone to read the species' genome. You cannot synthesize what
   you have not read: 25% research allows a weak serum capped at 40%, 50% a full serum capped at 70%,
   75% a serum that goes all the way. At 100% the strain holds without a stabilizer.
3. **Centrifuge** — burns ordinary fuel, turns a raw sample into a purified genome in 10 seconds.
4. **Synthesizer** — genome + mutagen base + catalyst → serum. The base is made with a bottle o'
   enchanting, so transformation costs experience. Harder species need more catalyst and more time;
   a genome of 85% purity gives two ampoules.
5. **Inject** — right-click once. What stays in your hand is the empty syringe: the needle is reused,
   the reagents are the cost.
6. **Live with it** — dose again, or stop and let it fade, or roll it back.

Everything the lab needs is made at the **Bio Workbench**, which has its own recipes and a list of them
next to the grid: click one and its ingredients are laid out from your inventory.

### 🌳 The gene tree

Press **G** for a catalogue of every species you have sampled: the creature, its research, what each
threshold unlocks and its genes — abilities in green, weaknesses in red. The genes stay hidden until
you have read a quarter of the genome.

---

## 🧫 The five stages

| Stage | Progress | What happens |
|---|---|---|
| **I. Infection** | 1–20% | Nothing visible. Particles, and the first passive trait |
| **II. Manifestation** | 21–40% | Your skin takes the strain's colour. First ability, first weakness |
| **III. Mutation** | 41–70% | Glowing eyes, the creature's head grows over yours, the body takes its size |
| **IV. Domination** | 71–99% | More of the creature on your skeleton — arms, bones, body. Your skin still shows through |
| **V. Full Form** | 100% | **You are rendered as the creature itself.** This stage does not decay |

Every transition is an event: a sound, a screen flash, a line in chat, and a short withdrawal while the
body rearranges itself.

### Flesh and bone

Nothing is laid over you — **your own model turns into the creature**. From stage II the infection
spreads from where the needle went in, bone by bone, and every bone it reaches slowly takes the shape
of its counterpart in the creature's body: a skeleton's limbs thin down to bones, a creeper's legs
shrink to stubs as the body sinks onto them, its arms wither away and its hind legs sprout from under
you. The skin changes with the shape — patches of the creature's flesh with an inflamed edge, in a
pattern that is yours alone, closing into the creature's hide as each bone finishes. Every new stage
wrenches the body with convulsions, and from stage III it twitches on its own. By 100% you already have
the creature's shape; the full form is the last step, not a swap. First person follows all of it.

The body changes with them. Hitbox width, height and eye height move towards the creature's own
values — at full form a creeper is 1.7 blocks tall — and reach grows or shrinks: a zombie's long arms
reach half a block further, a creeper has nothing to reach with.

### Full form

At 100% the player model is replaced by the **real model of the creature**, drawn through the vanilla
mob renderer. That means its own animations and render layers, with your armour and your held item on
it. Your walk cycle, head turn, crouch, swim, sprint, hurt flashes and burning all carry across, and
your name still floats above you on a server. In first person you get the creature's own arm — or no
arm at all, if it never had one. Turn it off with `fullFormRender` if it clashes with another mod.

---

## ⚗️ Strains in this build

| Strain | Gives | Takes |
|---|---|---|
| **Zombie** | Undead strength, regeneration in darkness, immunity to Hunger, +4 health | Burns in sunlight, moves 15% slower. No sleep from III; from IV only meat and rot, and no fine work |
| **Skeleton** | Arrows hit 1.5× harder, poison and hunger do nothing | −4 health, blasts hurt 1.5× more, burns in sunlight. No sleep from III; from IV cannot eat and never heals naturally |
| **Creeper** | Immune to blasts, +12% speed | Panics near cats, −1 attack damage, 1.5× fire damage, sheds armour at stage V. Loses its hands: no off-hand from III; from IV only five belt slots, no tools, weapons or crafting; at V three slots and no building |

### Your own strains

A strain is a JSON file in a datapack: `data/<namespace>/mutagen/strains/<name>.json`. It names the
creature, its colour, height, difficulty, synthesis cost and a list of traits. `/reload` applies changes
on a running server, and operators see in chat which files were skipped and why. The format and every
trait type are described in [docs/DESIGN.md](docs/DESIGN.md).

```json
{
  "entity": "minecraft:witch",
  "color": "#6A2C8C",
  "difficulty": 2,
  "synthesis": { "catalyst": "mutagen:catalyst", "catalyst_count": 1, "time": 400 },
  "body": {
    "model_layer": "minecraft:witch",
    "spread": ["head", "body"],
    "height": 1.95, "eye_height": 1.62
  },
  "traits": [
    { "type": "effect_immunity", "from_stage": "II", "effects": ["minecraft:poison"] },
    { "type": "attribute", "from_stage": "mutation", "attribute": "minecraft:generic.max_health", "amount": -2 }
  ]
}
```

---

## 🎛️ Control

- **Stabilizer** — locks your current level so it stops decaying. Use it again to release the lock.
- **Reversal serum** — rolls you back 15% per dose, with nausea, weakness and damage. **Milk does not
  cure a strain**, and that is on purpose.
- **`/mutagen`** — `status`, `list`, `dose`, `set`, `lock`, `clear`, `research`. Permission level 2.
  Takes both `mutagen:zombie` and the short `zombie`. The honest path to stage V takes in-game days,
  so testing needs a shortcut; `dose` ignores research limits for the same reason.

## ⚙️ Config

`config/mutagen.json`:

| Key | Default | Meaning |
|---|---|---|
| `transformationSpeed` | `1.0` | Multiplier for absorption and decay |
| `doseStrength` | `20.0` | Reserve added by one 100%-purity dose |
| `humanityPenalties` | `true` | The body refusing armour and the rest of the price |
| `sunlightBurn` | `true` | Undead strains burning in daylight |
| `hud` | `true` | The mutation indicator |
| `fullFormRender` | `true` | Replacing the player model at stage V |
| `maxGenes` | `1` | Genes the body can hold. Chimeras arrive in `0.5` |
| `knowledgeScope` | `world` | `world` — research is shared; `player` — each player keeps their own |
| `researchSpeed` | `1.0` | Multiplier for research gained per sequencer run |
| `doubleSerumPurity` | `85.0` | Genome purity from which a synthesis gives two ampoules; `101` turns it off |
| `hybridRender` | `true` | The creature's parts on your body at stages III–IV |
| `bodyMorph` | `true` | Proportions and posture change with the infection |
| `bodyMotion` | `true` | Convulsions between stages and twitches from stage III |
| `pehkuiIntegration` | `true` | Use Pehkui for body size when it is installed |

---

## 🗺️ Where this is going

The full plan — every version up to 1.0 and the 2.0 branch after it — is in **[ROADMAP.md](ROADMAP.md)**.
The systems behind it, including the ones not built yet, are in **[docs/DESIGN.md](docs/DESIGN.md)**.

| Version | Name | What it brings |
|---|---|---|
| **0.2** | Abilities | Active abilities on a key, the apex ability, twelve curated strains |
| **0.3** | Stability | Overdose, mutation flares, breakdown, and a med pod that resets a strain safely |
| **0.4** | Chimeras | Two and then three genes, the compatibility matrix, suppressors |
| **0.5** | Any Mob | A strain derived procedurally from any entity, including modded ones |
| **0.6** | The World | The abandoned KRONA lab, cryo storage, the incubator. Saves stop breaking here |
| **0.7** | Society | Villagers, golems and pets react to what you are. Multiplayer |
| **0.8** | Open | `MutagenAPI` v1, JEI/EMI, Jade, datapack documentation |
| **0.9** | Polish | Balance, performance, sound, migration from every earlier version |

## 🧩 Compatibility

| Mod | What happens |
|---|---|
| **Pehkui** | Body size, eyes, reach and inertia go through it. Optional; turn off with `pehkuiIntegration` |
| **Sodium / Embeddium** | Works as is: every mutation layer is drawn through vanilla model parts |
| **Iris / Oculus** | Mutation overlays skip the shadow pass of shader packs |
| **Skin and cape mods** | Your skin is whatever they provide. The cape leaves with you in full form |
| **Figura** | An avatar may cover the hybrid and the full form. Turn off `hybridRender` / `fullFormRender` if they fight |

The log lists which of these were found at startup.

Nothing on that list will be a hard dependency. Recommended integrations like **Pehkui** are detected at
runtime and the mod stays fully playable without them.

## 🧪 Testing

The protocol is in [docs/TESTING.md](docs/TESTING.md), and it opens with a ten-minute check that tells
you whether everything installed correctly. The most useful report is the one about **how it felt**:
how long before you noticed something was wrong with your character without looking at the indicator,
and which weakness felt unfair.

Known limitations for this release are listed at the end of [CHANGELOG.md](CHANGELOG.md).

## 📜 License

MIT — see [LICENSE.txt](LICENSE.txt).
