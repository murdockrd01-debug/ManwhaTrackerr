## 5.10.0
- Descargas sin conexión mejoradas: ahora corren con una notificación ("Descargando para leer sin conexión"), así que siguen aunque minimices la app o bloquees el teléfono. Si la app se cierra a mitad, al volver a abrir "Leer" se retoman solas.
- Se guarda la carátula de cada manhwa descargado, y se ve sin conexión.
- En "Leer › Descargados" ves cuánto espacio ocupa cada manhwa y el total, y un botón 🗑 para borrar todo lo bajado de uno.

## 5.9.1
- Corrección interna para que compile el modo sin conexión (misma función que 5.9.0).

## 5.9.0
- Arreglado el aviso "The coroutine scope left the composition" dentro de los capítulos: aparecía al pasar rápido por las páginas y ya no se muestra como error (la página simplemente se vuelve a pedir).
- Modo sin conexión: en la ficha de cada manhwa, junto a cada capítulo hay un botón ⬇ para bajarlo al teléfono, y arriba "⬇ Siguientes 5" (desde donde ibas) y "⬇ Todos". Los capítulos bajados llevan ✓ (con 🗑 para borrarlos) y se leen sin internet. En "Leer" aparece la sección "Descargados" para abrirlos sin conexión. Las descargas siguen mientras la app esté abierta.

## 5.8.2
- Ficha del manhwa: se quitaron los botones − y + del capítulo (el avance es automático). El número se puede seguir corrigiendo a mano.

## 5.8.1
- Pantalla principal: se quitó el botón "+" de las tarjetas (en lista y en cuadrícula).

## 5.8.0
- Carátulas: en Más › Apariencia puedes elegir su tamaño (Pequeña, Mediana, Grande, Muy grande) para la cuadrícula y la lista de la pantalla principal.
- Filtro +18: opción (apagada por defecto) para difuminar las carátulas de los manhwas +18 (género Hentai, y Ecchi si lo activas). En la ficha de cada manhwa puedes marcarlo o quitarle la marca a mano. En Android 12 o más es un desenfoque; en versiones anteriores, un mosaico.

## 5.7.2
- Diagnóstico de fuentes: el error que se copia ahora incluye las últimas peticiones de red (dirección y código de respuesta), para ver qué recibió la fuente.

## 5.7.1
- Fuentes: el botón "Quitar" de una extensión instalada ya funciona (faltaba el permiso de Android para desinstalar). Si el desinstalador no se pudiera abrir, ahora lo avisa.

## 5.7.0
- Lo que empiezas a leer en una fuente se agrega solo a tus manhwas (en "Leyendo"), y se guarda el avance al terminar cada capítulo. En la ficha de cada manhwa hay un botón "Agregar a mis manhwas" (queda en "Pendiente") o el aviso "Está en tus manhwas".

## 5.6.4
- Continuar leyendo: ya abre el capítulo que sigue aunque la fuente no traiga el número de capítulo (se lee del nombre, como hace Komikku). Si la fuente no numera sus capítulos, lo avisa.

## 5.6.3
- "Continuar leyendo" te lleva justo donde ibas: al capítulo que sigue al último que terminaste y, si lo dejaste a medias, a la página donde te quedaste. La primera vez, si un resultado de la búsqueda se llama igual que tu manhwa, se abre solo.

## 5.6.2
- Lector en línea: zoom con dos dedos (hasta 4×), doble toque para acercar o volver, y arrastre de lado cuando la página está ampliada. El desplazamiento hacia abajo sigue igual.

## 5.6.1
- "Continuar leyendo" (en cada manhwa de tu lista) ahora abre la lectura en línea de la app, no las descargas de Komikku. Si ya abriste ese manhwa en una fuente, va directo al capítulo siguiente al que llevas; si no, lo busca en la última fuente que usaste y, al elegirlo, la app recuerda el vínculo.
- Al leer en línea, el avance se guarda en el manhwa vinculado aunque la fuente lo llame distinto.

## 5.6.0
- "Leer" ahora es la lectura en línea: el botón Leer abre directamente tus fuentes (extensiones), con "Continuar donde te quedaste", acceso a Fuentes y a la última fuente que usaste. Leer archivos descargados (Komikku, .cbz) sigue disponible abajo del todo y en Más › Lector.

## 5.5.3
- Explorar recuerda dónde estabas: al abrirlo vuelve a la última fuente, y en el inicio aparece "Continuar donde te quedaste" con el último manhwa que abriste. Girar el teléfono ya no reinicia la pantalla.

## 5.5.2
- Leer: nueva tarjeta "Explorar fuentes" arriba (con acceso rápido a Fuentes), para leer en línea con tus extensiones sin buscar en Más.

## 5.5.1
- Explorar: las fuentes que exigen el interceptor de Cloudflare (como las de keiyoushi) ya cargan; si una página pide verificación, la app la resuelve con un WebView interno (hasta 30 segundos).

## 5.5.0
- Nuevo "Explorar" (Más › Explorar, y botón en Fuentes): ves las fuentes de tus extensiones instaladas, sus manhwas populares y recientes, buscas, abres la ficha con sus capítulos y los lees en línea. Al llegar a la última página, el capítulo se guarda en tu manhwa si ya lo tienes en tu lista. Experimental: algunas fuentes (Cloudflare, JavaScript) aún no funcionan.

## 5.4.0
- Primera prueba del motor de extensiones (experimental): en Más › Fuentes, las extensiones instaladas tienen un botón "Probar" que las carga dentro de la app y muestra los manhwas populares de su primera fuente. Si falla, muestra el error para copiarlo. Todavía no se puede leer ni buscar.

## 5.3.5
- Mantenimiento interno: herramientas de compilación actualizadas (preparación para leer con extensiones). Sin cambios visibles.

## 5.3.4
- Fuentes: ya lee el índice nuevo (`index.pb`) de keiyoushi: aparece la lista completa de extensiones con su ícono, y se instalan con la versión 1.6.

## 5.3.3
- Fuentes: detecta mejor las listas falsas de "Outdated app", descarta las que quedaron guardadas, y al actualizar un repositorio con formato nuevo copia el diagnóstico solo.

## 5.3.2
- Fuentes: entiende el índice nuevo `index.json` y detecta cuando un repositorio solo trae avisos de "Outdated app". Si el repositorio solo ofrece `index.pb`, aparece un botón "Copiar diagnóstico" para poder enseñarle ese formato a la app.

## 5.3.1
- Fuentes: ahora acepta la dirección de la página de GitHub del repositorio (por ejemplo github.com/keiyoushi/extensions) y la convierte sola a la dirección correcta.

## 5.3
- Nueva sección "Fuentes" (Más › Fuentes): agrega los repositorios de extensiones de Komikku/Mihon con su dirección, mira la lista con búsqueda e idiomas, e instala, actualiza o quita extensiones. Es la primera fase: las extensiones se instalan igual que en Komikku, y todavía no se usan para leer dentro de la app.
- La app no trae ningún repositorio; tú pones el que quieras.

## 5.2.2
- Arreglo: ahora puedes volver al capítulo anterior. Antes, al cargar el siguiente capítulo (.cbz) se borraba el anterior y ya no se podía regresar.
- La lectura continua también funciona hacia arriba: al subir hasta el principio de un capítulo, aparece el anterior encima. "‹ Anterior" lleva al inicio del capítulo anterior.

## 5.2.1
- Barra de abajo: los nombres de las secciones ya no se parten en dos líneas.

## 5.2
- Lectura continua: al llegar al final de un capítulo, el siguiente aparece debajo y sigues bajando sin tocar nada. La barra de arriba y la de página cambian al capítulo que estás leyendo.
- Cada capítulo se marca como leído (y se suma a tu biblioteca) al pasar por su final, con la opción de deshacer en la separación entre capítulos.

## 5.1
- La ventana "Leer" ahora es un panel conectado a tu biblioteca: "Continuar donde te quedaste", los manhwas con capítulos descargados por leer (con portada, tu avance y cuántos te faltan) y los que ya estás al día.
- La app recorre tu carpeta de descargas de Komikku y sabe qué capítulos tienes sin leer. El botón de actualizar vuelve a recorrerla.
- La lista de capítulos marca lo leído según tu biblioteca (aunque lo hayas leído en Komikku), el que sigue y el que dejaste a medias. Si el manhwa no está en tu biblioteca, puedes agregarlo desde ahí.
- Un capítulo cuenta como leído al llegar al final, y en "Fin del capítulo" puedes deshacerlo.

## 5.0
- "Continuar leyendo" elige mejor la carpeta del manhwa: si hay dos con nombres parecidos, prefiere la que se llama igual.

## 4.9
- Nuevo botón "Leer" en la barra de abajo: abre el lector directo, sin pasar por Más.

## 4.8
- Continuar leyendo: en la ficha de cada manhwa, el botón ahora abre el lector de la app directo en el capítulo que sigue (o en el que dejaste a medias). Komikku queda como botón aparte para descargar capítulos nuevos.
- El lector recuerda la carpeta de descargas de Komikku que elegiste.

## 4.7
- El lector ahora suma tu avance: al terminar un capítulo descargado de Komikku, el capítulo leído del manhwa sube solo en tu biblioteca (y cuenta en el calendario de actividad).

## 4.6
- Nuevo lector integrado (Más › Lector): lee capítulos descargados desde una carpeta o un archivo .cbz/.zip, con zoom, barra de progreso, capítulo siguiente/anterior y "Continuar" donde lo dejaste.

## 4.5
- Ajustes rediseñados: una lista corta (General, Captura rápida, Actualizaciones, Ayuda y errores) y cada una abre su propia página. Todas las opciones siguen ahí.
- Más: filas más compactas en grupos (Datos, Personalizar, App) y las copias manuales juntas en una sola fila.

## 4.4
- Biblioteca: los números de arriba ahora filtran al tocarlos, y listas y géneros están en un botón "Filtros" siempre visible.
- Estadísticas: nuevo calendario de actividad con días activos, capítulos del mes y racha de lectura.

## 4.3
- Progreso casi en tiempo real: Komikku sube tu avance a AniList y la app lo trae al abrirla, sin copias. Configúralo en Más › Sincronización.

## 4.2
- Registro de errores en Ajustes: si la app se cierra sola, el detalle queda guardado y lo compartes con un toque.
- La copia automática ya no se reescribe si no cambió nada desde la última.

## 4.1
- Novedades: ahora la app muestra qué trae cada versión, al buscar actualizaciones y la primera vez que la abres después de actualizar.

## 4.0
- Nuevo modo Voz en el Repaso: dices la palabra en inglés y la app comprueba tu pronunciación.

## 3.9
- Burbuja flotante: tócala para escribir una palabra sin salir de Komikku, mantenla pulsada para capturar la pantalla.
- La barra de notificación vuelve a aparecer si la quitas deslizándola, y hay un botón en Ajustes para mostrarla otra vez.

## 3.8
- Captura rápida de texto: con el botón de accesibilidad, las teclas de volumen o un mosaico del panel rápido, la app lee los globos de la pantalla y guardas los que toques.

## 3.7
- Guardar texto de una imagen compartida: la app lee el texto con OCR y guardas frases o palabras.

## 3.6
- El Repaso trae primero las palabras más útiles (se puede cambiar en Ajustes).
- Nuevo diseño del detalle de cada manhwa: cabecera con portada, progreso con botones − y +, barra de avance y botón para continuar en tu lector.

## 3.5
- Las tarjetas ajustan el tamaño de letra para que las frases largas no se pisen.
- Volteo de tarjeta más marcado.

## 3.4
- Las actualizaciones se descargan e instalan desde la propia app, sin depender del navegador.

## 3.3
- Nuevo diseño del Repaso: tarjeta que se voltea, deslizar para responder, colores y contadores.

## 3.2
- Campo Tipo (manhwa, manga, manhua) en cada obra.
- Elige tu app lectora (Komikku, Mihon, Mangayomi…) en Ajustes.

## 3.1
- Importar tu lista de AniList por nombre de usuario.

## 3.0
- Descubrir funciona sin internet con las últimas sugerencias guardadas.
- Botón + más grande en la cuadrícula.

## 2.9
- Mejor accesibilidad: el estado de cada manhwa se anuncia en la cuadrícula.

## 2.8
- La app ahora también está en inglés (sigue el idioma del teléfono o se elige en Ajustes).

## 2.7
- Pruebas automáticas en cada versión.
- Aviso de versión nueva, pantalla de Ajustes y guía de inicio.
