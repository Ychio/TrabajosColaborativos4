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
