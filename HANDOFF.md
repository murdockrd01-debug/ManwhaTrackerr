# Lectoon — documento de seguimiento

Lugar común donde Claude y ChatGPT dejan por escrito qué se hizo, qué falta y cómo probarlo. Quien termine una tarea actualiza este archivo y sube los cambios a GitHub.

## Reglas del proyecto (no se negocian)

1. **Las extensiones se conservan.** No se eliminan, desactivan ni restringen extensiones ni sus capacidades. Se mantiene la compatibilidad con las actuales, se puede seguir instalando y usando extensiones y desarrollando nuevas.
2. **La seguridad se mejora sin romper esa regla** (validaciones, permisos, avisos, controles). Si una corrección pudiera cambiar el comportamiento de alguna extensión, primero se explica al dueño del proyecto y se espera su autorización.
3. **Estabilidad primero.** Sin actualizaciones de librerías ni cambios solo por mejorar. Prioridad: estabilidad, proteger los datos del usuario, corregir errores reales, ordenar el código, actualizar solo lo necesario, probar.
4. **Nunca borrar datos del usuario.** Si cambia la base de datos, hay migración de Room. Nunca tocar secretos ni el keystore.
5. **Trabajo:** commits pequeños y descriptivos; documentar aquí cualquier problema antes de arreglarlo. (Desde 2026-11-28 el dueño decidió seguir solo con Claude; si otra herramienta vuelve a participar, usar ramas separadas y no sobrescribir su trabajo.)
6. **Ser honesto:** nada de lo que dependa del tacto o de un teléfono real está verificado hasta que alguien lo prueba.
7. El dueño del proyecto no programa: hay que explicarle en español y sin tecnicismos.

## Cómo se trabaja

- Rama de trabajo: `main`. Antes de empezar: `git pull --rebase origin main` y mirar `git log`.
- Cada versión: subir `versionCode`/`versionName` en `app/build.gradle.kts`, entrada nueva arriba en `app/src/main/assets/changelog.md`, pruebas unitarias para la lógica nueva, textos nuevos en `TrData.kt` (traducción al inglés).
- Compilar solo en GitHub Actions: `gh workflow run build.yml -R murdockrd01-debug/manwhatrackerr --ref main`; comprobar que el `headSha` del run coincide con el último commit y leer las anotaciones si falla.
- El APK se publica como `ManhwaTracker.apk`: **no cambiar ese nombre**, la actualización dentro de la app lo busca.
- `applicationId` sigue siendo `com.manhwatracker.app` (el nombre visible es Lectoon): cambiarlo rompería las actualizaciones y los datos.

## Estado actual (v5.34.0, 2026-11-27)

La app compila (GitHub Actions, última compilación verificada en GitHub Actions). Funciones recientes: buscador general en todas las fuentes, fuentes favoritas/ocultas, lector con zoom, paso automático al siguiente capítulo con precarga, descargas sin internet (incluida la descarga automática de los siguientes), contador de capítulos sin leer, filtros guardados por fuente, importación de copias `.tachibk`, casita ⌂ para volver a Fuentes, repositorio de extensiones de fábrica (keiyoushi).

**Sin verificar en un teléfono real:** zoom con dos dedos, cuenta atrás del paso automático, servicio de descargas y notificaciones, avisos de capítulos nuevos, carga de extensiones reales (incluido QuickJS y el arreglo de `runBlockingK`), portadas sin internet, contador de sin leer.

## Revisión inicial (2026-11-27) — qué sigue siendo válido

- **A. Actualizaciones:** solo un riesgo real conocido: QuickJS 0.9.2 (`app.cash.quickjs`) trae librerías nativas sin alineación de 16 KB (teléfonos nuevos con páginas de 16 KB podrían fallar al cargarlo). Sin comprobar. El resto de librerías funciona; no actualizar en bloque (Kotlin arrastra KSP y los plugins de Compose y serialización).
- **B. Seguridad de extensiones:** ya existe confianza por firma (`ExtensionHost.NeedsTrust`), rechazo de extensiones sin firma, vuelta a pedir confianza si cambia la firma y verificación de Android al instalar. Sin tráfico sin cifrar. Límite inherente: las extensiones corren en el mismo proceso y con los mismos permisos que la app (aislarlas exigiría otro proceso y rompería la compatibilidad; descartado).
- **C. Organización:** `ExploreActivity.kt` (~1330 líneas) y `MainActivity.kt` (~1230) mezclan varias pantallas. Los textos traducidos usan el texto en español como clave (`TrData.kt`): cambiar una frase en español pierde su traducción.
- **D. Pruebas:** 28 archivos de pruebas unitarias (solo lógica). Sin pruebas de pantallas, gestos, descargas ni carga de extensiones.

## Plan de trabajo (pendiente de autorización del dueño)

1. [hecho] Recrear este `HANDOFF.md`.
2. Seguridad de extensiones, sin quitar nada: mostrar mejor firma y origen al pedir confianza; avisar si una extensión instalada cambia de firma; comprobar integridad del archivo descargado; revisar qué datos entran en las copias de seguridad de Android.
3. QuickJS y teléfonos de 16 KB: proponer la opción menos arriesgada y esperar autorización (puede afectar la carga de algunas extensiones).
4. Dividir `ExploreActivity.kt` en archivos por pantalla (solo mover código).
5. Más pruebas donde se pueda y lista corta de comprobaciones para hacer en el teléfono.

## Estudio efectivo

Objetivo del dueño: que el modo estudio sirva para **aprender de verdad**, no para amontonar palabras. Aquí se anotan ideas y quien las haga las registra abajo.

**Ya existe:** repaso espaciado de palabras (cajas 0–5, `Srs`), práctica de escritura con tolerancia a erratas (`Typing.kt`), palabras guardadas con ejemplo y capítulo, y desde 5.31.0 las **Estructuras** (`StructuresActivity`, `data/Structures.kt`) con ejercicio de completar, notas propias y hoja imprimible (`data/PrintSheet.kt`).

**Principios a seguir:**
1. Recordar activamente (intentar responder antes de ver la respuesta) enseña más que releer.
2. Repasar a intervalos crecientes, no todo cada día.
3. Aprender palabras dentro de frases reales (las del propio manhwa), no sueltas.
4. Producir: escribir frases propias con lo aprendido.
5. Pocas cosas cada día, con meta pequeña y progreso visible.
6. Papel y lápiz como apoyo: hoja imprimible con espacio para escribir.

**Ideas abiertas (sin hacer):**
- Ejercicio de estructuras con varias frases por estructura y rotación (hoy hay una sola).
- Repasar una estructura de forma espaciada según se falle o acierte.
- Mini-lectura: pedir que el usuario complete huecos en frases sacadas de sus palabras guardadas.
- Meta diaria (p. ej. 5 palabras + 1 estructura) y racha.
- Fichas imprimibles recortables de palabras difíciles.
- Revisar el contenido gramatical de `Structures.kt` (redacción, errores, ejemplos más naturales).

**Preguntas abiertas:** ¿qué hace que una palabra "se quede"? ¿cuántas palabras nuevas por día son razonables? ¿cómo medir que el usuario realmente aprende y no solo marca "aprendida"?

**Reglas:** no borrar datos de estudio del usuario; cualquier cambio en la base de datos lleva migración de Room; textos nuevos en `TrData.kt`.

## Mejora de frases del modo estudio (en revisión)

- Rama de ChatGPT: `chatgpt/study-phrase-usefulness`; parte del `main` actual de v5.31.0.
- Objetivo: clasificar frases guardadas por utilidad estimada, en lugar de mostrar únicamente «Frase». Ejemplo visual: «Muy útil · Frase».
- La estimación se calcula con la frecuencia de sus palabras principales, ignorando palabras gramaticales frecuentes pero conservando partículas de phrasal verbs como `up`. No debe presentarse como frecuencia exacta de la expresión completa.
- Las frases pendientes también se incluyen en el proceso que completa frecuencias antiguas. La clasificación se muestra tanto en la lista de Estudio como durante el repaso.
- No se tocaron extensiones, seguridad de extensiones, QuickJS, organización general, pruebas generales, estructuras gramaticales ni impresión/PDF.
- Pendiente de comprobar: compilación de GitHub Actions y validación funcional en el teléfono.

## Registro de trabajo

- 2026-11-28 — v5.34.0: la pantalla **Estudio** pasa a tener pestañas (Repaso / Palabras / Estructuras / Ejercicios) en `StudyScreen.kt`. `StructuresActivity` se eliminó y su contenido vive en `StructuresPanel.kt` (impresión en `StudyPrint.kt`); se quitó la fila de Estructuras del menú Más y su entrada del manifiesto. Nuevo `ExercisesPanel.kt` + `data/GrammarDrill.kt` (completar frases con alternativas `a|b`, y "¿qué estructura es?" con distractores que excluyen estructuras que el detector también reconoce en esa frase; 44 ejercicios propios + los mini ejercicios de la guía). Pruebas en `GrammarDrillTest`. Sin verificar en teléfono. Idea abierta: guardar resultados de las rondas para repasar las estructuras falladas de forma espaciada.
- 2026-11-28 — v5.33.0: Estructuras → pestaña **Mis frases**: `data/StructureDetector.kt` detecta (con reglas, sin internet) qué estructuras usa una frase y por qué; frases guardadas en Prefs `struct_phrases`; botón para traer las frases de Estudio; la hoja Imprimir/PDF agrupa las frases por estructura (`PrintSheet.group`) con renglones para escribir las propias. Guía ampliada a 22 estructuras (pasado continuo, pasado perfecto, presente perfecto continuo, tercer condicional). El detector es heurístico: puede equivocarse (p. ej. adjetivos terminados en -ed, verbos raros). Pruebas en `StructureDetectorTest`. Sin verificar en teléfono. Decisión del dueño: se sigue trabajando solo con Claude.
- 2026-11-28 — v5.32.1: Claude integró a `main` la rama de ChatGPT `chatgpt/study-phrase-usefulness` (5.32.0: utilidad estimada también para frases, "Muy útil · Frase", sin nivel aparte "Frase"; avance rápido, sin conflictos) y encima añadió en `Utility.kt` `stars`, `advice` y `summary`, mostrados en la tarjeta de `StudyScreen.kt` sin abrirla (pruebas en `UtilityTest`). Límite conocido: la utilidad de una frase es la media geométrica de la frecuencia de sus palabras importantes, no la de la frase completa. Idea: afinar esa estimación (p. ej. phrasal verbs comunes). Sin verificar en teléfono.
- 2026-11-27 — v5.31.0: pantalla **Estructuras** solo con modo estudio (menú Más): 18 estructuras explicadas, ejercicio de completar, nota propia, marcar entendida, progreso y hoja Imprimir/PDF (estructuras con renglones + palabras con columna de frase propia). Archivos: `StructuresActivity.kt`, `data/Structures.kt`, `data/PrintSheet.kt`, `data/Study.kt` (Prefs `struct_done`, `struct_note_<id>`), `MoreScreen.kt`, `AndroidManifest.xml`, `TrData.kt`, pruebas `StructuresTest`/`PrintSheetTest`. Sin verificar en teléfono: la impresión/PDF (WebView + PrintManager) y el aspecto de la pantalla. Nueva sección "Estudio efectivo" arriba.
- 2026-11-27 — v5.30.0: la app pasa a llamarse **Lectoon** (solo el nombre visible). README reescrito. Se quitaron `CLAUDE.md` y el antiguo `HANDOFF.md` (volvió este archivo, nuevo). Revisión inicial pedida por el dueño: ver arriba. Sin cambios de código en esta entrada.
- Historial anterior (v5.8.0 a v5.29.0): ver `git log` y `app/src/main/assets/changelog.md`.
