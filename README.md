# Truevisuals (Fabric 1.21.4, klient)

Klientowy mod z własnymi "visualsami":

- wirujące, kolorowe pierścienie (smugi) wokół graczy
- świecący okrąg pod graczem + rozchodząca się fala
- magiczny krąg z obracającym się napisem
- gwiazdki unoszące się wokół graczy i w powietrzu
- mały HUD "Potions"
- menu (**prawy Shift**): tło void z gwiazdami, mgławicami i spadającymi gwiazdami, animacja otwierania/zamykania
- ragdoll: po zabiciu gracza jego postać (ze skinem) wylatuje w górę, spada, odbija się, leży 3 s i znika

Ustawienia: `config/visuals.json` (albo z menu).

## Budowanie
Wrzuć repo na GitHub – workflow `build` zbuduje jar (Actions -> artifact `visuals-mod`).
Lokalnie: `gradle build` (Java 21, Gradle 8.12).
