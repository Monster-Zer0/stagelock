# StageLock: Examples and KubeJS guide

> This is the **supplemental README**: copy-paste examples with plain explanations. For every command, setting and datapack field, see the **[advanced README](README.md)**.

StageLock lets you lock parts of Minecraft until a player is ready for them. Items, recipes, blocks, mobs,
dimensions and more stay locked until the player has unlocked a **stage**.

A **stage** is just a name you make up, like `iron_age`, `nether` or `chapter_2`. A player either has it or doesn't.
You decide what each stage locks, and how players earn it: advancements, quests, crafting something, killing a boss.

This page has two parts:

1. **[Examples](#examples):** copy, paste, change the names.
2. **[How it works in KubeJS](#how-it-works-in-kubejs):** what each piece means, for when you want to write your own.

---

## Before you start

You need **KubeJS** installed alongside StageLock.

1. Open your instance folder and go to `kubejs/server_scripts/`.
2. Create a file there, for example `stagelock.js`. Any name ending in `.js` works.
3. Paste examples from this page into it.
4. In game, type **`/reload`**. Your changes apply right away, with no restart needed.
5. **Test in Survival mode.** Players in Creative mode ignore every lock, so you'll think nothing works.
6. Give yourself a stage to check it unlocks: **`/stagelock grant @s iron_age`**.
   Take it away again with **`/stagelock revoke @s iron_age`**.

> **Tip:** Hold an item and type **`/stagelock why @s`**. It tells you which locks apply to that item and
> whether they're active for you.

> **About the `//` lines:** in the examples, anything after `//` is a note for you. The game ignores it, so you
> can leave notes in your own file the same way.

---

## Examples

Every example below is a complete, working piece of script. Most lock rules go **inside** a
`StageLockEvents.restrictions(event => { ... })` block. You only need one of those blocks, and you can put as many lines
inside it as you like:

```js
StageLockEvents.restrictions(event => {
  // your lock rules go here, one per line
})
```

The examples show that block each time so each one works on its own. In your own file, put all your
lines inside a single block.

### Locking items

#### 1. Lock one item until a stage

*Players can't pick up, use, hold or craft diamonds until they have the `iron_age` stage.*

```js
StageLockEvents.restrictions(event => {
  event.item('minecraft:diamond').requires('iron_age')
})
```

What players see:
- The item shows as **"Unfamiliar Item"** with a padlock, and its tooltip says **"Requires: iron_age"**.
- If it ends up in their inventory, it's dropped.
- It's hidden in JEI, REI and EMI until they unlock it.

#### 2. Lock several items at once

*Put the item names in square brackets, separated by commas.*

```js
StageLockEvents.restrictions(event => {
  event.item(['minecraft:diamond', 'minecraft:diamond_pickaxe', 'minecraft:diamond_sword']).requires('iron_age')
})
```

#### 3. Lock every item with a tag

*A name starting with `#` means "every item with this tag". This one covers diamonds from every mod at once.*

```js
StageLockEvents.restrictions(event => {
  event.item('#c:gems/diamond').requires('iron_age')
})
```

> **Finding names:** press **F3 + H** in game to turn on advanced tooltips. Item names like `minecraft:diamond`
> then show under every item. JEI, REI and EMI can also show an item's tags.

#### 4. Lock an entire mod

*A name starting with `@` means "every item from this mod".*

```js
StageLockEvents.restrictions(event => {
  event.item('@mekanism').requires('tech_age')
})
```

#### 5. Lock an entire mod, except a few items

```js
StageLockEvents.restrictions(event => {
  event.item('@mekanism')
       .except(['mekanism:configurator', 'mekanism:basic_bin'])
       .requires('tech_age')
})
```

#### 6. Let players carry an item but not use it

*Good for "you found something you can't use yet". The item stays in their inventory and stays visible in JEI with a padlock.*

```js
StageLockEvents.restrictions(event => {
  event.item('minecraft:elytra').requires('end_explorer').allow('pickup', 'hold', 'viewer')
})
```

#### 7. Only stop crafting it

*They can still find it, use it and trade for it; they just can't make it themselves.*

```js
StageLockEvents.restrictions(event => {
  event.item('minecraft:enchanting_table').requires('magic').only('craft')
})
```

#### 8. Stop players wearing armor

*They can carry it, but if they put it on, it comes straight back off.*

```js
StageLockEvents.restrictions(event => {
  event.item('#c:armors/diamond').requires('iron_age').only('equip')
})
```

If that tag doesn't exist in your pack, list the pieces instead:
`['minecraft:diamond_helmet', 'minecraft:diamond_chestplate', 'minecraft:diamond_leggings', 'minecraft:diamond_boots']`.

#### 9. Make a tool useless until a stage

*The pickaxe mines at bare-hand speed and can't attack, but they can hold it.*

```js
StageLockEvents.restrictions(event => {
  event.item('minecraft:diamond_pickaxe').requires('iron_age').only('mine', 'attack', 'use')
})
```

#### 10. Delete the item instead of dropping it

*If a locked item gets into their inventory, it disappears instead of being dropped on the ground.*

```js
StageLockEvents.restrictions(event => {
  event.item('minecraft:nether_star').requires('endgame').hold('destroy')
})
```

#### 11. Give a locked item a mystery name

```js
StageLockEvents.restrictions(event => {
  event.item('minecraft:diamond').requires('iron_age').hideName('Shiny Rock')
})
```

#### 12. Keep the real name and tooltip, just add a "Requires" line

```js
StageLockEvents.restrictions(event => {
  event.item('minecraft:diamond').requires('iron_age').showName().tooltip('append')
})
```

#### 13. Show your own message when something is blocked

*Replaces the default "You don't know how to use … yet" message above the hotbar.*

```js
StageLockEvents.restrictions(event => {
  event.item('minecraft:ender_pearl').requires('end_explorer').message('The pearl hums, but nothing happens.')
})
```

#### 14. Stop players summoning the Wither

*The Wither is built from skulls rather than spawned, so lock the skulls being placed.*

```js
StageLockEvents.restrictions(event => {
  event.item('minecraft:wither_skeleton_skull').requires('endgame').only('place')
})
```

### Locking recipes

#### 15. Lock one recipe

*Recipe names show in JEI, REI and EMI when advanced tooltips are on (F3 + H).*

```js
StageLockEvents.restrictions(event => {
  event.recipe('minecraft:iron_pickaxe').requires('stone_age')
})
```

#### 16. Lock every recipe that makes an item

*Covers every way to craft it, including other mods' recipes for the same item.*

```js
StageLockEvents.restrictions(event => {
  event.recipeOutput('minecraft:iron_pickaxe').requires('stone_age')
})
```

#### 17. Lock a whole type of crafting

*Here, nothing can be made in a Blast Furnace until `iron_age`.*

```js
StageLockEvents.restrictions(event => {
  event.recipeType('minecraft:blasting').requires('iron_age')
})
```

Other common types:
- `minecraft:smelting`
- `minecraft:smoking`
- `minecraft:smithing`
- `minecraft:stonecutting`
- `minecraft:campfire_cooking`

#### 18. Lock all of a mod's recipes

```js
StageLockEvents.restrictions(event => {
  event.recipe('@create').requires('create_unlocked')
})
```

#### 19. Keep locked recipes visible in JEI

*Normally, locked recipes are hidden. This keeps them visible so players can see what's coming.*

```js
StageLockEvents.restrictions(event => {
  event.recipeOutput('minecraft:netherite_ingot').requires('nether').showInViewers()
})
```

> **Machines:** furnaces and other machines follow the stages of the player who **placed** them, even when that
> player is offline.

### Locking blocks

#### 20. Stop players breaking a block

```js
StageLockEvents.restrictions(event => {
  event.block('minecraft:obsidian').requires('iron_age').only('break')
})
```

#### 21. Stop players opening or using a block

*They can't right-click it: no opening the GUI, no using it.*

```js
StageLockEvents.restrictions(event => {
  event.block('minecraft:enchanting_table').requires('magic').only('interact')
})
```

#### 22. Disguise ores as stone

*Players without the stage see plain stone. Mining it gives cobblestone. Once they unlock the stage, the real ore appears.*

```js
StageLockEvents.restrictions(event => {
  event.block('#c:ores/diamond').requires('iron_age').only().disguiseAs('minecraft:stone')
})
```

#### 23. Let them break it, but get nothing

```js
StageLockEvents.restrictions(event => {
  event.block('#c:ores/uranium').requires('nuclear').only('drops')
})
```

### Locking dimensions

#### 24. Lock the Nether until a stage

```js
StageLockEvents.restrictions(event => {
  event.dimension('minecraft:the_nether').requires('nether').message('The portal refuses you.')
})
```

#### 25. Lock the End

```js
StageLockEvents.restrictions(event => {
  event.dimension('minecraft:the_end').requires('endgame')
})
```

Players who log in inside a locked dimension are sent back to spawn.

### Mobs

#### 26. Stop a mob spawning until someone nearby is ready

*Endermen only spawn naturally within 128 blocks of a player who has the stage. Spawners still work.*

```js
StageLockEvents.restrictions(event => {
  event.entity('minecraft:enderman').requires('endgame').preventSpawn(128)
})
```

Spawn eggs and commands always work. This only affects natural spawning.

#### 27. Stop players trading with villagers

*They can still hit villagers, but can't open trades.*

```js
StageLockEvents.restrictions(event => {
  event.entity('minecraft:villager').requires('trading').allowAttack()
})
```

#### 28. Stop players attacking a mob

```js
StageLockEvents.restrictions(event => {
  event.entity('minecraft:warden').requires('deep_dark').allowInteract()
})
```

### Fluids and enchantments

These two use a slightly different style: you describe the lock as a list of settings inside `event.add({ ... })`.

#### 29. Lock lava buckets

*Players can't scoop up lava or pour it out until `iron_age`.*

```js
StageLockEvents.restrictions(event => {
  event.add({ type: 'stagelock:fluid', requires: 'iron_age', fluids: '#minecraft:lava' })
})
```

#### 30. Lock an enchantment

*Any item with Mending, books included, is locked until `nether`. Here players may still carry it.*

```js
StageLockEvents.restrictions(event => {
  event.add({
    type: 'stagelock:enchantment',
    requires: 'nether',
    enchantments: 'minecraft:mending',
    actions: { hold: 'allow', pickup: false }
  })
})
```

### Recipe viewers (JEI, REI, EMI)

A locked item is already hidden from JEI, REI and EMI, together with the pages that would show it: its
recipes, anvil and grindstone pages, machine recipes, loot and trade pages. These two examples go further.

#### 31. Hide a whole JEI page until a stage

*The anvil page stays hidden until `iron_age`. Each recipe viewer names its pages differently, so list the
one you use (or all of them). Type **`/stagelock_categories`** in game to see the names; click one to copy it.*

```js
StageLockEvents.restrictions(event => {
  event.viewerCategory([
    'minecraft:anvil',          // JEI
    'emi:anvil_repairing',      // EMI
    'minecraft:plugins/anvil'   // REI
  ]).requires('iron_age')
})
```

#### 32. Don't let a locked item show up anywhere

*By default a page stays visible when the locked item is only one of several choices in a slot, such as a
smelting recipe that takes "any iron tool". If you want a pack where players can't see anything ahead of them,
open `config/stagelock-server.toml` in your instance folder and set:*

```toml
[viewers]
	recipeHiding = "STRICT"
```

*Now every page that shows a locked item, even as one option among many, is hidden until the item unlocks.*

### Combining stages

#### 33. Needs two stages

*Square brackets mean "all of these".*

```js
StageLockEvents.restrictions(event => {
  event.item('minecraft:beacon').requires(['nether', 'endgame'])
})
```

#### 34. Needs either of two stages

*`any_of` means "at least one of these". Good for "tech path or magic path".*

```js
StageLockEvents.restrictions(event => {
  event.item('minecraft:experience_bottle').requires({ any_of: ['tech_path', 'magic_path'] })
})
```

#### 35. Only allowed *before* a stage

*`not` flips it: the starter item works only until they finish the tutorial.*

```js
StageLockEvents.restrictions(event => {
  event.item('minecraft:wooden_sword').requires({ not: 'tutorial_done' }).only('use', 'attack')
})
```

### Setting up stages

You don't have to set up stages at all; any name works as soon as you use it. Setting one up adds a nice name,
an automatic unlock, and other extras. These examples go inside a **`StageLockEvents.stages`** block:

```js
StageLockEvents.stages(event => {
  // stage setups go here
})
```

#### 36. Give a stage a nice name

*Tooltips then say "Requires: Iron Age" instead of "Requires: iron_age".*

```js
StageLockEvents.stages(event => {
  event.create('iron_age').displayName('Iron Age')
})
```

#### 37. Unlock a stage with a vanilla advancement

*The stage is granted automatically when the player gets the advancement. Find advancement names in the
advancements screen with F3 + H on, or in the
[Minecraft wiki](https://minecraft.wiki/w/Advancement).*

```js
StageLockEvents.stages(event => {
  event.create('iron_age').displayName('Iron Age').unlockOnAdvancement('minecraft:story/smelt_iron')
})
```

#### 38. Announce unlocks in chat

```js
StageLockEvents.stages(event => {
  event.create('iron_age').displayName('Iron Age').announce()
})
```

The player sees **"Unlocked: Iron Age"**.

#### 39. Chain stages in order

*Granting `iron_age` automatically grants `stone_age` too if they skipped it.*

```js
StageLockEvents.stages(event => {
  event.create('stone_age').displayName('Stone Age')
  event.create('iron_age').displayName('Iron Age').requires('stone_age')
  event.create('nether').displayName('Nether').requires('iron_age')
})
```

#### 40. Share a stage with the whole team

*When anyone on the team earns the advancement, the whole team gets the stage. This uses FTB Teams parties, or
vanilla `/team`.*

```js
StageLockEvents.stages(event => {
  event.create('outpost').displayName('Outpost').scope('team').unlockOnAdvancement('mypack:build_outpost')
})
```

### Giving players stages

These run on their own, outside the `restrictions` and `stages` blocks.

#### 41. When they craft something for the first time

```js
ItemEvents.crafted('minecraft:iron_pickaxe', event => {
  StageLock.grant(event.player, 'iron_age')
})
```

#### 42. Everyone, when a boss dies

*A **global** stage counts for every player on the server, including ones who join later.*

```js
EntityEvents.death('minecraft:ender_dragon', event => {
  StageLock.grantGlobal('post_dragon')
})
```

#### 43. A starting stage when they join

```js
PlayerEvents.loggedIn(event => {
  StageLock.grant(event.player, 'stone_age')
})
```

Granting a stage someone already has does nothing, so this is safe to run on every login.

#### 44. From a quest, command block or function

Use the command. `@p` means the nearest player, and `@s` means whoever runs it:

```
/stagelock grant @p iron_age
```

FTB Quests' **Stage** task and reward also work, through KubeJS stages (FTB XMod Compat).

#### 45. Give a reward when a stage unlocks

```js
StageLockEvents.granted('nether', event => {
  if (event.player) event.player.give('minecraft:fire_charge')
})
```

The `if (event.player)` check matters: a team or global stage can unlock while nobody is online.

#### 46. Check a stage in your own script

*Blaze rods do nothing when right-clicked until the player has `nether`.*

```js
ItemEvents.rightClicked('minecraft:blaze_rod', event => {
  if (!StageLock.has(event.player, 'nether')) {
    event.player.setStatusMessage('You sense nothing yet.')
    event.cancel()
  }
})
```

### A complete small progression

Paste this whole thing into one file to get a three-step pack: Stone Age, then Iron Age, then the Nether.

```js
// 1. The stages and how they unlock
StageLockEvents.stages(event => {
  event.create('stone_age').displayName('Stone Age').announce()
       .unlockOnAdvancement('minecraft:story/upgrade_tools')
  event.create('iron_age').displayName('Iron Age').announce().requires('stone_age')
       .unlockOnAdvancement('minecraft:story/smelt_iron')
  event.create('nether').displayName('Nether').announce().requires('iron_age')
       .unlockOnAdvancement('minecraft:story/form_obsidian')
})

// 2. What each stage locks
StageLockEvents.restrictions(event => {
  // Stone Age: iron tools can't be crafted yet
  event.recipeOutput(['minecraft:iron_pickaxe', 'minecraft:iron_axe', 'minecraft:iron_sword']).requires('stone_age')

  // Iron Age: diamonds are a mystery, diamond ore looks like stone, no blast furnace, no lava buckets
  event.item('#c:gems/diamond').requires('iron_age').hideName('Shiny Rock').allow('pickup', 'hold')
  event.block('#c:ores/diamond').requires('iron_age').only().disguiseAs('minecraft:stone')
  event.recipeType('minecraft:blasting').requires('iron_age').showInViewers()
  event.add({ type: 'stagelock:fluid', requires: 'iron_age', fluids: '#minecraft:lava' })

  // Nether: no portal travel until you've made obsidian
  event.dimension('minecraft:the_nether').requires('nether').message('Form some obsidian first.')
})

// 3. A little reward
StageLockEvents.granted('nether', event => {
  if (event.player) event.player.give('minecraft:flint_and_steel')
})
```

---

## How it works in KubeJS

### Where things go

- **Location:** all StageLock scripts go in **`kubejs/server_scripts/`**.
- **When they run:** they run again on every **`/reload`**.
- **Splitting files:** you can spread them across as many files as you like.

### The four StageLock events

| Event | What goes in it | When it runs |
|---|---|---|
| `StageLockEvents.restrictions(event => { ... })` | Lock rules: what is locked and by which stage. | Every load and `/reload`. |
| `StageLockEvents.stages(event => { ... })` | Stage setup: names, unlocks, chains. | Every load and `/reload`, before the lock rules. |
| `StageLockEvents.granted('stage', event => { ... })` | Things to do when a stage is gained. | Each time the stage is granted. |
| `StageLockEvents.revoked('stage', event => { ... })` | Things to do when a stage is lost. | Each time the stage is revoked. |

For `granted` and `revoked`, leave out `'stage'` to run for every stage.

### Reading a lock rule

Every lock rule is one line built from parts, read left to right:

```js
event.item('minecraft:diamond').requires('iron_age').allow('pickup', 'hold').hideName('Shiny Rock')
//    └── what to lock ──────┘ └── who may use it ──┘ └── which parts stay open ──┘└── extras ───────┘
```

1. **What to lock:** `event.item(...)`, `event.recipe(...)`, `event.block(...)` and the others.
2. **`.requires(...)`:** the stage(s) needed. This part is **required**.
3. **Optional extras,** added with dots, in any order.

### Naming things

| You write | It means |
|---|---|
| `'minecraft:diamond'` | that exact item, block, mob or fluid |
| `'#c:gems/diamond'` | everything with that tag |
| `'@mekanism'` | everything from that mod |
| `['a', 'b', 'c']` | all of those, in a list |

### Writing `requires`

| You write | The player needs |
|---|---|
| `'iron_age'` | that stage |
| `['iron_age', 'nether']` | all of them |
| `{ any_of: ['tech', 'magic'] }` | at least one of them |
| `{ all_of: ['iron_age', 'nether'] }` | all of them (the same as the square brackets) |
| `{ not: 'tutorial_done' }` | to **not** have that stage |

You can nest them: `{ any_of: [['a', 'b'], 'c'] }` means "a and b, or c".

### What you can lock

| Start with | Locks |
|---|---|
| `event.item(...)` | items |
| `event.recipe(...)` | recipes, by recipe name, or `'@mod'` |
| `event.recipeOutput(...)` | every recipe that makes these items |
| `event.recipeType(...)` | every recipe of a crafting type, such as `'minecraft:blasting'` |
| `event.block(...)` | placed blocks |
| `event.dimension(...)` | travel to a dimension |
| `event.entity(...)` | mobs and other entities |
| `event.viewerCategory(...)` | whole pages (categories) in JEI, REI or EMI |
| `event.add({ ... })` | anything else, written as a list of settings. It's needed for fluids and enchantments. |

### Item options

An item lock blocks **everything** to start with. Use `only` or `allow` to change that.

| Option | What it does |
|---|---|
| `.only('use', 'craft')` | Block only the listed actions. |
| `.allow('pickup', 'hold')` | Unblock the listed actions. Everything else stays blocked. |
| `.except('mod:item')` | Leave these items out of the lock. |
| `.hold('drop')` | A locked item in the inventory is thrown out. This is the default. |
| `.hold('destroy')` | A locked item in the inventory is deleted. |
| `.hideName('Shiny Rock')` | Show this name instead of the real one. |
| `.showName()` | Keep the real name. |
| `.tooltip('append')` | Keep the normal tooltip and add the lock lines. Also `'replace'` (the default) and `'none'`. |
| `.message('…')` | Your own "can't do that" message. |

**Actions** you can name in `only` and `allow`:

| Action | Blocks |
|---|---|
| `pickup` | picking it up off the ground |
| `use` | right-clicking with it, plus eating, drinking, bows and shields |
| `place` | placing it as a block |
| `attack` | hitting things with it |
| `mine` | mining with it (it mines at bare-hand speed instead) |
| `equip` | wearing it (armor, Curios slots) |
| `hold` | keeping it in the inventory at all |
| `craft` | crafting it |
| `viewer` | showing it in JEI, REI and EMI. Allowed means it shows with a padlock. |

### Recipe options

| Option | What it does |
|---|---|
| `.showInViewers()` | Keep the locked recipe visible in JEI, REI and EMI. Normally it's hidden. |

### Block options

A block lock blocks `break`, `interact` and `place` to start with.

| Option | What it does |
|---|---|
| `.only('break')` | Block only the listed actions: `break`, `interact`, `place`, `drops`. |
| `.allow('place')` | Unblock the listed actions. |
| `.only('drops')` | Can break it, but get nothing from it. |
| `.disguiseAs('minecraft:stone')` | Players without the stage see, mine and get drops from this block instead. |
| `.except('mod:block')` | Leave these blocks out of the lock. |
| `.message('…')` | Your own message. |

### Dimension options

| Option | What it does |
|---|---|
| `.message('…')` | The message shown when travel is refused. |

### Mob options

A mob lock blocks attacking and interacting to start with.

| Option | What it does |
|---|---|
| `.allowAttack()` | Players may attack it. |
| `.allowInteract()` | Players may right-click it: trading, riding, leashing. |
| `.preventSpawn(128)` | It only spawns naturally near a player, within 128 blocks, who has the stage. |
| `.preventSpawn(128, false)` | The same, and spawners are blocked too. |
| `.message('…')` | Your own message. |

### Fluid and enchantment settings (inside `event.add`)

| Setting | Fluid lock | Enchantment lock |
|---|---|---|
| `type` | `'stagelock:fluid'` | `'stagelock:enchantment'` |
| `requires` | the stage(s) | the stage(s) |
| what to lock | `fluids: '#minecraft:lava'` | `enchantments: 'minecraft:mending'` |
| `actions` | `{ pickup: true, place: true, viewer: 'hide' }`. `pickup` fills buckets and `place` pours them. | The same actions as items, for example `{ hold: 'allow' }`. |
| extra | — | `check_books: false` leaves enchanted books alone. |

In `event.add`, every action is blocked unless you set it to `false` (or `'allow'`/`'show'`).

### Stage setup options (inside `StageLockEvents.stages`)

| Option | What it does |
|---|---|
| `event.create('iron_age')` | Starts setting up a stage. |
| `.displayName('Iron Age')` | The name players see. |
| `.description('…')` | A short description. |
| `.icon('minecraft:iron_ingot')` | An item to represent the stage. |
| `.requires('stone_age')` | Stages that come first. They're granted automatically with this one. |
| `.unlockOnAdvancement('minecraft:story/smelt_iron')` | Getting this advancement grants the stage. Use it more than once for several advancements. |
| `.scope('team')` | The advancement unlock goes to the whole team (`'team'`) or the whole server (`'global'`). |
| `.announce()` | Says "Unlocked: …" in chat. |
| `.runFunction('mypack:reward')` | Runs a datapack function for the player when unlocked. |

### What you can read in `granted` and `revoked`

| Write | Gives you |
|---|---|
| `event.player` | The player. This can be **empty** for team or global stages when nobody is online, so check with `if (event.player)`. |
| `event.stage` | The stage's name. |
| `event.scope` | `'player'`, `'team'` or `'global'`. |
| `event.playerStages` | All the player's stages. |

### The `StageLock` helper

You can use it in any server script.

| Write | What it does |
|---|---|
| `StageLock.has(player, 'iron_age')` | Whether the player has the stage. |
| `StageLock.test(player, { any_of: ['a', 'b'] })` | Checks a requirement written like `requires`. |
| `StageLock.stages(player)` | All the player's stages. |
| `StageLock.grant(player, 'iron_age')` | Gives the player the stage. |
| `StageLock.revoke(player, 'iron_age')` | Takes it away. |
| `StageLock.clear(player)` | Takes away all the player's own stages. |
| `StageLock.grantGlobal('post_dragon')` | Gives the stage to everyone on the server. |
| `StageLock.revokeGlobal('post_dragon')` | Takes the global stage away. |
| `StageLock.hasGlobal('post_dragon')` | Whether the global stage is set. |
| `StageLock.grantTeam(player, 'outpost')` | Gives the stage to the player's whole team. |
| `StageLock.revokeTeam(player, 'outpost')` | Takes it away from the team. |
| `StageLock.isLocked(player, item)` | Whether anything about this item is locked for the player. |
| `StageLock.isLocked(player, item, 'use')` | Whether one action is locked, using the action names above. |

KubeJS's own `player.stages.add('x')`, `player.stages.remove('x')` and `player.stages.has('x')` work too, and do the same
thing as `StageLock.grant`, `revoke` and `has`.

### When two locks cover the same thing

The stricter one wins:
- If either lock blocks an action, it's blocked.
- If one lock deletes an item and another only drops it, it's deleted.
- If one lock hides an item in JEI and another shows it, it's hidden.

`/stagelock why @s` shows which lock is deciding.
