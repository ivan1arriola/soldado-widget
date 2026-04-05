# Soldado Widget (Android)

Widget de pantalla de inicio con un soldado estilo pixel-art que cambia de pose y frase al tocarlo.

## Que incluye

- App Android en Kotlin
- App Widget clasico con `AppWidgetProvider`
- 4 frames de soldado en drawables XML
- Frases aleatorias al toque
- Estado por widget guardado en `SharedPreferences`

## Ejecutar

1. Abre el proyecto en Android Studio.
2. Sincroniza Gradle.
3. Ejecuta la app en un dispositivo o emulador.
4. En la pantalla de inicio, agrega el widget **Soldado Widget**.
5. Toca el soldado para ver el cambio de pose y frase.

## Personalizacion rapida

- Cambia frases en `app/src/main/res/values/strings.xml`.
- Cambia el comportamiento al toque en `app/src/main/java/com/ivan1arriola/soldadowidget/SoldadoWidgetProvider.kt`.
- Reemplaza los frames en `app/src/main/res/drawable/soldado_frame_*.xml` por sprites PNG si quieres un estilo mas detallado.
