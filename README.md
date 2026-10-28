# StageLock

> This is the **advanced README**: the complete reference for commands, datapack JSON, KubeJS, configuration and the Java API.
> New to StageLock or to scripting? Start with the **[supplemental README](README-SUPPLEMENTAL.md)**: 44 copy-paste examples with plain explanations.

Stage-based progression gating for **NeoForge 1.21.1**. It is a modern alternative to GameStages: one mod,
configured with datapack JSON or KubeJS.

Stages are named milestones such as `iron_age` or `nether`. A player can have them in three ways:
personally, through their team (FTB Teams or vanilla `/team`), or because the stage is global for the server.

Rules lock content until the player has the required stages:
- items
- recipes
- blocks
- fluids
- enchantments
- dimensions
- mobs

The server enforces every rule. The client mirrors the same rules, so nothing flickers on screen. JEI, REI
and EMI hide locked content and bring it back as soon as it unlocks.

---

## Contents

1. [Quick start](#quick-start)
2. [Core concepts](#core-concepts)
3. [Commands](#commands)
4. [Stage definitions](#stage-definitions)
5. [Restrictions (datapack JSON)](#restrictions-datapack-json)
6. [KubeJS](#kubejs)
7. [Vanilla datapack hooks](#vanilla-datapack-hooks)
8. [Integrations](#integrations)
9. [Configuration](#configuration)
10. [Troubleshooting](#troubleshooting)
11. [Coming from GameStages](#coming-from-gamestages)
12. [Java API](#java-api-for-mod-developers)
13. [Development](#development)

---

## Quick start

**With a datapack.** Save this as `data/mypack/stagelock/restrictions/diamonds.json`, either in a datapack or in
KubeJS's `kubejs/data/` folder:

```json
{
  "type": "stagelock:item",
  "requires": "iron_age",
  "items": ["#c:gems/diamond", "minecraft:diamond_pickaxe"],
  "hidden_name": "Shiny Rock"
}
```

**Or with KubeJS.** Put this in `kubejs/server_scripts/stages.js`:

```js
StageLockEvents.restrictions(event => {
  event.item(['#c:gems/diamond', 'minecraft:diamond_pickaxe']).requires('iron_age').hideName('Shiny Rock')
})
```

Then:

1. Run `/reload`.
2. Switch to survival mode. Creative players bypass every rule by default.
3. Diamonds now show as "Shiny Rock", can't be picked up or used, and are dropped from your inventory.
4. Run `/stagelock grant @s iron_age` and they unlock immediately.

To see a complete progression, enable the example pack:
`/datapack enable "mod/stagelock:datapacks/examples"`.

---

## Core concepts

### Stage ids

A stage id is 1–64 characters from `a-z 0-9 _ . : / -`, for example `iron_age` or `mypack:nether`. Ids are
lowercased automatically.

### Scopes

A player's **effective stages** combine all four sources below. Every rule checks the effective stages.

| Scope | Stored on | Granted with |
|---|---|---|
| player | the player (works while they are offline) | `/stagelock grant`, `StageLock.grant(player, …)`, `player.stages.add(…)` |
| team | the team: an FTB Teams party, or a vanilla `/team` team | `/stagelock team grant`, `StageLock.grantTeam(player, …)` |
| global | the server | `/stagelock global grant`, `StageLock.grantGlobal(…)` |
| contributed | other mods, via `StageLockAPI.registerStageSource` | (read-only) |

### Requirement expressions (`requires`)

Every rule has a `requires` field. You write it the same way in JSON and in KubeJS:

| Form | Meaning |
|---|---|
| `"iron_age"` | has `iron_age` |
| `["a", "b"]` | has all of them; `[]` always passes |
| `{"all_of": [ … ]}` | has all of them |
| `{"any_of": [ … ]}` | has at least one |
| `{"not": <expr>}` | the opposite of the expression |

These can be nested. For example, `{"any_of": [["a", "b"], "c"]}` means "a and b, or c".

A rule is **locked** while the player does not meet its `requires`. Once they meet it, the rule does nothing.

### Selectors

Rules pick their targets with selectors:

| Form | Matches | Used by |
|---|---|---|
| `"minecraft:diamond"` | one entry | items, blocks, fluids, entities, enchantments |
| `"#c:gems"` | a tag | items, blocks, fluids, entities, enchantments |
| `"@mekanism"` | everything from one mod | items, blocks, fluids, entities, enchantments, recipe ids |
| `{"items": "minecraft:diamond_sword", "components": {"minecraft:damage": 5}}` | a vanilla item predicate, which can match components | items only |

A field that takes selectors accepts either one selector or a list of them.

### When several rules match

Only locked rules count. Among those, the strictest outcome wins, no matter which datapack or script each rule came from:

- **Yes/no actions:** this covers `pickup`, `use`, `break`, `attack`, entering a dimension, crafting a recipe, and so on. Any locked rule that blocks the action blocks it.
- **`hold`:** `destroy` beats `drop`, and `drop` beats `allow`. A rule that allows holding never cancels another rule that drops the item.
- **`viewer`:** `hide` beats `lock`, and `lock` beats `show`.
- **Messages:** they come from the rule that decided the action. The tooltip and hidden name come from the strictest locked rule.
- **`disguise_as`:** if two locked rules disguise the same block, the first loaded rule wins.

`/stagelock why <player>` lists every rule that matches the held item, and shows which one decides each action.

### Who is exempt

Exempt players ignore every rule. By default that means players in creative or spectator mode. You can also exempt
operators with `opBypassLevel` (see [Configuration](#configuration)). Machines and fake players have their own rules, covered in
[recipes](#stagelockrecipe) and [configuration](#configuration).

---

## Commands

Every command also works as `/stage`. You can turn that alias off in `stagelock-common.toml`.

| Command | Permission | What it does |
|---|---|---|
| `/stagelock grant <players> <stage> [silent]` | 2 | Grants a stage. Works on offline players named in the profile cache. `silent` skips the chat message. |
| `/stagelock revoke <players> <stage> [silent]` | 2 | Revokes a stage. |
| `/stagelock grantall <players>` | 2 | Grants every defined stage. |
| `/stagelock clear <players>` | 2 | Removes all player-scope stages. |
| `/stagelock list` | 0 | Your own stages: player scope and effective. |
| `/stagelock list <player>` | 2 | Someone else's stages. |
| `/stagelock check <player> <stage>` | 2 | Whether the player has the stage. |
| `/stagelock global grant\|revoke <stage>` | 2 | Server-wide stages. |
| `/stagelock global list` | 2 | Lists server-wide stages. |
| `/stagelock team grant\|revoke <team> <stage>` | 2 | Team stages. `<team>` is a vanilla team name or an FTB Teams party name. |
| `/stagelock team list <team>` | 2 | Lists a team's stages. |
| `/stagelock why <player>` | 2 | Every rule matching the held item, whether each is locked, and which one decides each action. |
| `/stagelock dump` | 2 | Writes every stage definition and rule, from JSON and scripts alike, to `config/stagelock/dump.md`. |
| `/stagelock import gamestages` | 3 | Copies GameStages player data (`playerdata/gamestages/*.dat`) into StageLock. |

---

## Stage definitions

Definitions are **optional**: any valid stage id works without one. A definition adds a display name for
"Requires: …" tooltips, prerequisites, automatic unlocks and grant effects.

Definitions live in `data/<ns>/stagelock/stages/<path>.json`. The stage id is `<ns>:<path>` unless the file sets `"id"`.

```json
{
  "id": "iron_age",
  "display_name": "Iron Age",
  "description": "Smelt your first iron ingot.",
  "icon": "minecraft:iron_ingot",
  "requires": ["stone_age"],
  "unlock": { "advancements": ["minecraft:story/smelt_iron"], "scope": "player" },
  "on_grant": { "message": true, "function": "mypack:iron_age_unlocked" }
}
```

| Field | Type | Default | Meaning |
|---|---|---|---|
| `id` | stage id | `<ns>:<path>` | The stage this file defines. |
| `display_name` | text component or string | the id | Shown in tooltips, messages and `/stagelock dump`. |
| `description` | text component or string | none | A free-form description. |
| `icon` | item id | none | An icon for UIs. An item from a mod that isn't installed is ignored; it doesn't break the definition. |
| `requires` | list of stage ids | `[]` | Prerequisites. See `dependencyMode` below. |
| `unlock.advancements` | list of advancement ids | `[]` | Earning any one of them grants the stage. |
| `unlock.scope` | `player` / `team` / `global` | `player` | Where an advancement unlock grants the stage. `team` falls back to the player if they have no team. |
| `on_grant.message` | boolean | `false` | Sends "Unlocked: <name>" to every affected online player. |
| `on_grant.function` | function id | none | Runs this function as each affected player, at permission level 2. |

**Prerequisites** (`dependencyMode` in `stagelock-server.toml`):
- `GRANT_PARENTS` (the default) also grants any missing prerequisites, all the way up the chain.
- `REFUSE` skips the grant.
- `IGNORE` grants it anyway.

**Typo checks:** once any definitions exist, a rule that mentions an undefined stage logs a warning. With
`strictStages = true`, that rule is rejected instead.

**Moving to or from ProgressiveStages:** StageLock lets you grant stages that have no definition.
ProgressiveStages refuses them ("Stage does not exist"). If your pack might switch between the two mods, define every stage
that anything grants.

---

## Restrictions (datapack JSON)

Rule files live in `data/<ns>/stagelock/restrictions/**/*.json`. Subfolders are fine, KubeJS's `kubejs/data/`
works too, and `/reload` applies changes.

A file holds one rule, or several as `{"rules": [ {…}, {…} ]}`. A rule's id is `<ns>:<path>`, with `/0`, `/1`, …
added for each entry in a `rules` list.

Every rule has `type` and `requires`. Inside `actions`, **everything is blocked unless you set it to `false`**
(or `"allow"`/`"show"`). `message` replaces the default action-bar text.

### `stagelock:item`

```json
{
  "type": "stagelock:item",
  "requires": "iron_age",
  "items": ["#c:gems/diamond", "@mekanism"],
  "except": ["mekanism:configurator"],
  "actions": { "pickup": false, "hold": "allow", "viewer": "show" },
  "hide_name": true,
  "hidden_name": "Shiny Rock",
  "tooltip": "append",
  "message": "The gem slips from your fingers."
}
```

| Field | Type | Default | Meaning |
|---|---|---|---|
| `items` | item selectors | *(required)* | What the rule covers. |
| `except` | item selectors | `[]` | Exceptions to `items`. |
| `actions` | object | everything blocked | See the next table. |
| `hide_name` | boolean | `true` | Show locked items under a hidden name: in tooltips, the hotbar, item frames and Jade. |
| `hidden_name` | text | "Unfamiliar Item" | The name to show instead. |
| `tooltip` | `replace` / `append` / `none` | `replace` | `replace` keeps only the name and the "Locked / Requires" lines. `append` keeps the normal tooltip and adds them. `none` leaves the tooltip alone. |
| `message` | text | per-action default | The action-bar message shown when something is blocked. |

**Item actions:**

| Action | Values | Default | Blocks |
|---|---|---|---|
| `pickup` | boolean | `true` | Picking the item up from the ground. If `hold` isn't `allow`, pickup is blocked too, whatever this says. |
| `use` | boolean | `true` | Right-click on air, blocks and entities, plus eating, drinking, bows and shields. |
| `place` | boolean | `true` | Placing the item as a block. |
| `attack` | boolean | `true` | Hitting entities while holding it. |
| `mine` | boolean | `true` | Mining with it. The `restrictedToolMining` setting decides whether that means bare-hand speed or no mining at all. |
| `equip` | boolean | `true` | Wearing it in armor slots or Curios slots. Worn items are taken off. |
| `hold` | `allow` / `drop` / `destroy` | `drop` | Keeping it in the inventory: `drop` throws it out, `destroy` deletes it. |
| `craft` | boolean | `true` | Crafting it. Every recipe that outputs it is locked. |
| `viewer` | `hide` / `show` / `lock` | `hide` | `hide` removes it from JEI, REI and EMI. `show` and `lock` keep it visible with a padlock icon. |

### `stagelock:recipe`

```json
{
  "type": "stagelock:recipe",
  "requires": "stone_age",
  "recipes": ["minecraft:iron_pickaxe", "@create"],
  "outputs": ["minecraft:iron_axe", "#c:ingots/steel"],
  "recipe_types": ["minecraft:blasting"],
  "viewer": "hide"
}
```

| Field | Type | Default | Meaning |
|---|---|---|---|
| `recipes` | recipe ids or `@modid` | `[]` | Lock these recipes. |
| `outputs` | item selectors | `[]` | Lock recipes whose result matches. |
| `recipe_types` | recipe type ids | `[]` | Lock every recipe of these types. |
| `viewer` | `hide` / `show` / `lock` | `hide` | Whether locked recipes are hidden in JEI, REI and EMI. |

A recipe is locked if it matches **any** of `recipes`, `outputs` or `recipe_types`, and you need at least one of them.
Locked recipes fall through to any unlocked recipe with the same ingredients. The recipe book hides them.

**Where recipe locks apply:**
- the crafting table and the 2×2 inventory grid;
- the smithing table and the stonecutter;
- campfires;
- the crafter;
- furnaces, smokers and blast furnaces.

Machines use the stages of **the player who placed them**, even while that player is offline. Machines
placed before StageLock was installed are claimed by the first player who right-clicks them.

**Machines and grids from other mods:**
- Lookups made while a player takes the result are gated by that player's stages.
- Everything else follows `contextlessPolicy`: `ALLOW` (the default) or `DENY`.

### `stagelock:block`

```json
{
  "type": "stagelock:block",
  "requires": "iron_age",
  "blocks": ["#c:ores/diamond"],
  "actions": { "break": false, "interact": false, "place": false },
  "disguise_as": "minecraft:stone"
}
```

| Field | Type | Default | Meaning |
|---|---|---|---|
| `blocks` | block selectors | *(required)* | What the rule covers. |
| `except` | block selectors | `[]` | Exceptions to `blocks`. |
| `actions.break` | boolean | `true` | Stops the player breaking the block. |
| `actions.interact` | boolean | `true` | Stops the player right-clicking the block (opening, using). |
| `actions.place` | boolean | `true` | Stops the player placing the block. |
| `actions.drops` | boolean | `false` | Lets the block be broken but drops nothing, not even XP. |
| `disguise_as` | block id | none | Locked players see this block instead, mine it at that block's speed and get its drops. Breaking is always allowed. |
| `message` | text | default | The action-bar message. |

### `stagelock:fluid`

```json
{ "type": "stagelock:fluid", "requires": "iron_age", "fluids": ["#minecraft:lava"],
  "actions": { "pickup": true, "place": true, "viewer": "hide" } }
```

| Field | Type | Default | Meaning |
|---|---|---|---|
| `fluids` | fluid selectors | *(required)* | What the rule covers. |
| `actions.pickup` | boolean | `true` | Filling a bucket or other container from a source block. |
| `actions.place` | boolean | `true` | Emptying a container of the fluid. |
| `actions.viewer` | `hide` / `show` / `lock` | `hide` | Whether the fluid shows in recipe viewers. |
| `message` | text | default | The action-bar message. |

### `stagelock:enchantment`

```json
{ "type": "stagelock:enchantment", "requires": "nether", "enchantments": ["minecraft:mending"],
  "check_books": true, "actions": { "hold": "allow", "pickup": false, "viewer": "show" } }
```

Any item carrying a matching enchantment is treated as a locked item.

| Field | Type | Default | Meaning |
|---|---|---|---|
| `enchantments` | enchantment selectors | *(required)* | What the rule covers. |
| `check_books` | boolean | `true` | Whether enchanted books count too. |
| `actions` | item actions | everything blocked | The same table as [`stagelock:item`](#stagelockitem). |
| `message` | text | default | The action-bar message. |

Item names stay visible, and the lock lines are added to the normal tooltip.

### `stagelock:dimension`

```json
{ "type": "stagelock:dimension", "requires": "nether", "dimensions": ["minecraft:the_nether"], "message": "Not yet." }
```

| Field | Type | Default | Meaning |
|---|---|---|---|
| `dimensions` | dimension id, or a list of them | *(required)* | Players, and vehicles carrying players, can't travel there. |
| `message` | text | default | The action-bar message. |

With `evictOnLogin` (on by default), a player who logs in inside a locked dimension is moved to world spawn.

### `stagelock:entity`

```json
{ "type": "stagelock:entity", "requires": "iron_age", "entities": ["minecraft:witch", "#minecraft:raiders"],
  "actions": { "attack": true, "interact": true, "spawn": { "range": 128, "allow_spawners": true } } }
```

| Field | Type | Default | Meaning |
|---|---|---|---|
| `entities` | entity type selectors | *(required)* | What the rule covers. |
| `actions.attack` | boolean | `true` | Stops the player attacking it. |
| `actions.interact` | boolean | `true` | Stops the player right-clicking it: trading, riding, leashing. |
| `actions.spawn` | object | none | If present, the mob only spawns when a player within `range` meets `requires`. |
| `actions.spawn.range` | 1–1024 | `128` | The radius, in blocks, to look for a qualifying player. |
| `actions.spawn.allow_spawners` | boolean | `true` | Whether spawners and trial spawners ignore the rule. |
| `message` | text | default | The action-bar message. |

Spawns from commands, spawn eggs, buckets, dispensers and conversions are never gated. Bosses that are built rather than spawned (the Wither from skulls, the Iron Golem from blocks) don't go through spawning either; lock the item that builds them instead.

---

## KubeJS

StageLock works with KubeJS 2101 (Minecraft 1.21.1):
- **Rules from scripts:** scripts can build the same rules and stage definitions as JSON. Script rules get ids like `kubejs:rule_0`.
- **Grant/revoke events:** scripts can react when a stage is granted or revoked.
- **`StageLock` helper:** a global object for reading and changing stages.
- **Built-in stage API:** StageLock is the backend for KubeJS's own `player.stages`.

All of this belongs in **`kubejs/server_scripts/`**. `StageLockEvents.restrictions` and `StageLockEvents.stages`
run again on every `/reload`.

### Event overview

| Event | Fires | Event object |
|---|---|---|
| `StageLockEvents.restrictions(e => …)` | on load and `/reload`, after the JSON rules are read | [restrictions event](#stagelockeventsrestrictions) |
| `StageLockEvents.stages(e => …)` | on load and `/reload`, before restrictions | [stages event](#stagelockeventsstages) |
| `StageLockEvents.granted('<stage>', e => …)` | after a stage is granted in any scope | [stage change event](#stagelockeventsgranted--revoked) |
| `StageLockEvents.revoked('<stage>', e => …)` | after a stage is revoked in any scope | [stage change event](#stagelockeventsgranted--revoked) |
| `PlayerEvents.stageAdded('<stage>', e => …)` | KubeJS's own event; still fires | KubeJS `StageChangedEvent` |
| `PlayerEvents.stageRemoved('<stage>', e => …)` | KubeJS's own event; still fires | KubeJS `StageChangedEvent` |

Leave out the stage argument on `granted`/`revoked` to listen for every stage.

### `StageLockEvents.restrictions`

```js
StageLockEvents.restrictions(event => {
  event.item('minecraft:diamond').requires('iron_age').hideName('Shiny Rock')
  event.item(['@mekanism']).except('mekanism:configurator').requires({ any_of: ['tech', 'magic'] }).only('use', 'place')
  event.item('#c:ingots/steel').requires('steel').allow('pickup', 'hold').tooltip('append').showName()

  event.recipe('minecraft:iron_pickaxe').requires('stone_age')
  event.recipeOutput('#c:ingots/steel').requires('steel')
  event.recipeType('minecraft:blasting').requires('iron_age').showInViewers()

  event.block('#c:ores/uranium').requires('nuclear').only().disguiseAs('minecraft:stone')
  event.dimension('minecraft:the_nether').requires('nether').message('Too hot!')
  event.entity('minecraft:enderman').requires('endgame').preventSpawn(128)

  // Anything the builders don't cover: write the datapack JSON directly.
  event.add({ type: 'stagelock:fluid', requires: 'iron_age', fluids: '#minecraft:lava' })
  event.add({ type: 'stagelock:enchantment', requires: 'nether', enchantments: 'minecraft:mending',
              actions: { hold: 'allow', viewer: 'show' } })
})
```

**Event methods.** Each returns a builder you can chain.

| Method | Argument | Returns | Equivalent JSON |
|---|---|---|---|
| `item(items)` | item selector(s): string or array | [ItemRule](#itemrule) | `stagelock:item` with `items` |
| `recipe(ids)` | recipe id(s) or `@modid` | [RecipeRule](#reciperule) | `stagelock:recipe` with `recipes` |
| `recipeOutput(items)` | item selector(s) | [RecipeRule](#reciperule) | `stagelock:recipe` with `outputs` |
| `recipeType(types)` | recipe type id(s) | [RecipeRule](#reciperule) | `stagelock:recipe` with `recipe_types` |
| `block(blocks)` | block selector(s) | [BlockRule](#blockrule) | `stagelock:block` |
| `dimension(dims)` | dimension id(s) | [DimensionRule](#dimensionrule) | `stagelock:dimension` |
| `entity(entities)` | entity selector(s) | [EntityRule](#entityrule) | `stagelock:entity` |
| `add(json)` | a JS object written exactly like a rule file | — | any type, including `stagelock:fluid` and `stagelock:enchantment` |

Every builder **must** call `.requires(...)`; a rule without it is an error.

**Methods on every builder:**

| Method | Argument | Meaning |
|---|---|---|
| `requires(req)` | `'stage'`, `['a','b']`, `{any_of: […]}`, `{all_of: […]}`, `{not: …}` | The requirement. **Required.** |
| `message(text)` | string | The custom action-bar message. Recipe rules ignore it. |

#### ItemRule

Every action starts out blocked. Narrow that with `only(...)`, or open some up with `allow(...)`. The action names
are `pickup`, `use`, `place`, `attack`, `mine`, `equip`, `hold`, `craft` and `viewer`.

| Method | Argument | Default | Meaning |
|---|---|---|---|
| `except(items)` | item selector(s) | none | Exceptions. |
| `only(...actions)` | action names | all blocked | Block only these actions. `only()` with no arguments blocks nothing. |
| `allow(...actions)` | action names | — | Stop blocking these actions. |
| `hold(mode)` | `'allow'` / `'drop'` / `'destroy'` | `'drop'` | What happens to the item in the inventory. Applies only while `hold` is blocked. |
| `hideName(name)` | string | hidden as "Unfamiliar Item" | Use a custom hidden name. |
| `showName()` | — | — | Keep the real name visible. |
| `tooltip(mode)` | `'replace'` / `'append'` / `'none'` | `'replace'` | The tooltip mode. |

`viewer` is `hide` while blocked and `show` once allowed. For `lock`, use `event.add({...})`.

#### RecipeRule

| Method | Meaning |
|---|---|
| `showInViewers()` | Keep locked recipes visible in JEI, REI and EMI (`viewer: show`). Without it they're hidden. |

Each builder sets one kind of selector. To combine several, use `event.add({type: 'stagelock:recipe', recipes: …, outputs: …})`.

#### BlockRule

The action names are `break`, `interact`, `place` and `drops`. `break`, `interact` and `place` start out blocked.

| Method | Argument | Meaning |
|---|---|---|
| `except(blocks)` | block selector(s) | Exceptions. |
| `only(...actions)` | action names | Block only these. `only('drops')` means "can break, gets nothing". |
| `allow(...actions)` | action names | Stop blocking these. |
| `disguiseAs(block)` | block id | Locked players see, mine and get drops from this block instead. |

#### DimensionRule

It has only the shared methods, `requires` and `message`.

#### EntityRule

| Method | Argument | Meaning |
|---|---|---|
| `allowAttack()` | — | Don't block attacking. |
| `allowInteract()` | — | Don't block interacting. |
| `preventSpawn(range)` | 1–1024 | Spawns only near a qualifying player. Spawners are still allowed. |
| `preventSpawn(range, allowSpawners)` | number, boolean | The same, with control over spawners. |

### `StageLockEvents.stages`

```js
StageLockEvents.stages(event => {
  event.create('stone_age').displayName('Stone Age').icon('minecraft:stone_pickaxe')
       .unlockOnAdvancement('minecraft:story/upgrade_tools').announce()
  event.create('iron_age').displayName('Iron Age').requires('stone_age')
       .unlockOnAdvancement('minecraft:story/smelt_iron').runFunction('mypack:iron_age')
  event.create('outpost').displayName('Outpost Built').scope('team')
       .unlockOnAdvancement('mypack:build_outpost')
})
```

| Method | Argument | JSON field | Meaning |
|---|---|---|---|
| `create(id)` | stage id | `id` | Starts, or continues, the definition for this stage. Returns a StageBuilder. |
| `displayName(text)` | string | `display_name` | The name shown in tooltips and messages. |
| `description(text)` | string | `description` | A free-form description. |
| `icon(item)` | item id | `icon` | An icon item. |
| `requires(...stages)` | stage ids | `requires` | Prerequisites. |
| `unlockOnAdvancement(id)` | advancement id | `unlock.advancements` | Earning it grants the stage. Call it again to add more advancements. |
| `scope(s)` | `'player'` / `'team'` / `'global'` | `unlock.scope` | Where advancement unlocks grant the stage. |
| `announce()` | — | `on_grant.message: true` | An "Unlocked: …" chat message. |
| `runFunction(id)` | function id | `on_grant.function` | Runs a function as each affected player. |

### `StageLockEvents.granted` / `revoked`

```js
StageLockEvents.granted('iron_age', event => {
  if (event.player) event.player.tell('Welcome to the Iron Age!')
})

StageLockEvents.granted(event => console.info(`${event.stage} granted (${event.scope}: ${event.target})`))
```

| Property | Type | Meaning |
|---|---|---|
| `event.player` | ServerPlayer **or null** | Each affected online player. The event fires once per affected player. For team or global changes with nobody online, it fires once with `null`. |
| `event.stage` | string | The stage that changed. |
| `event.scope` | `'player'` / `'team'` / `'global'` | Which scope changed. |
| `event.target` | string | The player's UUID, the team key (`vanilla:<name>` or `ftbteams:<uuid>`), or `'global'`. |
| `event.playerStages` | set of strings | `event.player`'s effective stages after the change; empty when `player` is null. |
| `event.server` | MinecraftServer | The server. |

### The `StageLock` global

It works in any server script.

| Method | Returns | Meaning |
|---|---|---|
| `StageLock.has(player, stage)` | boolean | Whether the player has the stage, counting all scopes. |
| `StageLock.test(player, requirement)` | boolean | Tests a requirement expression, for example `{any_of: ['a','b']}`. |
| `StageLock.stages(player)` | set of strings | Every effective stage. |
| `StageLock.grant(player, stage)` | boolean | Grants a player-scope stage. Returns `false` if the player already had it. |
| `StageLock.revoke(player, stage)` | boolean | Revokes a player-scope stage. |
| `StageLock.clear(player)` | — | Removes all player-scope stages. |
| `StageLock.grantGlobal(stage)` | boolean | Grants a global stage. |
| `StageLock.revokeGlobal(stage)` | boolean | Revokes a global stage. |
| `StageLock.hasGlobal(stage)` | boolean | Whether the global stage is set. |
| `StageLock.grantTeam(player, stage)` | boolean | Grants to the player's team. Returns `false` if they have no team. |
| `StageLock.revokeTeam(player, stage)` | boolean | Revokes from the player's team. |
| `StageLock.isLocked(player, item)` | boolean | Whether any rule locks this item for the player. |
| `StageLock.isLocked(player, item, action)` | boolean | The same, for one action: `'pickup'`, `'use'`, `'place'`, `'attack'`, `'mine'`, `'equip'`, `'hold'`, `'craft'` or `'viewer'`. |

### KubeJS's built-in `player.stages`

KubeJS's stage API reads and writes StageLock data. Old scripts and mods built on KubeJS stages keep
working, such as FTB Quests stage tasks and rewards through FTB XMod Compat.

| Call | Meaning |
|---|---|
| `player.stages.has(stage)` | The same as `StageLock.has`. |
| `player.stages.add(stage)` | Grants a player-scope stage. |
| `player.stages.remove(stage)` | Revokes a player-scope stage. |
| `player.stages.clear()` | Removes all player-scope stages. |
| `player.stages.all` | Every effective stage. |
| `player.stages.sync()` | Forces a resync to the client. You rarely need this. |

### Cookbook

```js
// Grant a stage the first time someone crafts an item
ItemEvents.crafted('minecraft:iron_pickaxe', event => StageLock.grant(event.player, 'iron_age'))

// Server-wide stage after the dragon dies
EntityEvents.death('minecraft:ender_dragon', event => StageLock.grantGlobal('post_dragon'))

// Give every new player a starting stage
PlayerEvents.loggedIn(event => StageLock.grant(event.player, 'stone_age'))

// Reward on unlock
StageLockEvents.granted('nether', event => {
  if (event.player) event.player.give('minecraft:fire_charge')
})

// Gate a custom right-click behaviour behind a stage
ItemEvents.rightClicked('minecraft:blaze_rod', event => {
  if (!StageLock.has(event.player, 'nether')) {
    event.player.setStatusMessage('You sense nothing yet.')
    event.cancel()
  }
})
```

---

## Vanilla datapack hooks

| Hook | JSON | Notes |
|---|---|---|
| Loot condition | `{"condition": "stagelock:has_stage", "requires": "nether", "entity": "this"}` | `entity`: `this`, `attacker`, `direct_attacker` or `attacking_player`. Gates chest loot, mob drops and block drops. |
| Advancement trigger | `{"trigger": "stagelock:has_stage", "conditions": {"requires": "nether"}}` | Checked on login and whenever stages change. |
| Entity predicate | `"type_specific": {"type": "stagelock:stages", "requires": "nether"}` | Works anywhere a vanilla entity predicate does, for example `/execute if predicate`. |

---

## Integrations

All are optional.

| Mod | What StageLock adds |
|---|---|
| KubeJS | Everything in the [KubeJS](#kubejs) section above. |
| JEI, REI, EMI | Locked items, fluids and recipes with `viewer: hide` are removed, and come back the moment they unlock. Packs whose rules are all `show` never trigger a viewer refresh. |
| FTB Teams | Party teams are used for team-scope stages. |
| FTB Quests | Stage tasks and rewards, through KubeJS stages (FTB XMod Compat). |
| Curios | Locked items can't be equipped in curio slots, and are taken out when a stage is revoked. |
| Jade | Disguised blocks show as their disguise, and locked blocks show what they require. |

A padlock icon marks locked items everywhere items render.

---

## Configuration

### `stagelock-server.toml` (per world)

| Section | Key | Default | Meaning |
|---|---|---|---|
| `bypass` | `creativeBypass` | `true` | Creative players ignore every rule. |
| `bypass` | `spectatorBypass` | `true` | Spectators ignore every rule. |
| `bypass` | `opBypassLevel` | `0` | Players at this permission level or above ignore every rule. `0` turns this off. |
| `stages` | `teamProvider` | `AUTO` | `AUTO` (FTB Teams if installed, otherwise vanilla), `FTBTEAMS`, `VANILLA` or `NONE`. |
| `stages` | `fakePlayers` | `[]` | Stages for machines and fake players, written `"<name or uuid>=stage1,stage2"`. Use `*` for every other fake player. |
| `stages` | `dependencyMode` | `GRANT_PARENTS` | What granting a stage does about missing prerequisites: `GRANT_PARENTS`, `REFUSE` or `IGNORE`. |
| `stages` | `strictStages` | `false` | Reject rules that use undefined stages (only once definitions exist). |
| `items` | `restrictedToolMining` | `BARE_HAND` | Mining with a locked tool: `BARE_HAND` (hand speed) or `DENY` (no mining). |
| `items` | `inventoryRescanTicks` | `20` | Inventories are rechecked whenever they change, and also fully this often. That catches `/give` and mods that write slots directly. `0` turns the periodic check off. |
| `world` | `evictOnLogin` | `true` | Move players who log in inside a locked dimension to spawn. |
| `recipes` | `automationPolicy` | `OWNER` | Machines with no player present: `OWNER` (the placer's stages), `ALLOW` or `DENY`. |
| `recipes` | `unownedPolicy` | `ALLOW` | With `OWNER`: machines that have no owner. |
| `recipes` | `contextlessPolicy` | `ALLOW` | Recipe lookups from other mods with no player or owner: `ALLOW` or `DENY`. |
| `messages` | `messageCooldownTicks` | `60` | The minimum time between repeated "you can't do that" messages. |

### `stagelock-client.toml`

| Key | Default | Meaning |
|---|---|---|
| `hideNames` | `true` | Show locked items under their hidden name. |
| `showRequirementsInTooltip` | `true` | Add the "Requires: …" line. |
| `showLockIcon` | `true` | Draw the padlock on locked items. |

### `stagelock-common.toml`

| Key | Default | Meaning |
|---|---|---|
| `registerStageAlias` | `true` | Register `/stage` as an alias of `/stagelock`. |

---

## Troubleshooting

| Symptom | Check |
|---|---|
| A rule does nothing | Are you in creative mode? Test in survival. Then run `/stagelock why @s` while holding the item. |
| A rule isn't loaded | The log shows `Loaded N restriction rules` and `Skipping restriction <id>: <reason>`. Run `/stagelock dump` to see everything that's active. |
| A typo in a stage name | With definitions present, the log warns `Restriction <id> uses undefined stages [...]`. |
| A definition's id isn't what you expected | Without `"id"`, a file at `data/mypack/stagelock/stages/iron_age.json` defines `mypack:iron_age`, not `iron_age`. |
| An item obtained a different way bypasses its lock | Lock the recipe as well (`craft` or `stagelock:recipe`), and gate loot with the `stagelock:has_stage` loot condition. |
| Automation still crafts locked recipes | Machines use their owner's stages. Other mods' machines without one follow `contextlessPolicy`. |

---

## Coming from GameStages

| GameStages | StageLock |
|---|---|
| `/gamestage add <player> <stage>` | `/stagelock grant <player> <stage>` |
| ItemStages `restrict(...)` | a `stagelock:item` rule, or `event.item(...)` |
| RecipeStages | `stagelock:recipe`, or `craft` on an item rule |
| DimensionStages | `stagelock:dimension` |
| MobStages | `stagelock:entity` with `spawn` |
| OreStages | `stagelock:block` with `disguise_as` |
| `known_stages.json` | stage definitions, plus `strictStages` |
| Existing player data | `/stagelock import gamestages` |

---

## Java API (for mod developers)

```java
StageLockAPI.has(player, "iron_age");                    // client and server
StageLockAPI.grant(serverPlayer, "iron_age");            // or grant(uuid, stages) for offline players
StageLockAPI.global().grant("blood_moon");
StageLockAPI.team("vanilla:red").grant("outpost");
StageLockAPI.registerStageSource((server, uuid) -> Set.of(...));  // read-only stages from your mod
StageLockAPI.registerTeamProvider("mymod", provider);
RestrictionTypes.register(new RestrictionType<>(id, mapCodec));   // your own rule type
```

**Events** (on `NeoForge.EVENT_BUS`):

| Event | When | Notes |
|---|---|---|
| `StageEvent.Grant` / `StageEvent.Revoke` | before a change | Can be cancelled. |
| `StageEvent.Granted` / `StageEvent.Revoked` | after a change | Carries the scope, the target and the stage. |
| `StagesChangedEvent` | after a player's effective stages change | Fires on both sides, at most once per player per tick. |
| `RestrictionsReloadedEvent` | after rules change | On the server and on the client. |
| `CollectRulesEvent.Restrictions` / `CollectRulesEvent.StageDefinitions` | during a reload | Add rules or definitions from code. |

---

## Development

```bash
./gradlew build                                 # jar + unit tests
./gradlew runGameTestServer                     # in-world GameTests
./gradlew runGameTestServer -Pdev_kubejs=true   # the same with KubeJS (needs run/kubejs/server_scripts)
./gradlew runClient -Pdev_jei=true              # also: dev_emi, dev_rei, dev_kubejs
```
