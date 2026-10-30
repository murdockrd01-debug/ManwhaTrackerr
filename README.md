# Manhwa Tracker

App Android sencilla para llevar tu lista de manhwas.

- Importa el backup de **Komikku** (también sirve el de Mihon): trae títulos, portadas y capítulos leídos.
- Agrega manhwas a mano; la portada se busca sola en AniList.
- Botón **+** en cada tarjeta para sumar un capítulo rápido.
- Estados: Leyendo, Pausado, Terminado, Pendiente.
- **Buscar y ordenar** la lista (recientes, A–Z, casi terminados, más por leer). Recuerda el orden que elegiste.
- **Deshacer** el +1 si lo tocaste por error (aparece un aviso abajo con el botón Deshacer).
- **Estudio de inglés**: guarda palabras o frases desde la barra de notificaciones o seleccionando texto en otra app; la traducción y el significado se buscan solos.
- **Repaso con tarjetas**: repaso espaciado (1, 3, 7, 14 y 30 días). Lo que fallas vuelve a salir en la misma ronda.
- **Modo escritura** en el repaso: ves la traducción en español y escribes la palabra en inglés (perdona mayúsculas, signos y una errata en palabras largas; tiene botón de *Pista*).
- **Pronunciación**: botón 🔊 en cada palabra y en el repaso, con la voz en inglés de tu teléfono (sin internet).
- **Palabras por manhwa**: en el detalle de cada manhwa ves las palabras que guardaste de él y puedes repasar solo esas.
- **Estadísticas**: racha de días repasando, palabras aprendidas por semana, capítulos leídos por mes y de qué obras aprendes más (menú lateral → *Estadísticas*).
- **Nota y comentario** por manhwa (1 a 10), y orden por *Mejor calificados*.
- **Cuadrícula de portadas**: botón *Orden y vista* → *Ver como cuadrícula*.
- **Sincronización automática** (menú lateral → *Sincronización y copias*): la app lee sola la copia de seguridad automática de Komikku de la carpeta que elijas (se actualiza al abrir la app), y guarda sola una copia de tus datos en otra carpeta (una por día, se conservan las 7 últimas).
- **Mi copia (exportar / restaurar)**: menú ⋮ → *Exportar mi copia* guarda un archivo `.json` con tus manhwas y tu vocabulario. *Restaurar mi copia* lo mezcla con lo que ya tengas, sin borrar nada.
- Todo se guarda en tu teléfono. No usa cuentas.

Descargar el APK

Ve a [Releases](../../releases) y descarga `ManhwaTracker.apk` de la última versión.
Pásalo al teléfono e instálalo (Android pedirá permitir instalar desde esa fuente).
Cada versión nueva se instala encima de la anterior sin borrar tus datos.

## Cómo usarla

1. En Komikku: **Más → Copia de seguridad y restauración → Crear copia de seguridad**. Se genera un archivo `.tachibk`.
2. En Manhwa Tracker: **Importar backup** y elige ese archivo.
3. Cuando leas más, repite el paso 1 y 2. Los manhwas existentes solo se actualizan, no se duplican, y no pierdes el estado que hayas puesto.
