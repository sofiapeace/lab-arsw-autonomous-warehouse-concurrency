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
`PackageQueue / takeNext()`

**Estado compartido involucrado:**  
`La lista interna pending (List<Parcel>)`

**Comportamiento observado:**  
`Se observan tres consecuencias distintas. (1) Dos robots cargan el mismo paquete: el registro termina con más entradas que paquetes existentes y con IDs repetidos. (2) Los robots arrojan IndexOutOfBoundsException al intentar extraer paquetes de la cola. (3) En el caso más grave la simulación nunca termina: los robots quedan girando indefinidamente y el proceso consume un núcleo al 100%`

**¿Por qué ocurre?**  
`Ocurre por el patrón check-then-act. takeNext() hace tres cosas por separado y sin ninguna protección: pregunta pending.isEmpty(), luego lee pending.get(0) y solo después ejecuta pending.remove(0). Como nada impide que otro robot entre en medio, se dan dos casos. Caso frecuente: dos robots alcanzan a leer get(0) antes de que alguno haga remove(0), así que ambos se llevan el mismo paquete; ese paquete se entrega dos veces y otro queda sin procesar. Caso del último elemento: un robot verifica que la lista no está vacía, otro retira el único que quedaba, y cuando el primero intenta retirarlo ya no existe, lo que produce IndexOutOfBoundsException. Además ArrayList no está preparado para varios hilos: dos remove(0) simultáneos pueden dejar su contador interno de tamaño en negativo. Desde ese momento la lista responde que no está vacía pero no entrega ningún elemento, y como un robot solo termina cuando takeNext() devuelve null, los robots giran para siempre y el programa no termina`

**Evidencia de ejecución:**

```
$ java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 50 32 500
Run 01 -> RACE/ANOMALY | pending=0, processedCounter=461, registry=503, uniqueParcels=418, uniquePositions=431, positionsContiguous=false
Run 02 -> RACE/ANOMALY | pending=0, processedCounter=474, registry=508, uniqueParcels=443, uniquePositions=444, positionsContiguous=false
[warehouse-robot-21] Queue anomaly: IndexOutOfBoundsException
[warehouse-robot-7] Queue anomaly: IndexOutOfBoundsException
[warehouse-robot-24] Queue anomaly: IndexOutOfBoundsException

Anomalous runs: 50/50

$ java -cp target/classes edu.eci.arsw.warehouse.app.WarehouseMain
[warehouse-robot-12] Queue anomaly: IndexOutOfBoundsException

$ java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 30 8 100
Run 20 -> RACE/ANOMALY | pending=0, processedCounter=100, registry=99, uniqueParcels=97, uniquePositions=96, positionsContiguous=false
(la corrida 21 nunca terminó)
```

**Lectura de la evidencia:** con 500 paquetes iniciales el registro quedó con 508
entradas y solo 443 IDs distintos, es decir 65 paquetes se entregaron más de una vez
y 57 no quedaron registrados nunca. La medición es válida porque se tomó después de
que todos los hilos terminaron: el probe llama `awaitCompletion()` antes de
verificar. La corrida con 8 robots y 100 paquetes no llegó a terminar: se quedó
detenida en la corrida 21 consumiendo un núcleo al 100% y escribiendo 22 millones de
líneas de `IndexOutOfBoundsException` (1.5 GB de log) en tres minutos, y hubo que
abortarla manualmente.

---

## Race Condition #2

**Clase / método involucrado:**  
`WarehouseStatistics / recordProcessed()`

**Estado compartido involucrado:**  
`Las variables processedParcels (int) y totalProcessingMillis (long)`

**Comportamiento observado:**  
`El número de paquetes procesados es menor al total de paquetes, y la cantidad de elementos en el registro no coincide con el contador de procesados ni con el total`

**¿Por qué ocurre?**  
`Se debe al patrón "Read-Modify-Write". Cuando dos hilos intentan actualizar un contador no atómico al mismo tiempo (ej. contador++), leen el mismo valor base (ej. 10), ambos lo incrementan a 11 y lo guardan. En lugar de ser 12, el contador queda en 11, "perdiendo" un paquete en la estadística. Lo mismo ocurre con totalProcessingMillis, que se actualiza igual. Este error no produce ninguna excepción ni mensaje: el número simplemente queda por debajo del trabajo realmente hecho`

**Evidencia de ejecución:**

```text
$ java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 50 32 500
Run 02 -> RACE/ANOMALY | pending=0, processedCounter=474, registry=508, uniqueParcels=443, uniquePositions=444, positionsContiguous=false
Run 13 -> RACE/ANOMALY | pending=0, processedCounter=488, registry=499, uniqueParcels=489, uniquePositions=472, positionsContiguous=false

$ java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 30 16 250
Run 30 -> RACE/ANOMALY | pending=0, processedCounter=241, registry=251, uniqueParcels=242, uniquePositions=223, positionsContiguous=false
```

**Lectura de la evidencia:** en las tres corridas el contador quedó por debajo del
número de registros, aunque ambos se incrementan una sola vez por paquete, dentro de
la misma iteración y por el mismo robot. En la corrida 02 la diferencia es de 34
incrementos destruidos. Como el probe llama `awaitCompletion()` antes de
verificar, ningún robot estaba a mitad de trabajo: la diferencia no puede explicarse
por paquetes en vuelo.

---

## Race Condition #3

**Clase / método involucrado:**  
`DeliveryRegistry / register()`

**Estado compartido involucrado:**  
`La variable nextPosition (int) y la lista deliveries (List<DeliveryRecord>)`

**Comportamiento observado:**  
`La bandera positionsContiguous es false, lo que significa que el orden de entrega está corrupto. Además, uniquePositions es menor que registry size, indicando que hay posiciones repetidas. También se pierden registros: con 500 paquetes solo quedaron 443 IDs distintos. En algunas corridas el programa ni siquiera llega al final y aborta con NullPointerException`

**¿Por qué ocurre?**  
`Son tres problemas dentro del mismo método. (1) Posiciones repetidas: dos robots leen al mismo tiempo que la siguiente posición disponible es la "15". Ambos registran su paquete en la posición "15" y luego actualizan el contador a "16". Resultan dos paquetes en la misma posición, violando la regla de secuencia, y además la numeración 1..N queda con huecos. (2) Registros perdidos: deliveries es un ArrayList que varios robots modifican al mismo tiempo, de modo que algunos add() se pisan entre sí y esos registros desaparecen sin dejar rastro ni error. (3) Copia inconsistente: snapshot() ejecuta List.copyOf sobre la lista mientras los robots la siguen modificando; si alcanza a leer una posición todavía vacía, falla con NullPointerException y aborta el programa`

**Evidencia de ejecución:**

```text
$ java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 50 32 500
Run 02 -> RACE/ANOMALY | pending=0, processedCounter=474, registry=508, uniqueParcels=443, uniquePositions=444, positionsContiguous=false
Run 27 -> RACE/ANOMALY | pending=0, processedCounter=458, registry=501, uniqueParcels=472, uniquePositions=442, positionsContiguous=false

$ java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe
Run 10 -> RACE/ANOMALY | pending=0, processedCounter=239, registry=247, uniqueParcels=236, uniquePositions=225, positionsContiguous=false
Exception in thread "main" java.lang.NullPointerException
        at java.base/java.util.Objects.requireNonNull(Objects.java:233)
        at java.base/java.util.ImmutableCollections.listFromArray(ImmutableCollections.java:192)
        at java.base/java.util.List.of(List.java:1172)
        at java.base/java.util.ImmutableCollections.listCopy(ImmutableCollections.java:174)
        at java.base/java.util.List.copyOf(List.java:1193)
        at edu.eci.arsw.warehouse.core.DeliveryRegistry.snapshot(DeliveryRegistry.java:25)
        at edu.eci.arsw.warehouse.app.WarehouseSimulation.snapshot(WarehouseSimulation.java:68)
        at edu.eci.arsw.warehouse.verification.RaceConditionProbe.main(RaceConditionProbe.java:22)
```

**Lectura de la evidencia:** en la corrida 02 hay 508 registros pero solo 444
posiciones distintas, es decir 64 entregas recibieron una posición que ya estaba
ocupada, y `positionsContiguous=false` confirma que la numeración 1..N tiene huecos.
La traza de `NullPointerException` señala exactamente `DeliveryRegistry.java:25`, la
línea del `List.copyOf`, y abortó el probe en la corrida 11 de 30: la lista fue
copiada mientras otro robot la estaba modificando y contenía una posición vacía.

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
