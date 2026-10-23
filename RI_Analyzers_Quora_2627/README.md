# Práctica de análisis de texto y sugerencias — RI 2026/27

## Entrada

`quora_queries_50000.tsv` (UTF-8, separado por tabuladores), con cabecera:

```
qid\tquestion\tfrequency
```

`frequency` es la cantidad de apariciones de la pregunta en pares de Quora, no popularidad de búsqueda.

## Compilar y ejecutar

Se requiere JDK 21+ y Maven:

```bash
mvn clean compile
mvn exec:java -Dexec.mainClass=es.ugr.ri.Suggestion -Dexec.args="quora_queries_50000.tsv standard frequency prefix"
```

Opciones:

- Analyzer: `standard`, `english`, `whitespace`.
- Pesos: `uniform`, `frequency`.
- Estrategia opcional: `prefix`, `fuzzy`, `infix`, `next`.
- Si se omite la estrategia, aparece un menú interactivo.
- Escribir `FIN` para salir de una serie de consultas.

El TSV no está incluido: se genera por separado a partir del dataset de Quora.

**Nota de verificación:** el proyecto se ha revisado frente a las firmas de las API de Lucene; no se ha podido realizar una compilación Maven en el entorno de generación, donde Maven y sus dependencias no están disponibles. Validar con `mvn clean compile` antes de publicarlo.
