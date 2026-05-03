# TFG-Donacion-Renal

## Descripción

Aplicación móvil Android desarrollada como parte del Trabajo de Fin de Grado **“Aplicación móvil para la predicción de donantes renales válidos en asistolia no controlada y desarrollo de una plataforma web con modelos de aprendizaje automático”**.

La aplicación actúa como una herramienta de apoyo a la decisión clínica para la evaluación de posibles donantes renales en asistolia no controlada (uDCD). Permite introducir variables clínicas disponibles durante el proceso asistencial y obtener una predicción basada en reglas clínicas derivadas de modelos médicos previamente definidos.

El sistema está orientado a personal sanitario y busca facilitar una evaluación rápida, estructurada y trazable en escenarios de urgencia.

---

## Objetivo

El objetivo principal de la aplicación es proporcionar una herramienta móvil que permita:

- Registrar datos clínicos esenciales de un posible donante renal en asistolia no controlada.
- Aplicar modelos clínicos basados en reglas para estimar la validez del donante.
- Realizar predicciones en dos momentos del proceso:
  - Punto medio de la reanimación cardiopulmonar.
  - Fase posterior a la RCP o transferencia.
- Gestionar usuarios y roles mediante Firebase.
- Almacenar y consultar predicciones realizadas.
- Exportar resultados para su revisión posterior.

---

## Funcionalidades principales

- Registro e inicio de sesión de usuarios.
- Gestión de roles de usuario.
- Introducción de variables clínicas del caso.
- Selección del momento de predicción:
  - Modelo de punto medio de RCP.
  - Modelo de transferencia.
- Cálculo automático de la puntuación clínica.
- Clasificación del donante como válido o no válido según el punto de corte correspondiente.
- Almacenamiento de resultados en Firebase Firestore.
- Consulta del historial de predicciones.
- Exportación de resultados en formato CSV/PDF.

---

## Modelos clínicos implementados

La aplicación implementa dos modelos basados en reglas clínicas. Estos modelos combinan distintas variables del paciente y del proceso asistencial mediante una fórmula ponderada y comparan la puntuación obtenida con un punto de corte.

Los modelos disponibles son:

- **Modelo de punto medio de RCP**: utilizado durante una fase intermedia del proceso de reanimación.
- **Modelo de transferencia**: utilizado en la fase posterior a la reanimación, incorporando información adicional del proceso.

Estos modelos no corresponden a modelos de aprendizaje automático entrenados dentro de la aplicación móvil, sino a reglas clínicas implementadas directamente en la lógica de la app.

---

## Tecnologías utilizadas

- Kotlin
- Android Studio
- Android SDK
- Firebase Authentication
- Firebase Firestore
- Gradle

---

## Estructura general del repositorio

```text
.
├── app/                         # Módulo principal de la aplicación Android
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/            # Código fuente en Kotlin (activities, lógica, modelos)
│   │   │   ├── res/             # Recursos de la app (layouts XML, strings, estilos, imágenes)
│   │   │   └── AndroidManifest.xml  # Configuración principal de la aplicación (permisos, actividades)
│   ├── build.gradle.kts         # Configuración de compilación del módulo app
│   └── google-services.json     # Configuración de Firebase (Auth, Firestore, etc.)
├── gradle/                      # Scripts y configuración del sistema de build Gradle
├── build.gradle.kts             # Configuración global del proyecto
├── settings.gradle.kts          # Definición de módulos del proyecto
└── README.md                    # Documentación principal del repositorio
```
