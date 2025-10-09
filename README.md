# Beast Master

***Take command of your loyal companions!*** Beast Master is a comprehensive pet and mount management mod that gives you unprecedented control over your animal friends in Minecraft.

**Written entirely by Deepseek AI for Minecraft v1.18.2.**

## 🌏 Features

### 🐺 Smart Pet & Mount Summoning

- *Whistle Commands*: Call all your pets or mounts with simple commands
- *Name-based Summoning*: Call specific animals by name
- *Cross-Dimensional*: Summon companions from any dimension
- *Into the Wild*: Release your pets or mounts as desired

### 🛡️ Health & Combat Management

- *Auto-Regeneration*: Heal your companions over time
- *Immortality Toggle*: Pets and mounts can never die
- *Smart Combat*: Pets stop fighting when health drops below threshold
- *Injury System*: Mounts buck riders when too injured to carry you
- *No Friendly-Fire*: You can't hurt your pets or mounts

## ⚙️ Configuration

```json
{
  "petRegen": true,
  "mountRegen": false,
  "petImmortal": true,
  "mountImmortal": true,
  "healthRequiredToFight": 20,
  "healthRequiredToMove": 20,
  "whistleCooldownSeconds": 30,
  "disableFriendlyFire": true,
  "supportedPEtEntities": ["minecraft:wolf", "minecraft:cat", "minecraft:parrot"],
  "supportedMountEntities": ["minecraft:horse", "minecraft:donkey", "minecraft:mule", "minecraft:llama", "minecraft:pig"]
}
```

You can -- in theory (untested) -- add support for modded pets or mounts:

```json
"supportedPetEntities": [
  "minecraft:wolf",
  "alexsmobs:raccoon",
  "iceandfire:ampithere"
],
"supportedMountEntities": [
  "minecraft:horse",
  "dragonmounts:dragon",
  "alexsmobs:rhinoceros"
]
```

## 📜 Commands

```
/beast pet whistle          - Call all your pets
/beast pet whistle <name>   - Call specific pet by name
/beast pet find             - Register nearby pets
/beast pet list             - List all callable pets
/beast pet setfree <name>   - Set a pet free
/beast pet dismiss <name>   - Remove a pet from the world
/beast pet debug            - Debug information
```

```
/beast mount whistle        - Call all your mounts
/beast mount whistle <name> - Call specific mount by name
/beast mount find           - Register nearby mounts
/beast mount list           - List all callable mounts
/beast mount setfree <name> - Set a mount free
/beast mount dismiss <name> - Remove a mount from the world
/beast mount debug          - Debug information
```

## 📋 External Links

- [Followers Teleport Too](https://modrinth.com/mod/followers-teleport-too) - pets follow teleport
- [Horse Buff](https://modrinth.com/mod/horsebuff) - horses stay put when idle
- [Horse Expert](https://modrinth.com/mod/horse-expert) - viewable stats on horses
- [Horse Stonks](https://modrinth.com/mod/horse-stonks) - breeding improves offspring