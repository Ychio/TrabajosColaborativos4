package es.ugr.ri.vector;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Orquestación de evaluación con tabla resumen y detalle por consulta. */
public final class MovieEvaluator {
    public record Summary(String method, int queries, double p, double r, double f1, double map, double ndcg) {}
    public record QueryResult(String queryId, String method, int numRelevant, int numRetrieved,
                              RetrievalMetrics.Scores scores) {}
    public record EvaluationReport(Summary summary, List<QueryResult> perQuery) {}

    @FunctionalInterface
    public interface Retriever { List<RankedMovie> search(String query) throws Exception; }

    public static Summary evaluate(String method, Retriever retriever, EvaluationData data, int k) throws Exception {
        return evaluateDetailed(method, retriever, data, k).summary();
    }

    public static EvaluationReport evaluateDetailed(String method, Retriever retriever,
                                                    EvaluationData data, int k) throws Exception {
        List<RetrievalMetrics.Scores> all = new ArrayList<>();
        List<QueryResult> perQuery = new ArrayList<>();
        int skipped = 0;

        for (var entry : data.queries.entrySet()) {
            Set<String> relevant = data.relevant.getOrDefault(entry.getKey(), Set.of());
            // Política explícita: las consultas sin ningún relevante en el índice se omiten de la media
            if (relevant.isEmpty()) {
                skipped++;
                continue;
            }
            List<String> ids = retriever.search(entry.getValue()).stream()
                    .limit(k)
                    .map(RankedMovie::id)
                    .toList();
            RetrievalMetrics.Scores scores = RetrievalMetrics.calculate(ids, relevant, k);
            all.add(scores);
            perQuery.add(new QueryResult(entry.getKey(), method, relevant.size(), ids.size(), scores));
        }

        if (skipped > 0) {
            System.out.printf("%s: %d consultas omitidas (sin relevantes en el corpus)%n", method, skipped);
        }

        Summary summary = new Summary(
                method,
                all.size(),
                RetrievalMetrics.mean(all, RetrievalMetrics.Scores::precision),
                RetrievalMetrics.mean(all, RetrievalMetrics.Scores::recall),
                RetrievalMetrics.mean(all, RetrievalMetrics.Scores::f1),
                RetrievalMetrics.mean(all, RetrievalMetrics.Scores::ap),
                RetrievalMetrics.mean(all, RetrievalMetrics.Scores::ndcg));
        return new EvaluationReport(summary, perQuery);
    }

    /** Exporta las métricas por consulta en formato CSV o TSV (según extensión del archivo). */
    public static void writePerQuery(Path path, List<QueryResult> rows, int k) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        String sep = path.toString().toLowerCase(Locale.ROOT).endsWith(".tsv") ? "\t" : ",";
        try (BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            out.write(String.join(sep,
                    "query_id", "system", "num_relevant", "num_retrieved",
                    "P@" + k, "R@" + k, "F1@" + k, "AP@" + k, "NDCG@" + k));
            out.newLine();
            for (QueryResult r : rows) {
                RetrievalMetrics.Scores s = r.scores();
                out.write(String.format(Locale.ROOT,
                        "%s%s%s%s%d%s%d%s%.4f%s%.4f%s%.4f%s%.4f%s%.4f",
                        r.queryId(), sep,
                        r.method(), sep,
                        r.numRelevant(), sep,
                        r.numRetrieved(), sep,
                        s.precision(), sep,
                        s.recall(), sep,
                        s.f1(), sep,
                        s.ap(), sep,
                        s.ndcg()));
                out.newLine();
            }
        }
    }
}