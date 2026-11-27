# Echo Music para PC

Versión de escritorio (Windows) de [Echo Music](https://github.com/EchoMusicApp/Echo-Music), hecha con
Compose Desktop. Reutiliza el código Kotlin de Echo Music que no depende de Android
(cliente de YouTube Music, proveedores de letras, Shazam y Canvas).

## Requisitos

- Windows 10/11
- JDK 21 (por ejemplo Oracle JDK o Microsoft Build of OpenJDK)

## Ejecutar

```powershell
.\run-desktop.bat
```

o directamente:

```powershell
.\gradlew.bat run
```

Tests:

```powershell
.\gradlew.bat test
```

Instalador:

```powershell
.\build-installer.bat
```

Deja `EchoMusic-<versión>.msi` (con su `.sha256`) en `build\compose\binaries\main\msi` y
`EchoMusic-<versión>.exe` en `build\compose\binaries\main\exe`. La primera vez Gradle descarga WiX solo.
La versión se cambia en `appVersion` de `build.gradle.kts`.

Se instala solo para el usuario (`%LOCALAPPDATA%\EchoMusic`), así que ni la instalación ni las
actualizaciones piden permisos de administrador.

## Publicar una versión (actualización automática)

Echo Music busca una vez al día la última *release* de GitHub del repositorio puesto en
Ajustes → Actualizaciones. Si es más nueva, descarga el `.msi`, comprueba su SHA-256 y lo instala solo
al cerrarse (o con "Reiniciar ahora"), y se vuelve a abrir.

1. Sube `appVersion` en `build.gradle.kts` y ejecuta `build-installer.bat`.
2. Crea una *release* con la etiqueta `v<versión>` (por ejemplo `v1.2.1`) y adjunta el `.msi`,
   su `.msi.sha256` y, para instalaciones nuevas, el `.exe`.

Sin una huella SHA-256 (la que GitHub calcula a los archivos subidos, o el `.msi.sha256`) no se instala
nada automáticamente. GitHub solo responde sin iniciar sesión si el repositorio es público: con el
repositorio privado la app no encuentra las versiones.

## Funciones

- Reproducción con cola, aleatorio, repetir, crossfade, ecualizador, velocidad, normalización de volumen,
  calidad de audio, saltar canciones que fallan, SponsorBlock y teclas multimedia globales
- Continúa donde lo dejaste al reiniciar
- Inicio de YouTube Music, búsqueda completa con sugerencias, Charts, estados de ánimo y géneros,
  novedades, podcasts y comentarios
- Cuenta de YouTube Music: playlists, "Me gusta", álbumes y artistas guardados
- Música local (MP3, M4A, WAV, AIFF), descargas y playlists propias
- Letras de 7 fuentes con sincronización palabra por palabra, romanización y traducción con IA
  (OpenRouter, DeepL o Mistral con tu propia clave)
- Canvas (portadas animadas), Echo Find (reconocer canciones), Escuchar Juntos, Discord
- Last.fm y ListenBrainz, bloqueo de artistas y "No me gusta", copia de seguridad, temas de color
- Historial privado (pausar y borrar), historial de Echo Find, editar playlists y respuestas a comentarios
- Mini reproductor siempre visible (Ctrl + M), iniciar con Windows y aviso de versiones nuevas
- Panel multimedia de Windows: título, artista, portada, ⏮ ⏯ ⏭ y barra para adelantar (también con la app en la bandeja)
- Isla dinámica arriba de la pantalla: hora, reproductor, cola, controles y letra sincronizada al pasar el mouse
- Botones ⏮ ⏯ ⏭ en la vista previa de la barra de tareas
- Pausa al desconectar audífonos (Bluetooth o con cable) y sigue al reconectarlos
- Music Flow (descubrir deslizando) y playlists con IA: crear, "De tus gustos" y modificar (con tu clave de OpenRouter o Mistral)
- Modo video: el videoclip oficial en el reproductor, sincronizado (360p, lo que YouTube permite sin tokens especiales)
- Spotify: tus playlists y Me gusta (cada canción se busca en YouTube Music) y los Canvas de Spotify
- La cookie de YouTube y las claves se guardan cifradas con Windows (DPAPI)

Los datos del usuario se guardan en `%USERPROFILE%\.echomusic`.

## Estructura

| Carpeta | Contenido |
|---|---|
| `src/main/kotlin` | La aplicación de escritorio |
| `src/shims/kotlin` | Reemplazos mínimos de `android.content.Context`, `android.util.Base64` y Timber para que el código compartido compile sin Android |
| `src/test/kotlin` | Tests (los que usan red solo corren con `-DlyricsProbe=true`) |
| `shared/` | Código tomado **sin cambios** de Echo Music (salvo los arreglos de `scripts/innertube-desktop.patch`) |
| `shared/spotify` | Cliente de Spotify de Echo Music (biblioteca, Canvas y búsqueda en YouTube Music); arreglo en `scripts/spotify-desktop.patch` |
| `shared/echo-app` | Archivos sueltos de la app de Echo Music: traducción, Last.fm, ListenBrainz, firma de Shazam |
| `shared/echo-app-romanization` | `LyricsUtils.kt` de Echo Music; al compilar se usa solo la parte de romanización |

## Actualizar el código compartido

Cuando salga una versión nueva de Echo Music:

```powershell
git clone https://github.com/EchoMusicApp/Echo-Music.git C:\temp\Echo-Music
.\scripts\update-shared.ps1 -EchoMusic C:\temp\Echo-Music
.\gradlew.bat test
```

El script copia los módulos y archivos que usa la PC y vuelve a aplicar los arreglos de `innertube`.

## Pendiente

- Del celular: Jellyfin/Navidrome, tipografías

## Licencia

GPL-3.0, igual que Echo Music (ver `LICENSE`). Basado en el trabajo de los autores de Echo Music.
