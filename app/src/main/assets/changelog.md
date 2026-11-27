## 5.32.0
- Modo estudio: las frases guardadas ahora también reciben una clasificación de utilidad, por ejemplo **«Muy útil · Frase»** o **«Poco común · Frase»**, y entran en el orden de estudio.
- La utilidad de una frase se estima a partir de sus palabras principales (incluyendo partículas de phrasal verbs como *up*). Es una orientación, no la frecuencia exacta de la expresión completa.

## 5.31.0
- Nuevo en el modo estudio: **Estructuras** (menú Más, solo se ve con el modo estudio activo). 18 estructuras del inglés explicadas en español con ejemplos, un truco para no equivocarte y un mini ejercicio para completar la frase.
- Puedes marcar cada estructura como "ya la entiendo", ver tu progreso y escribir tu propia nota.
- Botón Imprimir / PDF: genera una hoja con las estructuras (con renglones para escribir a mano) y tus palabras guardadas (con una columna para tu propia frase), para estudiar con papel y lápiz.

## 5.30.0
- La app ahora se llama Lectoon.

## 5.29.0
- Casita ⌂ siempre visible: en la barra de arriba del lector (al lado de la flecha), en la ficha de la fuente y en la lista de portadas de cada fuente. Un toque te lleva directo a la lista de Fuentes, sin dar atrás varias veces.
- La flecha de atrás y el gesto de atrás del teléfono hacen lo mismo: del lector vuelven a la ficha del manhwa.

## 5.28.0
- Corregido: al tocar "Leer" en un manhwa que nunca habías leído en una fuente, se abría la última fuente que usaste (la de otro manhwa). Ahora cada manhwa recuerda dónde lo leíste y vuelve directo a esa ficha; si nunca lo vinculaste, se busca en todas tus fuentes para que elijas, y desde ahí queda recordado.
- Menos pasos para salir: botón "⌂ Fuentes" en la ficha de una fuente y "Ir a Fuentes" en el menú ⋮ del lector. Al volver ya no se pasa por la lista de la fuente.

## 5.27.0
- Lector: pasa solo al siguiente capítulo. Al llegar al final aparece una barrita de 2,5 segundos y, si no la cancelas con "Quedarme aquí" o saliendo de esa tarjeta, abre el siguiente capítulo. Se puede apagar en el ⋮ del lector ("Pasar solo al siguiente capítulo").
- Cuando vas por el 60% del capítulo, la app prepara el siguiente (su lista de páginas y las primeras 3 imágenes), para que el cambio sea casi instantáneo.

## 5.26.0
- Contador de capítulos sin leer: en la biblioteca, cada manhwa que ya abriste en una fuente muestra "+N" (en la lista, "+N sin leer") cuando la fuente tiene más capítulos que tu avance. Se actualiza al abrir la ficha en la fuente y con la revisión automática de capítulos nuevos. Los terminados no lo muestran.
- Las categorías ya existían: los estados (Leyendo, Pausado, Terminado, Pendiente) y tus listas propias, con sus filtros arriba en la biblioteca.

## 5.25.0
- Si una fuente falla al abrir un manhwa o sus capítulos, ahora hay un botón "Buscar en otras fuentes": hace la búsqueda general con ese título y, al abrir el resultado en otra fuente, sigue unido a tu avance.

## 5.24.0
- Fuentes favoritas: toca la ☆ de una fuente en Leer y sube al principio de la lista.
- Fuentes ocultas: en el ⋮ de cada fuente puedes ocultarla; deja de salir en la lista y en el buscador general. Abajo aparece "Mostrar ocultas" para volver a verlas.

## 5.23.0
- Nuevo ajuste en el ⚙ de Leer: "Bajar solos los siguientes". Si lo activas, al leer un capítulo la app baja los próximos 3 para que sigas leyendo sin internet. Respeta "Bajar solo con Wi-Fi". Viene apagado.

## 5.22.0
- Lector por páginas: zoom con dos dedos (hasta 4×), doble toque para ampliar o volver, y con la página ampliada un dedo la mueve sin cambiar de página.
- Los filtros de cada fuente (géneros, estado, orden…) se recuerdan: al abrir "Filtros" están como los dejaste. "Restablecer" los borra.
- Las portadas de los manhwas descargados se usan en la biblioteca: cargan al instante y se ven sin internet.
- Limpieza: se quitó código viejo del lector de archivos y de la sincronización con carpetas.

## 5.21.0
- Se quitó todo lo relacionado con abrir o sincronizar con Komikku: el botón de abrir la app lectora, el lector de archivos descargados, la elección de "App para leer" y la importación automática desde una carpeta.
- Importar copia de otra app: en Más › Copias manuales › "Importar copia de otra app" sirve el archivo .tachibk de Komikku, Mihon y Tachiyomi (comparten formato). Las copias de anime de Aniyomi no tienen mangas y la app lo avisa.
- El aviso de versión nueva de abajo ahora tiene una X para descartarlo.

## 5.20.1
- La app ya trae añadido el repositorio de extensiones de keiyoushi: quien la instale entra a Leer → Fuentes y ya ve la lista para instalar, sin pegar nada. Se puede quitar y no vuelve a aparecer. Quien ya tenía repositorios no se ve afectado.

## 5.20.0
- Buscador general: en Leer, escribe un nombre y se busca en todas tus fuentes a la vez. Te muestra en cuáles está (por ejemplo, "Solo Leveling" está en Asura Scans, Comic Scans…), con las portadas de cada fuente; toca una para abrirla.

## 5.19.0
- Pantalla "Leer" rediseñada y más corta: "Continuar donde te quedaste" más compacto, "Capítulos nuevos" en filas pequeñas, "Descargados" plegado en una sola fila (se abre al tocarlo) y las fuentes en una lista ordenada por nombre, con buscador cuando hay muchas.
- Los avisos de capítulos nuevos, "Bajar solo con Wi-Fi", "Revisar ahora" y el lector de archivos .cbz pasaron al botón ⚙ de arriba.

## 5.18.1
- Arreglado un cierre inesperado al usar fuentes de keiyoushi ("NoSuchMethodError … runBlockingK"): las extensiones nuevas necesitan una versión más reciente de la librería de corrutinas, que ahora se actualizó a la misma que usa Mihon.
- Si una extensión es incompatible con la app, ahora sale como error de esa fuente en vez de cerrar toda la app.

## 5.18.0
- Pantalla de detalle de cada manhwa rediseñada para que sea más compacta: portada más pequeña, capítulo y total en una sola fila, y estado y tipo juntos en una tarjeta.
- Las secciones largas (Mi opinión, Géneros y listas, Reseñas, Parecidos y Palabras del modo estudio) ahora vienen plegadas con un resumen de una línea; se abren al tocarlas.
- Buscar otra portada, marcar +18 y eliminar pasaron al menú ⋮ de la cabecera.
- Se quitó el botón "Abrir en Komikku" de esta pantalla.

## 5.17.0
- Fuentes con JavaScript: se incluyó el motor QuickJS, así que las extensiones que lo necesitan (varias de keiyoushi) ya pueden funcionar.
- Filtros de búsqueda: en cada fuente, botón "Filtros" con lo que ofrezca (géneros, estado, orden, etc.), con "Aplicar" y "Restablecer". Se puede buscar solo con filtros, sin escribir nada.
- Ajustes de la fuente: botón "Ajustes" en las fuentes que los tienen (dominio o espejo, idioma, calidad...). Al cerrarlos, la lista se recarga.

## 5.16.1
- Corrección interna para que compile 5.16.0 (avisos de capítulos nuevos); sin cambios en la app.

## 5.16.0
- Avisos de capítulos nuevos: cada 6 horas (con internet) la app revisa en las fuentes lo que lees y te avisa con una notificación cuando sale un capítulo que aún no has leído. La primera revisión solo toma nota (no avisa). En "Leer" hay un interruptor para apagarlo, un botón "Revisar ahora" y una sección "Capítulos nuevos" que abre el manhwa directo en el capítulo que sigue.
- Android no garantiza la hora exacta de las revisiones en segundo plano; puede tardar más de 6 horas.

## 5.15.0
- Lo que agregas desde una fuente (con el botón o al empezar a leer) ahora entra con más datos: total de capítulos (el más alto que trae la fuente), tipo (Manhwa, Manga o Manhua si la fuente lo indica) y, si sus etiquetas dicen que es +18, queda marcado como +18 para el filtro de difuminado. Si ya estaba en tu lista sin total de capítulos, se completa.

## 5.14.0
- Lector en línea: toca el centro de la pantalla para ocultar o mostrar los controles (al ocultarlos también se esconden las barras del sistema y la pantalla no se apaga mientras lees).
- Nuevos modos de lectura en el menú ⋮: vertical continuo (como antes), paginado de izquierda a derecha y paginado de derecha a izquierda (manga). En paginado, toca el lado izquierdo o derecho para cambiar de página; el modo se recuerda.

## 5.13.0
- Ficha del manhwa en una fuente: los capítulos que ya leíste (según tu avance) salen atenuados con ✓, el que te toca lleva la etiqueta "siguiente", y el que dejaste a medias "en curso". Arriba hay un botón "Ir a mi siguiente capítulo" que baja hasta él.

## 5.12.0
- Descargas: la app ahora pide el permiso de notificaciones (Android 13+) la primera vez que bajas algo, para que veas el avance.
- Botón "✕ Cancelar descargas" en la ficha y acción "Cancelar" en la notificación. Lo que ya se bajó se conserva.
- Opción "Bajar solo con Wi-Fi" en Leer: con datos móviles las descargas esperan.
- Desde el lector: menú ⋮ → "Bajar los próximos 5 capítulos".

## 5.11.1
- Arreglado: "Continuar leyendo" (y "Siguiente capítulo") podían abrir el capítulo al final y darlo por leído. El lector heredaba la posición del capítulo anterior; ahora cada capítulo empieza limpio, en la primera página, o en la página donde lo dejaste si estaba a medias.
- Si tu avance quedó adelantado por este error, corrígelo en el lector: ⋮ → "Fijar como mi avance".

