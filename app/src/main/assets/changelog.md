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

