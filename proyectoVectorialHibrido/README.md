# Práctica 2 — Recuperación vectorial, híbrida y evaluación

Java 21, Apache Lucene 10.5.2, LangChain4j y `all-MiniLM-L6-v2` (384 dimensiones).

## Dos modos de ejecución

El **primer argumento** de `MovieEvaluatorMain` y `MovieSearcherMain` determina el origen de los índices:

- `sample`: crea **en memoria** los índices de las cinco películas incluidas en `MovieData` y ejecuta las tres consultas docentes. Los índices desaparecen al finalizar la JVM.
- `<directorio-indices>`: abre **índices persistentes** previamente construidos; no vuelve a cargar el JSONL, no crea embeddings de películas ni reindexa.

El evaluador acepta tres argumentos: `<origen-indices> <consultas.tsv> <qrels.txt>`. En modo `sample` los dos archivos son opcionales y, si se omiten, se usan `sample_queries.tsv` y `sample_qrels.txt`. La opción sin argumentos equivale a `sample`.

## Ejecución

Desde la raíz del proyecto:

```bash
mvn compile

# Ejemplo autónomo, cinco películas y tres consultas (RAM)
mvn exec:java -Dexec.mainClass=es.ugr.ri.vector.MovieEvaluatorMain -Dexec.args="sample" -Dk=5

# Crear índices persistentes a partir del corpus completo (una sola vez)
mvn exec:java -Dexec.mainClass=es.ugr.ri.vector.MovieIndexerMain -Dexec.args="/ruta/peliculas.jsonl indices/peliculas"

# Evaluar esos mismos índices con las 50 consultas y sus juicios
mvn exec:java -Dexec.mainClass=es.ugr.ri.vector.MovieEvaluatorMain -Dexec.args="indices/peliculas src/main/resources/evaluation/queries.tsv src/main/resources/evaluation/qrels.txt" -Dk=10

# Buscar en el índice ya creado, sin volver a indexar
mvn exec:java -Dexec.mainClass=es.ugr.ri.vector.MovieSearcherMain -Dexec.args="indices/peliculas a detective discovers artificial memories"

# Búsqueda autónoma en RAM
mvn exec:java -Dexec.mainClass=es.ugr.ri.vector.MovieSearcherMain -Dexec.args="sample cloned dinosaurs escape"
```

El indexador acepta exactamente dos argumentos: `<sample|peliculas.jsonl> <directorio-indices>`. La indexación en disco crea tres subdirectorios `lexical`, `chunks` y `global` y **reemplaza** sus contenidos al volver a ejecutar el indexador. Los argumentos del evaluador **no** incluyen el JSONL.

## Ficheros de evaluación

- `src/main/resources/evaluation/sample_queries.tsv`: 3 consultas sobre las 5 películas de muestra.
- `src/main/resources/evaluation/sample_qrels.txt`: relevancia binaria ficticia, simplificada.
- `src/main/resources/evaluation/queries.tsv`: 50 consultas para la colección completa.
- `src/main/resources/evaluation/qrels.txt`: juicios para esas 50 consultas.
- `src/main/resources/evaluation/qrels_auditoria.csv`: fichero de auditoría de juicios.

Formato de consultas: `id<TAB>texto`. Formato de qrels TREC: `id 0 movie_id relevancia`.

## Trabajo que queda para los estudiantes

**`TextChunker` está intencionadamente incompleto:** produce un único fragmento con las primeras `chunk.words` palabras de cada película. Los estudiantes deben completar la segmentación en múltiples fragmentos, controlar solapamiento y cobertura, y reconstruir los índices después de cambiarla. El proyecto incluye una implementación ilustrativa de RRF y métricas para facilitar la comprobación y experimentación.

Parámetros Java opcionales: `-Dchunk.words=120`, `-Dk=10`, `-Dcandidates=100`, `-Dlimit=...`. Los índices en disco dependen del modelo de embeddings utilizado y deben reconstruirse al cambiar de modelo. El evaluador sí calcula nuevos embeddings de las consultas, utilizando `all-MiniLM-L6-v2`.

**Interpretación de las métricas:** en esta versión, los juicios positivos de películas ausentes se excluyen y se avisa de ello; las consultas sin relevantes en el índice se omiten de la media. Para la evaluación final debe indexarse la colección completa y revisarse que no falten películas relevantes. No deben interpretarse las métricas del ejemplo de cinco películas como una evaluación exhaustiva.

## Nota sobre la verificación

El código se ha actualizado sobre el proyecto anterior. La ejecución completa de Maven/ONNX debe comprobarse en un entorno con Maven y dependencias disponibles antes de distribuirlo.

## Cambios realizados

### Actividad 1. Segmentación

`TextChunker` ya no está incompleto: divide cada sinopsis en fragmentos de como máximo `chunk.words` palabras con un solapamiento de `chunk.overlap` palabras, cubriendo todo el texto. Nuevo parámetro `-Dchunk.overlap=20` (0 ≤ overlap < chunk.words). Hay que reindexar siempre que cambien `chunk.words`, `chunk.overlap` o el modelo. La indexación del corpus completo tarda unos 26 minutos; para lanzarla en segundo plano: `nohup mvn -q exec:java ... > indexacion.log 2>&1 &` (mientras se ejecuta no usar `mvn clean`).

**Clases y métodos modificados:**
- `TextChunker`: reescrita. Nuevo constructor `TextChunker(int, int)`; nuevos métodos `windows(int)`, `words(String)`, `chunkWords()`, `overlap()`; `split(Movie)` reimplementado.
- `RunConfig`: nuevo campo `chunkOverlap`; nuevos métodos `chunker()` y `describe()`.
- `MovieIndexerMain.main`: mide el tiempo de indexación e imprime los parámetros.
- `MovieSearcherMain.main`, `MovieEvaluatorMain.main`, `DemoMain.main`: usan `config.chunker()`.
- `pom.xml`: añadidos JUnit 5 y maven-surefire-plugin.
- Nuevas clases: `ChunkInspectMain` y `TextChunkerTest`.

**Comandos:**
```bash
mvn test                                                              # 9 pruebas de segmentación
mvn -q exec:java -Dexec.mainClass=es.ugr.ri.vector.ChunkInspectMain   # ver los fragmentos de las 5 películas

# Comparar configuraciones (w, o) sobre 500 películas
for cfg in "100 0" "100 25" "150 0" "150 25"; do
  set -- $cfg
  echo "=========== w=$1 o=$2 ==========="
  mvn -q exec:java -Dexec.mainClass=es.ugr.ri.vector.MovieIndexerMain \
      -Dexec.args="peliculas.jsonl indices/exp" -Dlimit=500 \
      -Dchunk.words=$1 -Dchunk.overlap=$2 2>/dev/null
done
```

Las salidas de estos experimentos están en `resultados/actividad1/`.

### Actividad 2. De fragmentos a películas

La búsqueda vectorial se separa en dos pasos: buscar los fragmentos (`chunkHits`) y agruparlos por película (`ChunkAggregator`). Hay dos agregaciones: **MAX** (nota del mejor fragmento, la original) y **TOP2** (media de los dos mejores; si solo hay uno entre los candidatos, el segundo valor es la nota mínima de los candidatos). El evaluador incluye la fila `VECTOR_TOP2`.

**Clases y métodos modificados:**
- `MovieSearcher`: nuevos métodos `chunkHits(String, int)` y `numChunks()`; `vectorChunks(String, int, int)` reimplementado con `ChunkAggregator` (sigue usando MAX); nueva sobrecarga `vectorChunks(String, int, int, Aggregation)`, que avisa si los candidatos no dan *k* películas.
- `MovieSearcherMain.main`: muestra también el bloque `VECTOR (top2)`.
- `MovieEvaluatorMain.main`: evalúa también `VECTOR_TOP2` e imprime los parámetros usados.
- Nuevas clases: `ChunkHit` (fragmento sin agrupar), `ChunkAggregator` (agregaciones MAX y TOP2), `ChunkHitsMain` (fragmentos en bruto, películas distintas, tiempo y top-k por agregación) y `ChunkAggregatorTest` (7 pruebas).

**Comandos:**
```bash
mvn test                                                         # 16 pruebas (segmentación + agregación)

# Fragmentos sin agrupar y posición de una película concreta (3746 = Blade Runner)
mvn -q exec:java -Dexec.mainClass=es.ugr.ri.vector.ChunkHitsMain \
    -Dexec.args="indices/peliculas a replicant kills his creator" -Dcandidates=1000 -Dtrack=3746

# Efecto del número de candidatos
for c in 20 100 300; do
  echo "=========== candidates=$c ==========="
  mvn -q exec:java -Dexec.mainClass=es.ugr.ri.vector.ChunkHitsMain \
      -Dexec.args="indices/peliculas a replicant kills his creator" -Dcandidates=$c -Dshow=0 2>/dev/null
done
```
Parámetros de `ChunkHitsMain`: `-Dcandidates`, `-Dk`, `-Dshow` (trozos a imprimir, 20), `-Dtrack` (movie_id a localizar), `-Dreps` (repeticiones para medir el tiempo, 5).

Las salidas están en `resultados/actividad2/` y la evaluación con las 50 consultas en `resultados/corpus/`.

### Actividad 3. Recuperación híbrida y fusión

La fusión de rankings se realiza siempre a nivel de película (después de agrupar los fragmentos de `chunks`). `RankFusion` mantiene el RRF sin ponderar y añade **RRF ponderado** ($\sum \alpha_j / (c + \text{rank}_j(d))$ con $\sum \alpha_j = 1$) y **Fusión Lineal con normalización min-max** en $[0, 1]$ (asignando `0.0` a películas ausentes en uno de los rankings y `1.0` si todos los elementos de un ranking empatan). Los parámetros se configuran mediante `-Drrf.c=60`, `-Dfusion.depth=20` y `-Dfusion.alpha=0.6` (peso para BM25; $1-\alpha$ para VECTOR).

**Clases y métodos modificados:**
- `RankFusion`: nuevos métodos `weightedRrf(...)` (sobrecarga para 2 rankings y general para $m$ rankings con validación de pesos) y `linearFusion(...)` con normalización min-max. Ajuste en `addRrf` para contar rangos sobre películas únicas.
- `RunConfig`: nuevos campos `rrfC`, `fusionDepth` y `fusionAlpha` leídos de propiedades `-D` y mostrados en `describe()`.
- `MovieSearcher`: nuevas sobrecargas `hybridRrf(query, candidates, depth, c, k)`, `hybridWeightedRrf(...)` y `hybridLinear(...)`.
- `MovieSearcherMain`: muestra también `RRF_WEIGHTED` y `FUSION_LINEAL` e imprime para las fusiones los rangos de entrada (`[bm25=#.. vec=#..]`).
- `MovieEvaluatorMain`: evalúa también `RRF_WEIGHTED` y `FUSION_LINEAL`.
- Nueva clase de pruebas: `RankFusionTest` (6 pruebas JUnit).

**Comandos:**
```bash
mvn test                                                         # 22 pruebas (segmentación + agregación + fusión)

# Evaluación comparativa de las 7 configuraciones sobre el corpus completo
mvn -q exec:java -Dexec.mainClass=es.ugr.ri.vector.MovieEvaluatorMain \
    -Dexec.args="indices/peliculas src/main/resources/evaluation/queries.tsv src/main/resources/evaluation/qrels.txt" \
    -Dk=10 -Dcandidates=100 -Dfusion.alpha=0.6 > resultados/actividad3/evaluacion_fusiones_k10_a06.txt

# Inspección de consultas (términos distintivos vs semántica) con rangos de entrada
mvn -q exec:java -Dexec.mainClass=es.ugr.ri.vector.MovieSearcherMain \
    -Dexec.args="indices/peliculas The James Bond film featuring a high-stakes poker game against Le Chiffre" \
    -Dk=10 -Dcandidates=100 > resultados/actividad3/consulta_distintiva_Q040.txt

mvn -q exec:java -Dexec.mainClass=es.ugr.ri.vector.MovieSearcherMain \
    -Dexec.args="indices/peliculas A person discovers that their memories were artificially implanted" \
    -Dk=10 -Dcandidates=100 > resultados/actividad3/consulta_semantica_Q041.txt
```
Las salidas de estos experimentos están en resultados/actividad3/.

### Actividad 4. Evaluación verificable y análisis por consulta

Se han verificado las métricas de `RetrievalMetrics` (`P@k`, `R@k`, `F1@k`, `AP@k`, `MAP@k`, `NDCG@k`) mediante casos calculados a mano en `RetrievalMetricsTest` y se ha añadido exportación por consulta en formato CSV o TSV.

**Convenciones y política de evaluación:**
- `P@k` divide siempre entre `k` aunque el sistema devuelva menos de `k` resultados.
- `AP@k` en `RetrievalMetrics` divide entre el total de relevantes del juicio (`|R_q|`), no entre `min(k, |R_q|)`.
- `EvaluationData.restrictTo(ids)` elimina juicios positivos hacia películas ausentes del índice (mostrando aviso si ocurre) y `MovieEvaluator.evaluateDetailed` omite del promedio las consultas sin relevantes en el índice. Sobre el corpus completo (`29.796` películas) no falta ningún juicio (`missing = 0`) y se evalúan las `50` consultas (`N = 50`).

**Clases y métodos modificados:**
- `MovieEvaluator`: nuevos registros `QueryResult` y `EvaluationReport`; nuevos métodos `evaluateDetailed(...)` y `writePerQuery(Path, List<QueryResult>, int)` (detecta `.tsv` o `.csv` por extensión). `evaluate(...)` se mantiene por compatibilidad.
- `MovieEvaluatorMain`: soporta `-Deval.out=<ruta.csv|tsv>` para exportar métricas por consulta conservando la tabla resumen.
- Nueva clase de pruebas: `RetrievalMetricsTest` (9 pruebas JUnit; 31 pruebas en total en `mvn test`).

**Comandos:**
```bash
mvn test                                                         # 31 pruebas en total

# Evaluación completa con exportación por consulta
mvn -q exec:java -Dexec.mainClass=es.ugr.ri.vector.MovieEvaluatorMain \
    -Dexec.args="indices/peliculas src/main/resources/evaluation/queries.tsv src/main/resources/evaluation/qrels.txt" \
    -Dk=10 -Dcandidates=100 -Deval.out=resultados/actividad4/por_consulta_k10.csv > resultados/actividad4/evaluacion_final_k10.txt

# Demostración autocontenida (sample)
mvn -q exec:java -Dexec.mainClass=es.ugr.ri.vector.MovieEvaluatorMain \
    -Dexec.args="sample" -Dk=5 -Deval.out=resultados/sample/por_consulta_sample_k5.csv > resultados/sample/evaluacion_sample_k5.txt
```
Las salidas y las inspecciones de las consultas (Q003, Q006, Q038, Q045) están en resultados/actividad4/ y resultados/sample/.

### Actividad 5. Experimentos, reproducibilidad y discusión

Toda la comparativa experimental de las 7 configuraciones mínimas (`BM25`, `VECTOR`, `VECTOR_TOP2`, `GLOBAL`, `RRF`, `RRF_WEIGHTED`, `FUSION_LINEAL`) se reproduce mediante propiedades `-D` desde línea de comandos, sin editar constantes en el código fuente:

- Segmentación e indexación (`MovieIndexerMain`): `-Dchunk.words=120`, `-Dchunk.overlap=20` (corpus completo: `29.796` películas, `132.440` fragmentos).
- Búsqueda y fusión (`RunConfig`): `-Dk=10`, `-Dcandidates=100`, `-Drrf.c=60`, `-Dfusion.depth=20`, `-Dfusion.alpha=0.6`.
- Exportación por consulta (`MovieEvaluatorMain`): `-Deval.out=<ruta.csv|tsv>`.

**Clases implicadas en la configuración reproducible:**
- `RunConfig`: centraliza todas las propiedades `-D` (`chunk.words`, `chunk.overlap`, `k`, `candidates`, `rrf.c`, `fusion.depth`, `fusion.alpha`) y las registra en `describe()`.
- `MovieEvaluatorMain` y `MovieSearcherMain`: imprimen los parámetros activos al inicio de cada ejecución para garantizar la trazabilidad de cada salida.

**Comandos para reproducir la tabla comparativa completa:**
```bash
# 1. Verificación completa de pruebas unitarias (31 tests)
mvn test

# 2. Tabla comparativa de las 7 condiciones sobre las 50 consultas (corpus completo)
mvn -q exec:java -Dexec.mainClass=es.ugr.ri.vector.MovieEvaluatorMain \
    -Dexec.args="indices/peliculas src/main/resources/evaluation/queries.tsv src/main/resources/evaluation/qrels.txt" \
    -Dk=10 -Dcandidates=100 -Drrf.c=60 -Dfusion.depth=20 -Dfusion.alpha=0.6 \
    -Deval.out=resultados/actividad4/por_consulta_k10.csv > resultados/actividad4/evaluacion_final_k10.txt

# 3. Ejemplo variando el peso alpha sin recompilar ni editar constantes
mvn -q exec:java -Dexec.mainClass=es.ugr.ri.vector.MovieEvaluatorMain \
    -Dexec.args="indices/peliculas src/main/resources/evaluation/queries.tsv src/main/resources/evaluation/qrels.txt" \
    -Dk=10 -Dcandidates=100 -Dfusion.alpha=0.7

# 4. Demostración en modo sample (5 películas, 3 consultas)
mvn -q exec:java -Dexec.mainClass=es.ugr.ri.vector.MovieEvaluatorMain \
    -Dexec.args="sample" -Dk=5 -Deval.out=resultados/sample/por_consulta_sample_k5.csv > resultados/sample/evaluacion_sample_k5.txt
```
Los resultados finales y el detalle por consulta se encuentran en resultados/actividad4/ y resultados/sample/, y su análisis (mejoras, empeoramientos y limitaciones) en la memoria del proyecto.
