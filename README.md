# Yailen Video Maker (Android)

Convierte un texto en un video MP4 con voz en off, subtítulos y movimiento.
100 % local: usa `TextToSpeech`, `MediaCodec` y `MediaMuxer` nativos de Android.
Sin APIs de pago, sin internet, sin marcas de agua.

## Cómo obtener el APK instalable

### Opción A: Android Studio
1. Abre esta carpeta con Android Studio (Koala o más nuevo).
2. Espera a que sincronice Gradle.
3. Build > Build Bundle(s)/APK(s) > Build APK(s).
4. El archivo queda en `app/build/outputs/apk/debug/app-debug.apk`.

### Opción B: GitHub Actions (sin instalar nada en la PC)
1. Crea un repositorio en github.com y sube todo el contenido de esta carpeta.
2. Ve a la pestaña Actions > "Build APK" > espera ~5 min.
3. Descarga el artefacto `YailenVideoMaker-APK`, descomprímelo y pasa `app-debug.apk` al celular.

### Instalar en el celular
Abre el APK > permite "Instalar apps desconocidas" para tu gestor de archivos > Instalar.
Requiere Android 10 o superior y una voz en español (Google Text-to-Speech).

## Estructura
```
YailenVideoMaker/
├── build.gradle.kts            (plugins: AGP 8.5.2, Kotlin 2.0.20)
├── settings.gradle.kts
├── gradle.properties
├── gradlew + gradle/wrapper/
├── .github/workflows/build.yml (compila el APK en la nube)
└── app/
    ├── build.gradle.kts        (Compose + Material3, sin librerías de pago)
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/ (tema, ícono)
        └── java/com/yailen/videomaker/
            ├── MainActivity.kt   (interfaz Jetpack Compose)
            ├── VideoMaker.kt     (TTS + MediaCodec H.264/AAC + MediaMuxer)
            └── SceneRenderer.kt  (fondo animado, zoom en fotos, subtítulos)
```

## Cómo funciona
1. El guion se parte en frases: cada frase es una escena.
2. `TextToSpeech.synthesizeToFile` genera la voz de cada escena (WAV) y mide su duración.
3. Cada fotograma se dibuja en un Canvas (fondo con círculos en movimiento o tus fotos con zoom/paneo, título y subtítulo con fade-in) y se codifica a H.264 con `MediaCodec`.
4. La voz se codifica a AAC con `MediaCodec`.
5. `MediaMuxer` une video y audio y lo guarda en Películas/YailenVideoMaker.
