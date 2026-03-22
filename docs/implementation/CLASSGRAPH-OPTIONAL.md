# ClassGraph optional machen in Morphium

## Kontext

Morphium nutzte ClassGraph (v4.8.184) an 7 Stellen im Produktionscode fuer Runtime-Classpath-Scanning. Das ist **inkompatibel mit Quarkus** (Build-Time-Philosophie, GraalVM Native Image, geschlossene Class-World) und problematisch fuer Spring Boot/Jakarta EE. Es gab keine Moeglichkeit fuer Frameworks, eigene Discovery-Ergebnisse zu injizieren -- ClassGraph war hart verdrahtet.

**Ziel:** ClassGraph **optional** machen (`<optional>true</optional>`), ohne Standalone-Anwender zu beeintraechtigen. Frameworks koennen Discovery-Ergebnisse vorab registrieren, sodass ClassGraph nie aufgerufen wird. Standalone-User bemerken keine Aenderung.

**Ergebnis:** Umgesetzt in morphium PR #13 (`Bardioc1977/morphium`) und quarkus-morphium PR #31 (`Bardioc1977/quarkus-morphium`). Zero Breaking Changes.

## Betroffene Stellen (7 Produktionscode-Stellen)

| # | Datei | Methode | Scannt nach |
|---|-------|---------|-------------|
| 1 | `AnnotationAndReflectionHelper.java` | `init()` | `@Entity`, `@Embedded` -> typeId-Map |
| 2 | `ObjectMapperImpl.java` | Konstruktor | `@Entity` -> collectionName->Class Map |
| 3 | `Morphium.java` | `initializeAndConnect()` | `@Messaging` -> Messaging-Impl |
| 4 | `Morphium.java` | `initializeAndConnect()` | `@Driver` -> Driver-Impl |
| 5 | `Morphium.java` | `checkCapped()` | `@Capped` -> Capped-Validierung |
| 6 | `Morphium.java` | `checkIndices(ClassInfoFilter)` | `@Entity` -> Index-Validierung |
| 7 | `InMemoryDriver.java` | `connect()` | `MongoCommand`-Subklassen |

## Architektur-Entscheidungen

### Kernprinzip: Typ-Isolation

Das zentrale Problem: `<optional>true</optional>` allein genuegt **nicht**. Die JVM loest Typen in Import-Statements und Catch-Clauses **beim Laden der Klasse** auf -- nicht erst bei Ausfuehrung. Ein `import io.github.classgraph.ClassGraph` oder `catch (ClassGraphException e)` in `ObjectMapperImpl` fuehrt zu `NoClassDefFoundError` beim Laden von `ObjectMapperImpl`, selbst wenn der Code-Pfad nie erreicht wird.

**Loesung:** Alle ClassGraph-Typen werden **ausschliesslich** in `ClassGraphHelper.java` referenziert. Keine andere Produktionsklasse importiert ClassGraph direkt. Intern nutzt `ClassGraphHelper` vollqualifizierte Namen (`new io.github.classgraph.ClassGraph()`) und `var` statt expliziter Typ-Deklarationen.

### Drei-Stufen-Discovery

```
1. EntityRegistry (Pre-Registration)  -- Framework-gesteuert, kein Scanning
       |
       v  (leer?)
2. ClassGraphHelper (Runtime-Scan)    -- Standalone-Fallback, optional
       |
       v  (nicht verfuegbar?)
3. Leere Ergebnisse + Warnung         -- Graceful Degradation
```

## Implementierung im Detail

### Neue Klassen in morphium-core

#### `ClassGraphHelper.java`

**Pfad:** `morphium-core/src/main/java/de/caluga/morphium/ClassGraphHelper.java`

Package-private Utility, zentralisiert **alle** ClassGraph-Zugriffe.

```java
final class ClassGraphHelper {
    private static volatile boolean available;
    private static final AtomicBoolean warned = new AtomicBoolean(false);

    static { available = checkClassGraphPresent(); }

    // Prueft TCCL und eigenen ClassLoader (wichtig fuer Quarkus)
    static boolean isAvailable() { ... }

    // Einmalige Warnung wenn ClassGraph fehlt
    static void warnIfUnavailable() { ... }

    // --- Scan-Methoden (6 Stueck) ---
    static Map<String, String> scanEntityTypeIds()
    static Map<String, Class<?>> scanEntityCollections(Function<Class<?>, String>)
    static List<Class<?>> scanForAnnotatedClasses(String annotationFqcn)
    static List<String> scanForAnnotatedClassNamesFiltered(String annotationFqcn, Object filter)
    static Class<?> scanForMessagingImpl(String msgImplName)
    static Class<?> scanForDriverImpl(String driverName)
}
```

Verfuegbarkeitspruefung via `Class.forName("io.github.classgraph.ClassGraph", false, classLoader)` -- erst TCCL, dann eigener ClassLoader. Lazy Re-Check bei jedem `isAvailable()`-Aufruf, da der TCCL sich aendern kann (Quarkus setzt seinen ClassLoader nach dem Extension-Laden).

#### `EntityRegistry.java`

**Pfad:** `morphium-core/src/main/java/de/caluga/morphium/EntityRegistry.java`

Oeffentliche Pre-Registration API. Wird von Frameworks **vor** der Morphium-Instanziierung aufgerufen.

```java
public final class EntityRegistry {
    private static volatile Map<String, String> preRegisteredTypeIds;    // typeId -> FQCN
    private static volatile Set<Class<?>> preRegisteredEntities;         // alle Entity-Klassen

    // Framework-API
    public static synchronized void preRegisterEntities(Collection<Class<?>> entityClasses)
    public static synchronized void preRegisterEntityNames(Collection<String> classNames)
    public static boolean hasPreRegisteredEntities()
    public static synchronized void clear()  // fuer Hot-Reload

    // Getter (unmodifiable, nie null)
    public static Map<String, String> getPreRegisteredTypeIds()
    public static Set<Class<?>> getPreRegisteredEntities()
}
```

**Hierarchie-aware Annotation-Lookup:** `findAnnotationInHierarchy()` durchsucht Superklassen-Kette und Interfaces (Breadth-First), analog zu `AnnotationAndReflectionHelper.getAnnotationFromHierarchy`. Damit werden Subklassen, die `@Entity`/`@Embedded` von einer Superklasse erben, korrekt registriert.

**Thread-Safety:** Volatile Fields + `synchronized` auf Schreib-Methoden. Getter erstellen TOCTOU-sichere Snapshots via lokale Variablen.

### Aenderungen an bestehenden Klassen

#### `AnnotationAndReflectionHelper.init()` (Stelle 1)

Vorher: Direkte ClassGraph-Nutzung mit `import io.github.classgraph.*`.

Nachher:
```java
private void init() {
    Map<String, String> preRegistered = EntityRegistry.getPreRegisteredTypeIds();
    if (!preRegistered.isEmpty()) {
        classNameByType.putAll(preRegistered);
        return;
    }
    if (!ClassGraphHelper.isAvailable()) {
        ClassGraphHelper.warnIfUnavailable();
        return;
    }
    Map<String, String> scanned = ClassGraphHelper.scanEntityTypeIds();
    classNameByType.putAll(scanned);
}
```

Keine ClassGraph-Imports mehr in dieser Klasse.

#### `ObjectMapperImpl` Konstruktor (Stelle 2)

Vorher: Direkte ClassGraph-Nutzung mit `catch (ClassGraphException e)`.

Nachher: Neuer `else if`-Zweig zwischen Cache-Check und ClassGraph-Scan:
```java
if (cachedClassByCollectionName != null) {
    // bestehendes Caching -- unveraendert
} else if (EntityRegistry.hasPreRegisteredEntities()) {
    Set<Class<?>> preRegistered = EntityRegistry.getPreRegisteredEntities();
    for (Class<?> cls : preRegistered) {
        if (annotationHelper.isAnnotationPresentInHierarchy(cls, Entity.class)) {
            classByCollectionName.put(getCollectionName(cls), cls);
        }
    }
    cachedClassByCollectionName = new ConcurrentHashMap<>(classByCollectionName);
} else if (ClassGraphHelper.isAvailable()) {
    Map<String, Class<?>> scanned = ClassGraphHelper.scanEntityCollections(this::getCollectionName);
    classByCollectionName.putAll(scanned);
} else {
    ClassGraphHelper.warnIfUnavailable();
}
```

Keine ClassGraph-Imports mehr in dieser Klasse.

#### Driver-Registry in `Morphium.java` (Stellen 3+4)

Statische Registries fuer Built-in-Implementierungen -- ClassGraph ist hier Overkill:

```java
private static final ConcurrentHashMap<String, Class<? extends MorphiumDriver>> DRIVER_REGISTRY = new ConcurrentHashMap<>();
static {
    DRIVER_REGISTRY.put(PooledDriver.driverName, PooledDriver.class);
    DRIVER_REGISTRY.put(SingleMongoConnectDriver.driverName, SingleMongoConnectDriver.class);
    DRIVER_REGISTRY.put(InMemoryDriver.driverName, InMemoryDriver.class);
}
public static void registerDriver(String name, Class<? extends MorphiumDriver> driverClass) { ... }

private static final ConcurrentHashMap<String, Class<? extends MorphiumMessaging>> MESSAGING_REGISTRY = new ConcurrentHashMap<>();
static {
    MESSAGING_REGISTRY.put("StandardMessaging", SingleCollectionMessaging.class);
    MESSAGING_REGISTRY.put("SingleCollectionMessaging", SingleCollectionMessaging.class);
    MESSAGING_REGISTRY.put("MultiCollectionMessaging", MultiCollectionMessaging.class);
}
public static void registerMessaging(String name, Class<? extends MorphiumMessaging> messagingClass) { ... }
```

In `initializeAndConnect()`: Registry-Lookup zuerst, `ClassGraphHelper.scanForDriverImpl()`/`scanForMessagingImpl()` nur als Fallback fuer Custom-Implementierungen.

#### `checkCapped()` und `checkIndices()` (Stellen 5+6)

Beide Methoden pruefen jetzt `EntityRegistry.hasPreRegisteredEntities()` zuerst, dann `ClassGraphHelper.scanForAnnotatedClasses()` als Fallback. Keine ClassGraph-Imports.

Die deprecated `checkIndices(ClassInfoFilter)` behaelt den ClassGraph-Typ in der Signatur fuer Rueckwaertskompatibilitaet. Neue Overload `checkIndices(Predicate<Class<?>>)` fuer Framework-Nutzung.

#### `InMemoryDriver.connect()` (Stelle 7)

Vorher: ClassGraph-Scan nach `MongoCommand`-Subklassen bei jedem `connect()`.

Nachher: Explizite statische Liste aller ~36 bekannten Command-Klassen:

```java
private static final List<Class<? extends MongoCommand<?>>> KNOWN_COMMANDS = List.of(
    FindCommand.class, InsertMongoCommand.class, UpdateMongoCommand.class,
    DeleteMongoCommand.class, AggregateMongoCommand.class, CountMongoCommand.class,
    // ... 30+ weitere
);
```

Kein ClassGraph mehr noetig. Die Liste wird durch `InMemoryDriverCommandListTest` gegen den ClassGraph-Scan verifiziert (im Test-Scope, wo ClassGraph verfuegbar ist).

### Maven POM-Aenderungen

**`morphium-core/pom.xml`:**
```xml
<dependency>
    <groupId>io.github.classgraph</groupId>
    <artifactId>classgraph</artifactId>
    <optional>true</optional>
</dependency>
```

ClassGraph bleibt Compile-Time-Dependency (Imports in ClassGraphHelper kompilieren), wird aber **nicht transitiv** an Consumer weitergegeben.

### Quarkus-Extension Anpassungen

#### `MorphiumProcessor.java` (Build-Time)

Nutzt Quarkus' Jandex-Index (`CombinedIndexBuildItem`) statt ClassGraph:

```java
IndexView index = combinedIndex.getIndex();
DotName entityDotName = DotName.createSimple(Entity.class.getName());
DotName embeddedDotName = DotName.createSimple(Embedded.class.getName());

for (AnnotationInstance ai : index.getAnnotations(entityDotName)) {
    if (ai.target().kind() == AnnotationTarget.Kind.CLASS) {
        allClassNames.add(ai.target().asClass().name().toString());
    }
}
// analog fuer @Embedded
recorder.setMappedClassNames(new ArrayList<>(allClassNames));
```

#### `MorphiumProducer.java` (Runtime)

```java
private Morphium buildMorphium() {
    EntityRegistry.clear();
    ObjectMapperImpl.clearEntityCache();
    AnnotationAndReflectionHelper.clearTypeIdCache();
    var entityNames = MorphiumRecorder.getMappedClassNames();
    if (!entityNames.isEmpty()) {
        EntityRegistry.preRegisterEntityNames(entityNames);
    }
    // ... Morphium erstellen ...
}
```

`clear()` + `preRegisterEntityNames()` stellt sicher, dass Quarkus Dev-Mode Hot-Reload korrekt funktioniert (neuer QuarkusClassLoader bei jedem Reload).

## Testabdeckung

### morphium-core (34 Tests)

| Testklasse | Tests | Prueft |
|-----------|-------|--------|
| `EntityRegistryTest` | 14 | Pre-Registration, Null-Validierung, unannotierte Klassen, Hierarchie-Annotations (Superklasse + Interface), Hot-Reload, ObjectMapper-Integration |
| `DriverRegistryTest` | 8 | Built-in-Driver Aufloesung, Custom-Driver, Null-Validierung, Messaging-Registry |
| `ClassGraphHelperTest` | 9 | isAvailable, scanEntityTypeIds, scanEntityCollections, scanForAnnotatedClasses, scanForDriverImpl, scanForMessagingImpl |
| `ClassGraphFallbackTest` | 2 | Morphium funktioniert ohne Pre-Registration, typeId-Aufloesung via ClassGraph-Fallback |
| `InMemoryDriverCommandListTest` | 1 | KNOWN_COMMANDS vollstaendig (Vergleich mit ClassGraph-Scan) |

### quarkus-morphium (7 Integration-Tests)

| Testklasse | Tests | Prueft |
|-----------|-------|--------|
| `MorphiumEntityRegistryTest` | 7 | Build-Time Pre-Registration E2E: hasPreRegisteredEntities, @Entity-Klassen enthalten, @Embedded enthalten, typeIds-Map, collectionName-Aufloesung, Reverse-Lookup, typeId-Aufloesung |

## Ablauf-Diagramm

```
                    Morphium-Start
                         |
              +----------+-----------+
              |                      |
    EntityRegistry hat           EntityRegistry
    Pre-Registrierungen?         leer?
              |                      |
              v                      v
    Nutze Pre-Registration     ClassGraph
    (kein Scanning)            verfuegbar?
                                /        \
                              ja          nein
                              |            |
                              v            v
                        ClassGraph     Leere Maps +
                        Runtime-Scan   Warnung (einmalig)
```

## Zusammenfassung

| Was | Vorher | Nachher |
|-----|--------|---------|
| ClassGraph-Dependency | transitiv, mandatory | `<optional>true</optional>` |
| ClassGraph-Imports | 4 Produktionsklassen | nur `ClassGraphHelper` |
| Entity-Discovery | nur ClassGraph | Pre-Registration -> ClassGraph -> leer |
| Driver/Messaging | nur ClassGraph | Statische Registry -> ClassGraph -> Default |
| InMemoryDriver Commands | ClassGraph-Scan bei connect() | Statische `KNOWN_COMMANDS` Liste |
| Quarkus | ClassGraph Workarounds | Jandex Build-Time + EntityRegistry |
| Breaking Changes | -- | Zero |
