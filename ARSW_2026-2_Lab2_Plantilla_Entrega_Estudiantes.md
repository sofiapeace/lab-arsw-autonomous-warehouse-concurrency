# ARSW — Laboratorio #2
## Plantilla de entrega — Autonomous Warehouse

**Asignatura:** Arquitecturas de Software — ARSW
**Periodo:** 2026-2  
**Laboratorio:** #2 — Autonomous Warehouse  
**Tema:** Race Conditions · Critical Sections · Thread Coordination  
**Tecnología:** Java 21 · Maven · JUnit 5  

---

## 0. Información del equipo

| Integrante                | Código / ID | GitHub     |
|---------------------------|-------------|------------|
| Jose Luis Lancheros Ayora | 100102647   | Lanch3ros  |
| Gina Sofia Garcia Zapata  | 1000100098  | sofiapeace |

**Repositorio:**  
https://github.com/sofiapeace/lab-arsw-autonomous-warehouse-concurrency

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
❯ java -version
openjdk version "21.0.11" 2026-04-21
OpenJDK Runtime Environment Homebrew (build 21.0.11)
OpenJDK 64-Bit Server VM Homebrew (build 21.0.11, mixed mode, sharing)
❯ mvn -version
Apache Maven 3.9.16 (2bdd9fddda4b155ebf8000e807eb73fd829a51d5)
Maven home: /opt/homebrew/Cellar/maven/3.9.16/libexec
Java version: 26.0.1, vendor: Homebrew, runtime: /opt/homebrew/Cellar/openjdk/26.0.1/libexec/openjdk.jdk/Contents/Home
Default locale: en_CO, platform encoding: UTF-8
OS name: "mac os x", version: "26.5.2", arch: "aarch64", family: "mac"
```

**Nota:** `java` en el PATH es JDK 21.0.11, mientras que Maven se ejecuta sobre
JDK 26.0.1. Esto no afecta el laboratorio: `pom.xml` define
`<maven.compiler.release>21</maven.compiler.release>`, por lo que el bytecode
generado es Java 21. La salida de `mvn clean test` lo confirma:
`Compiling 15 source files with javac [debug release 21]`.

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

- Robots: 12 (valor por defecto)
- Paquetes: 100 (valor por defecto)

**Resultado observado:**

```text
❯ java -cp target/classes edu.eci.arsw.warehouse.app.WarehouseMain
Starting warehouse with 12 robots and 100 parcels...

--- STARTER REPORT (intentionally premature) ---
Initial parcels : 100
Pending parcels : 66
Processed count : 22
Registry size   : 22
Current leader  : Robot-08 / parcel 8 / position 1
----------------------------------------------

[warehouse-robot-12] Queue anomaly: IndexOutOfBoundsException
```

**Observaciones:** el reporte se imprime con 66 paquetes aún pendientes,
porque `WarehouseMain` usa `Thread.sleep(60)` en vez de `join()`. 
`Processed count` (22) coincide aquí con `Registry size` (22), pero son dos
contadores independientes y no siempre coinciden. Están alojados en objetos
distintos y se actualizan mediante dos llamadas separadas del robot
(`deliveryRegistry.register(...)` y `statistics.recordProcessed(...)`), por lo que
un robot puede ser fotografiado entre ambas. En la ejecución con 24 robots y 250
paquetes la diferencia sí es visible: `Processed count : 38` frente a
`Registry size : 43`.

**La suma no cuadra, y en este punto es esperado.** 66 pendientes + 22 registrados
= 88, no 100. Los 12 restantes están *en vuelo*: cada uno de los 12 robots sostiene
un paquete que ya retiró de la cola pero que todavía no registra. Un snapshot
tomado a mitad de ejecución nunca cuadra, por lo que **este dato no constituye
evidencia de una condición de carrera**. La evidencia válida solo se obtiene
después de la terminación de todos los hilos.

**Defecto real visible.** La línea
`[warehouse-robot-12] Queue anomaly: IndexOutOfBoundsException` sí es evidencia
directa de acceso concurrente no sincronizado: `PackageQueue.takeNext()` evaluó
`isEmpty()` como falso y aun así `get(0)`/`remove(0)` falló, porque otro robot
modificó la lista entre ambas operaciones.

---

# 2. Estado mutable compartido

Identifique los objetos y variables compartidas entre múltiples threads.

| Objeto / Clase | Estado mutable compartido                               | Quién lee                                                                                              | Quién modifica                                                                     | Riesgo identificado                                                                                                                                                                                                                                                                                                                                                                                                                               |
|---|---------------------------------------------------------|--------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `PackageQueue` | List<Parcel> pending (ArrayList, no sincronizado)       | Los N robots vía takeNext(), el hilo principal vía pendingCount()                                      | Los N robots vía takeNext() (remove(0))                                            | El método pregunta si la lista está vacía, luego mira el primer paquete y solo después lo saca. Entre esos tres pasos otro robot puede hacer lo mismo, así que dos robots terminan cargando el mismo paquete. A este patrón se le llama check-then-act (revisar y luego actuar). Además, como ArrayList no está preparado para varios hilos, la lista se daña por dentro: aparece IndexOutOfBoundsException y su contador interno de tamaño puede quedar en negativo. Cuando eso pasa, la lista dice que no está vacía pero no entrega nada, los robots se quedan girando para siempre y el programa nunca termina                                                                                                                                                                                                                                                             |
| `DeliveryRegistry` | int nextPosition y List<DeliveryRecord> deliveries (ArrayList, no sincronizado) | Los N robots leen nextPosition dentro de register(), el hilo coordinador lee deliveries vía snapshot() | Los N robots vía register(), nextPosition = nextPosition + 1 y deliveries.add(...) | 1. Asignar la posición se hace en dos pasos: primero se lee nextPosition y después se guarda ese valor más uno. Si dos robots leen antes de que alguno alcance a escribir, ambos reciben la misma posición de llegada (uniquePositions=444 frente a registry=508). 2. Varios robots agregan registros al mismo tiempo sobre un ArrayList que no está preparado para eso, así que hay registros que simplemente se pierden (57 paquetes sin rastro) y quedan espacios vacíos dentro de la lista. 3. snapshot() copia la lista mientras los robots la siguen modificando; si alcanza a copiar uno de esos espacios vacíos, falla con NullPointerException, que fue lo que abortó la corrida 11 del probe |
| `WarehouseStatistics` | int processedParcels y long totalProcessingMillis (ninguno volatile) | Los N robots leen ambos campos dentro de recordProcessed(), el hilo coordinador los lee vía processedParcels() y totalProcessingMillis() en snapshot() | Los N robots vía recordProcessed(), processedParcels = current + 1 y totalProcessingMillis = accumulated + elapsedMillis | Sumar 1 no es una sola operación: el robot lee el valor, le suma 1 y lo vuelve a guardar. Si dos robots leen 10 al mismo tiempo, ambos guardan 11, así que se procesaron dos paquetes pero el contador solo subió uno. Ese incremento perdido no produce ningún error visible: el número simplemente queda por debajo del trabajo realmente hecho. Evidencia válida, medida cuando ya todos los hilos habían terminado: processedCounter=474 frente a registry=508, es decir 34 incrementos destruidos. Lo mismo ocurre con totalProcessingMillis. Como además ninguno de los dos campos es volatile, un robot puede seguir viendo un valor viejo aunque otro ya lo haya cambiado |
| `SimulationControl` | volatile boolean paused | Los N robots vía awaitIfPaused(), el hilo coordinador vía isPaused() | Únicamente el hilo coordinador, vía pause() y resume() | Aquí no se dañan los datos: el campo es volatile, así que cuando el hilo principal cambia paused todos los robots alcanzan a ver el cambio. El problema es otro: awaitIfPaused() espera dando vueltas en un ciclo (while (paused) Thread.onSpinWait()) en lugar de dormirse. Un robot pausado sigue ocupando un núcleo del procesador al 100% sin hacer nada útil, de modo que N robots pausados desperdician N núcleos. Además, cuando se llama a pause() no hay nada que garantice que los robots ya se hayan detenido: algunos siguen a mitad de su trabajo, por lo que un reporte tomado durante la pausa puede quedar incompleto |
| Otro: `WarehouseSimulation.snapshot()` | No es un campo, sino estado compuesto: los cuatro objetos anteriores leídos como si fueran una sola unidad (pendingCount(), processedParcels(), totalProcessingMillis() y registry.snapshot()) | El hilo coordinador, desde WarehouseMain, PauseResumeDemo y RaceConditionProbe | Los N robots, de forma continua, entre una lectura y la siguiente | Los cuatro valores no se leen al mismo tiempo, sino uno después del otro, y los robots siguen trabajando entre una lectura y la siguiente. Por eso el reporte puede mezclar datos de momentos distintos y mostrar una situación que en realidad nunca ocurrió. Es como fotografiar cuatro relojes de a uno: cada foto es correcta por separado, pero juntas no muestran la misma hora. A esto se le llama lectura inconsistente o torn read. Lo importante es que arreglar las cuatro clases por separado no soluciona este problema: aunque cada una sea segura por su cuenta, leerlas una tras otra sigue siendo inseguro. La solución es leer los cuatro valores cuando todos los robots estén detenidos, o impedir que trabajen mientras dura la lectura. |

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
