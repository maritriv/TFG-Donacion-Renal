# TFG-Donacion-Renal


## Descripción
Proyecto TFG - Aplicación móvil para la **predicción de donantes renales válidos en asistolia no controlada (uDCD)** usando **capnometría** (EtCO₂ al inicio y en el punto medio) y variables clínicas mínimas.
---

## Objetivo
Construir un **MVP** que:
- Registre datos esenciales del caso uDCD de forma **rápida y usable**.
- Calcule una **probabilidad de validez** del donante renal (modelo local).
- Genere un **informe PDF** con resultados y trazabilidad (versión de modelo, fecha).
- Funcione **sin conexión** y respete **privacidad** (datos pseudonimizados, cifrado local).


## Funcionalidades
- **Usuarios y roles** (coordinador / médico) con permisos:
  - Coordinador: ve **todos** los casos.
  - Médico: ve **solo** sus pacientes/asignados.
- **Registro de caso**: EtCO₂_inicio, EtCO₂_medio, tiempos (RCP, isquemia), edad, etc.
- **Predicción**: probabilidad (%) + etiqueta (Válido / No válido) con **umbral configurable**.
- **Historial**: lista de casos, filtros y detalle.
- **Informe**: **descarga en PDF** del caso y resultado.
- **Modo offline**: todo usable sin red; exportación cuando haya conexión.
- **Seguridad**: almacenamiento local **cifrado**; sin identificadores personales.

> Futuro (no bloqueante para el MVP): sincronización servidor/hospital, dashboard web, i18n.

---

## Estructura general del repositorio
```
.
├── README.md
├── docs/
│   ├── SRS.md              # Documento maestro de requisitos (formal)
│   ├── anexos/             # Material de apoyo (glosario, casos de uso, refs…)
│   ├── figuras/            # Diagramas, mockups, esquemas
│   └── entregas/           # Lo que prepares para enviar al profesor
├── project/
│   └── roadmap.md          # Plan de hitos y tareas principales
├── src/                    # Código de la app / modelo (más adelante)
├── notebooks/              # Exploración de datos y pruebas (más adelante)
└── data/                   # Solo datasets sintéticos / de ejemplo
```
---

##  Instalación del entorno

Pasos necesarios para instalar el proyecto. Descripción paso a paso de cómo poner en funcionamiento el entorno de desarrollo.

----
