# EcoTry

[🇩🇪 Deutsche Version](README.de.md)

Server-side Fabric mod for **Minecraft 1.21.11** that bridges the [EcoBal Economy API](https://modrinth.com/mod/ecobal)
and command blocks.

EcoBal's `/eco take` replies "Balance too low" but still counts as a success. `/ecotry` throws a real error in that
case, which Minecraft treats as a failure: `execute store success` stores 0, conditional chain command blocks don't
fire and comparators stay off.

## Installation

Put `ecotry-1.0.0.jar` into the server's `mods` folder, next to [Fabric API](https://modrinth.com/mod/fabric-api) and
[EcoBal](https://modrinth.com/mod/ecobal) 1.0.1+ (EcoBal itself needs
[fabric-permissions-api](https://modrinth.com/mod/fabric-permissions-api)). Players don't need the mod. In
singleplayer, install it in the client's `mods` folder. [Mod Menu](https://modrinth.com/mod/modmenu) is optional and
adds the settings screen.

## Commands (operator level 2, like command blocks)

| Command | Success (result value) | Failure |
|---|---|---|
| `/ecotry take <players> <amount> [notify\|silent]` | amount taken (number of players) | not enough money |
| `/ecotry give <players> <amount> [notify\|silent]` | amount paid out (number of players) | EcoBal error |
| `/ecotry has <players> <amount> [notify\|silent]` | enough money, nothing taken (number of players) | not enough money |
| `/ecotry balance <player>` | balance, rounded down | – |
| `/ecotry reload` | config reloaded | – |

- `<players>`: a name or selector (`@p`, `@s`, `@a[...]`, `@r`), online players only. With several targets every
  player is handled on their own; the command only fails when it worked for nobody.
- `balance` takes exactly one player. For everyone: `execute as @a store result score @s eco_balance run ecotry balance @s`
- `<amount>`: 0.01 or more, decimals allowed.
- `notify` / `silent` overrides the `playerFeedback` setting for that one call.

## Example: shop button with a "then" and an "else" branch

Once: `/scoreboard objectives add eco_ok dummy`

1. Impulse block (Needs Redstone): `execute store success score #result eco_ok run ecotry take @p 100`
2. Chain block (Unconditional, Always Active): `execute if score #result eco_ok matches 1 run give @p minecraft:diamond`
3. Chain block (Unconditional, Always Active): `execute if score #result eco_ok matches 0 run tellraw @p {"text":"Not enough money!","color":"red"}`

Only need the "then" branch? Put `ecotry take @p 100` into the impulse block and a conditional chain block behind it.

## Settings

With Mod Menu: **Mods → EcoTry → settings button** (singleplayer and your own LAN worlds). On a server, edit
`config/ecotry.json` and run `/ecotry reload`.

| Key | Default | Meaning |
|---|---|---|
| `playerFeedback` | `false` | short message to the affected player |
| `actionBar` | `false` | show that message above the hotbar instead of in the chat |
| `language` | `"en_us"` | language of all texts for players without the mod: `"en_us"` or `"de_de"` |
| `messages` | English texts | player messages with `&` color codes, `&#RRGGBB` and `{player}`, `{amount}`, `{balance}`; empty = no message |

**Languages:** EcoTry ships English and German. Players who have the mod installed see their own game language;
everybody else sees the `language` of the server. Messages you have not changed follow that setting – set
`"language": "de_de"` and the default messages turn German; edited messages stay as you wrote them.

## Development

```
gradlew build              # builds build/libs/ecotry-1.0.0.jar, runs the server game tests and the translation check
gradlew runClientGameTest  # end-to-end test in a real client, screenshots in build/run/clientGameTest/screenshots
python tools/gen_icon.py . # regenerates the icon
```

The server game tests (`src/gametest`) cover take/give/has/balance, several targets, command blocks and a datapack
function. The client game test (English, 1920×1080) builds the shop button from the example above, buys a diamond,
fails the second purchase, and changes the settings through the Mod Menu screen (language switch, discard prompt).
EcoBal is a compile-only dependency and is never packed into the jar: a local `libs/EcoBal-1.0.1.jar` is used if present (it is not part of the repository), otherwise Gradle downloads the same version from Modrinth.

## License

[MIT](LICENSE)
