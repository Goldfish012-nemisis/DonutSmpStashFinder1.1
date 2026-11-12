# Stash Hunter — build voor Minecraft 26.1.2

Deze addon is ingesteld op Java 25, Minecraft 26.1.2 en Meteor Client 26.1-SNAPSHOT.

## JAR bouwen via GitHub Actions

De build vereist Java 25 en Gradle 9.4.1. Die tools zijn niet beschikbaar in de huidige buildomgeving, daarom is er een GitHub Actions-workflow toegevoegd.

1. Maak een lege GitHub-repository aan.
2. Pak deze ZIP uit en upload de volledige inhoud naar de repository (inclusief de verborgen `.github`-map).
3. Open in GitHub het tabblad **Actions**.
4. Kies de workflow **build** en klik op **Run workflow** (of push een commit).
5. Open de voltooide workflow-run en download het artifact **stash-hunter**.
6. Pak het artifact uit. Plaats `stash-hunter-1.0.0.jar` in de `mods`-map van je Fabric 26.1.2-instance, samen met Meteor Client.

## Belangrijk

De workflow compileert de broncode; de exacte compatibiliteit met de gebruikte Meteor Client-build wordt pas bevestigd als de GitHub-build slaagt. Als de build faalt, stuur dan de volledige foutmelding uit de Actions-log, dan kan de code gericht worden aangepast.
