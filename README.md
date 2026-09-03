# Android Performance GFX & Perfetto Plugin

Un plugin para Android Studio / IntelliJ IDEA diseñado para analizar métricas de rendimiento gráfico en dispositivos Android y realizar capturas de trazas con Perfetto.

## 🚀 Características

- **Detección de Dispositivos**: Reconocimiento automático de dispositivos Android conectados y emuladores mediante ADB.
- **Análisis de GFX**: Generación de informes detallados a partir de `dumpsys gfxinfo` mostrando métricas clave de renderizado CPU y GPU.
- **Monitoreo de Renderizado Lento**:
  - Slow UI Thread
  - Bitmap Uploads
  - Missed Vsync
- **Conteo de Frozen Frames**: Identificación de cuadros congelados con latencias superiores a 700ms.
- **Captura de Perfetto**: Interfaz con control Play/Stop para realizar capturas interactiva de trazas del sistema y analizar el comportamiento de la aplicación en detalle.

## 🛠️ Requisitos e Instalación

- **IDE**: Android Studio / IntelliJ IDEA (2024.1 o superior).
- **Java/Kotlin**: JDK 17, Kotlin 2.0.0.
- **Herramientas de desarrollo**: `adb` accesible en el PATH del sistema.

## 📦 Construcción del Proyecto

Para compilar el proyecto y generar el archivo ZIP instalable del plugin:

```bash
./gradlew buildPlugin
```

El archivo ejecutable del plugin se generará en `build/distributions/`.

## 📜 Licencia

Este proyecto está distribuido bajo la licencia MIT.
