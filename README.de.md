# EcoTry

[🇬🇧 English version](README.md)

Serverseitiger Fabric-Mod für **Minecraft 1.21.11** als Brücke zwischen der
[EcoBal Economy API](https://modrinth.com/mod/ecobal) und Command-Blöcken.

EcoBals `/eco take` meldet „Balance too low“, zählt aber trotzdem als erfolgreich. `/ecotry` wirft in diesem Fall
einen echten Fehler, den Minecraft als Fehlschlag wertet: `execute store success` speichert 0, „Bedingt“-Kettenblöcke
lösen nicht aus und Komparatoren bleiben aus.

## Installation

`ecotry-1.0.0.jar` in den `mods`-Ordner des Servers legen, zusammen mit der
[Fabric API](https://modrinth.com/mod/fabric-api) und [EcoBal](https://modrinth.com/mod/ecobal) 1.0.1+ (EcoBal
braucht selbst die [fabric-permissions-api](https://modrinth.com/mod/fabric-permissions-api)). Spieler brauchen den
Mod nicht. Im Einzelspieler kommt er in den `mods`-Ordner des Clients. [Mod Menu](https://modrinth.com/mod/modmenu)
ist optional und bringt den Einstellungsbildschirm.

## Befehle (OP-Level 2, wie Command-Blöcke)

| Befehl | Erfolg (Rückgabewert) | Fehlschlag |
|---|---|---|
| `/ecotry take <spieler> <betrag> [notify\|silent]` | Betrag abgezogen (Anzahl Spieler) | zu wenig Geld |
| `/ecotry give <spieler> <betrag> [notify\|silent]` | Betrag ausgezahlt (Anzahl Spieler) | EcoBal-Fehler |
| `/ecotry has <spieler> <betrag> [notify\|silent]` | genug Geld, nichts abgezogen (Anzahl Spieler) | zu wenig Geld |
| `/ecotry balance <spieler>` | Kontostand, abgerundet | – |
| `/ecotry reload` | Config neu geladen | – |

- `<spieler>`: Name oder Selektor (`@p`, `@s`, `@a[...]`, `@r`), nur Online-Spieler. Bei mehreren Zielen wird jeder
  Spieler einzeln behandelt; der Befehl schlägt nur fehl, wenn es bei keinem geklappt hat.
- `balance` nimmt genau einen Spieler. Für alle: `execute as @a store result score @s eco_balance run ecotry balance @s`
- `<betrag>`: ab 0.01, Kommazahlen erlaubt.
- `notify` / `silent` überschreibt für diesen einen Aufruf die Einstellung `playerFeedback`.

## Beispiel: Shop-Knopf mit True- und False-Zweig

Einmalig: `/scoreboard objectives add eco_ok dummy`

1. Impuls-Block (Benötigt Redstone): `execute store success score #result eco_ok run ecotry take @p 100`
2. Ketten-Block (Unbedingt, Immer aktiv): `execute if score #result eco_ok matches 1 run give @p minecraft:diamond`
3. Ketten-Block (Unbedingt, Immer aktiv): `execute if score #result eco_ok matches 0 run tellraw @p {"text":"Zu wenig Geld!","color":"red"}`

Nur True-Zweig nötig? Dann reicht `ecotry take @p 100` im Impuls-Block und ein „Bedingt“-Kettenblock dahinter.

## Einstellungen

Mit Mod Menu: **Mods → EcoTry → Einstellungs-Button** (Einzelspieler und eigene LAN-Welten). Auf einem Server
`config/ecotry.json` bearbeiten und `/ecotry reload` ausführen.

| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `playerFeedback` | `false` | kurze Nachricht an den betroffenen Spieler |
| `actionBar` | `false` | diese Nachricht über der Hotbar statt im Chat |
| `language` | `"en_us"` | Sprache aller Texte für Spieler ohne den Mod: `"en_us"` oder `"de_de"` |
| `messages` | englische Texte | Nachrichten mit `&`-Farbcodes, `&#RRGGBB` und `{player}`, `{amount}`, `{balance}`; leer = keine Nachricht |

**Sprachen:** EcoTry bringt Englisch und Deutsch mit. Spieler mit installiertem Mod sehen ihre eigene Spielsprache,
alle anderen die `language` des Servers. Nachrichten, die du nicht geändert hast, folgen dieser Einstellung – mit
`"language": "de_de"` werden die Standardnachrichten deutsch, selbst geschriebene bleiben, wie sie sind.

## Bauen und testen

```
gradlew build              # baut build/libs/ecotry-1.0.0.jar, inkl. Server-Gametests und Übersetzungsprüfung
gradlew runClientGameTest  # End-to-End-Test im echten Client, Screenshots in build/run/clientGameTest/screenshots
python tools/gen_icon.py . # erzeugt das Icon neu
```

Die Server-Gametests (`src/gametest`) prüfen take/give/has/balance, mehrere Ziele, Command-Blöcke und eine
Datapack-Funktion. Der Client-Gametest (Englisch, 1920×1080) baut den Shop-Knopf aus dem Beispiel, kauft einen
Diamanten, scheitert beim zweiten Kauf und ändert die Einstellungen über den Mod-Menu-Bildschirm (Sprachwechsel,
„Änderungen verwerfen?“). EcoBal ist nur Compile-Abhängigkeit und wird nie in die .jar gepackt: Liegt `libs/EcoBal-1.0.1.jar` vor, wird sie genutzt (sie gehört nicht zum Repository), sonst lädt Gradle dieselbe Version von Modrinth.

## Lizenz

[MIT](LICENSE)
