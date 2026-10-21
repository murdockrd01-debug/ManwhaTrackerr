# Manhwa Tracker

App Android sencilla para llevar tu lista de manhwas.

- Importa el backup de **Komikku** (también sirve el de Mihon): trae títulos, portadas y capítulos leídos.
- Agrega manhwas a mano; la portada se busca sola en AniList.
- Botón **+** en cada tarjeta para sumar un capítulo rápido.
- Estados: Leyendo, Pausado, Terminado, Pendiente.
- Todo se guarda en tu teléfono. No usa cuentas.

## Cómo obtener el APK (sin instalar nada en la PC)

1. Entra a https://github.com y crea una cuenta gratis (o inicia sesión).
2. Crea un repositorio nuevo (botón **New**). Ponle un nombre, déjalo **Private** si quieres, y créalo.
3. En el repositorio, toca **uploading an existing file** y arrastra **todo el contenido** de esta carpeta (descomprime el .zip primero). Importante: la carpeta oculta `.github` también debe subirse. Si tu navegador no la deja arrastrar, usa **Add file → Create new file**, escribe `.github/workflows/build.yml` como nombre y pega el contenido de ese archivo.
4. Pulsa **Commit changes**.
5. Ve a la pestaña **Actions**. Verás una ejecución llamada "Compilar APK" corriendo (tarda unos 5 a 8 minutos la primera vez).
6. Cuando salga con el check verde, ábrela y baja hasta **Artifacts**. Descarga **ManhwaTracker-apk** (viene en un .zip, descomprímelo para sacar `app-debug.apk`).
7. Pasa el APK al teléfono e instálalo (Android te pedirá permitir instalar desde esa fuente).

Si la ejecución sale en rojo, copia el texto del error y envíamelo para corregirlo.

## Cómo usarla

1. En Komikku: **Más → Copia de seguridad y restauración → Crear copia de seguridad**. Se genera un archivo `.tachibk`.
2. En Manhwa Tracker: **Importar backup** y elige ese archivo.
3. Cuando leas más, repite el paso 1 y 2. Los manhwas existentes solo se actualizan, no se duplican, y no pierdes el estado que hayas puesto.
