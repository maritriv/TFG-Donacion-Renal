# TFG-Donacion-Renal

## Descripción
Trabajo de Fin de Grado - Aplicación móvil para la **predicción de donantes renales válidos en asistolia no controlada (uDCD)** usando **capnometría** (EtCO₂ en punto medio y en transferencia) y variables clínicas mínimas.

---

El trabajo completo consta de dos repositorios:

- Un primer repositorio (este), que desarrolla una aplicación móvil en Android basada en reglas clínicas derivadas de una tesis doctoral.
- Un segundo repositorio, que integra el pipeline de datos, modelos de aprendizaje automático y una plataforma web para su uso y comparación.

Este repositorio incluye:

- una aplicación móvil Android orientada a personal sanitario,
- la implementación de modelos clínicos basados en reglas,
- y la gestión de usuarios y predicciones mediante Firebase.

---

## Objetivo del repositorio

El objetivo de este repositorio es desarrollar una aplicación móvil que permita:

1. Introducir datos clínicos de un posible donante renal en asistolia no controlada.
2. Aplicar modelos clínicos basados en reglas en tiempo real.
3. Obtener una predicción de validez del donante en distintos momentos del proceso.
4. Almacenar y consultar las predicciones realizadas.
5. Facilitar la trazabilidad y exportación de resultados.

---

## Funcionalidades principales

### Autenticación y usuarios
- Registro e inicio de sesión de usuarios.
- Gestión de roles:
  - **Médico**
  - **Administrador**
- Persistencia de usuarios mediante Firebase Authentication.

---

### Predicción clínica

La aplicación permite realizar predicciones en dos momentos del proceso:

- **Punto medio de la reanimación cardiopulmonar (MID)**
- **Fase de transferencia (TRANSFER)**

Para cada caso:

- Introducción de variables clínicas.
- Cálculo automático de la puntuación.
- Clasificación del donante:
  - Válido
  - No válido
- Visualización del resultado.

---

### Gestión de predicciones

- Almacenamiento de predicciones en Firebase Firestore.
- Consulta del historial de predicciones.
- Filtrado y ordenación de resultados.
- Visualización detallada de cada caso.

---

### Exportación de resultados

- Exportación individual de predicciones en PDF.
- Exportación del historial completo en CSV.

---

## Modelos clínicos implementados

La aplicación implementa dos modelos basados en reglas clínicas derivadas de una tesis doctoral.

Estos modelos:

- combinan variables clínicas mediante una función lineal ponderada,
- calculan una puntuación,
- y comparan el resultado con un punto de corte para determinar la validez del donante.

Modelos disponibles:

- **Modelo MID** → evaluación en punto medio de RCP  
- **Modelo TRANSFER** → evaluación en fase posterior a la reanimación  

⚠️ Estos modelos no son modelos de aprendizaje automático entrenados en la aplicación, sino reglas clínicas implementadas directamente en la lógica del sistema.

---

## Arquitectura de la aplicación

La aplicación sigue una arquitectura basada en:

- **Activities** → gestión de pantallas e interacción con el usuario  
- **Modelos de datos** → representación de variables clínicas y resultados  
- **Capa de acceso a datos** → integración con Firebase (Auth + Firestore)  
- **Lógica de negocio** → cálculo de predicciones clínicas  

---

## Estructura del repositorio

```text
.
├── app/                         # Módulo principal de la aplicación Android
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/            # Código fuente (Activities, lógica, modelos)
│   │   │   │   ├── ui/          # Pantallas de la aplicación
│   │   │   │   ├── model/       # Modelos de datos y coeficientes clínicos
│   │   │   │   ├── data/        # Acceso a Firebase y gestión de datos
│   │   │   │   └── utils/       # Funciones auxiliares
│   │   │   ├── res/             # Recursos (layouts, strings, estilos)
│   │   │   └── AndroidManifest.xml  # Configuración de la aplicación
│   ├── build.gradle.kts         # Configuración del módulo app
│   └── google-services.json     # Configuración de Firebase
├── gradle/                      # Configuración de Gradle
├── build.gradle.kts             # Configuración global del proyecto
├── settings.gradle.kts          # Definición de módulos
└── README.md                    # Este documento
```
---

## Requisitos

Para ejecutar la aplicación se necesita:

- Android Studio
- JDK 17 o superior
- Dispositivo Android o emulador
- Proyecto configurado en Firebase

---

## Instalación del entorno

**1. Clonar el repositorio:**
```bash
git clone https://github.com/maritriv/TFG-Donacion-Renal.git
cd TFG-Donacion-Renal
```

**2. Abrir en Android Studio:**

- Abrir Android Studio
- Seleccionar "Open project"
- Cargar el repositorio

3. **Configurar Firebase:**

- Crear un proyecto en Firebase
- Añadir una app Android
- Descargar el archivo google-services.json
- Colocarlo en:
  `app/google-services.json`

**4. Sincronizar proyecto**

- Ejecutar sincronización de Gradle  
- Verificar dependencias  

---

**5. Ejecutar la aplicación**

- Seleccionar emulador o dispositivo  
- Ejecutar el proyecto desde Android Studio  

---

## Flujo de uso de la aplicación

### 1. Autenticación

El usuario puede:

- iniciar sesión  
- registrarse  
- recuperar contraseña  

---

### 2. Pantalla principal

- Visualización de estadísticas básicas  
- Acceso a funcionalidades principales  

---

### 3. Realizar predicción

1. Selección del momento clínico:
   - MID  
   - TRANSFER  

2. Introducción de datos clínicos  

3. Cálculo automático de la predicción  

4. Visualización del resultado  

---

### 4. Historial

- Visualización de predicciones anteriores  
- Filtrado y ordenación  
- Exportación de resultados  

---

### 5. Gestión de usuarios (admin)

- Visualización de usuarios  
- Modificación de datos  
- Eliminación de usuarios  

---

## Tecnologías utilizadas

### Aplicación móvil

- Kotlin  
- Android SDK  
- Android Studio  

### Backend y persistencia

- Firebase Authentication  
- Firebase Firestore  

---

## Relación con el sistema completo

Este repositorio representa la parte móvil del sistema.

El sistema completo incluye:

- Aplicación móvil basada en reglas clínicas (este repositorio)  
- Pipeline de datos y modelos de machine learning  
- Plataforma web para comparación de modelos  

---

## Consideraciones

- Proyecto desarrollado en el contexto de un Trabajo de Fin de Grado.  
- Aplicación orientada a apoyo a la decisión clínica.  
- No sustituye la evaluación médica profesional.  

---

## Autora

**Marina Triviño de las Heras**

Estudiante de cuarto año del grado en Ingeniería de Datos e Inteligencia Artificial  
Universidad Complutense de Madrid  

---

## Repositorios relacionados

- Pipeline completo y web: [TFG-Donacion-Renal-ml-web](https://github.com/maritriv/TFG-Donacion-Renal-ml-web.git)
