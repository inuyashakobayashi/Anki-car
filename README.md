# Anki Overdrive Controller

Ein umfassender Anki Overdrive Fahrzeugcontroller mit Bluetooth-Verbindung, Streckenerfassung und Fahrzeugsteuerung.

## 🚀 Schnellstart

### Systemanforderungen

- **Betriebssystem**: Linux (getestet auf Pop!_OS )
- **Java**: JDK 22 (vom Projekt gefordert)
- **Maven**: 3.6.0 oder höher
- **Bluetooth**: Bluetooth 4.0+ (BLE) kompatible Adapter

### Projektstruktur

```
src/main/java/de/pdbm/anki/example/
└── AnkiControlExample.java    # Haupt-Controller-Klasse
```

**Hauptklasse**: `de.pdbm.anki.example.AnkiControlExample`

## 📋 Installation und Ausführung

### 1. Umgebung vorbereiten

Stellen Sie sicher, dass die erforderliche Software installiert ist:

```bash
# Java-Version prüfen (JDK 22 erforderlich)
java -version

# Maven-Version prüfen
mvn -version

# Bluetooth-Dienst-Status prüfen
sudo systemctl status bluetooth
```

### 2. Java 22 installieren

Falls Java 22 noch nicht installiert ist:



**Methode : SDKMAN verwenden**
```bash
# SDKMAN installieren
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Java 22 installieren und als Standard festlegen
sdk install java 22-open
sdk default java 22-open
```

### 3. Bluetooth vorbereiten

Bluetooth starten und auffindbar machen:

```bash
# Bluetooth-Controller starten
bluetoothctl

# In bluetoothctl ausführen:
power on
agent on
discoverable on
scan on
```

**Wichtig**: Anki-Fahrzeug in den Pairing-Modus versetzen (Taste gedrückt halten bis LED blinkt)



### 4. Programm ausführen

**Empfohlene Methode (mit Maven):**

```bash
mvn exec:java -Dexec.mainClass="de.pdbm.anki.example.AnkiControlExample"
```

**Alternative Methode (mit kompilierter Klasse):**

```bash
java -cp target/car-controller-1.0-SNAPSHOT.jar de.pdbm.anki.example.AnkiControlExample
```

**javadocs erstellen**
```bash
mvn javadoc:javadoc
```
## 🎮 Bedienungsanleitung

### Startablauf

1. **Programm starten** - automatische Bluetooth-Geräteerkennung
2. **Anki-Fahrzeug auswählen** aus der Liste erkannter Geräte
3. **Verbindung abwarten** (dauert normalerweise einige Sekunden)
4. **Funktion wählen** aus dem Hauptmenü

### Hauptfunktionen

```
===== 🚗 Anki Fahrzeug-Controller =====
1: 📊 Status prüfen        - Fahrzeugverbindung, Batterie, Position anzeigen
2: 🏃 Geschwindigkeit       - Fahrzeuggeschwindigkeit steuern (0-1000)
3: ↔️ Spurwechsel          - Fahrzeugposition auf der Strecke steuern
4: 🗺️ Streckenerfassung    - Automatische Streckenlayout-Erkennung
5: 🎮 Grundsteuerung       - Demonstration der Grundfunktionen
6: 🧪 Spezielle Tests      - Start-Stopp und Spurwechsel-Tests
7: 📋 Streckenbericht      - Detaillierte Streckenanalyse
8: 🔔 Benachrichtigungstest - Fahrzeugbenachrichtigungssystem testen
9: ❌ Beenden
```

### Streckenerfassung

Die Streckenerfassung ist die Kernfunktion des Programms:

1. Menüpunkt **4: 🗺️ Streckenerfassung** wählen
2. Erfassungsgeschwindigkeit eingeben (empfohlen: 300-500)
3. Fahrzeug fährt automatisch und sammelt Streckendaten
4. Enter drücken zum Stoppen
5. Gesammelte Streckenkarte und Statistiken anzeigen

## 🔧 Fehlerbehebung

### Häufige Probleme

1. **Anki-Fahrzeug nicht gefunden**
   ```bash
   # Bluetooth neu starten
   sudo systemctl restart bluetooth
   
   # Bluetooth-Controller neu starten
   bluetoothctl
   scan on
   ```

2. **Verbindung fehlgeschlagen**
    - Fahrzeugbatterie prüfen
    - Fahrzeug neu starten (Taste 5 Sekunden gedrückt halten)
    - Pairing-Modus prüfen (LED blinkt)

3. **Java-Versionsfehler**
   ```bash
   # Java 22 Verwendung sicherstellen
   java -version
   
   # Falls falsche Version, Standard neu setzen
   sudo update-alternatives --config java
   ```

4. **Berechtigungsprobleme**
   ```bash
   # Benutzer zur Bluetooth-Gruppe hinzufügen
   sudo usermod -a -G bluetooth $USER
   # Abmelden und neu anmelden
   ```

5. **Maven-Kompilierungsfehler**
   ```bash
   # Bereinigen und neu kompilieren
   mvn clean compile
   
   # Bei weiteren Problemen Java-Umgebung prüfen
   mvn -version
   echo $JAVA_HOME
   ```



## 📝 Projektmerkmale

- **Benutzerfreundliche Oberfläche**: Verwendung von Emoji-Icons für bessere Übersicht
- **Echtzeitüberwachung**: Live-Verfolgung von Fahrzeugposition und Streckendaten
- **Intelligente Erfassung**: Automatische Erkennung verschiedener Streckentypen
- **Umfassende Tests**: Verschiedene Testfunktionen zur Systemverifikation
- **Detaillierte Berichte**: Ausführliche Streckenanalyse mit Statistiken

## 🛠️ Entwicklungshinweise

### Funktionen erweitern

Neue Funktionen hinzufügen:

1. Neue Methoden in `AnkiControlExample.java` hinzufügen
2. Neue Option in der Hauptmenü-Switch-Anweisung hinzufügen
3. Neu kompilieren: `mvn clean package`

### Abhängigkeiten

Hauptabhängigkeiten des Projekts:
- **Anki Janki SDK**: Kern-Fahrzeugsteuerung
- **Bluetooth BLE Library**: Bluetooth Low Energy Kommunikation
- **SLF4J**: Protokollierung



## 📊 Streckentypen

Das System erkennt folgende Streckenelemente:

- **➡️ STRAIGHT**: Gerade Streckenabschnitte
- **🔄 CORNER**: Kurven
- **🏁 START/FINISH**: Start-/Ziellinie
- **✖️ INTERSECTION**: Kreuzungen


