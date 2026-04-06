# Soldado Widget (Android)

Widget de pantalla de inicio con un soldado estilo pixel-art que cambia de pose y frase al tocarlo.

## Que incluye

- App Android en Kotlin
- App Widget clasico con `AppWidgetProvider`
- 4 frames de soldado en drawables XML
- Frases aleatorias al toque
- Estado por widget guardado en `SharedPreferences`
- Modo extension para leer recordatorios de `Deposito_ART1`

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

## Integracion con Deposito_ART1

La app ahora puede actuar como extension de `Deposito_ART1` y mostrar recordatorios reales de la misma base de datos (via API del servidor).

1. En `Deposito_ART1` configura una variable de entorno:

```env
SOLDADO_WIDGET_AUTH_SECRET="tu_secreto_largo_y_unico"
```

2. Inicia `Deposito_ART1`.
3. Abre la app `Soldado Widget` y completa:
	- URL base (ej: `https://tu-dominio`)
	- usuario de Deposito_ART1
	- password de Deposito_ART1
4. Pulsa `Guardar configuracion`, luego `Iniciar sesion` y finalmente `Sincronizar recordatorios`.

Endpoint usado por el widget:

- `POST /api/extensions/soldado-widget/login`
- `GET /api/extensions/soldado-widget/recordatorios?limit=3`
- Header de sync: `Authorization: Bearer <accessToken>`
