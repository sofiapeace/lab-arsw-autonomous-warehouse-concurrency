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
`4dd8fa9` — commit donde queda cerrado el contenido del informe.

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
`Race Condition #1 — PackageQueue.takeNext()`

Para que la traza sea legible se supone una cola con solo tres paquetes,
`pending = [P1, P2, P3]`, y dos robots, A y B. El código analizado es:

```java
public Parcel takeNext() {
    if (pending.isEmpty()) {          // paso 1
        return null;
    }
    Parcel selected = pending.get(0); // paso 2
    Thread.yield();
    pending.remove(0);                // paso 3
    return selected;
}
```

| Paso | Thread A | Thread B | Estado compartido |
|---:|---|---|---|
| 1 | `pending.isEmpty()` devuelve false | — | `pending = [P1, P2, P3]` |
| 2 | `selected = pending.get(0)` devuelve **P1** | — | `pending = [P1, P2, P3]`, A carga P1 pero todavía no lo saca |
| 3 | Se detiene en `Thread.yield()` | `pending.isEmpty()` devuelve false | `pending = [P1, P2, P3]`, P1 sigue en la lista |
| 4 | — | `selected = pending.get(0)` devuelve **P1** | `pending = [P1, P2, P3]`, A y B cargan el mismo paquete |
| 5 | `pending.remove(0)` elimina P1 | — | `pending = [P2, P3]` |
| 6 | — | `pending.remove(0)` elimina **P2** | `pending = [P3]`, P2 desaparece sin que nadie lo procese |

**Resultado:** P1 se entrega dos veces una por A y otra por B y P2 se elimina de la
cola sin haber sido procesado nunca. Se retiraron dos elementos y se procesaron dos
paquetes, así que la cantidad cuadra, pero el contenido es incorrecto.

### Explicación

¿Por qué este orden de ejecución produce un resultado incorrecto?

**Respuesta:**

`takeNext() no es atómico: consultar isEmpty(), leer get(0) y ejecutar remove(0) son tres operaciones separadas y cualquier otro robot puede entrar en medio. En el paso 2 el robot A ya decidió que se lleva P1, pero para la lista P1 sigue disponible porque todavía no lo ha retirado. Esa ventana entre "elegí el paquete" y "lo saqué de la lista" es la región crítica: mientras dura, la lista miente sobre lo que realmente está disponible, y B toma la misma decisión sobre el mismo paquete.`

`El daño se duplica en el paso 6 porque remove(0) elimina por posición y no por identidad: B no borra el paquete que carga, sino el que esté de primero en ese momento, que ya es P2. Por eso una sola condición de carrera produce a la vez dos síntomas: paquetes entregados más de una vez y paquetes que desaparecen sin registro. Como se retira exactamente un elemento por cada llamada, pending igual llega a 0 y la simulación parece terminar bien: el error no se ve en el conteo de pendientes, solo en el contenido del registro. Eso explica la evidencia de la sección 3, donde pending=0 pero hay 508 registros con apenas 443 IDs distintos sobre 500 paquetes. El Thread.yield() del código no causa el problema, solo ensancha la ventana para que ocurra en cada ejecución en lugar de ocasionalmente.`

---

# 5. Invariantes del sistema

Defina las invariantes que su solución debe preservar.

## Evaluación de las invariantes candidatas

El enunciado propone seis candidatas. Antes de fijar el conjunto definitivo se
clasificó cada una en esta tabla:

| # | Candidata | Clasificación | Razón |
|---:|---|---|---|
| 1 | Cada paquete se procesa a lo sumo una vez | **Incompleta** | "A lo sumo una vez" también se cumple si el paquete no se procesa nunca. Hay que unirla con la candidata 2 para obtener "exactamente una vez" |
| 2 | Ningún paquete desaparece del sistema | **Requerida** | Es la mitad que le falta a la candidata 1 |
| 3 | Las posiciones de llegada son únicas | **Derivada** | Se deduce de la candidata 4: si N registros ocupan exactamente las posiciones 1..N, entonces son N valores distintos y no puede haber repetidos |
| 4 | Las posiciones forman una secuencia válida 1..N | **Requerida** | Es más fuerte que la 3 y es la que realmente hay que verificar |
| 5 | El contador de procesados coincide con el número de registros | **Requerida, pero hay que acotarla** | Solo tiene sentido cuando ningún robot está a mitad de una iteración. Entre `register()` y `recordProcessed()` la igualdad no se cumple ni siquiera en un programa correcto |
| 6 | Al reportar la simulación como terminada no quedan paquetes pendientes | **Incompleta** | `pending=0` se cumplió en el 100% de las corridas defectuosas de la sección 3. Por sí sola no prueba nada: hay que exigir además que el registro tenga tantas entradas como paquetes iniciales y que todos los hilos hayan terminado |

Ninguna candidata resultó innecesaria.

## I1

`Cada paquete se procesa exactamente una vez: el registro no contiene IDs de paquete repetidos y ningún paquete queda sin registrar. Une las candidatas 1 y 2, porque por separado ninguna alcanza. Se verifica con uniqueParcels == registry.size() == initialParcels. Es la invariante que viola la condición de carrera #1 (takeNext), donde P1 se entregó dos veces y P2 desapareció.`

## I2

`Las posiciones de llegada forman la secuencia 1..N sin huecos ni repeticiones, siendo N el número de registros. Se verifica con positionsContiguous == true, que además implica uniquePositions == registry.size(). Es la invariante que viola la condición de carrera #3 (register), donde 64 entregas recibieron una posición ya ocupada.`

## I3

`Cuando ningún robot está a mitad de una iteración, processedParcels == deliveries.size(). El alcance es parte de la invariante: se exige al terminar todos los hilos o con todos los robots detenidos en el punto seguro, nunca en cualquier instante arbitrario. Es la invariante que viola la condición de carrera #2 (recordProcessed), con 34 incrementos destruidos.`

## I4 — opcional

`Cuando el programa reporta que la simulación terminó deben cumplirse las tres condiciones a la vez: pendingParcels == 0, deliveries.size() == initialParcels y todos los hilos robot efectivamente terminados. Las tres juntas, porque pending == 0 se cumplía incluso en las corridas corruptas y porque exigir la terminación real de los hilos es lo que descarta tanto el reporte prematuro de WarehouseMain como el caso en que la simulación nunca termina.`

---

# 6. Regiones críticas

Documente cada región crítica identificada.

| Clase | Región crítica | Invariante protegida | Mecanismo usado | ¿Por qué ese tamaño? |
|---|---|---|---|---|
|PackageQueue |El bloque completo del método takeNext() |Ningún paquete se procesa más de una vez / No desaparecen paquetes |synchronized en el método |Se protege todo el bloque del método porque la verificación (isEmpty) y la extracción (remove) están acopladas lógicamente; si se dejaran separadas, se rompería la atomicidad permitiendo condiciones de carrera |
|DeliveryRegistry |Las líneas en el método register() donde se lee y modifica nextPosition y se añade a deliveries |Las posiciones de entrega son únicas y forman una secuencia válida de 1..N |synchronized en el método |Se abarca todo el proceso de asignación y almacenamiento para garantizar que dos hilos no lean la misma nextPosition al mismo tiempo y colisionen |
|WarehouseStatistics |Las operaciones de lectura, cálculo e incremento en el método recordProcessed() |El contador de procesados y el tiempo total reflejan fielmente las entregas reales |Bloque o método synchronized |Se protege el bloque crítico que realiza la operación "Read-Modify-Write" para evitar que múltiples hilos sobrescriban los contadores y se pierdan datos |

**¿Qué pasaría con el rendimiento si la región protegida fuera innecesariamente grande?**
Si la región crítica fuera innecesariamente grande (por ejemplo, si bloquearamos clases enteras o hiciéramos métodos extremadamente largos y pesados que incluyeran tareas fuera de la mutación de estado), el rendimiento y el throughput (rendimiento de procesamiento por unidad de tiempo) decaerían drásticamente.

Esto ocurre porque transformaríamos un entorno altamente concurrente en una ejecución prácticamente secuencial: los hilos (robots) tendrían que hacer fila larguísima para entrar a ejecutar bloques de código que no lo necesitan, bloqueando los procesadores y aumentando la latencia de toda la simulación. La meta del diseño concurrente es que el bloqueo (synchronized) esté activo el menor tiempo posible, solo protegiendo los datos en memoria crítica

---

# 7. Decisiones de sincronización

## 7.1 Alternativas consideradas

Marque y explique cuáles evaluaron:

- [X] `synchronized`
- [X] `AtomicInteger`
- [ ] Colecciones concurrentes
- [ ] `Lock`
- [X] `wait()` / `notifyAll()`
- [ ] Otra: `________________________`

### Alternativa 1

**Descripción:**  
`Uso de un único bloqueo global (global lock) sincronizando todas las clases con un objeto único o haciendo todos los métodos públicos sincronizados por defecto`

**Ventaja:**  
`Garantiza seguridad total contra condiciones de carrera de forma inmediata y sin análisis complejo de granularidad`

**Desventaja:**  
`Destruye por completo el rendimiento y el throughput (rendimiento de procesamiento), ya que serializa la ejecución de los robots y elimina los beneficios de la concurrencia al obligarlos a hacer fila para cualquier operación`

### Alternativa 2

**Descripción:**  
`Reemplazar los contadores de WarehouseStatistics por AtomicInteger/AtomicLong con incrementAndGet()/addAndGet(), evitando synchronized en esa clase`

**Ventaja:**  
`Es lock-free: usa instrucciones de hardware (compare-and-swap) en vez de bloquear hilos, lo que puede reducir la contención bajo alta concurrencia comparado con synchronized`

**Desventaja:**  
`No sirve para PackageQueue ni DeliveryRegistry, porque ahí la operación involucra varias variables relacionadas (lista + contador) que deben cambiar como una sola unidad — los tipos Atomic* solo garantizan atomicidad de una variable individual, no de una transacción sobre varias`

### Decisión final

**Mecanismo seleccionado:**  
`Primitivas de monitor de Java (synchronized) aplicadas con la menor granularidad posible en los métodos críticos`

**Justificación:**  
`Seleccionamos synchronized porque nos permite garantizar exclusión mutua de manera nativa, protegiendo estrictamente las regiones críticas donde ocurre la mutación del estado compartido (en PackageQueue, DeliveryRegistry y WarehouseStatistics). Al aplicar synchronized a nivel de método o bloque específico (y no de forma global), logramos preservar el paralelismo y la concurrencia de los robots, asegurando el cumplimiento de las invariantes con el menor costo posible en el rendimiento del sistema`


**¿Por qué Thread.sleep(...) no es un sustituto válido de join() cuando se espera a que un trabajador termine?**
Thread.sleep(n) asume que se puede predecir cuánto tardará el trabajo concurrente, lo cual es falso en general: el tiempo de ejecución de los robots depende de la carga (número de parcels, jitter aleatorio, número de núcleos disponibles, scheduling del SO). Si n es muy corto, el reporte se imprime antes de que termine el trabajo (justo el bug del starter). Si n es muy largo, se desperdicia tiempo esperando innecesariamente. join(), en cambio, es una sincronización basada en un evento real (la terminación del hilo), no en una estimación de tiempo — es correcto sin importar cuánto tarde el trabajo, y no depende de suposiciones frágiles sobre el hardware o la carga
---

# 8. Finalización de threads

Explique cómo garantizaron que el programa solamente genera el reporte final cuando todos los robots han terminado.

**Mecanismo utilizado:**  
`Thread.join(), invocado sobre cada WarehouseRobot desde WarehouseSimulation.awaitCompletion(), llamado explícitamente antes de imprimir el reporte final`

**Explicación:**  
`Como se evidenció en la sección 1.2, el starter imprime el reporte con 66 de 100 paquetes aún pendientes porque WarehouseMain.main() no espera a que terminen los robots antes de reportar. La corrección consiste en invocar simulation.awaitCompletion() —que recorre la lista de robots y llama robot.join() sobre cada uno— antes de tomar el snapshot() y de imprimir el reporte. join() bloquea al hilo llamador hasta que el robot correspondiente termine completamente su run(), garantizando que las 12 (o N) tareas hayan finalizado antes de leer cualquier estado compartido, y que el reporte se imprima exactamente una vez, ya en el estado final consistente del sistema`


### Pregunta

¿Por qué usar `Thread.sleep(...)` no sería una solución correcta para esperar la finalización de todos los workers?

**Respuesta:**  
`Porque sleep(n) pausa el hilo actual un tiempo fijo, sin ninguna relación con el estado real de otros hilos — asume que se puede predecir cuánto tardará el trabajo concurrente, lo cual es falso en general (depende del número de parcels, del jitter aleatorio en el procesamiento, del número de robots activos y del scheduling del sistema operativo). Si el tiempo estimado es corto, el reporte se imprime antes de que termine el trabajo —exactamente el bug observado en la sección 1.2, donde de los 100 paquetes solo 22 se habían procesado—; si es demasiado largo, se desperdicia tiempo esperando innecesariamente. join(), en cambio, sincroniza sobre un evento real —la terminación efectiva del hilo— y es correcto sin importar cuánto tarde el trabajo en la práctica`

---

# 9. PAUSE / RESUME

## 9.1 Problema inicial

Explique por qué el busy waiting de la implementación inicial no es adecuado.

**Respuesta:**  
`La implementación inicial usa while (paused) { Thread.onSpinWait(); } en awaitIfPaused(), lo que obliga a cada robot pausado a comprobar la bandera repetidamente en un bucle ajustado, consumiendo ciclos de CPU de forma continua sin realizar trabajo útil. Con varios robots pausados simultáneamente (los 12 del ejemplo), esto desperdicia recursos de procesamiento que podrían usarse para otras tareas del sistema, y no escala: cuantos más robots, más núcleos quedan ocupados solo "preguntando" si ya pueden continuar, en lugar de estar realmente inactivos hasta que exista un evento real que los despierte `

---

## 9.2 Solución implementada

Explique cómo implementaron:

- `pause()`
- espera de los workers
- `resume()`
- despertar coordinado de los workers

**Respuesta:**  
`Se convirtió SimulationControl en un monitor: pause(), resume(), awaitIfPaused() e isPaused() son todos métodos synchronized sobre la misma instancia.

pause() cambia paused = true dentro de la sección sincronizada.
Los robots, en vez de hacer spin, llaman awaitIfPaused(), que evalúa while (paused) { wait(); }: si está pausado, el hilo llama wait(), lo cual libera el lock de SimulationControl y suspende al hilo sin consumir CPU, hasta ser notificado.
resume() cambia paused = false y llama notifyAll(), que despierta a todos los hilos que estaban en wait() sobre ese objeto (se usó notifyAll() y no notify() porque este último solo despertaría a un robot arbitrario, dejando al resto de los 12 bloqueados indefinidamente)`

---

## 9.3 Snapshot consistente

Cuando la simulación está pausada, registre:

```text
$ java -cp target/classes edu.eci.arsw.warehouse.app.PauseResumeDemo

--- PAUSED SNAPSHOT ---
Initial parcels : 180
Pending parcels : 66
Processed count : 114
Registry size   : 114
Current leader  : Robot-02 / parcel 2 / position 1
Simulation paused = true

--- FINAL SNAPSHOT ---
Initial parcels : 180
Pending parcels : 0
Processed count : 180
Registry size   : 180
Current leader  : Robot-02 / parcel 2 / position 1
```

Explique cómo garantizan que esos valores representan un estado consistente.

**Respuesta:**  
`Los valores son consistentes por dos razones. Primero, los robots no se detienen en cualquier punto: el llamado a awaitIfPaused() está al inicio del ciclo principal, es decir después de register() y de recordProcessed(). Un robot detenido ya terminó de escribir el paquete que llevaba y nunca queda a mitad de una escritura, y por eso Processed count y Registry size siempre coinciden (114 y 114). Segundo, wait() suspende realmente al hilo en lugar de dejarlo consultando la bandera, así que mientras dura la pausa ningún robot detenido toca PackageQueue, DeliveryRegistry ni WarehouseStatistics.`

`Hay un límite que conviene declarar: pause() es una solicitud, no una detención inmediata. Un robot que ya tomó un paquete lo termina antes de llegar al punto seguro, de modo que la pausa se propaga en el tiempo que dura un paquete (~25 ms). Por eso el snapshot no se toma en el instante mismo de pausar sino tras un margen de espera. La verificación de que ese margen fue suficiente es empírica: pendientes + registro = 66 + 114 = 180, exactamente el total de paquetes, lo que demuestra que ningún robot quedó con un paquete en la mano al momento de la foto. Garantizarlo por construcción, y no por margen, exigiría que la pausa contara cuántos robots ya llegaron al punto seguro antes de permitir la lectura.`

---

# 10. Verificación con RaceConditionProbe

Ejecute:

```bash
java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 100 32 500
```

## Resultados

| Robots | Paquetes | Runs | Anomalías antes | Anomalías después |
|---:|---:|---:|---:|---:|
| 8 | 100 | 100 | 20/20 | 0/100 |
| 16 | 250 | 100 | 30/30 | 0/100 |
| 32 | 500 | 100 | 50/50 | 0/100 |

**Nota sobre los denominadores.** Las mediciones "antes" se tomaron con menos
corridas que las "después" porque el starter no siempre lograba completar la serie:
con 8 robots y 100 paquetes la sonda se colgó en la corrida 21 y hubo que abortarla
manualmente, y con la configuración por defecto abortó en la corrida 11 con
`NullPointerException`. Aun así el resultado es concluyente: **el 100% de las
corridas del starter fue anómalo en las tres configuraciones**, mientras que la
solución final completa las 100 corridas sin una sola anomalía.

### Resultado final esperado

```
Anomalous runs: 0/100
```

**Salida obtenida:**

```text
Anomalous runs: 0/100
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

Para demostrar de forma rigurosa y empírica que la solución implementada es correcta, nos basamos en los resultados de la sonda de verificación (RaceConditionProbe) y el diseño arquitectónico de concurrencia:

Invariantes: Mediante el uso de bloques synchronized con mínima granularidad, garantizamos las propiedades fundamentales del sistema: ningún paquete se procesa más de una vez, las posiciones de entrega forman una secuencia perfecta de 1..N, y los registros de estadísticas son exactos.

Múltiples ejecuciones: Se ejecutó el comando de prueba a través de 100 iteraciones consecutivas, logrando que el 100% de las corridas finalicen sin arrojar excepciones (IndexOutOfBoundsException) y resultando en Anomalous runs: 0/100.

Distintas cargas: El sistema demostró estabilidad y ausencia de bloqueos mutuos (Deadlocks) al ser sometido a estrés con diversas configuraciones, superando pruebas pesadas (ej. 32 robots y 500 paquetes) que fuerzan al planificador del sistema operativo a realizar miles de cambios de contexto.

Ausencia de resultados duplicados: Las métricas finales indican que el tamaño del registro (registry=500) es exactamente igual a las posiciones únicas (uniquePositions=500) y la bandera positionsContiguous retorna true, demostrando que no hubo colisiones de lectura-escritura en el DeliveryRegistry.

Ausencia de paquetes perdidos: Al finalizar, los paquetes pendientes llegan exactamente a cero (pending=0) y el contador de paquetes procesados coincide con el total despachado (processedCounter=500), evidenciando que se solucionaron los patrones de 'Read-Modify-Write' en WarehouseStatistics.

Finalización correcta: La integración de robot.join() garantiza estructuralmente que el hilo principal se suspenda hasta que el último robot termine su ejecución. Esto previene impresiones prematuras del reporte y asegura que las métricas globales reflejen el 100% del trabajo realizado.

Consistencia durante pausa: el monitor con wait() y notifyAll() hace que los robots se detengan en puntos seguros, o sea después de haber terminado de registrar el paquete que llevaban. Por eso un robot detenido nunca deja una escritura a medias y el contador de procesados coincide con el tamaño del registro. Como se explica en la sección 9.3, la pausa no es instantánea, porque un robot que ya tomó un paquete lo termina antes de detenerse, así que el snapshot se toma tras un margen de espera y su consistencia se comprueba verificando que los pendientes más los registrados sumen el total de paquetes.

Conclusión:
El rediseño del sistema utilizando primitivas de sincronización de Java sobre las regiones críticas estrictas eliminó todas las condiciones de carrera detectadas. Las pruebas confirman que el modelo garantiza la integridad atómica de los datos y respeta el ciclo de vida de los hilos, logrando un sistema concurrente confiable.
---

# 12. Impacto en atributos de calidad

| Atributo | Impacto de la solución | Evidencia / métrica |
|---|---|---|
| Correctitud / Reliability |Aumenta significativamente: se eliminan las tres condiciones de carrera documentadas en la sección 3 |RaceConditionProbe pasa de anomalías visibles (Run 02 de la sección 3: processedCounter=474≠registry=508, uniquePositions=444≠508) a 0/100 en la solución final |
| Performance / Throughput |Existe un ligero overhead inherente al uso de bloqueos (adquirir y liberar el monitor). Sin embargo, el throughput general se preservó al usar granularidad mínima (bloqueando solo las líneas estrictas de mutación). Además, se mejoró el rendimiento general del procesador al eliminar la "espera activa" |Reducción del 100% de los ciclos de CPU desperdiciados durante el estado de pausa, al reemplazar Thread.onSpinWait() por el bloqueo pasivo de wait(). Los robots no forman "cuellos de botella" masivos gracias a que las regiones críticas son muy cortas |
| Maintainability |El código es más limpio y fácil de razonar matemáticamente. Al centralizar la lógica de concurrencia de las pausas en el monitor SimulationControl |Uso de estándares idiomáticos de Java (synchronized, wait(), notifyAll(), join()). Código legible donde el límite de la "región crítica" es evidente |
| Scalability |La solución escala muy bien de forma vertical (añadiendo más procesadores o núcleos a la misma máquina para soportar más hilos) |La evidencia recae en la naturaleza de la JVM: los candados de synchronized solo existen en la memoria local de una única máquina. Si se añade un balanceador de carga con múltiples servidores, la solución perdería su efecto protector y requeriría refactorizarse hacia bloqueos distribuidos |

---

# 13. Trade-off principal

¿Qué ganaron y qué sacrificaron al introducir sincronización?

**Respuesta:**

`Se ganó corrección verificable: el sistema ahora sostiene los invariantes bajo cualquier interleaving posible del scheduler, no solo "la mayoría de las veces" como el starter. A cambio, se sacrificó parte del throughput del starter original, porque ahora los robots pueden quedar bloqueados esperando un lock cuando compiten por el mismo recurso (por ejemplo, varios de los 12+ robots intentando tomar un parcel de PackageQueue al mismo tiempo). Este costo es aceptable porque el starter "rápido" era en realidad incorrecto —su velocidad venía precisamente de no garantizar nada—, y el trade-off se minimizó manteniendo la granularidad de los locks lo más pequeña posible (un lock por objeto, no un lock global compartido entre las tres clases), preservando la mayor parte del paralelismo real entre operaciones sobre recursos distintos`
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
- [X] No

**Justificación:**

`No. El synchronized solo funciona dentro de una misma JVM. El candado existe en la memoria de ese programa y en ningún otro lado. Si App A bloquea su DeliveryRegistry, App B y App C ni se enteran, porque cada una tiene su propia copia de ese objeto en su propia memoria. Cada instancia tiene su propio candado y ninguno puede detener a los hilos de las otras dos.`

`Esto se ve rápido con nuestras invariantes. La I2 dice que las posiciones de llegada van de 1 hasta N sin repetirse. Pero cada instancia arranca su nextPosition en 1, entonces las tres le darían la posición 1 a paquetes diferentes. Es el mismo problema de la condición de carrera #3, solo que ahora pasa entre programas distintos y el synchronized no puede hacer nada. La I1 también se cae, porque nada evita que dos instancias tomen el mismo paquete. Lo importante es que arreglar la concurrencia dentro de una JVM no sirve automáticamente cuando hay varias corriendo al tiempo.`

---

## 14.2 Evolución arquitectónica

¿Qué alternativa consideraría para garantizar consistencia entre múltiples instancias?

- [X] Transacción en base de datos
- [X] Restricción / constraint en base de datos
- [ ] Optimistic locking / versionado
- [ ] Lock distribuido
- [ ] Otra: `________________________`

**Decisión propuesta:**

`Sacar el estado compartido de la memoria de los programas y guardarlo en la base de datos. La lista de paquetes y el registro de entregas pasan a ser tablas. Para tomar un paquete se usa una transacción, y para que no se repitan las posiciones de llegada se pone una restricción UNIQUE en esa columna.`

**Justificación:**

`El problema de fondo no es cambiar un candado por otro. Es que el estado compartido ya no cabe en un solo programa. Mientras cada instancia tenga su propia copia en memoria no hay sincronización que sirva, porque no es un estado sino tres.`

`Escogimos la base de datos porque ya es lo único que las tres instancias comparten, entonces no toca agregar nada nuevo. Para tomar un paquete se hace una consulta que busca una fila pendiente y la marca como tomada dentro de la misma transacción. Así la base de datos se encarga de que dos instancias no puedan llevarse la misma fila, que es justo lo que hacía el synchronized en takeNext(). En SQL esto se logra con SELECT FOR UPDATE SKIP LOCKED. Para la posición de llegada se usa una secuencia de la base de datos y se agrega la restricción UNIQUE. Esa restricción sirve mucho porque la base de datos la hace cumplir siempre, incluso si el código llega a tener un error. Un candado en cambio solo funciona si el programador se acuerda de pedirlo.`

---

# 15. Mini ADR

## ADR-001 — Concurrency control for warehouse shared state

### Context

`La simulación del almacén corre cada robot como un hilo de Java y todos comparten los mismos cuatro objetos: PackageQueue, DeliveryRegistry, WarehouseStatistics y SimulationControl. El código inicial no tenía ninguna sincronización, entonces aparecían paquetes entregados dos veces, registros que se perdían, posiciones de llegada repetidas y contadores por debajo del trabajo real. En una corrida el programa ni siquiera terminó y se quedó girando.`

`Había que corregir esto con dos restricciones del enunciado: no quitar la concurrencia y no resolverlo poniendo un solo candado global que volviera todo secuencial.`

### Decision

`Usar las primitivas de monitor de Java (synchronized) sobre las regiones críticas mínimas, con un candado por objeto y no uno compartido entre las tres clases. En PackageQueue se protege takeNext() completo, porque revisar si la lista está vacía, mirar el primer paquete y sacarlo tienen que pasar como una sola operación. En DeliveryRegistry se protege register(), porque asignar la posición y guardar el registro también van juntos. En WarehouseStatistics se protege recordProcessed(), que es donde se suma 1 a los dos contadores.`

`Para la pausa se convirtió SimulationControl en un monitor con wait() y notifyAll(), en lugar del ciclo de espera activa que traía el código inicial. La terminación de los hilos se coordina con join() desde awaitCompletion(). El trabajo pesado, o sea el Thread.sleep que simula el transporte del paquete, queda por fuera de los bloques sincronizados para no bloquear a los demás robots mientras uno "camina".`

### Alternatives considered

1. `Un candado global, ya sea un objeto único compartido o marcando todos los métodos públicos como synchronized. Es la opción más fácil y garantiza que no haya condiciones de carrera, pero vuelve la ejecución prácticamente secuencial: los robots harían fila para cualquier operación y se perdería el beneficio de la concurrencia. El enunciado además lo prohíbe explícitamente.`
2. `Reemplazar los contadores por AtomicInteger y AtomicLong. Funciona bien para WarehouseStatistics porque son variables sueltas, pero no sirve para PackageQueue ni para DeliveryRegistry, donde hay que mover varias cosas juntas (la lista más el contador de posición) como una sola unidad. Los tipos Atomic solo garantizan atomicidad de una variable a la vez.`

### Quality attributes affected

`Correctitud: mejora mucho, es el objetivo del cambio. Rendimiento: baja un poco porque los robots pueden quedar esperando un candado, aunque se mitigó dejando las regiones críticas lo más cortas posible. Mantenibilidad: mejora, porque toda la lógica de concurrencia quedó concentrada en las tres clases de core y no repartida por el código. Escalabilidad: solo sirve dentro de una misma JVM, como se explica en la sección 14.`

### Evidence

`RaceConditionProbe con 100 corridas, 32 robots y 500 paquetes da Anomalous runs: 0/100, con registry=500, uniqueParcels=500, uniquePositions=500 y positionsContiguous=true. Antes de los cambios las mismas configuraciones daban 50/50, 30/30 y 20/20 corridas anómalas, y en el caso de 8 robots la sonda se colgó en la corrida 21. mvn clean test pasa sin errores y PauseResumeDemo entrega sumas exactas (66 pendientes + 114 registrados = 180 paquetes).`

### Consequences

`Los robots ahora pueden quedar esperando cuando dos quieren el mismo objeto al tiempo, así que hay algo menos de paralelismo que en el código inicial. Como se usó un candado por objeto y no uno global, dos robots sí pueden trabajar al mismo tiempo si están tocando cosas distintas, por ejemplo uno registrando una entrega mientras otro toma un paquete de la cola. El estado compartido queda consistente únicamente dentro de una JVM.`

### Risks

`Si alguien agrega después un método nuevo que toque el estado compartido y se le olvida sincronizarlo, el problema vuelve sin que nada avise, porque estos errores no lanzan excepciones sino que dejan números mal. Si alguien mete trabajo pesado dentro de un bloque synchronized se pierde el paralelismo que se quiso conservar. Y si el sistema se despliega en varias instancias, synchronized deja de proteger la invariante y toca cambiar de enfoque, como se analizó en la sección 14.`

---

# 16. Cambios realizados

Resuma los principales cambios de código.

| Archivo / Clase | Cambio realizado | Razón |
|---|---|---|
| `PackageQueue` | Se marcó `takeNext()` como `synchronized` y se quitó el `Thread.yield()` que tenía en la mitad | Revisar si la lista está vacía, mirar el primer paquete y sacarlo tienen que pasar como una sola operación. Si quedan separados, dos robots se llevan el mismo paquete y la lista se daña por dentro |
| `DeliveryRegistry` | Se marcó `register()` como `synchronized` y se quitó el `Thread.yield()` | Asignar la posición de llegada y guardar el registro deben ir juntos. Si no, dos robots reciben la misma posición y además se pierden registros porque varios escriben sobre la lista al tiempo |
| `WarehouseStatistics` | Se marcó `recordProcessed()` como `synchronized` y las sumas quedaron directas: `processedParcels++` y `totalProcessingMillis += elapsedMillis` | Sumar 1 no es una sola operación. Si dos robots leen el mismo valor antes de que alguno escriba, se pierde un incremento y el contador queda por debajo del trabajo que sí se hizo |
| `SimulationControl` | Se reemplazó la espera activa por un monitor. `pause()`, `resume()`, `awaitIfPaused()` e `isPaused()` quedaron `synchronized`, usando `wait()` y `notifyAll()`. El campo `paused` ya no necesita ser `volatile` porque todo el acceso pasa por métodos sincronizados | Con `Thread.onSpinWait()` cada robot pausado ocupaba un núcleo al 100% sin hacer nada útil. Con `wait()` el hilo queda suspendido de verdad y no gasta CPU hasta que `resume()` lo despierta. Se usó `notifyAll()` y no `notify()` porque este último despertaría a un solo robot y dejaría a los demás bloqueados |
| `WarehouseRobot` | Se agregó el manejo de `InterruptedException` alrededor de la llamada a `awaitIfPaused()` | Ese método ahora puede lanzar esa excepción porque usa `wait()`. Al capturarla se restaura la marca de interrupción del hilo y el robot sale del ciclo de forma ordenada en vez de morir de golpe |
| `WarehouseMain` | Se quitó el `Thread.sleep(60)` junto con el reporte prematuro, y se llama `simulation.awaitCompletion()` antes de imprimir un único reporte final | `Thread.sleep(60)` era una adivinanza: el hilo principal suponía que en 60 ms los robots ya habrían terminado, y por eso el reporte salía con 66 paquetes todavía pendientes. `awaitCompletion()` hace `join()` sobre cada robot, o sea que espera un hecho real (que el hilo termine) y no un tiempo estimado. Así el reporte se imprime una sola vez y con el estado final |

---

# 17. Pruebas ejecutadas

| Prueba | Comando | Resultado |
|---|---|---|
| Compilación y tests | `mvn clean test` | BUILD SUCCESS. Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 |
| Simulación estándar | `java -cp target/classes edu.eci.arsw.warehouse.app.WarehouseMain` | Un solo reporte final, ya no sale el prematuro. Pending 0, Processed 100, Registry 100 sobre 100 paquetes |
| Simulación con más carga | `java -cp target/classes edu.eci.arsw.warehouse.app.WarehouseMain 24 250` | Pending 0, Processed 250, Registry 250 sobre 250 paquetes |
| RaceConditionProbe (configuración exigida) | `java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 100 32 500` | `Anomalous runs: 0/100`, con registry=500, uniqueParcels=500, uniquePositions=500 y positionsContiguous=true |
| RaceConditionProbe (otras cargas) | `... RaceConditionProbe 100 8 100` y `... RaceConditionProbe 100 16 250` | `Anomalous runs: 0/100` en las dos. Con esto quedan verificadas a 100 corridas las tres configuraciones de la tabla de la sección 10 |
| Pause / Resume | `java -cp target/classes edu.eci.arsw.warehouse.app.PauseResumeDemo` | Snapshot pausado con 66 pendientes y 114 registrados, que suman los 180 paquetes. Processed y Registry coinciden. Snapshot final en 0 pendientes y 180 registrados |

---

# 18. Conclusiones

Incluya entre **3 y 5 conclusiones concretas**.

1. `Un programa concurrente puede parecer correcto y estar mal. El código inicial terminaba sin errores y mostraba números creíbles, pero al correr la sonda 50 veces seguidas resultó que el 100% de las corridas estaba mal. Una sola ejecución buena no prueba nada. Para saber si un sistema concurrente funciona hay que correrlo muchas veces y verificar invariantes, no mirar si "salió bien esta vez".`
2. `Arreglar una condición de carrera no es agregar candados sino darse cuenta de cuáles operaciones tienen que pasar como una sola. El problema de takeNext() no era que le faltara synchronized en general, era que revisar si la lista está vacía, mirar el primer paquete y sacarlo estaban separados. Por eso el candado va justo sobre esas tres líneas y no sobre toda la clase, y por eso el Thread.sleep que simula el transporte quedó por fuera.`
3. `Esperar por tiempo no es sincronizar. Thread.sleep(60) suponía que en 60 milisegundos los robots ya habrían terminado, y esa suposición se rompe apenas cambia la carga o la máquina. join() no supone nada porque espera un hecho real, que es que el hilo termine. La diferencia se vio directo en el reporte: 66 paquetes todavía pendientes contra 0.`
4. `Una condición de carrera no solo daña datos, también puede tumbar el programa. Con 8 robots y 100 paquetes el ArrayList quedó con su contador interno de tamaño en negativo, entonces la lista decía que no estaba vacía pero no entregaba nada. Los robots quedaron girando para siempre, el proceso ocupó un núcleo al 100% y escribió 22 millones de líneas de error en tres minutos. Un dato corrupto se puede corregir, un programa que nunca termina toca matarlo a mano.`
5. `El synchronized solo protege dentro de una misma JVM. Si mañana el almacén se despliega en tres instancias detrás de un balanceador, todo este trabajo de sincronización deja de servir y hay que mover el estado compartido a la base de datos. O sea que la solución correcta no depende solo del lenguaje sino de dónde vive el estado que se está protegiendo.`

---

# 19. Checklist de entrega

- [X] El proyecto compila con `mvn clean test`.
- [X] El código utiliza Java 21.
- [X] No se eliminó la concurrencia.
- [X] No existe busy waiting en la solución final.
- [X] El programa espera correctamente la finalización de todos los robots.
- [X] Las regiones críticas están justificadas.
- [X] Se preservan las invariantes definidas.
- [X] El `RaceConditionProbe` final no presenta anomalías.
- [X] Se documentó el análisis arquitectónico.
- [X] Se incluyó el ADR.
- [X] El repositorio contiene commits claros.
- [X] Se incluyó la URL del repositorio y el commit final.

**Sustento de cada punto:**

| Punto | Dónde se verifica |
|---|---|
| Compila con `mvn clean test` | Sección 17. BUILD SUCCESS con 2 pruebas y 0 fallas |
| Java 21 | `pom.xml` define `<maven.compiler.release>21</maven.compiler.release>`, y la nota de la sección 1.1 explica la diferencia con el JDK sobre el que corre Maven |
| No se eliminó la concurrencia | Los robots siguen siendo hilos de plataforma (`WarehouseRobot extends Thread`). No se usaron pools ni hilos virtuales, como pide el enunciado |
| Sin busy waiting | `SimulationControl` usa `wait()` y `notifyAll()`. No queda ningún `Thread.onSpinWait()` en el código |
| Espera la finalización de los robots | `WarehouseMain` llama `awaitCompletion()`, que hace `join()` sobre cada robot antes del reporte final. Secciones 8 y 16 |
| Regiones críticas justificadas | Sección 6, con la invariante que protege cada una y por qué ese tamaño |
| Invariantes preservadas | Sección 5 las define y la sonda las verifica en la sección 10 |
| Sonda sin anomalías | `0/100` en las tres configuraciones: 8/100, 16/250 y 32/500. Secciones 10 y 17 |
| Análisis arquitectónico | Sección 14 |
| ADR | Sección 15 |
| Commits claros | Historial del repositorio, con un commit por sección y el motivo del cambio en el cuerpo |

---

## Nota

No se evalúa la cantidad de texto. Se evalúa la capacidad de demostrar:

> **problema → evidencia → invariante → región crítica → decisión → implementación → verificación → trade-off arquitectónico**
