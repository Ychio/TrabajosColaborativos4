package es.ugr.ri.vector;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.lucene.index.DirectoryReader;

/**
 * Evaluación: sample [consultas.tsv qrels.txt] o <directorio-indices> <consultas.tsv> <qrels.txt>.
 * En modo disco nunca lee el JSONL ni reindexa los documentos.
 */
public final class MovieEvaluatorMain {
    private static final Path SAMPLE_QUERIES = Path.of("src/main/resources/evaluation/sample_queries.tsv");
    private static final Path SAMPLE_QRELS = Path.of("src/main/resources/evaluation/sample_qrels.txt");

    public static void main(String[] args) throws Exception {
        String source = args.length == 0 ? "sample" : args[0];
        boolean sample = source.equalsIgnoreCase("sample");
        if ((!sample && args.length != 3) || (sample && args.length != 1 && args.length != 3 && args.length != 0)) {
            throw new IllegalArgumentException("Uso: MovieEvaluatorMain sample [consultas.tsv qrels.txt] "
                    + "| <directorio-indices> <consultas.tsv> <qrels.txt>");
        }
        Path queries = args.length == 3 ? Path.of(args[1]) : SAMPLE_QUERIES;
        Path qrels = args.length == 3 ? Path.of(args[2]) : SAMPLE_QRELS;
        RunConfig config = RunConfig.fromProperties();
        EvaluationData data = EvaluationData.load(queries, qrels);
        try (IndexDirectories indexes = sample ? IndexDirectories.memory() : IndexDirectories.disk(Path.of(source))) {
            if (sample) {
                var movies = MovieData.sample();
                var stats = new MovieIndexer().build(movies, indexes,
                        config.chunker(), new EmbeddingService());
                System.out.printf("Modo RAM: %d películas, %d fragmentos%n", stats.movies(), stats.chunks());
            }
            // Los juicios se restringen a las películas presentes en el índice abierto.
            // En la colección completa esto no cambia nada si los qrels son coherentes.
            Set<String> ids = indexedMovieIds(indexes);
            EvaluationData restricted = data.restrictTo(ids);
            int missing = data.relevant.values().stream()
                    .mapToInt(s -> (int) s.stream().filter(id -> !ids.contains(id)).count()).sum();
            if (missing > 0) System.err.printf("AVISO: %d juicios positivos hacen referencia a películas ausentes del índice%n", missing);
            System.out.printf("Modo=%s | películas indexadas=%d | consultas=%s | qrels=%s | k=%d%n",
                    sample ? "RAM" : "DISCO", ids.size(), queries, qrels, config.k());
            System.out.println("Parámetros: " + config.describe());
            try (MovieSearcher searcher = new MovieSearcher(indexes, new EmbeddingService())) {
                List<MovieEvaluator.Summary> results = List.of(
                    MovieEvaluator.evaluate("BM25", q -> searcher.bm25(q,config.k()),restricted,config.k()),
                    MovieEvaluator.evaluate("VECTOR", q -> searcher.vectorChunks(q,config.candidateChunks(),config.k()),restricted,config.k()),
                    MovieEvaluator.evaluate("VECTOR_TOP2", q -> searcher.vectorChunks(q,config.candidateChunks(),config.k(),ChunkAggregator.Aggregation.TOP2),restricted,config.k()),
                    MovieEvaluator.evaluate("GLOBAL", q -> searcher.vectorGlobal(q,config.k()),restricted,config.k()),
                    MovieEvaluator.evaluate("RRF", q -> searcher.hybridRrf(q,config.candidateChunks(),config.k()),restricted,config.k())
                );
                System.out.printf("%-10s %5s %8s %8s %8s %8s %8s%n", "SISTEMA", "N", "P@k", "R@k", "F1@k", "MAP@k", "NDCG@k");
                for (var r : results) System.out.printf(Locale.ROOT,"%-10s %5d %8.3f %8.3f %8.3f %8.3f %8.3f%n",
                        r.method(),r.queries(),r.p(),r.r(),r.f1(),r.map(),r.ndcg());
            }
        }
    }
    private static Set<String> indexedMovieIds(IndexDirectories indexes) throws Exception {
        Set<String> ids = new HashSet<>();
        try (DirectoryReader reader = DirectoryReader.open(indexes.lexical)) {
            for (var leaf : reader.leaves()) {
                var live = leaf.reader().getLiveDocs();
                for (int doc = 0; doc < leaf.reader().maxDoc(); doc++) {
                    if (live != null && !live.get(doc)) continue;
                    String id = leaf.reader().storedFields().document(doc).get("movie_id");
                    if (id != null) ids.add(id);
                }
            }
        }
        return ids;
    }
}
