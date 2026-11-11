# Manhwa Tracker — instrucciones para Claude

Lee **`HANDOFF.md`** (contexto completo, flujo de trabajo, mapa del código y hoja de ruta) antes de empezar.

Reglas que no se negocian:
- Responde al usuario **en español**. No sabe programar: haz tú todo el trabajo, incluido subir a `main` y compilar. Nunca le pidas editar nada en GitHub.
- Trabaja directo en `main` (nada de ramas/PR que él deba fusionar). Antes de empezar: `git pull --rebase origin main` y revisa `git log`.
- Cada versión: subir `versionCode`/`versionName`, entrada nueva arriba en `app/src/main/assets/changelog.md`, pruebas unitarias para la lógica nueva, textos nuevos en `TrData.kt`.
- Compilación solo en GitHub Actions: `gh workflow run build.yml -R murdockrd01-debug/manwhatrackerr --ref main`, espera, comprueba que `headSha` coincide con tu commit y lee las anotaciones si falla (ver HANDOFF.md §3).
- Nunca toques secretos ni el keystore. Nunca borres datos del usuario: si cambias la base de datos, escribe la migración de Room.
- Sé honesto: no puedes probar en un celular; lo que dependa del tacto está sin verificar.
- Al terminar, actualiza el "Registro de trabajo" de `HANDOFF.md`.
