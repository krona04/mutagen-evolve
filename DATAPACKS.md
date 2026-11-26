# Making strains with datapacks

Every strain in Mutagen is a JSON file. The twelve that come with the mod are no different from one you
write yourself: they live in the mod's own built-in datapack, and the code doesn't treat them specially.
So you can add a strain for any mob, tweak or replace the built-in ones, or remove them, without
touching Java.

This page covers what you can do, what you can't, and every field.

---

## Quick start

1. Make a datapack in your world: `world/datapacks/my_strains/`.
2. Add a `pack.mcmeta`:

   ```json
   { "pack": { "pack_format": 48, "description": "My Mutagen strains" } }
   ```

3. Put your strain at `data/<namespace>/mutagen/strains/<name>.json`, for example
   `data/my_strains/mutagen/strains/witch.json`:

   ```json
   {
     "entity": "minecraft:witch",
     "color": "#6A2C8C",
     "difficulty": 2,
     "synthesis": { "catalyst": "minecraft:glowstone_dust", "catalyst_count": 2, "time": 400 },
     "body": {
       "model_layer": "minecraft:witch",
       "spread": ["head", "body"],
       "height": 1.95, "eye_height": 1.62
     },
     "traits": [
       { "type": "effect_immunity", "from_stage": "II", "effects": ["minecraft:poison"] },
       { "type": "attribute", "from_stage": "mutation", "attribute": "minecraft:generic.max_health", "amount": -2 },
       { "type": "mob_neutrality", "from_stage": "mutation", "entities": ["minecraft:witch"] }
     ],
     "abilities": [
       {
         "type": "effect_burst", "from_stage": "manifestation",
         "name": "Brew", "cooldown": 400, "cost": { "hunger": 3 },
         "effects": ["minecraft:regeneration"], "duration": 200
       }
     ]
   }
   ```

4. Run `/reload`. Ops get a chat message with the number of strains loaded, and `/mutagen list` should show
   the witch. Try it with `/mutagen set my_strains:witch 60`.

The witch's crossed arms are a single `arms` part in her model. Both of your arms turn into it, each taking
its own half, so you don't need to map them by hand.

The strain's id is its file path: `data/my_strains/mutagen/strains/witch.json` becomes `my_strains:witch`.

If a file is broken, it's skipped and the rest still load. The reason shows up in the server log and in
chat for ops, e.g. `my_strains:witch: Unknown stage: VI`.

---

## What you can and can't do

**You can:**

- Add a strain for any mob that has a living-entity model, including mobs from other mods.
- Give it any mix of the 33 trait types and 11 ability types listed below.
- Change a built-in strain: put a file at `data/mutagen/mutagen/strains/zombie.json` in your datapack and it
  replaces the mod's zombie.
- Switch a built-in strain off. There's no clean "disable" flag yet, but overriding its file with `{}` works:
  the loader skips it and notes it in the log.
- Change the tags the mod uses: `mutagen:needs_hands` (items and blocks that need hands),
  `mutagen:fine_work`, `mutagen:meat`, `mutagen:sweet_foods`.

**You can't (yet):**

- Add a new *kind* of trait or ability. You combine the existing ones; new types need Java.
- Get hand-made choreography. The built-in strains have extra poses and timing written in code: the wolf
  dropping to all fours, the bee folding its limbs into its abdomen, the phantom raising its arms into wings.
  Your strain still transforms the same way they do (see below), just without those extras.
- Build a strain from a mob automatically. That's planned for `0.5`.

### How your strain transforms

Your strain uses the same method as the built-in ones. Nothing is laid on top of the player: the player's
six body parts (head, torso, two arms, two legs) bend and stretch, little by little, into the parts of the
mob's model, and the mob's skin replaces the player's pixel by pixel, starting where the needle went in.
At 100% the player is drawn as the real mob.

- **Order.** Bones start changing in the order of `spread`, a little apart, and all finish together at
  100%. The skin follows the same order.
- **Matching.** Each player bone turns into the mob part with the same name (`head`, `body`, `right_arm`…),
  or the one you give in `parts`. For crossed-arm mobs (witch, villagers, illagers) both arms go to `arms`.
- **Missing parts.** A bone the mob doesn't have (a cow has no arms until you map its front legs) melts into
  the torso instead of staying human.
- **Shared parts.** When several bones turn into one part, they split it between them, so nothing overlaps:
  a pair takes left and right halves, a torso with merged limbs is split along its length.
- **Extras.** Small details of the mob (noses, ears, horns) grow out of the part they sit on, and `grow`
  parts like tails grow from their bone.
- **Hitbox and camera** follow the visible change.

Two cases still use the older method, where the mob's parts appear over the player's body: a mob model
without a `body` part to anchor on, and the first-person hand. A strain for the spider or cave spider gets
the built-in spider's jointed legs.

Strains are synced to clients when they join and after every `/reload`, so players don't need the datapack
installed on their side. Server and client do need the same Mutagen version.

---

## Names and translations

Every text field (`name` of an ability, `description` of a trait) is a translation key. If there's no
translation for it, the key itself is shown. So for a quick strain you can just write plain text:
`"name": "Brew"`.

For proper translations, ship a resource pack with a lang file and use keys:

| Key | Used for |
|---|---|
| `mutagen.strain.<namespace>.<name>` | The strain's name. Without it: "*Mob* Strain" |
| `mutagen.role.<name>` | The one-line role shown in the gene tree |
| `mutagen.ability_help.<name>.<index>` | The help line under each ability in the gene tree (index from 0) |
| anything you put in `name` / `description` | Ability names and trait descriptions |

---

## Stages

Wherever a field asks for a stage, you can write it three ways:

| Word | Numeral | Number | Progress |
|---|---|---|---|
| `infection` | `I` | `1` | 1–20% |
| `manifestation` | `II` | `2` | 21–40% |
| `mutation` | `III` | `3` | 41–70% |
| `domination` | `IV` | `4` | 71–99% |
| `full` | `V` | `5` | 100% |

---

## Strain fields

| Field | Required | Default | What it is |
|---|---|---|---|
| `entity` | yes | | The mob this strain comes from. One strain per mob |
| `color` | no | `#7FAE6B` | `"#RRGGBB"` or a number. Used for the HUD, particles and, with `skin: color`, the infection |
| `scale` | no | `1.0` | Render scale in full form |
| `difficulty` | no | `1` | 1–10. Harder species take longer to sequence |
| `synthesis` | no | | `catalyst` (item, default: Mutagen's catalyst), `catalyst_count` (1–64, default 1), `time` in ticks (default 300) |
| `body` | no | | How the body changes, see below |
| `traits` | no | `[]` | Passive traits, see below |
| `abilities` | no | `[]` | Active abilities, see below. Order matters |
| `vision` | no | | Creature vision, see below |

### `body`

| Field | Default | What it is |
|---|---|---|
| `model_layer` | `<entity>#main` | Which vanilla model layer to take parts from, e.g. `"minecraft:zombie"` or `"minecraft:drowned#outer"` |
| `parts` | matched by name | Which part of the mob each player bone becomes. Each entry: `part` (path in the model, like `"head"` or `"body/tail"`), `bone` (player bone), `grow` (`true` for parts a human doesn't have, like tails), `scale` (`[x, y, z]`) |
| `absorb` | `[]` | Player bones that should melt into the torso. Bones with no matching part do this automatically |
| `spread` | right arm, body, left arm, head, legs | The order bones start changing and the skin spreads |
| `skin` | `color` | Only for the first-person hand and the older method: `texture` takes infection colours from the mob's texture (player-shaped textures only), `color` uses the strain colour |
| `morph` | `[]` | Extra posture: `bone` and `rotation` in degrees |
| `width`, `height`, `eye_height` | the mob's own | Hitbox and eye height in full form |
| `reach` | `0` | Extra reach in blocks in full form (can be negative) |
| `overlays` | `[]` | Extra layers drawn on the hybrid: `texture`, optional `model_layer`, `emissive` for glowing eyes |

Player bones are `head`, `body`, `right_arm`, `left_arm`, `right_leg`, `left_leg`. If you leave out `parts`,
bones are matched to mob parts with the same name (and arms to `arms` if that's all the mob has), which works
for every humanoid mob. Two bones may point at the same part; they'll share it. For four-legged mobs, map
the legs yourself, like this:

```json
"body": {
  "model_layer": "minecraft:wolf",
  "parts": [
    { "part": "head" }, { "part": "body" },
    { "part": "right_front_leg", "bone": "right_arm" }, { "part": "left_front_leg", "bone": "left_arm" },
    { "part": "right_hind_leg", "bone": "right_leg" }, { "part": "left_hind_leg", "bone": "left_leg" },
    { "part": "tail", "bone": "body", "grow": true }
  ]
}
```

### `vision`

| Field | Default | What it is |
|---|---|---|
| `shader` | | A post-effect chain, e.g. `"minecraft:shaders/post/creeper.json"`, or your own from a resource pack |
| `from_stage` | `manifestation` | When the vision starts. It gets stronger with progress and is full at 100% |

Players only see it if they've turned creature vision on (**N**).

---

## Traits

Every trait has `type` and `from_stage`. Most also take an optional `description` (shown in the gene tree).
The `mutagen:` prefix on the type is optional.

### Upsides

| Type | Fields | What it does |
|---|---|---|
| `attribute` | `attribute`, `amount`, `operation` (`add_value` / `add_multiplied_base` / `add_multiplied_total`) | Any attribute: health, speed, damage, armour, step height, gravity, swim speed… Negative amounts are fine |
| `effect_immunity` | `effects` | Immune to these effects |
| `damage_factor` | `damage_type` (damage type tag, e.g. `"#minecraft:is_fire"`), `factor` | Multiplies incoming damage. `0` = immune, above `1` = weakness |
| `dark_regeneration` | `amplifier`, `max_light` | Regeneration in the dark |
| `permanent_effect` | `effect`, `amplifier` | An effect that's always on |
| `dark_effect` | `effect`, `amplifier`, `max_light` | An effect that's on only in the dark |
| `water_effect` | `effect`, `amplifier` | An effect that's on only in water |
| `arrow_power` | `factor` | Your arrows hit harder |
| `mob_neutrality` | `kin` (entity tag) and/or `entities` | These mobs stop targeting you |
| `wall_climb` | | Climb walls like a spider |
| `web_walker` | | Cobwebs don't slow you |
| `thorns` | `damage` | Hurts anyone who hits you in melee |
| `on_hit_effect` | `effect`, `duration`, `amplifier`, `self_damage` | Your melee hits apply an effect; can cost you health |
| `growth_aura` | `radius`, `tries` | Crops and saplings nearby grow faster |
| `flight` | `speed`, `exhaustion`, `min_food`, `rain` | Creative-style flight. Costs hunger; stops below `min_food`, and in rain/water if `rain` is `false` |
| `aquatic_body` | | Fast, buoyant swimming, gills and clear underwater vision |

### Downsides

| Type | Fields | What it does |
|---|---|---|
| `sun_burn` | | Burns in daylight without a helmet |
| `water_damage` | `amount`, `rain` | Water (and rain) hurts |
| `dry_out` | `grace` (seconds), `amount` | Weakens, then gets hurt after too long out of water |
| `water_dependence` | | Breathes only in water; hops and sees poorly on land |
| `cat_fear` | `radius` | Panics near cats and ocelots |
| `fear` | `entities`, `radius` | Panics near these mobs |
| `homesick` | `dimension`, `effects` | Gets these effects outside its home dimension |
| `hoard` | `items` (item tag) | Can't throw these items away |
| `diet` | `foods` (item tag) | Eats only these foods |
| `no_eating` | `food_level` | Doesn't eat at all; hunger stays at `food_level` |
| `no_sleep` | | Can't sleep |
| `armor_rejection` | `slots` (default: all four) | Can't wear armour in these slots |
| `no_offhand` | | No off-hand |
| `no_tools` | | Can't hold anything in `mutagen:needs_hands` |
| `no_crafting` | `stations` (block tag), `grid` | Can't use these stations; with `grid`, not even the inventory crafting grid |
| `no_building` | | Can't place blocks (doors, chests and levers still work) |
| `inventory_limit` | `hotbar` | Only this many hotbar slots; the rest of the inventory is locked |

If a server sets `humanityPenalties` to `false`, these are all switched off: `armor_rejection`, `no_offhand`,
`no_tools`, `no_crafting`, `no_building`, `inventory_limit`, `no_sleep`, `diet` and `no_eating`.

---

## Abilities

Order matters: the first non-apex ability goes on **R**, the second on **V**, and the one with
`"apex": true` goes on **B**. An apex also needs 100% research.

Every ability has:

| Field | Default | What it is |
|---|---|---|
| `type` | | One of the types below |
| `from_stage` | | When it unlocks |
| `name` | | Shown in the HUD and gene tree |
| `cooldown` | `200` | In ticks |
| `cost` | free | `hunger` and/or `health`. Saturation goes first; health never takes the last heart |
| `apex` | `false` | The **B** ability |
| `requires` | `any` | `water`, `dry`, `airborne`, `ground`, `nether` or `dark` |

If an ability can't fire (wrong place, no target, too hungry), nothing is spent.

| Type | Fields | What it does |
|---|---|---|
| `effect_burst` | `effects`, `duration`, `amplifier` | Gives you effects |
| `leap` | `power`, `lift`, `effects`, `duration`, `amplifier` | Jumps where you look; optional effects after |
| `shockwave` | `radius`, `damage`, `knockback`, `ignite` | Hits and pushes everything around you |
| `volley` | `arrows`, `spread`, `velocity`, `damage` | Fires a fan of arrows that can't be picked up |
| `fireball` | `count`, `spread` | Fires small fireballs |
| `hex` | `range` *or* `radius`, `damage`, `effects`, `duration`, `amplifier`, `particle`, `sound` | Magic hit on the target you're looking at, or on everyone around you if `radius` is set |
| `vortex` | `radius`, `pull`, `damage`, `duration` | Pulls everyone in and slows them |
| `lunge` | `range`, `power`, `damage` | Throws you at the target; hits on contact |
| `blink` | `range`, `effects`, `duration` | Teleports to a safe spot where you're looking |
| `reveal` | `radius`, `duration` | Makes living things around you glow through walls |
| `rally` | `kin` (entity tag) and/or `entities`, `radius`, `duration` | Turns nearby mobs on whoever last hurt you. Nothing is spawned |

---

## Tips

- Copy a built-in strain and change it. They're all in the mod jar under
  `data/mutagen/mutagen/strains/`.
- Keep the deal fair: the mod's own rule is that every upside comes with a downside.
- `/mutagen set <strain> <percent>` jumps straight to any stage, and `/mutagen research <strain> 100`
  unlocks the apex, so you don't have to play through days to test.
- F3 shows exact progress numbers in the strain panel.
- If something doesn't show up, check the server log for a line starting with your strain id.
