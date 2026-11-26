# 🧬 Mutagen

**Become the mob. Pay what it costs.**

![Mutagen Banner](https://i.imgur.com/QNd2ZcX.png)

> ⚠️ **EARLY ACCESS**
>
> This is **0.2 «Abilities & Transformations»**. Twelve creatures to turn into, each with its own
> abilities and its own problems. Things will change.
>
> * **Loaders:** Fabric & NeoForge
> * **Minecraft:** 1.21.1
> * **Strains:** zombie, skeleton, creeper, spider, enderman, drowned, blaze, phantom, bee, wolf, piglin, guardian
> * **Datapacks:** add your own strains for any mob, see [DATAPACKS.md](DATAPACKS.md)

> ### Required
> * **[Architectury API](https://modrinth.com/mod/architectury-api)**
> * **[GeckoLib](https://modrinth.com/mod/geckolib)**
> * **[Fabric API](https://modrinth.com/mod/fabric-api)** *(Fabric only)*

> ### Recommended
> * **[Pehkui](https://modrinth.com/mod/pehkui)** handles your body size (hitbox, eye height, reach) if it's
>   installed. Without it the mod uses vanilla scaling and works just the same.

---

## 📖 About

There's no button here that turns you into a wolf.

If you want to become something, you have to hunt it first. Take a tissue sample with a syringe, study
the genome, brew a serum, and then keep injecting yourself for a few in-game days while your body slowly
gives in.

A dose doesn't push your progress up on the spot. It goes into a reserve, and your body absorbs it at
about 1% every 30 seconds. Skip your doses and you start drifting back to human. Once you hit the full
form, though, there's nothing human left to drift back to, and only a reversal serum will undo it.

You get the creature's strengths, and you get its problems too: you might burn in daylight, panic around
cats, or find you can no longer hold a sword. That's the deal, and every ability in the mod comes with
its price in the same update.

---

## 🔬 How it works

```
hunt the mob  →  syringe  →  sequencer (study the species)
                          →  centrifuge  →  synthesizer  →  inject  →  live with it
```

1. **Syringe.** Right-click a mob. It takes a bit of damage, gets angry, and won't give you another sample
   for ten minutes. Weak, restrained or adult mobs give better samples; spawner mobs give bad ones. Golden
   and netherite syringes last longer and take cleaner samples. Sample quality decides how strong your
   serum ends up.
2. **Sequencer.** Feed it samples and redstone to study the species. You can't make a serum for something
   you haven't studied: at 25% you get a weak serum that stops at 40%, at 50% one that goes to 70%, and at
   75% one that takes you all the way. At 100% the strain holds on its own without a stabilizer.
3. **Centrifuge.** Runs on regular fuel and turns a raw sample into a purified genome in ten seconds.
4. **Synthesizer.** Genome + mutagen base + catalyst = serum. The base needs a bottle o' enchanting, so
   changing yourself costs XP. Tougher species need more catalyst and more time. A genome at 85% purity or
   higher gives you two ampoules.
5. **Inject.** One right-click. You keep the syringe; the serum is what you pay for.
6. **Live with it.** Keep dosing, stop and let it wear off, or roll it back.

All the lab gear is crafted at the **Bio Workbench**. It has a recipe list next to the grid; click a recipe
and it pulls the ingredients out of your inventory for you.

### 🌳 Gene tree

Press **G** to see every species you've sampled: how far you've studied it, what each research step
unlocks, its strengths, weaknesses and abilities, and what each ability costs. You won't see any of the
genes until you've read a quarter of the genome.

---

## 🧫 Five stages

| Stage | Progress | What happens |
|---|---|---|
| **I. Infection** | 1–20% | Nothing you can see yet, apart from a few particles and the first passive trait |
| **II. Manifestation** | 21–40% | Patches of the creature start showing on your skin. First ability |
| **III. Mutation** | 41–70% | Your body starts changing shape. Second ability |
| **IV. Domination** | 71–99% | You're more creature than human. Flyers can fly, handless bodies lose the off-hand |
| **V. Full Form** | 100% | You *are* the creature. This stage doesn't wear off |

Each stage change hits hard: a sound, a flash on screen, a message in chat, and a moment where your body
convulses.

### What the change looks like

Nothing gets pasted on top of you. Your own model bends into the creature's: heads, torsos and limbs
stretch and shrink toward the creature's proportions, and its skin spreads over yours pixel by pixel,
starting where the needle went in. Every creature does it differently. A spider grows two extra pairs of
jointed legs before you drop onto them, a wolf grows a tail and goes down on all fours, a phantom's arms
turn into wings, and a bee folds its arms and legs into a growing abdomen. You start walking the way the
creature walks, too.

Your hitbox and camera height follow the visible change, so a creeper really is shorter and an enderman
really does have to duck through doors. At 100% you're drawn as the actual mob, with its own animations,
your armour and whatever you're holding. If that clashes with another mod, turn off `fullFormRender`.

---

## ⚗️ The strains

| Strain | The good | The bad |
|---|---|---|
| **Zombie** | More health, armour and damage, regenerates in the dark, immune to Hunger | 12% slower, burns in the sun, can't sleep; meat only in full form |
| **Skeleton** | Stronger arrows, night vision, immune to poison and Hunger, softer falls | Less health, explosions hurt more, burns in the sun, can't sleep |
| **Creeper** | Takes less from explosions, faster, extra armour, creepers leave you alone | Scared of cats, fire hurts more |
| **Spider** | **Climbs walls**, walks through cobwebs, jumps higher, sees in the dark | Fire hurts more, can't wear a helmet from stage IV |
| **Enderman** | Steps over full blocks, more health and damage, arrows hit you less | Water and rain burn |
| **Drowned** | Swims fast, breathes and sees underwater | Slow on land, burns in the sun; in full form it dries out after three minutes away from water |
| **Blaze** | Fire can't hurt you from stage III, **flies** from stage IV, softer falls | Water and rain burn and ground you, arrows hurt more |
| **Phantom** | Light body, night vision, **the fastest flyer** from stage IV | Less health, burns in the sun, no chestplate over the wings |
| **Bee** | **Crops grow faster around you**, **flies** from stage IV, barely feels a fall | Less health, sweet food only, rain grounds you |
| **Wolf** | Faster, bites harder, raw and rotten meat is fine | Meat only, scared of llamas, fire hurts more |
| **Piglin** | Piglins treat you as one of them, no gold needed. Fire hurts less, more health and damage | Can't throw away anything gold, scared of zombies and zombified piglins, weaker outside the Nether in full form |
| **Guardian** | Swims fast and floats, gills, clear underwater vision, spikes that hurt attackers, more armour and health | Hops slowly on land, can't see well there, and from stage IV runs out of air out of water |

Most species also stop attacking you once you're far enough along.

Creatures without hands (creeper, spider, blaze, phantom, bee, wolf and guardian) can't use the off-hand
from stage IV, and in full form they can't use tools, craft, build or wear armour. Turn all of that off
with `humanityPenalties` if it's not your thing.

**Flying.** The phantom, the bee and the blaze fly from stage IV: double-tap jump, like in creative.
Flying burns hunger, you drop out of the sky below four drumsticks, and rain or water grounds you.

**Catalysts.** Most serums use the mod's own catalyst. Six want something from their creature's world
instead: ender pearls for the enderman, blaze powder for the blaze, phantom membrane for the phantom,
honeycomb for the bee, gold ingots for the piglin and prismarine crystals for the guardian.

### Making your own

All twelve strains are plain JSON files in the mod's built-in datapack, and yours work exactly the same way.
Drop a file into `data/<namespace>/mutagen/strains/` in any datapack and you can turn players into any mob,
modded ones included, give it its own traits, abilities and creature vision, or change and switch off the
built-in strains. `/reload` applies it on a running server, and clients don't need the datapack.
Your strain transforms the same way the built-in ones do: the body bends into the mob's model and its skin
spreads pixel by pixel.

How to do it, with a working example and every field explained: **[docs/DATAPACKS.md](docs/DATAPACKS.md)**.

---

## 🩸 Abilities

Every strain has three. The first unlocks at stage II on **R**, the second at stage III on **V**. The third
one, the **apex**, goes on **B** and needs both the full form and the genome studied to 100%.

They cost hunger, health or both, and then go on cooldown. Health costs go straight through armour, and an
ability won't fire if it would kill you. Some only work in the right place: in water, out of the rain,
in the air, on solid ground, in the dark or in the Nether. If an ability fails (no target, wrong place,
not enough food), you don't pay anything and it doesn't go on cooldown.

Bites, charges and dives only hurt if you actually reach the target. Calls like *The Pack* use mobs that
are already around; nothing gets spawned.

| Strain | **R** | **V** | **B** (apex) |
|---|---|---|---|
| **Zombie** | *Dead Flesh*: quick regeneration and resistance | *Ground Slam*: hits everyone around you | *The Horde*: nearby zombies go after whoever hurt you |
| **Skeleton** | *Dead Eye*: see living things through walls | *Volley*: three arrows | *Arrow Storm*: nine arrows |
| **Creeper** | *Pressure Release*: pushes everyone back | *Camouflage*: a few seconds of invisibility | *Detonation*: a big blast that costs you 4 HP and leaves blocks alone |
| **Spider** | *Pounce*: leap forward | *Venom Spit*: poison a target | *Cocoon*: slows and weakens a target |
| **Enderman** | *Blink*: teleport where you're looking | *Void Stare*: slows and weakens a target | *Void Collapse*: pulls everyone in and slows them |
| **Drowned** | *Water Surge*: dash underwater | *Undertow*: pulls everyone in | *The Tide*: damages and slows everyone around |
| **Blaze** | *Fireball* | *Fire Salvo*: three fireballs | *Inferno*: sets everyone around on fire |
| **Phantom** | *Wingbeat*: jump up and glide down | *Night Veil*: invisibility in the dark | *Dive*: dive-bomb a target from the air |
| **Bee** | *Wingbeat*: jump up and float down | *Sting*: poison, costs half a heart | *The Swarm*: nearby bees go after whoever hurt you |
| **Wolf** | *Scent*: see living things through walls | *Leaping Bite*: jump at a target and bite | *The Pack*: nearby wolves go after whoever hurt you |
| **Piglin** | *Fury*: strength and speed | *Charge*: rush a target | *Warband*: nearby piglins join in (Nether only) |
| **Guardian** | *Sonar*: see living things through walls | *Beam*: a laser hit from range | *Elder Curse*: slows and weakens everyone around |

The guardian's and drowned's abilities only work underwater, and the blaze's only work when you're dry.

---

## 🎛️ Controls

- **Strain panel.** Top left, out of the chat's way: your strain, stage, progress and abilities with their
  keys and cooldowns. If an ability is greyed out, the icon next to it tells you why. Move it with
  `hudCorner` in the config.
- **Stabilizer.** Freezes your current level so it stops wearing off. Use it again to unfreeze.
- **Reversal serum.** Takes you back 15% per dose, with nausea, weakness and some damage. Milk won't fix
  this, on purpose.
- **Creature vision.** Off by default. Press **N** and the world starts looking the way the creature sees
  it: faintly at stage II, fully in the full form. Green for the creeper, eight eyes for the spider, heat
  for the blaze, and something different for every strain.
- **Keys.** **R**, **V** and **B** for abilities, **N** for creature vision, **G** for the gene tree. You can
  rebind them under Options → Controls → Mutagen.
- **`/mutagen`** (op level 2): `status`, `list`, `dose`, `set`, `lock`, `clear`, `research`, `abilities`, `use`.
  Works with `mutagen:zombie` or just `zombie`. Getting to stage V the normal way takes days, so these are
  there for testing, and `dose` skips the research limits.

## ⚙️ Config

`config/mutagen.json`:

| Key | Default | What it does |
|---|---|---|
| `transformationSpeed` | `1.0` | How fast doses absorb and wear off |
| `doseStrength` | `20.0` | How much one 100%-purity dose adds |
| `humanityPenalties` | `true` | Losing hands, armour and so on |
| `sunlightBurn` | `true` | Undead strains burning in daylight |
| `hud` | `true` | Shows the strain panel |
| `hudCorner` | `top_left` | Panel position: `top_left`, `top_right`, `bottom_right`, `bottom_left` |
| `fullFormRender` | `true` | Draw you as the actual mob at stage V |
| `maxGenes` | `1` | How many strains one body can hold. Chimeras come later |
| `knowledgeScope` | `world` | `world`: everyone shares research. `player`: each player has their own |
| `researchSpeed` | `1.0` | How much each sequencer run teaches you |
| `doubleSerumPurity` | `85.0` | Genome purity for two ampoules per synthesis; `101` turns it off |
| `hybridRender` | `true` | The creature's parts on your body at stages II–IV |
| `bodyMorph` | `true` | Your proportions and posture change |
| `bodyMotion` | `true` | Convulsions between stages and twitching later on |
| `pehkuiIntegration` | `true` | Use Pehkui for body size if it's installed |
| `mobVision` | `false` | Creature vision. Also toggled with **N** |

---

## 🧩 Compatibility

| Mod | Notes |
|---|---|
| **Pehkui** | Handles body size if installed. Turn off with `pehkuiIntegration` |
| **Sodium / Embeddium** | Works fine |
| **Iris / Oculus** | Works; mutation overlays skip the shadow pass |
| **Skin and cape mods** | Your skin is whatever they give you. The cape disappears in full form |
| **Figura** | Your avatar might cover the hybrid or the full form. Turn off `hybridRender` / `fullFormRender` if they fight |

The log says which of these it found when the game started. Apart from GeckoLib, none of them are required.

## 📜 License

MIT, see [LICENSE.txt](LICENSE.txt).

## 🐛 Bug Reports & Suggestions

Found a bug or have a feature idea?  
Please open an issue on the **[GitHub Issues Page](https://github.com/krona04/mutagen-evolve/issues)**.

