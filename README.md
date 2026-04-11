# SkyCity

SkyCity ist ein umfangreiches Minecraft-Plugin fuer Paper/Spigot 1.20, das ein eigenes Skyblock-/Inselsystem mit geschuetzter Void-Welt, Insel-Core, Fortschrittssystem, Chunk-Freischaltungen, Grundstuecken, Rechtemanagement und Teleportfunktionen bereitstellt. Jeder Spieler kann eine eigene Insel erstellen, diese schrittweise ausbauen und anderen Spielern gezielt Rechte fuer die komplette Insel oder nur fuer einzelne Bereiche geben.

Das Plugin ist nicht nur ein einfaches `/is create`, sondern ein komplettes Insel-Management-System. Es erstellt beim Start eine eigene SkyCity-Welt, teleportiert neue Spieler zum Spawn, fuehrt sie zur Inselerstellung und verwaltet danach Schutz, Fortschritt, Upgrades, Besucherrechte, Warps, PvP-Zonen und viele Detailregeln automatisch im Hintergrund.

Zum aktuellen Stand gehoeren ausserdem ein vollstaendiger deutscher Biom-Shop, Insel- und Parcel-Spielmechaniken wie PvP, PvE, Checkpoints und Capture The Flag, automatische Inaktivitaetswarnungen fuer Inseln sowie ein deutlich verfeinertes Schutz- und Rechtesystem fuer Interaktionen, Container, Entities und Technik.

## Was das Plugin macht

SkyCity erzeugt eine eigene Void-Welt namens `skycity_world`. In dieser Welt besitzt jeder Spieler eine grosse Insel-Flaeche, die intern aus vielen Chunks besteht. Die Insel wird nicht blind komplett auf einmal erzeugt, sondern vorbereitet, vorgeneriert und nach Bedarf weiter aufgebaut. Dadurch koennen Spieler schnell starten, waehrend weitere Chunks im Hintergrund verarbeitet werden.

Zentrales Element jeder Insel ist der `SkyCity Core`. Dieser Core ist gleichzeitig Bedienoberflaeche, Upgrade-Zentrum und Fortschrittsanzeige. Ueber ihn koennen Spieler:

- den Inselstatus ansehen
- Upgrade-Fortschritt pruefen
- XP einlagern und wieder entnehmen
- Inselanzeigen steuern
- Insel- und Chunk-Menues oeffnen
- Rechte, Besucheroptionen und Warps verwalten

Zusammen mit den Befehlen `/is`, `/warp` und `/spawn` entsteht daraus ein komplettes Citybuild-/Skyblock-System fuer strukturierte Inselverwaltung.

## Wie man das Plugin benutzt

### 1. Server starten und Plugin laden

Nach dem Start des Servers erzeugt SkyCity automatisch seine eigene Welt und den zentralen Spawn-Bereich. Die Konfigurationsdatei ist aktuell nur als Standarddatei vorhanden; die Hauptlogik steckt direkt im Plugin.

### 2. Als Spieler beitreten

Neue Spieler ohne Insel werden automatisch zum Spawn teleportiert. Dort erhalten sie regelmaessig einen Hinweis, dass sie mit `/is create` ihre Insel erstellen koennen.

### 3. Eigene Insel erstellen

Mit:

```text
/is create
```

wird die Insel in die Erstellungs-Warteschlange eingetragen. Der Spieler wird zum Spawn gesetzt, bekommt Statusmeldungen zur Vorbereitung und wird automatisch auf seine Insel teleportiert, sobald der Startbereich fertig generiert ist.

### 4. Insel im Alltag nutzen

Nach der Erstellung sind die wichtigsten Schritte:

- mit `/is` das Inselmenue oeffnen
- mit `/is home` zur Insel zurueckkehren
- mit `/is setspawn` den Inselspawn neu setzen
- den Core platzieren bzw. verwenden
- Chunks schrittweise freischalten
- Rechte an Member, Owner oder Grundstuecks-Spieler vergeben
- Warps und Titel setzen

## Zentrale Funktionen im Detail

## 1. Eigene SkyCity-Welt

SkyCity arbeitet in einer separaten Welt `skycity_world`. Diese Welt ist als Void-Welt aufgebaut und speziell fuer Inseln gedacht.

Wichtige Eigenschaften:

- keine natuerliche Monster-Spawnlogik
- Wetterzyklus deaktiviert
- Schlaflosigkeit deaktiviert
- Mob-Griefing deaktiviert
- Vanilla Nether- und End-Portale in SkyCity deaktiviert
- normale externe Welten sind gesperrt, ausgenommen Farmwelt-artige Welten

Dadurch bleibt die SkyCity-Welt kontrolliert, performant und fuer das Inselsystem konsistent.

## 2. Spawn-System

Das Plugin legt einen geschuetzten Spawn-Bereich an. Dort duerfen normale Spieler nicht bauen oder abbauen. Neue Spieler landen zunaechst dort, bis sie eine eigene Insel besitzen. Auch Spieler ohne gueltige Insel oder mit fehlendem Zugang werden dorthin zurueck teleportiert.

Befehl:

```text
/spawn
```

Teleportiert den Spieler jederzeit zum Spawn.

## 3. Inselerstellung und Hintergrund-Generierung

Inseln werden nicht nur gespeichert, sondern aktiv vorbereitet:

- die Insel bekommt einen festen Platz im Inselraster
- der zentrale Startbereich wird zuerst bereitgestellt
- weitere Chunks werden im Hintergrund vorgeneriert
- Spieler erhalten Fortschrittsmeldungen waehrend der Generierung

Das Plugin stellt ausserdem sicher, dass Spawnpunkt und Core-Bereich einer Insel vorhanden und sicher bleiben.

## 4. SkyCity Core

Der Core ist das Herzstueck der Insel. Er ist ein spezielles Item bzw. ein spezieller Block, ueber den die Insel verwaltet wird.

Core-Funktionen:

- zeigt Inselstatus und Upgrade-Informationen
- erlaubt das Einlagern und Auszahlen von Erfahrung
- oeffnet Insel-, Chunk-, Teleport- und Verwaltungsmenues
- kann verschiedene Anzeige-Modi durchschalten
- zeigt Fortschritt und wichtige Inselinfos direkt am Core an

Wenn ein Spieler den Core abbauen moechte, darf das nur ein Master oder Owner. Besucher oder einfache Member koennen den Core nicht entfernen.

## 5. Insel-Level und Fortschritt

SkyCity besitzt ein Fortschritts- und Levelsystem fuer Inseln. Bestimmte platzierte oder verarbeitete Bloecke zaehlen in den Inselwert und in Upgrade-Anforderungen hinein. Ueber den Core kann der Spieler sehen:

- welche Anforderungen fuer das naechste Level fehlen
- wie viel Blockwert seine Insel aktuell besitzt
- welche Limits fuer Technik und Kreaturen auf dem aktuellen Level gelten

Das Levelsystem beeinflusst unter anderem:

- Hopper-Limits
- Piston-Limits
- Observer-Limits
- Dispenser-Limits
- Tier-Limits
- Golem-Limits
- Villager-Limits
- verschiedene Farm-/Technikgrenzen

Dadurch gibt es echten Progress statt nur freiem Inselbau ohne Ziel.

## 6. Chunk-System und Freischaltungen

Jede Insel besteht aus vielen Chunks, die nicht automatisch alle offen sind. Spieler koennen ihre Insel kontrolliert erweitern.

Wichtige Befehle:

```text
/is showchunks
/is hidechunks
/is chunkunlock
/is chunkapprove <insel-owner-uuid> <relX> <relZ>
```

So funktioniert es:

- `/is showchunks` aktiviert eine visuelle Vorschau der Chunks
- `/is chunkunlock` schaltet den aktuellen Chunk frei
- Grenz-Chunks koennen eine Freigabe von Nachbarinseln benoetigen
- `/is chunkapprove ...` bestaetigt solche Anfragen

Das System verhindert unkontrollierte Ueberschneidungen und schuetzt benachbarte Inseln.

Zusatzlich stoppen technische Verschiebungen wie Pistons oder Flying Machines nicht nur an Insel- und Chunk-Grenzen, sondern auch an Parcel-Grenzen. Dadurch koennen Spiel- oder Farmmechaniken nicht in fremde oder gesperrte Bereiche hineinschieben.

## 7. Inselrechte und Rollen

SkyCity trennt die Inselrechte sauber in mehrere Stufen.

### Master

Mit:

```text
/is masterinvite <spieler>
/is masteraccept
/is masterleave
```

koennen zusaetzliche Master eingeladen werden. Diese Rolle ist maechtiger als normale Member und eignet sich fuer enge Projektpartner.

### Owner-Verwaltung

Mit:

```text
/is owner add <spieler>
/is owner remove <spieler>
```

koennen weitere Owner hinzugefuegt oder entfernt werden.

Owner werden im System an allen relevanten Verwaltungsstellen wie Master behandelt, etwa bei Shop-Funktionen und Inselverwaltung. Gleichzeitig koennen Owner sich selbst sauber von einer Insel austragen, ohne dass sie andere Owner entfernen duerfen.

### Member-Rechte

Mit:

```text
/is member <spieler> [build|container|redstone|all]
/is unmember <spieler> [build|container|redstone|all]
```

koennen feingranulare Rechte vergeben werden.

Dabei lassen sich getrennt steuern:

- Bauen
- Containerzugriff
- Redstone-Nutzung
- alle Rechte zusammen

Das ist besonders nuetzlich, wenn ein Spieler zwar Kisten oeffnen, aber nichts abbauen duerfen soll.

Member koennen sich ausserdem selbst wieder aus ihren Member-Rechten austragen. Befehle wie Owner- oder Member-Verwaltung beziehen sich dabei auf die Insel, auf der der Spieler gerade steht, damit Mehrfach-Insel-Konstellationen klarer und logischer funktionieren.

## 8. Inselschutz und Besucheroptionen

Das Plugin schuetzt Inseln standardmaessig sehr strikt. Ohne Rechte duerfen Spieler typischerweise nicht:

- bauen oder abbauen
- Container oeffnen
- Redstone bedienen
- Tueren, Trapdoors oder Zauntore nutzen
- Buttons, Hebel oder Druckplatten verwenden
- bestimmte Farm-/Nutzungsbloecke bedienen
- Reittiere oder Fahrzeuge nutzen

Gleichzeitig gibt es Besucher-Einstellungen fuer Inseln und Grundstuecke. Master oder Owner koennen damit gezielt erlauben, was Besucher duerfen, zum Beispiel:

- Tueren benutzen
- Trapdoors benutzen
- Zauntore benutzen
- Buttons / Hebel / Druckplatten nutzen
- Container verwenden
- Farmen bedienen
- reiten
- teleportieren

Der Schutz deckt inzwischen auch viele erweiterte Interaktionen ab, darunter:

- Buckets und Fluessigkeiten
- Doppelkisten und Entity-Inventare
- Enderchests
- Item Frames, Glow Item Frames und Gemaelde
- ArmorStands
- Villager-Interaktionen
- Leashes
- Lectern-Buecher
- Fahrzeug-Zerstoerung
- Hopper-Transfers nur innerhalb desselben Insel-/Parcel-Kontexts

Dadurch ist das Verhalten deutlich naeher an einem vollstaendigen Regionschutz und fuer Inseln, Parcels und Spielzonen konsistent.

So kann eine Insel komplett privat oder bewusst offen gestaltet werden.

## 9. Titel, Spawn und Warps

Spieler koennen ihre Insel sichtbar benennen und als Ziel fuer Teleports anbieten.

Befehle:

```text
/is title <text>
/is title clear
/is setspawn
/warp
/warp <name>
```

Funktionen:

- der Inseltitel wird gesetzt oder geloescht
- der Inselspawn kann neu definiert werden
- verfuegbare Warps lassen sich mit `/warp` auflisten
- mit `/warp <name>` wird direkt teleportiert

Zusatzlich bietet das Plugin Teleport-Menues im Core- und Inselmenue.

## 10. Grundstuecke / Parcels

Auf einer Insel koennen eigene Grundstuecke definiert werden. Damit lassen sich groessere Inseln intern aufteilen, etwa fuer Shops, Events, Arenen oder Mitspieler-Bereiche.

Wichtige Befehle:

```text
/is plot wand
/is plot create
/is plot delete
/is plot list
/is plot owner add <spieler>
/is plot owner remove <spieler>
/is plot member add <spieler>
/is plot member remove <spieler>
```

Typischer Ablauf:

1. Mit `/is plot wand` den Grundstuecks-Stab holen.
2. Zwei Positionen mit dem Stab setzen.
3. Mit `/is plot create` das Grundstueck erstellen.
4. Danach Spieler als `owner` oder `member` fuer genau dieses Grundstueck eintragen.

Damit koennen innerhalb derselben Insel unterschiedliche Zonen mit eigenen Rechten entstehen.

Parcels besitzen dabei nicht nur eigene Owner-, Member- und Besucherrechte, sondern koennen auch als eigenstaendige Spielzonen mit PvP, PvE, Games, CTF, Checkpoints und Countdown-BossBars genutzt werden.

## 11. Moderation auf Inseln und Grundstuecken

Fuer stoerende Spieler gibt es Moderationsbefehle.

Gesamte Insel:

```text
/is kick <spieler>
/is ban <spieler>
/is unban <spieler>
```

Nur aktuelles Grundstueck:

```text
/is pkick <spieler>
/is pban <spieler>
/is punban <spieler>
```

Spieler, die ein gebanntes Grundstueck betreten, erhalten einen Countdown und werden anschliessend automatisch entfernt, falls sie die Zone nicht verlassen.

## 12. PvP-Zonen auf Grundstuecken

Grundstuecke koennen als aktive PvP-Zonen verwendet werden. Das ist eine besondere Funktion fuer Events, Arenen oder riskantere Handels-/Kampfbereiche.

Wenn PvP auf einem Grundstueck aktiv ist:

- PvP ist nur in dieser Zone erlaubt
- nicht freigegebene Spieler erhalten einen Countdown zum Verlassen
- beim Tod bleibt das Inventar erhalten
- es wird kein normales Drop- oder XP-Verhalten genutzt
- stattdessen wird zufaellig ein Item vom Opfer auf den Sieger uebertragen
- es gibt ein PvP-Scoreboard mit Kills auf diesem Grundstueck

Das unterscheidet SkyCity deutlich von einfachen Inselplugins.

Neben klassischem PvP gibt es auch einen `Games`-Modus fuer Parcels. Dieser arbeitet aehnlich wie PvP-Zonen, aber ohne normalen Spielerschaden, und ist fuer Event- oder Minigame-Flaechen gedacht.

Im Parcel-Menue lassen sich zusaetzlich ein konfigurierbarer Countdown mit BossBar sowie ein Start-Countdown per Title einblenden. Die Zeit kann direkt im Menue angepasst, gestartet und wieder gestoppt werden.

## 13. Checkpoints, Woll-Logik und Teleport-Platten

SkyCity unterstuetzt eine eigene Checkpoint-/Teleport-Mechanik ueber farbige Wolle, Druckplatten und Lava. Damit lassen sich Parcours, Jump-and-Run-Strecken, PvE-Routen oder Teleport-Verbindungen direkt auf Inseln und Grundstuecken bauen.

Grundregeln:

- alle Wollfarben zaehlen
- alle Druckplatten-Typen zaehlen
- die Wolle darf direkt unter der Druckplatte liegen oder einen Block tiefer
- die Teleport-Blickrichtung einer Druckplatte entspricht der Blickrichtung des Spielers beim Platzieren
- die Mechanik koppelt nie bereichsuebergreifend
- auf Grundstuecken gilt sie nur innerhalb genau dieses Grundstuecks
- ausserhalb von Grundstuecken gilt sie nur im freien Inselbereich

### 1 erkannte Platte

Wenn es fuer eine Woll-/Druckplatten-Kombination in einem Bereich genau eine erkannte Platte gibt:

- wird sie als echter Checkpoint behandelt
- ueber ihr schwebt ein Enderauge
- beim Betreten wird ein positiver Klang abgespielt

### 2 erkannte Platten

Wenn es fuer dieselbe Woll-/Druckplatten-Kombination genau zwei erkannte Platten gibt:

- werden beide als Teleport-Paar gekoppelt
- beide zeigen Portal-/Enderman-Partikel
- beim Betreten einer Platte wird zur anderen teleportiert
- dabei wird der Enderman-Teleport-Sound abgespielt
- diese 2er-Konstellation zaehlt bewusst nicht als Checkpoint

### 3 oder mehr erkannte Platten

Wenn es fuer dieselbe Woll-/Druckplatten-Kombination drei oder mehr erkannte Platten gibt:

- werden sie nicht als 2er-Teleport gekoppelt
- alle erkannten Ziele zeigen ein schwebendes Enderauge
- sie bleiben als Checkpoint-Ziele nutzbar

### Lava-Regeln

Lava kann mit derselben Woll-/Platten-Logik als Ruecksprung-Ausloeser verwendet werden.

- `Wolle + Druckplatte + Lava` nutzt dieselbe Kombination wie das zugehoerige Ziel
- auch `Wolle` ohne Druckplatte mit Lava darueber wird erkannt
- bei genau einem passenden Ziel ist kein vorheriger Checkpoint-Kontakt noetig

Sonderfall `WHITE_WOOL`:

- weisse Wolle mit Lava teleportiert immer zum zuletzt beruehrten echten Checkpoint
- 2er-Teleportpaare zaehlen dabei nicht als Checkpoint

Fallbacks und Sicherheit:

- wenn kein gueltiges Woll-/Platten-Ziel gefunden wird, faellt das System auf Plotspawn, Inselspawn und zuletzt `/spawn` zurueck
- abgebaute oder geaenderte Zielplatten werden vor dem Teleport geprueft und automatisch verworfen

Die Teamfarbenlogik fuer Parcel-Spiele ist absichtlich streng: Eine Teamfarbe wird nur erkannt, wenn ein Spieler direkt auf Wolle steht oder genau ein normaler, solider Block ueber passender Wolle liegt. Druckplatten, Wasser, Lava, Banner, Redstone-Komponenten, Schalter und aehnliche Mechaniken zaehlen bewusst nicht als gueltiger Zwischenblock.

Fuer spielerische Parcels gibt es ausserdem gezielte Sonderrechte wie Laub-, Leiter-, Schnee- und Banner-Nutzung, damit Spleef-, Parkour- oder Capture-Mechaniken moeglich sind, ohne normales Bauen komplett zu oeffnen.

## 14. Capture The Flag auf Parcels

SkyCity unterstuetzt Capture The Flag direkt auf Parcels im Games-Modus.

So funktioniert das System:

- `Target Block + Banner` bildet eine Flaggenbasis
- gegnerische Flaggen koennen per Linksklick aufgenommen werden
- die Flagge wird sichtbar am Spieler getragen
- `Wolle + Shelf` oder `Chiseled Bookshelf` bildet einen Capture-Checkpoint
- mehrere Shelfs innerhalb eines Parcels werden gemeinsam beruecksichtigt
- normale Shelfs koennen bis zu 3 Flaggen aufnehmen
- Chiseled Bookshelves koennen bis zu 6 Flaggen aufnehmen
- wird ein Flaggentraeger geschlagen, geht die Flagge sofort an ihre Basis zurueck
- auch Logout, Tod oder Verlassen des Parcels setzen eine getragene Flagge zurueck
- Parcel-Owner koennen CTF im Menue aktivieren und resetten

Gewonnen hat der Spieler oder das Team, wenn alle verfuegbaren Flaggen in gueltige Capture-Regale eingesetzt wurden.

## 15. Insel-Zeitmodus

Inseln koennen eine eigene Zeitdarstellung fuer Spieler haben. Das Plugin unterstuetzt verschiedene Modi wie:

- normal
- Tag
- Sonnenuntergang
- Mitternacht

Dadurch kann eine Insel atmosphaerisch angepasst werden, ohne dass die Weltzeit fuer alle Spieler gleich aussehen muss.

## 16. Biome und Inselanpassung

Ueber die Menues koennen Spieler Biome fuer Inselbereiche bzw. Chunks verwalten. Das erlaubt gestalterische Anpassungen fuer verschiedene Themenbereiche, Farmen oder Bauten.

Der Biom-Shop bietet die vollstaendige verfuegbare Biomliste an, inklusive Nether- und End-Biomen. Die Anzeige ist durchgehend deutsch, zeigt aber zusaetzlich immer den Originalnamen an. Inselweite Aenderungen sind absichtlich nur per `Shift-Rechtsklick` moeglich, damit man sich im Menue nicht verklickt.

Zusammen mit dem Chunk-System und den Insel-Templates entsteht so eine flexible Inselgestaltung.

## 17. Automatische Limits und Performance-Schutz

SkyCity begrenzt bestimmte technisch intensive Elemente aktiv, um die Inselwelt stabil zu halten.

Beispiele:

- maximal 100 Inventarbloecke pro Insel
- begrenzte Anzahl an Hoppern
- begrenzte Anzahl an Pistons
- begrenzte Anzahl an Observern
- begrenzte Anzahl an Dispensern
- Limits fuer Kaktus, Kelp und Bambus
- Limits fuer Tiere, Golems und Villager

Diese Grenzen orientieren sich am Insel-Level und helfen, Lag und Missbrauch einzudaemmen.

## 18. Shops, Nachtsicht und Komfortfunktionen

SkyCity enthaelt mehrere Komfort- und Shop-Funktionen fuer Inseln und Chunks.

Dazu gehoeren unter anderem:

- Wachstumsschub pro Chunk mit BossBar-Anzeige
- Zeitmodus-Kauf fuer Inseln
- Chunk- und inselweite Nachtsicht
- zentraler Insel-Shop mit Rechtepruefung fuer Master und Owner

Die Nachtsicht ist so umgesetzt, dass SkyCity nur seinen eigenen Effekt setzt und fremde Traenkeffekte nicht ueberschreibt oder entfernt. Dadurch gibt es weniger Flackern und saubereres Zusammenspiel mit normalen Minecraft-Effekten.

## 19. Inaktivitaet und automatische Warnungen

Inseln koennen bei sehr langer Inaktivitaet automatisch bereinigt werden. Vor einer solchen Loeschung werden online befindliche Owner, Master und Member jetzt vorab informiert.

Aktuell gibt es Warnstufen bei:

- 30 Tagen Restzeit
- 7 Tagen Restzeit
- 1 Tag Restzeit

So bekommen auch Mitspieler einer Insel rechtzeitig mit, wenn Handlungsbedarf besteht.

## 20. Chat- und GUI-gestuetzte Verwaltung

Viele Einstellungen werden nicht nur ueber Befehle, sondern ueber Menues im Core verwaltet. Dazu gehoeren unter anderem:

- Inselmenue
- Core-Menue
- Upgrade-Menue
- Chunk-Menue
- Biome-Menue
- Teleport-Menue
- Besucherrechte
- Owner-/Member-Verwaltung
- Parcel-Menues
- Shop-/Zeitmodus-Menues

Einige Eingaben wie Inselname, Warpname oder Grundstuecksname laufen ueber den Chat, nachdem sie im Menue gestartet wurden.

## Befehlsuebersicht

### Allgemein

```text
/spawn
/warp
/warp <name>
```

### Insel-Grundbefehle

```text
/is
/is create
/is home
/is setspawn
/is title <text>
/is title clear
```

### Chunk-Verwaltung

```text
/is showchunks
/is hidechunks
/is chunkunlock
/is chunkapprove <insel-owner-uuid> <relX> <relZ>
```

### Master / Owner / Member

```text
/is masterinvite <spieler>
/is masteraccept
/is masterleave
/is owner add <spieler>
/is owner remove <spieler>
/is member <spieler> [build|container|redstone|all]
/is unmember <spieler> [build|container|redstone|all]
```

### Moderation

```text
/is kick <spieler>
/is ban <spieler>
/is unban <spieler>
/is pkick <spieler>
/is pban <spieler>
/is punban <spieler>
```

### Grundstuecke

```text
/is plot wand
/is plot create
/is plot delete
/is plot list
/is plot owner add <spieler>
/is plot owner remove <spieler>
/is plot member add <spieler>
/is plot member remove <spieler>
```

## Typischer Spielerablauf

Ein normaler Spieler nutzt SkyCity meist so:

1. Server betreten und am Spawn landen.
2. Mit `/is create` die eigene Insel erstellen.
3. Nach der Teleportation den Startbereich ausbauen.
4. Mit `/is` oder dem Core das Menue oeffnen.
5. Chunks freischalten und die Insel vergroessern.
6. Member, Owner oder Master hinzufuegen.
7. Grundstuecke fuer Shops, Farmen oder PvP-Bereiche anlegen.
8. Inselwert und Upgrades ueber den Core verbessern.
9. Titel, Warps, Besucherrechte und Biome anpassen.

## Technischer Hinweis

Laut `plugin.yml` ist das Plugin fuer `api-version: 1.20` ausgelegt. Hauptbefehle sind:

- `/spawn`
- `/is`
- `/warp`

Die Permission `skycity.admin` ist fuer administrative Eingriffe vorgesehen.

## Server-Konfiguration

Fuer einen SkyCity-Server sollte die Weltkonfiguration passend gesetzt werden.

In der `server.properties`:

```properties
level-name=skycity_world
```

In der `bukkit.yml` zusaetzlich:

```yml
worlds:
  skycity_world:
    generator: SkyCity
```

Damit startet der Server direkt mit der vorgesehenen SkyCity-Welt und verwendet den Generator des Plugins fuer diese Welt.
