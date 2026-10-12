# StageLock

A GameStages-style progression gating mod for NeoForge 1.21.1, built as a single mod. Stages are named
flags that a player can get from three scopes: **player**, **team** (FTB Teams or vanilla `/team`) and **global**.
Datapack rules, or KubeJS scripts, lock items, recipes, blocks, fluids, enchantments, dimensions and
entities until the player has the required stages.

The server enforces every rule. The client mirrors the same rules so nothing flickers on screen.
Recipe viewers (JEI, REI and EMI) hide locked content and update live as stages change.

## Commands

`/stage` is an alias for `/stagelock`; you can turn the alias off in `stagelock-common.toml`.

```
/stagelock grant|revoke <players> <stage> [silent]   # works on offline players too
/stagelock grantall <players>                        # every defined stage
/stagelock clear <players>
/stagelock list [player]
/stagelock check <player> <stage>
/stagelock global grant|revoke|list <stage>
/stagelock team grant|revoke|list <team> <stage>
/stagelock why <player>                              # which rules apply to the held item
/stagelock dump                                      # writes config/stagelock/dump.md
/stagelock import gamestages                         # migrates GameStages player data
```

Stage ids are 1–64 characters from `a-z 0-9 _ . : / -`, for example `iron_age` or `mypack:nether`.

## Stage definitions (optional)

Stages work without a definition. A definition adds a display name, prerequisites, automatic unlocks
and grant effects. Put definitions in `data/<ns>/stagelock/stages/<name>.json`.

```json
{
  "id": "iron_age",
  "display_name": "Iron Age",
  "icon": "minecraft:iron_ingot",
  "requires": ["stone_age"],
  "unlock": { "advancements": ["minecraft:story/smelt_iron"], "scope": "player" },
  "on_grant": { "message": true, "function": "mypack:iron_age_unlocked" }
}
```

- **Prerequisites:** `requires` lists stages that must come first. By default, granting a stage also grants its missing prerequisites. The `dependencyMode` setting changes this.
- **Typo checks:** once any definitions exist, rules that mention an undefined stage log a warning. With `strictStages` on, those rules are rejected instead.
- **Granting undefined stages:** StageLock allows granting any valid stage id, defined or not. ProgressiveStages
  refuses stages it has no definition for ("Stage does not exist"). If your pack may move between the two mods,
  define every stage you grant: from commands, functions, advancements, KubeJS and quests.

## Restrictions

Rule files live in `data/<ns>/stagelock/restrictions/` (subfolders are allowed), and `/reload` picks up
changes. A file holds one rule, or `{"rules": [...]}` for several.

**`requires`** can be:
- `"stage"`
- `["a", "b"]` — all of them
- `{"any_of": [...]}`
- `{"all_of": [...]}`
- `{"not": ...}`

**Selectors:**
- An id, a `#tag`, or an `@modid`.
- For items only, a vanilla item predicate object, for example `{"items": "minecraft:diamond_sword", "components": {"minecraft:damage": 5}}`.

| Type | What it blocks |
|---|---|
| `stagelock:item` | `pickup`, `use`, `place`, `attack`, `mine`, `equip` (Curios slots included), `hold` (`allow`/`drop`/`destroy`), `craft`, `viewer` (`hide`/`show`/`lock`). The item's name becomes `hidden_name`, and the `tooltip` field is `replace`, `append` or `none`. |
| `stagelock:recipe` | Recipes matched by `recipes` (an id or `@mod`), `outputs` (item selectors) or `recipe_types`. |
| `stagelock:block` | `break`, `interact` and `place`. With `drops: true`, the block can be broken but drops nothing. `disguise_as` makes the block look, mine and drop like another block. |
| `stagelock:fluid` | Filling or emptying buckets and other containers, and showing the fluid in recipe viewers. |
| `stagelock:enchantment` | Any item carrying the enchantment, books included unless `check_books` is false. Uses the same `actions` as items. |
| `stagelock:dimension` | Travelling there. Players who log in inside a locked dimension are moved to spawn (`evictOnLogin`). |
| `stagelock:entity` | `attack` and `interact`. `spawn: {"range": 128}` only lets the mob spawn if a player within that range has the stage. |

Every action is blocked by default; set an action to `false` to allow it.

**Example item rule:**

```json
{
  "type": "stagelock:item",
  "requires": "iron_age",
  "items": ["#c:gems/diamond", "@mekanism"],
  "except": ["mekanism:configurator"],
  "actions": { "pickup": false, "hold": "allow" },
  "hidden_name": "Shiny Rock"
}
```

**Recipe gating** works for these crafting stations:
- the crafting table and the 2×2 inventory grid
- the smithing table and the stonecutter
- campfires
- furnaces and other machines, using the stages of the player who placed them (this works while that player is offline)
- the crafter

How the gating works:
- A locked recipe falls through to any unlocked recipe that uses the same ingredients.
- The recipe book hides locked recipes.
- **Machines and grids from other mods** are covered in two ways:
  - Machines from other mods that check recipes while a player is crafting are gated by that player's stages.
  - For everything else, `contextlessPolicy` in `stagelock-server.toml` decides whether locked recipes are allowed or denied.

### Built-in example pack

The mod ships an opt-in example progression: stone age, then iron age, then the Nether. It includes
disguised diamond ore, locked lava buckets, witch spawns tied to a stage, and an advancement that uses the trigger.

```
/datapack enable "mod/stagelock:datapacks/examples"
```

## Vanilla datapack hooks

| Hook | Usage |
|---|---|
| Loot condition | `{"condition": "stagelock:has_stage", "requires": "x", "entity": "this"}` |
| Advancement trigger | `{"trigger": "stagelock:has_stage", "conditions": {"requires": "x"}}` |
| Entity predicate | `"type_specific": {"type": "stagelock:stages", "requires": "x"}` |

## KubeJS

The `player.stages` API built into KubeJS is backed by StageLock, so existing scripts and FTB Quests
stage tasks and rewards keep working. KubeJS's own `PlayerEvents.stageAdded` and `PlayerEvents.stageRemoved` still fire.

```js
// server_scripts — re-run on every /reload
StageLockEvents.restrictions(event => {
  event.item('minecraft:diamond').requires('iron_age').hideName('Shiny Rock')
  event.item('@mekanism').except('mekanism:configurator').requires({any_of: ['tech', 'magic']}).only('use', 'place')
  event.recipe('minecraft:iron_pickaxe').requires('iron_age')
  event.recipeType('minecraft:blasting').requires('iron_age')
  event.block('#c:ores/uranium').requires('nuclear').disguiseAs('minecraft:stone')
  event.dimension('minecraft:the_nether').requires('nether').message('Too hot!')
  event.entity('minecraft:wither').requires('endgame').preventSpawn(128)
  event.add({type: 'stagelock:fluid', requires: 'iron_age', fluids: '#minecraft:lava'})
})

StageLockEvents.stages(event => {
  event.create('iron_age').displayName('Iron Age').requires('stone_age').unlockOnAdvancement('minecraft:story/smelt_iron')
})

StageLockEvents.granted('iron_age', event => event.player.tell('Welcome to the Iron Age'))

if (StageLock.has(player, 'iron_age')) { /* ... */ }
StageLock.grant(player, 'iron_age'); StageLock.grantGlobal('blood_moon'); StageLock.grantTeam(player, 'outpost')
```

## Other integrations

| Mod | What StageLock adds |
|---|---|
| JEI, REI, EMI | Locked items, fluids and recipes are hidden, and they come back when the stage is unlocked. |
| Jade | Disguised blocks show up as their disguise, and locked blocks show the stages they require. |
| Curios | Locked items can't go into curio slots, and are taken out of them when a stage is revoked. |
| FTB Teams | Party teams are used for team-scoped stages. |

A padlock icon marks locked items in every inventory, and also in recipe viewers when a rule uses `viewer: show` or `viewer: lock`.

## Config

- **`stagelock-server.toml`**
  - Bypasses for creative mode, spectator mode and operators.
  - Which team provider to use.
  - `dependencyMode` and `strictStages`.
  - What happens when mining with a locked tool: bare-hand speed, or no mining at all.
  - Recipe automation policies.
  - The message cooldown.
  - Stages for fake players.
- **`stagelock-client.toml`**: hidden names, requirement lines in tooltips, and the padlock icon.

## For mod developers

```java
StageLockAPI.has(player, "iron_age");                 // client and server
StageLockAPI.grant(serverPlayer, "iron_age");         // or grant(uuid, stages) for offline players
StageLockAPI.global().grant("blood_moon");
StageLockAPI.registerStageSource(...);                 // add stages from your own mod
StageLockAPI.registerTeamProvider("mymod", ...);
RestrictionTypes.register(new RestrictionType<>(id, codec)); // your own rule types
```

**Events** (on `NeoForge.EVENT_BUS`):
- `StageEvent.Grant` and `StageEvent.Revoke` can be cancelled. `StageEvent.Granted` and `StageEvent.Revoked` fire afterwards.
- `StagesChangedEvent` fires on both sides, batched to once per player per tick.
- `RestrictionsReloadedEvent` fires when the rules change.
- `CollectRulesEvent.Restrictions` and `CollectRulesEvent.StageDefinitions` let you add rules from code.

## Development

```bash
./gradlew test                                  # unit tests
./gradlew runGameTestServer                     # in-world GameTests
./gradlew runGameTestServer -Pdev_kubejs=true   # same tests, plus the KubeJS script test (run/kubejs/server_scripts)
./gradlew runClient -Pdev_jei=true              # also: dev_emi, dev_rei, dev_kubejs
```
