# SkyCity

SkyCity ist ein umfangreiches Minecraft-Plugin fuer Paper/Spigot 1.20, das ein eigenes Skyblock-/Inselsystem mit geschuetzter Void-Welt, Insel-Core, Fortschrittssystem, Chunk-Freischaltungen, Grundstuecken, Rechtemanagement und Teleportfunktionen bereitstellt. Jeder Spieler kann eine eigene Insel erstellen, diese schrittweise ausbauen und anderen Spielern gezielt Rechte fuer die komplette Insel oder nur fuer einzelne Bereiche geben.

Das Plugin ist nicht nur ein einfaches `/is create`, sondern ein komplettes Insel-Management-System. Es erstellt beim Start eine eigene SkyCity-Welt, teleportiert neue Spieler zum Spawn, fuehrt sie zur Inselerstellung und verwaltet danach Schutz, Fortschritt, Upgrades, Besucherrechte, Warps, PvP-Zonen und viele Detailregeln automatisch im Hintergrund.

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
- Rechte an Mitglieder, Owner oder Grundstuecks-Spieler vergeben
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

Wenn ein Spieler den Core abbauen moechte, darf das nur ein Insel-Owner. Besucher oder einfache Mitglieder koennen den Core nicht entfernen.

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

## 7. Inselrechte und Rollen

SkyCity trennt die Inselrechte sauber in mehrere Stufen.

### Master / Co-Owner

Mit:

```text
/is ownerinvite <spieler>
/is owneraccept
/is ownerleave
```

oder den Alias-Befehlen:

```text
/is masterinvite <spieler>
/is masteraccept
/is masterleave
```

koennen zusaetzliche Master eingeladen werden. Diese Rolle ist maechtiger als normale Mitglieder und eignet sich fuer enge Projektpartner.

### Owner-Verwaltung

Mit:

```text
/is owner add <spieler>
/is owner remove <spieler>
```

koennen weitere Insel-Owner hinzugefuegt oder entfernt werden.

### Mitgliederrechte / Trust-System

Mit:

```text
/is trust <spieler> [build|container|redstone|all]
/is untrust <spieler> [build|container|redstone|all]
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

## 8. Inselschutz und Besucheroptionen

Das Plugin schuetzt Inseln standardmaessig sehr strikt. Ohne Rechte duerfen Spieler typischerweise nicht:

- bauen oder abbauen
- Container oeffnen
- Redstone bedienen
- Tueren, Trapdoors oder Zauntore nutzen
- Buttons, Hebel oder Druckplatten verwenden
- bestimmte Farm-/Nutzungsbloecke bedienen
- Reittiere oder Fahrzeuge nutzen

Gleichzeitig gibt es Besucher-Einstellungen fuer Inseln und Grundstuecke. Inselbesitzer koennen damit gezielt erlauben, was Besucher duerfen, zum Beispiel:

- Tueren benutzen
- Trapdoors benutzen
- Zauntore benutzen
- Buttons / Hebel / Druckplatten nutzen
- Container verwenden
- Farmen bedienen
- reiten
- teleportieren

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
/is plot user add <spieler>
/is plot user remove <spieler>
```

Typischer Ablauf:

1. Mit `/is plot wand` den Grundstuecks-Stab holen.
2. Zwei Positionen mit dem Stab setzen.
3. Mit `/is plot create` das Grundstueck erstellen.
4. Danach Spieler als `owner` oder `user` fuer genau dieses Grundstueck eintragen.

Damit koennen innerhalb derselben Insel unterschiedliche Zonen mit eigenen Rechten entstehen.

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

## 13. Insel-Zeitmodus

Inseln koennen eine eigene Zeitdarstellung fuer Spieler haben. Das Plugin unterstuetzt verschiedene Modi wie:

- normal
- Tag
- Sonnenuntergang
- Mitternacht

Dadurch kann eine Insel atmosphaerisch angepasst werden, ohne dass die Weltzeit fuer alle Spieler gleich aussehen muss.

## 14. Biome und Inselanpassung

Ueber die Menues koennen Spieler Biome fuer Inselbereiche bzw. Chunks verwalten. Das erlaubt gestalterische Anpassungen fuer verschiedene Themenbereiche, Farmen oder Bauten.

Zusammen mit dem Chunk-System und den Insel-Templates entsteht so eine flexible Inselgestaltung.

## 15. Automatische Limits und Performance-Schutz

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

## 16. Chat- und GUI-gestuetzte Verwaltung

Viele Einstellungen werden nicht nur ueber Befehle, sondern ueber Menues im Core verwaltet. Dazu gehoeren unter anderem:

- Inselmenue
- Core-Menue
- Upgrade-Menue
- Chunk-Menue
- Biome-Menue
- Teleport-Menue
- Besucherrechte
- Owner-/Mitgliederverwaltung
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

### Master / Owner / Mitglieder

```text
/is ownerinvite <spieler>
/is owneraccept
/is ownerleave
/is masterinvite <spieler>
/is masteraccept
/is masterleave
/is owner add <spieler>
/is owner remove <spieler>
/is trust <spieler> [build|container|redstone|all]
/is untrust <spieler> [build|container|redstone|all]
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
/is plot user add <spieler>
/is plot user remove <spieler>
```

## Typischer Spielerablauf

Ein normaler Spieler nutzt SkyCity meist so:

1. Server betreten und am Spawn landen.
2. Mit `/is create` die eigene Insel erstellen.
3. Nach der Teleportation den Startbereich ausbauen.
4. Mit `/is` oder dem Core das Menue oeffnen.
5. Chunks freischalten und die Insel vergroessern.
6. Mitglieder oder Mitbesitzer hinzufuegen.
7. Grundstuecke fuer Shops, Farmen oder PvP-Bereiche anlegen.
8. Inselwert und Upgrades ueber den Core verbessern.
9. Titel, Warps, Besucherrechte und Biome anpassen.

## Technischer Hinweis

Laut `plugin.yml` ist das Plugin fuer `api-version: 1.20` ausgelegt. Hauptbefehle sind:

- `/spawn`
- `/is`
- `/warp`

Die Permission `skycity.admin` ist fuer administrative Eingriffe vorgesehen.
