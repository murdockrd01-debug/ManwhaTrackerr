# Traspaso del proyecto — Manhwa Tracker

Documento para que **cualquier otra sesión o cuenta de Claude** continúe el trabajo exactamente donde quedó, sin que el usuario tenga que editar nada en GitHub. Léelo completo antes de tocar código. Mantenlo al día (ver "Registro de trabajo" al final).

## 1. Qué es y quién es el usuario
- App Android (Kotlin + Jetpack Compose, Material 3) para llevar la lista de manhwas leídos en **Komikku**, con estudio opcional de inglés (vocabulario, repaso con tarjetas, captura de texto, voz).
- Repo: `murdockrd01-debug/ManwhaTrackerr` (también responde `manwhatrackerr`). Rama de trabajo: **`main`**.
- El usuario habla **español** y no programa. **Responde siempre en español**, corto y claro. No puede probar nada tú por él: tampoco tú tienes celular. Todo se verifica con la compilación de GitHub Actions.
- Preferencias ya expresadas: quiere funciones realmente útiles, no recordatorios, widgets, notas por capítulo, metas, ni exportar a CSV/Anki. Le molestan las pantallas largas: prefiere simple pero sin perder opciones. Quiere que el avance de lectura se registre solo, sin copias.
- **El usuario NO debe tener que hacer nada en GitHub** (ni PR, ni merge, ni secretos, ni subir archivos). Empuja directo a `main`. No dejes trabajo en ramas ni PR sin fusionar.

## 2. Cómo conseguir acceso al repo (sesión de Claude Code en la nube)
1. Si el repo no está en el disco, usa la herramienta `mcp__claude-code-remote__add_repo` (owner `murdockrd01-debug`, repo `ManwhaTrackerr`, `access: "push"`), clónalo con el comando que devuelva y luego `register_repo_root`. No pre-verifiques con curl: da 404 aunque exista.
2. `gh` ya viene configurado con el proxy. GraphQL no funciona; usa `gh api repos/...` (REST).
3. `gh secret ...` está bloqueado: **no intentes tocar secretos**. Ya están puestos.
4. Si ves un aviso "This repository moved", es normal.

## 3. Flujo de trabajo para CADA versión (así se ha hecho siempre)
1. `git pull --rebase origin main` **antes de empezar** y mira `git log --oneline -10`. Ha habido sesiones de varias cuentas a la vez; no sobrescribas trabajo ajeno. Si algo ya existe en `main`, mejóralo en vez de rehacerlo.
2. Cambia el código. Cada función nueva con lógica pura lleva **pruebas unitarias** en `app/src/test/java/com/manhwatracker/app/...`.
3. Sube la versión en `app/build.gradle.kts` (`versionCode` +1 y `versionName`) con `sed`.
4. Añade una sección `## <versión>` **al principio** de `app/src/main/assets/changelog.md` (2–3 viñetas en español; la usa la app para "Novedades" y la publicación de la release).
5. Textos nuevos de la interfaz → agrégalos a `TrData.kt` para la traducción al inglés (ver sección 5).
6. Commit con estos finales (usa el enlace de **tu** sesión si el sistema te lo da):
   ```
   Co-Authored-By: Claude <noreply@anthropic.com>
   Claude-Session: <url de tu sesión>
   ```
7. `git pull -q --rebase origin main && git push -q origin main`
8. Los pushes hechos por el proxy **no disparan** la compilación. Lánzala a mano:
   `gh workflow run build.yml -R murdockrd01-debug/manwhatrackerr --ref main`
   luego `gh run list -R murdockrd01-debug/manwhatrackerr -L1 --json databaseId,status,headSha` y consulta cada ~10 s con `gh run view <id> --json status,conclusion` hasta `completed`.
9. **Verifica que `headSha` de la ejecución == `git log -1 --format=%H`** (que se compiló tu commit y no otro).
10. Si falla: los logs completos dan 403. El workflow publica los errores como anotaciones; léelos así (termina con `; true` porque `grep` sin coincidencias devuelve 1 y parece un error falso):
    ```
    j=$(gh run view <id> -R murdockrd01-debug/manwhatrackerr --json jobs -q '.jobs[0].databaseId')
    gh api repos/murdockrd01-debug/ManwhaTrackerr/check-runs/$j/annotations -q '.[]|.message' | grep -E "^e: |FAILED|Failure|Test|Error"; true
    ```
11. Si compila, el workflow ya publica la release `v<versionName>` con `ManhwaTracker.apk` y las notas del changelog. Enlace fijo de descarga para el usuario:
    `https://github.com/murdockrd01-debug/ManwhaTrackerr/releases/latest/download/ManhwaTracker.apk`
12. Dile al usuario en español qué cambió, que no pudiste probarlo en un celular y dónde descargar.

La firma del APK sale del secreto `KEYSTORE_BASE64` (misma clave siempre, para que las actualizaciones se instalen encima). **Nunca** subas un keystore al repo ni cambies el firmado.

## 4. Mapa del código (`app/src/main/java/com/manhwatracker/app/`)
- `MainActivity.kt` — biblioteca (`ListScreen`: banda tocable, botón Filtros con hoja, orden, cuadrícula), tarjetas, detalle.
- `AppShell.kt` — navegación entre secciones y barra superior. `MoreScreen.kt` (menú Más, Apariencia, Icono), `SettingsScreen.kt` (Ajustes: lista corta con subpáginas General / Captura rápida / Actualizaciones / Ayuda y errores; `IntroGuide`).
- `StatsScreen.kt` — estadísticas, calendario de actividad. `RatingsScreen.kt`, `DiscoverScreen.kt`, `StudyScreen.kt`, `ReviewScreen.kt` (repaso: Tarjetas/Escritura/Voz), `SyncScreen.kt` (sincronización Komikku + AniList).
- **Lector integrado**: `ReaderActivity.kt` (UI: panel "Leer" conectado a la biblioteca, lista de capítulos con estados, lectura vertical con zoom, capítulo siguiente/anterior), `data/ReaderIndex.kt` (índice de lo descargado y capítulos por leer), `data/ReaderSource.kt` (carpetas SAF y .cbz/.zip extraídos a caché), `data/ReaderLogic.kt` (orden natural, zoom, páginas), `data/ReaderStore.kt` (posición y capítulos terminados), `data/ReaderProgress.kt` (al terminar un capítulo suma el avance en la biblioteca).
- Captura de texto: `CaptureService.kt` (servicio de accesibilidad), `CaptureOverlay.kt`, `CaptureBubble.kt`, `CaptureTile.kt`, `QuickInputActivity.kt`, `QuickAdd.kt`, `ScanActivity.kt`, `SaveWordActivity.kt`.
- `MainViewModel.kt` — estado y acciones (importar, `syncAniList`, `autoSync`, avance, estudio).
- `data/` — Room (`Models.kt` DAOs y `AppDatabase`, versión 8, migraciones 1→8; `Entities.kt`), `Stats.kt`, `ListImport.kt`, `AniList.kt`, `Study.kt` (`Prefs`), `Changelog.kt`, `CrashLog.kt`, `Update*.kt`, `Pronounce.kt`, etc.
- `Tr.kt` + `TrData.kt` — traducción.  `Theme.kt` — paletas.  `ManhwaApp.kt` — registro de errores.

## 5. Convenciones y trampas conocidas
- **Interfaz en español; `TrData.kt` traduce al inglés.** En el paquete `com.manhwatracker.app`, `Text` es un reemplazo propio (`Tr.kt`) que traduce solo; **no importes** `androidx.compose.material3.Text` ahí. Para textos fuera de `Text` usa `tr("...")`. Claves duplicadas en `mapOf` no fallan: gana la última (cuidado con choques de significado).
- Base de datos: si cambias entidades, sube la versión de Room **y escribe la migración**; el usuario tiene datos reales y no se pueden perder.
- Dependencias: Kotlin 2.0.20, Compose BOM 2024.09.00, AGP 8.5.2, Gradle 8.9, JDK 17, minSdk 26, target 34, Coil 2.7, ML Kit text-recognition 16.0.1. Evita añadir dependencias pesadas (el usuario quiere el APK liviano).
- Android 13+: la captura por accesibilidad pide "Permitir ajustes restringidos" en apps instaladas fuera de la tienda.
- No hay pruebas de interfaz. Todo lo que dependa del tacto (gestos, animaciones, accesibilidad, voz) **está sin verificar en dispositivo**: dilo con honestidad.
- Sigue el estilo: comentarios en español, funciones puras para lo que se pueda probar.

## 6. Estado actual
- Última versión publicada: ver `versionName` en `app/build.gradle.kts` y `changelog.md`.
- Hecho y compilado: biblioteca con filtros, estadísticas con calendario, Ajustes simplificados, sincronización con AniList (el progreso que Komikku sube a AniList se trae al abrir la app), lector integrado fase 1 y 2.
- **Verificado por el usuario en su teléfono:** la sincronización con AniList ("funciona correctamente"). Lo demás se considera sin probar hasta que él diga lo contrario.

## 7. Hoja de ruta (en este orden, salvo que el usuario diga otra cosa)
Lector integrado (idea del usuario: leer dentro de la app, poco a poco; Komikku sigue siendo la fuente de capítulos descargados):
1. ✅ Fase 1: lector (carpeta o .cbz, zoom, barra, siguiente/anterior, continuar).
2. ✅ Fase 2: al terminar un capítulo desde la carpeta de descargas de Komikku (`<fuente>/<manhwa>/<capítulo>`), sube el capítulo en la biblioteca (`ReaderProgress`). Pendiente: **recordar la carpeta de descargas** para abrirla directo y mostrar "Continuar leyendo" desde la biblioteca (botón en la tarjeta del manhwa que abra su carpeta/capítulo siguiente).
3. Fase 3: **captura de palabras dentro del lector** (OCR ML Kit sobre la imagen de la página, sin accesibilidad ni capturas de pantalla; tocar un globo y guardar la palabra con el manhwa y capítulo que se está leyendo).
4. Fase 4 (opcional): fuente en línea con API pública (p. ej. MangaDex).
Otras ideas aceptadas o pendientes: unir duplicados (por `anilistId` y similitud de título, con tabla de alias en una migración v9 para que las importaciones de Komikku no los recreen), historial real de lectura desde la copia de Komikku (campo `history` 104 del `.tachibk`), pantalla "Hoy", estado de publicación de AniList en las tarjetas, ritmo/fecha estimada de fin, APK más liviano (R8/minify, con mucho cuidado), modo discreto.

## 8. Mensaje para pegar al empezar una sesión nueva (el usuario lo copia tal cual)
> Continúa el proyecto Manhwa Tracker (repo `murdockrd01-debug/ManwhaTrackerr`, rama main). Lee `HANDOFF.md` y `CLAUDE.md` del repositorio y sigue exactamente ese flujo: haz `git pull --rebase`, revisa `git log` para ver qué hizo la sesión anterior, y continúa con la hoja de ruta (sección 7). Trabaja tú directamente: programa, sube a `main`, lanza la compilación y verifica el resultado; no me pidas editar nada en GitHub. Respóndeme en español y dime al final qué cambió y dónde descargar el APK.

## 9. Registro de trabajo (añade una línea por sesión: fecha, qué hiciste, qué quedó pendiente)
- 2026-11-11 — v4.4 filtros/calendario, v4.5 Ajustes simplificados, v4.6 lector (otra cuenta, fusionado y verificado), v4.7 seguimiento del lector → biblioteca + este documento. Pendiente: fase 2 (recordar carpeta/continuar desde la tarjeta) y fase 3.
- 2026-11-11 — v4.8 (fusionada a main a petición del usuario, que prefirió actualizar desde la propia app; aún sin probar en celular): botón "▶ Continuar leyendo" en la ficha del manhwa abre el lector directo en el capítulo que toca (`ReaderActivity.continueIntent`, `ReaderSource.findManhwaDir`, `ReaderProgress.pickChapter`); la carpeta de descargas elegida se recuerda (`ReaderStore.downloads`). Komikku queda como botón secundario. El CI publica las ramas distintas de main como pre-lanzamiento `prueba-<rama>` (no "latest"). También corrige la prueba `match` de v4.7, que rompía la compilación (v4.7 nunca llegó a publicarse). Pendiente: probar en celular; botón en la tarjeta de la biblioteca; fase 3 (captura de palabras en el lector).
- 2026-11-11 — v4.9: botón "Leer" (ícono ▶) en la barra de navegación inferior que abre `ReaderActivity` (el usuario confirmó que el lector funciona bien en su celular; carpeta de descargas: Documentos/manwhas/download). Pendiente: botón en la tarjeta de la biblioteca; fase 3.
- 2026-11-12 — revisión completa tras el trabajo de otra cuenta (v4.8 Continuar leyendo, v4.9 botón Leer): todo compila y pasa pruebas. v5.0: la carpeta del manhwa se elige por mejor coincidencia (nombre exacto antes que recortado).
- 2026-11-12 — v5.1: ventana "Leer" rediseñada como panel (continuar, por leer, al día) sincronizado en vivo con la biblioteca; índice de descargas; estados de capítulo según la biblioteca; deshacer al terminar un capítulo. Pendiente: probar en dispositivo; fase 3 (captura de palabras dentro del lector).
- 2026-11-13 — v5.2: lectura continua (los capítulos se encadenan al bajar; `Seg` + `ReaderLogic.flatIndex/locate/shouldLoadNext/finished`). Sin probar en dispositivo. Pendiente: fase 3 (captura de palabras dentro del lector).
- 2026-11-14 — v5.2.2: arregla que no se pudiera volver al capítulo anterior (la caché de .cbz borraba todos los demás capítulos extraídos; ahora conserva 6, `ReaderLogic.toEvict`) y añade lectura continua hacia arriba (`loadingPrev`/`noPrev` en `ReaderScreen`).
