# Lectoon — documento de seguimiento

Lugar común donde Claude y ChatGPT dejan por escrito qué se hizo, qué falta y cómo probarlo. Quien termine una tarea actualiza este archivo y sube los cambios a GitHub.

## Reglas del proyecto (no se negocian)

1. **Las extensiones se conservan.** No se eliminan, desactivan ni restringen extensiones ni sus capacidades. Se mantiene la compatibilidad con las actuales, se puede seguir instalando y usando extensiones y desarrollando nuevas.
2. **La seguridad se mejora sin romper esa regla** (validaciones, permisos, avisos, controles). Si una corrección pudiera cambiar el comportamiento de alguna extensión, primero se explica al dueño del proyecto y se espera su autorización.
3. **Estabilidad primero.** Sin actualizaciones de librerías ni cambios solo por mejorar. Prioridad: estabilidad, proteger los datos del usuario, corregir errores reales, ordenar el código, actualizar solo lo necesario, probar.
4. **Nunca borrar datos del usuario.** Si cambia la base de datos, hay migración de Room. Nunca tocar secretos ni el keystore.
5. **Trabajo en equipo (Claude y ChatGPT):** commits pequeños y descriptivos; ramas separadas para trabajos independientes; no sobrescribir ni borrar cambios del otro; revisar conflictos antes de integrar; documentar aquí cualquier problema antes de arreglarlo.
6. **Ser honesto:** nada de lo que dependa del tacto o de un teléfono real está verificado hasta que alguien lo prueba.
7. El dueño del proyecto no programa: hay que explicarle en español y sin tecnicismos.

## Cómo se trabaja

- Rama de trabajo: `main`. Antes de empezar: `git pull --rebase origin main` y mirar `git log`.
- Cada versión: subir `versionCode`/`versionName` en `app/build.gradle.kts`, entrada nueva arriba en `app/src/main/assets/changelog.md`, pruebas unitarias para la lógica nueva, textos nuevos en `TrData.kt` (traducción al inglés).
- Compilar solo en GitHub Actions: `gh workflow run build.yml -R murdockrd01-debug/manwhatrackerr --ref main`; comprobar que el `headSha` del run coincide con el último commit y leer las anotaciones si falla.
- El APK se publica como `ManhwaTracker.apk`: **no cambiar ese nombre**, la actualización dentro de la app lo busca.
- `applicationId` sigue siendo `com.manhwatracker.app` (el nombre visible es Lectoon): cambiarlo rompería las actualizaciones y los datos.

## Estado actual (v5.30.0, 2026-11-27)

La app compila (GitHub Actions, run de la 5.30.0 en verde). Funciones recientes: buscador general en todas las fuentes, fuentes favoritas/ocultas, lector con zoom, paso automático al siguiente capítulo con precarga, descargas sin internet (incluida la descarga automática de los siguientes), contador de capítulos sin leer, filtros guardados por fuente, importación de copias `.tachibk`, casita ⌂ para volver a Fuentes, repositorio de extensiones de fábrica (keiyoushi).

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

## Registro de trabajo

- 2026-11-27 — v5.30.0: la app pasa a llamarse **Lectoon** (solo el nombre visible). README reescrito. Se quitaron `CLAUDE.md` y el antiguo `HANDOFF.md` (volvió este archivo, nuevo). Revisión inicial pedida por el dueño: ver arriba. Sin cambios de código en esta entrada.
- Historial anterior (v5.8.0 a v5.29.0): ver `git log` y `app/src/main/assets/changelog.md`.
