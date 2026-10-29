# Banapi

App Android open source para crear imágenes con **Nano Banana 2.1** usando tu propia API key de la Gemini API.

- Sin cuentas ni servidores: la app habla directo con la API de Google.
- Tu API key se verifica con Google y se guarda cifrada, solo en tu teléfono.
- Cada usuario usa (y paga) su propia key. Puedes crearla en [Google AI Studio](https://aistudio.google.com/apikey).

> En desarrollo.

## Funciones

- **Crear:** chats como en Google AI Studio. Describe la imagen, adjunta fotos de referencia o para editar y marca con el dedo lo que quieres cambiar. Cada chat guarda su historial en el teléfono.
- **Opciones del chat:** proporción, resolución (1K, 2K o 4K), nivel de razonamiento, búsqueda de Google e instrucciones. Todo viene en Auto.
- **Gasto a la vista:** cada respuesta muestra cuánto costó (estimado) y los tokens que usó. Del historial se envía todo el texto y solo las imágenes de los últimos mensajes, para no gastar de más.
- **Galería:** mosaico de imágenes, visor con zoom, compartir, guardar en el teléfono y eliminar.
- **Ajustes:** API key, gasto del mes y total, saldo restante estimado, tema (sistema, claro u oscuro) y almacenamiento usado (imágenes y chats).

## Tecnologías

- Kotlin + Jetpack Compose (Material 3)
- Android 10 (API 29) o superior

## Compilar

1. Abre la carpeta del proyecto en Android Studio.
2. Espera a que termine la sincronización de Gradle.
3. Ejecuta la configuración `app` en un emulador o en tu teléfono.

O desde la terminal:

```bash
./gradlew assembleDebug
```

## Diseño

Colores, fuentes, bordes, espacios y tamaños están en un solo archivo:
`app/src/main/java/app/banapi/ui/theme/DesignTokens.kt`.

## Licencia

[MIT](LICENSE) © 2026 Agustín Alejandro Segovia

---

Banapi no está afiliada a Google. "Gemini" y "Nano Banana" son marcas de Google LLC.
