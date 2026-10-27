# Wynik weryfikacji — 26.10.2026

## Aktualizacja 1.0.1 — kolorystyka

Zbudowano APK z grafitowo-szarą paletą i białymi akcentami. Ponownie uruchomiono trzy istniejące testy na emulatorze: 3/3 zakończone sukcesem. Odświeżono podglądy w `screenshots/` i wizualnie sprawdzono ekran powitalny. Testów jednostkowych i Lint nie powtarzano przy zmianie samych kolorów i numeru wersji; ich wynik bazowy podano poniżej.

## Weryfikacja bazowa 1.0.0

- Kompilacja `assembleDebug`: sukces.
- Testy jednostkowe `testDebugUnitTest`: **20/20**, bez błędów.
- Android Lint: **0 błędów**; 6 ostrzeżeń informacyjnych (nowsze wersje narzędzi/zależności testowych, lokalizacja tekstów, reguły backupu). Zaszyfrowane dane aplikacji są przechowywane w `noBackupFilesDir`.
- Testy na emulatorze `Medium_Phone`, przez AndroidJUnitRunner: **3/3**, bez błędów, około 20 sekund.
- Przetestowano rzeczywiste połączenie HTTP do lokalnego serwera testowego, nagłówek autoryzacji, strumień SSE, renderowanie odpowiedzi i zapis historii.
- Przetestowano Android Keystore: ponowny odczyt zapisanego klucza i brak jawnej wartości klucza w pliku danych.
- Przetestowano nawigację Czat / Historia / Połączenia.
- Obejrzano zrzuty ekranu powitalnego i czatu: brak obcięć i nakładania elementów w rozdzielczości emulatora 1080×2400.

Zrzuty: `screenshots/welcome.png`, `screenshots/chat.png`, `screenshots/connections.png`. Widoczna na zrzucie rozmowa pochodzi z serwera testowego.

Nie wykonywano płatnych wywołań rzeczywistych dostawców — w projekcie nie ma kluczy użytkownika. Adaptery czterech protokołów zweryfikowano na danych testowych; działanie konkretnego modelu trzeba sprawdzić po wpisaniu własnego klucza i jego identyfikatora. Testy nie obejmują jeszcze fizycznego telefonu użytkownika.

Standardowe zadanie Gradle `connectedDebugAndroidTest` zatrzymało się przy pobieraniu dodatkowych zależności Unified Test Platform (błąd DNS). Te same zbudowane testy następnie pomyślnie uruchomiono bezpośrednio przez ADB. Skrypt `build-android.ps1 -DeviceTest` korzysta z tej zweryfikowanej ścieżki.
