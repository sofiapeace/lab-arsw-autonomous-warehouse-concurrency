# ARSW — Laboratorio #2
## Plantilla de entrega — Autonomous Warehouse

**Asignatura:** Arquitecturas de Software — ARSW
**Periodo:** 2026-2  
**Laboratorio:** #2 — Autonomous Warehouse  
**Tema:** Race Conditions · Critical Sections · Thread Coordination  
**Tecnología:** Java 21 · Maven · JUnit 5  

---

## 0. Información del equipo

| Integrante | Código / ID | GitHub |
|---|---|---|
| | | |
| | | |
| | | |

**Repositorio:**  
`PEGAR_AQUÍ_URL_DEL_REPOSITORIO`

**Commit final:**  
`PEGAR_AQUÍ_HASH_DEL_COMMIT`

---

# 1. Evidencia de ejecución inicial

## 1.1 Verificación del entorno

Incluya la salida de:

```bash
java -version
mvn -version
```

**Evidencia:**

```text
PEGAR_AQUÍ_LA_SALIDA
```

---

## 1.2 Ejecución inicial

Comando utilizado:

```bash
java -cp target/classes edu.eci.arsw.warehouse.app.WarehouseMain
```

o la configuración utilizada:

```bash
java -cp target/classes edu.eci.arsw.warehouse.app.WarehouseMain <robots> <packages>
```

**Configuración utilizada:**

- Robots:
- Paquetes:

**Resultado observado:**

```text
PEGAR_AQUÍ_LA_SALIDA_RELEVANTE
```

---

# 2. Estado mutable compartido

Identifique los objetos y variables compartidas entre múltiples threads.

| Objeto / Clase | Estado mutable compartido | Quién lee | Quién modifica | Riesgo identificado |
|---|---|---|---|---|
| `PackageQueue` | | | | |
| `DeliveryRegistry` | | | | |
| `WarehouseStatistics` | | | | |
| `SimulationControl` | | | | |
| Otro | | | | |

---

# 3. Condiciones de carrera encontradas

Documente **mínimo tres** comportamientos incorrectos o potencialmente incorrectos.

## Race Condition #1

**Clase / método involucrado:**  
`________________________________________`

**Estado compartido involucrado:**  
`________________________________________`

**Comportamiento observado:**  
`________________________________________`

**¿Por qué ocurre?**  
`________________________________________`

**Evidencia de ejecución:**

```text
PEGAR_AQUÍ_LA_EVIDENCIA
```

---

## Race Condition #2

**Clase / método involucrado:**  
`________________________________________`

**Estado compartido involucrado:**  
`________________________________________`

**Comportamiento observado:**  
`________________________________________`

**¿Por qué ocurre?**  
`________________________________________`

**Evidencia de ejecución:**

```text
PEGAR_AQUÍ_LA_EVIDENCIA
```

---

## Race Condition #3

**Clase / método involucrado:**  
`________________________________________`

**Estado compartido involucrado:**  
`________________________________________`

**Comportamiento observado:**  
`________________________________________`

**¿Por qué ocurre?**  
`________________________________________`

**Evidencia de ejecución:**

```text
PEGAR_AQUÍ_LA_EVIDENCIA
```

---

# 4. Interleaving

Seleccione una de las condiciones de carrera anteriores y represente un interleaving posible.

**Condición seleccionada:**  
`________________________________________`

| Paso | Thread A | Thread B | Estado compartido |
|---:|---|---|---|
| 1 | | | |
| 2 | | | |
| 3 | | | |
| 4 | | | |
| 5 | | | |
| 6 | | | |

### Explicación

¿Por qué este orden de ejecución produce un resultado incorrecto?

**Respuesta:**

`________________________________________________________________________`

`________________________________________________________________________`

---

# 5. Invariantes del sistema

Defina las invariantes que su solución debe preservar.

## I1

`________________________________________________________________________`

## I2

`________________________________________________________________________`

## I3

`________________________________________________________________________`

## I4 — opcional

`________________________________________________________________________`

---

# 6. Regiones críticas

Documente cada región crítica identificada.

| Clase | Región crítica | Invariante protegida | Mecanismo usado | ¿Por qué ese tamaño? |
|---|---|---|---|---|
| | | | | |
| | | | | |
| | | | | |

---

# 7. Decisiones de sincronización

## 7.1 Alternativas consideradas

Marque y explique cuáles evaluaron:

- [ ] `synchronized`
- [ ] `AtomicInteger`
- [ ] Colecciones concurrentes
- [ ] `Lock`
- [ ] `wait()` / `notifyAll()`
- [ ] Otra: `________________________`

### Alternativa 1

**Descripción:**  
`________________________________________________________________________`

**Ventaja:**  
`________________________________________________________________________`

**Desventaja:**  
`________________________________________________________________________`

### Alternativa 2

**Descripción:**  
`________________________________________________________________________`

**Ventaja:**  
`________________________________________________________________________`

**Desventaja:**  
`________________________________________________________________________`

### Decisión final

**Mecanismo seleccionado:**  
`________________________________________`

**Justificación:**  
`________________________________________________________________________`

`________________________________________________________________________`

---

# 8. Finalización de threads

Explique cómo garantizaron que el programa solamente genera el reporte final cuando todos los robots han terminado.

**Mecanismo utilizado:**  
`________________________________________`

**Explicación:**  
`________________________________________________________________________`

`________________________________________________________________________`

### Pregunta

¿Por qué usar `Thread.sleep(...)` no sería una solución correcta para esperar la finalización de todos los workers?

**Respuesta:**  
`________________________________________________________________________`

---

# 9. PAUSE / RESUME

## 9.1 Problema inicial

Explique por qué el busy waiting de la implementación inicial no es adecuado.

**Respuesta:**  
`________________________________________________________________________`

`________________________________________________________________________`

---

## 9.2 Solución implementada

Explique cómo implementaron:

- `pause()`
- espera de los workers
- `resume()`
- despertar coordinado de los workers

**Respuesta:**  
`________________________________________________________________________`

`________________________________________________________________________`

---

## 9.3 Snapshot consistente

Cuando la simulación está pausada, registre:

```text
Processed parcels:
Pending parcels:
Registry size:
Current leader:
```

Explique cómo garantizan que esos valores representan un estado consistente.

**Respuesta:**  
`________________________________________________________________________`

`________________________________________________________________________`

---

# 10. Verificación con RaceConditionProbe

Ejecute:

```bash
java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 100 32 500
```

## Resultados

| Robots | Paquetes | Runs | Anomalías antes | Anomalías después |
|---:|---:|---:|---:|---:|
| 8 | 100 | | | |
| 16 | 250 | | | |
| 32 | 500 | | | |

### Resultado final esperado

```text
Anomalous runs: 0/100
```

**Salida obtenida:**

```text
PEGAR_AQUÍ_LA_SALIDA
```

---

# 11. Evidencia de correctitud

Explique brevemente cómo demuestran que su solución es correcta.

Considere:

- invariantes;
- múltiples ejecuciones;
- distintas cargas;
- ausencia de resultados duplicados;
- ausencia de paquetes perdidos;
- finalización correcta;
- consistencia durante pausa.

**Conclusión:**

`________________________________________________________________________`

`________________________________________________________________________`

`________________________________________________________________________`

---

# 12. Impacto en atributos de calidad

| Atributo | Impacto de la solución | Evidencia / métrica |
|---|---|---|
| Correctitud / Reliability | | |
| Performance / Throughput | | |
| Maintainability | | |
| Scalability | | |

---

# 13. Trade-off principal

¿Qué ganaron y qué sacrificaron al introducir sincronización?

**Respuesta:**

`________________________________________________________________________`

`________________________________________________________________________`

---

# 14. Análisis arquitectónico

Suponga ahora que existen tres instancias de la aplicación:

```text
                 Load Balancer
                       |
            +----------+----------+
            |          |          |
          App A      App B      App C
            \          |          /
                    Database
```

## 14.1 Pregunta

¿Los bloques `synchronized` utilizados dentro de una JVM garantizan consistencia entre `App A`, `App B` y `App C`?

- [ ] Sí
- [ ] No

**Justificación:**

`________________________________________________________________________`

`________________________________________________________________________`

---

## 14.2 Evolución arquitectónica

¿Qué alternativa consideraría para garantizar consistencia entre múltiples instancias?

- [ ] Transacción en base de datos
- [ ] Restricción / constraint en base de datos
- [ ] Optimistic locking / versionado
- [ ] Lock distribuido
- [ ] Otra: `________________________`

**Decisión propuesta:**

`________________________________________________________________________`

**Justificación:**

`________________________________________________________________________`

---

# 15. Mini ADR

## ADR-001 — Concurrency control for warehouse shared state

### Context

`________________________________________________________________________`

`________________________________________________________________________`

### Decision

`________________________________________________________________________`

`________________________________________________________________________`

### Alternatives considered

1. `____________________________________________________________________`
2. `____________________________________________________________________`

### Quality attributes affected

`________________________________________________________________________`

### Evidence

`________________________________________________________________________`

### Consequences

`________________________________________________________________________`

### Risks

`________________________________________________________________________`

---

# 16. Cambios realizados

Resuma los principales cambios de código.

| Archivo / Clase | Cambio realizado | Razón |
|---|---|---|
| | | |
| | | |
| | | |
| | | |

---

# 17. Pruebas ejecutadas

| Prueba | Comando | Resultado |
|---|---|---|
| Compilación y tests | `mvn clean test` | |
| Simulación estándar | | |
| RaceConditionProbe | | |
| Pause / Resume | | |
| Otra | | |

---

# 18. Conclusiones

Incluya entre **3 y 5 conclusiones concretas**.

1. `______________________________________________________________________`
2. `______________________________________________________________________`
3. `______________________________________________________________________`
4. `______________________________________________________________________`
5. `______________________________________________________________________`

---

# 19. Checklist de entrega

- [ ] El proyecto compila con `mvn clean test`.
- [ ] El código utiliza Java 21.
- [ ] No se eliminó la concurrencia.
- [ ] No existe busy waiting en la solución final.
- [ ] El programa espera correctamente la finalización de todos los robots.
- [ ] Las regiones críticas están justificadas.
- [ ] Se preservan las invariantes definidas.
- [ ] El `RaceConditionProbe` final no presenta anomalías.
- [ ] Se documentó el análisis arquitectónico.
- [ ] Se incluyó el ADR.
- [ ] El repositorio contiene commits claros.
- [ ] Se incluyó la URL del repositorio y el commit final.

---

## Nota

No se evalúa la cantidad de texto. Se evalúa la capacidad de demostrar:

> **problema → evidencia → invariante → región crítica → decisión → implementación → verificación → trade-off arquitectónico**
