# T.F.N.C. - Torrent File Name Cleaner

## Opis

**TFNC (Torrent File Name Cleaner)** to aplikacja w Javie, która usuwa niechciane frazy z nazw plików i katalogów (np. dodatki z trackerów torrent).
Program działa w dwóch trybach:
- **GUI** (okno Swing),
- **CLI** (z terminala, skryptowalnie).

## Wymagania

- **Java 21**
- system Linux/Windows/macOS

## Budowanie projektu (Gradle 9.4.1)

Projekt został przeniesiony na **Gradle Wrapper 9.4.1**, więc nie musisz instalować Gradle globalnie.

```bash
chmod +x gradlew
./gradlew clean build --console=plain
```

Po buildzie:
- jar: `build/libs/T.F.N.C.-1.0-beta-4.jar`
- zależności runtime: `build/libs/lib/`

## Uruchamianie

### 1) Tryb GUI (domyślny)

```bash
./gradlew run --console=plain
```

lub bezpośrednio:

```bash
java -jar build/libs/T.F.N.C.-1.0-beta-4.jar --gui
```

### 2) Tryb CLI

Składnia:

```bash
java -jar build/libs/T.F.N.C.-1.0-beta-4.jar --cli <katalog> [dodatkowe-zakazane-słowo-1] [dodatkowe-zakazane-słowo-2] ...
```

Przykład:

```bash
java -jar build/libs/T.F.N.C.-1.0-beta-4.jar --cli "/dane/Filmy" "[nowy-tracker.example]"
```

W trybie CLI aplikacja **zawsze ładuje listę zakazanych słów z `blacklist.txt`**.  
Argumenty po ścieżce katalogu są opcjonalne i działają jako **dodatkowe słowa spoza listy** (tylko dla bieżącego uruchomienia).

### 3) Diagnostyka problemów z GUI

```bash
java -jar build/libs/T.F.N.C.-1.0-beta-4.jar --diagnose-gui
```

### 4) Cofanie zmian nazw z `history.txt`

```bash
java -jar build/libs/T.F.N.C.-1.0-beta-4.jar --undo-history <katalog>
```

To polecenie czyta `history.txt` i próbuje odwrócić zmiany w odwrotnej kolejności.

W GUI dostępny jest także przycisk **Undo from history** (cofa zmiany dla katalogu z pola `Directory`).

> Ważne: stare wpisy historii miały tylko nazwy (`old -> new`) bez pełnych ścieżek.  
> Dla takich wpisów cofanie działa wyłącznie wtedy, gdy w podanym katalogu jest dokładnie **jedno** dopasowanie nazwy.
> Przy wieloznaczności wpis jest pomijany, żeby nie zepsuć nazw bardziej.

## Testy

Uruchom testy (także gdy aktualnie nie ma jeszcze testów jednostkowych):

```bash
chmod +x gradlew
./gradlew test --console=plain
```

## Pliki robocze tworzone przez aplikację

W katalogu uruchomienia aplikacji aplikacja tworzy przy starcie (jeśli nie istnieją):
- `blacklist.txt` – zapis listy zakazanych fraz,
- `history.txt` – historia zmian nazw,
- `error.txt` – log błędów.

## Licencja

Projekt jest udostępniony na warunkach opisanych w pliku `LICENSE`.
