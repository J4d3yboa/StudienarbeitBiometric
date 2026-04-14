Dieses Repository enthält die Quelltexte der Android-Applikation "StudienarbeitBiometric". Die Software wurde spezifisch für die Datenerfassung und -verarbeitung von biometrischen Daten auf Smartwatches entwickelt. Um eine fehlerfreie Ausführung zu garantieren, sind bestimmte Systemanforderungen sowie ein strikter Build-Prozess einzuhalten.

Systemvoraussetzungen:
- Android Studio Otter (zwingend vorgeschriebene IDE für dieses Projekt).
- Git (zur lokalen Replikation des Repositories).
- Ausreichende Hardware-Ressourcen zur Virtualisierung eines Wear OS Emulators.

Installation und Inbetriebnahme:
- Klone das Repository über dein Terminal: git clone https://github.com/J4d3yboa/StudienarbeitBiometric.git
- Öffne die heruntergeladenen Projektdateien in Android Studio Otter.
- Initiiere einen vollständigen Gradle Sync. Dieser Schritt ist essenziell, um sämtliche Abhängigkeiten und Bibliotheken korrekt aufzulösen.
- Führe den Build-Prozess über das Gradle-Bausystem aus. Eine erfolgreiche Kompilierung ist Voraussetzung für die Erzeugung lauffähiger Artefakte.

Emulation und Testumgebung:
- Öffne den Device Manager innerhalb von Android Studio.
- Erstelle ein neues virtuelles Gerät (Virtual Device) zur Simulation.
- Definiere bei der Hardware-Auswahl zwingend ein Watch OS (Wear OS) Profil. Nur so lässt sich die spezifische Systemumgebung und Sensorik der Smartwatch korrekt emulieren.
- Führe die kompilierte Anwendung auf dem vollständig gestarteten Watch OS Emulator aus.

