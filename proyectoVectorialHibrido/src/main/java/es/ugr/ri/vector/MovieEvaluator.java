package es.ugr.ri.vector;
import java.util.*;
/** Orquestación de evaluación proporcionada: estudiantes pueden completar métricas. */
public final class MovieEvaluator {
    public record Summary(String method, int queries, double p, double r, double f1, double map, double ndcg) {}
    @FunctionalInterface public interface Retriever { List<RankedMovie> search(String query) throws Exception; }
    public static Summary evaluate(String method, Retriever retriever, EvaluationData data, int k) throws Exception {
        List<RetrievalMetrics.Scores> all = new ArrayList<>();
        int skipped = 0;
        for (var entry : data.queries.entrySet()) {
            Set<String> relevant = data.relevant.getOrDefault(entry.getKey(), Set.of());
            if (relevant.isEmpty()) { skipped++; continue; }
            List<String> ids = retriever.search(entry.getValue()).stream().limit(k).map(RankedMovie::id).toList();
            all.add(RetrievalMetrics.calculate(ids,relevant,k));
        }
        if (skipped > 0) System.out.printf("%s: %d consultas omitidas (sin relevantes en el corpus)%n",method,skipped);
        return new Summary(method,all.size(),RetrievalMetrics.mean(all,RetrievalMetrics.Scores::precision),
                RetrievalMetrics.mean(all,RetrievalMetrics.Scores::recall),RetrievalMetrics.mean(all,RetrievalMetrics.Scores::f1),
                RetrievalMetrics.mean(all,RetrievalMetrics.Scores::ap),RetrievalMetrics.mean(all,RetrievalMetrics.Scores::ndcg));
    }
}
