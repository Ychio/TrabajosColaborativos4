package es.ugr.ri.vector;
import java.util.*;
/** Demo integrada necesaria porque los índices RAM no sobreviven al fin del proceso. */
public final class DemoMain {
    public static void main(String[] args) throws Exception {
        RunConfig c = RunConfig.fromProperties();
        List<Movie> movies = MovieRepository.load(c.corpus(),c.limit());
        System.out.printf("Corpus: %d películas; %s%n", movies.size(), c.describe());
        EmbeddingService model = new EmbeddingService();
        try (IndexDirectories indexes = IndexDirectories.memory()) {
            var stats = new MovieIndexer().build(movies,indexes,c.chunker(),model);
            System.out.printf("Indexadas %d películas, %d fragmentos%n",stats.movies(),stats.chunks());
            try (MovieSearcher searcher = new MovieSearcher(indexes,model)) {
                if (args.length > 0) {
                    String q=String.join(" ",args);
                    show("BM25",searcher.bm25(q,c.k()));
                    show("VECTOR",searcher.vectorChunks(q,c.candidateChunks(),c.k()));
                    show("GLOBAL",searcher.vectorGlobal(q,c.k()));
                    show("RRF",searcher.hybridRrf(q,c.candidateChunks(),c.k()));
                } else {
                    // Evaluación docente: qrels restringidos al corpus realmente cargado.
                    final EvaluationData data=EvaluationData.load().restrictTo(movies.stream().map(Movie::id).collect(java.util.stream.Collectors.toSet()));
                    List<MovieEvaluator.Summary> summaries=List.of(
                            MovieEvaluator.evaluate("BM25",q -> searcher.bm25(q,c.k()),data,c.k()),
                            MovieEvaluator.evaluate("VECTOR",q -> searcher.vectorChunks(q,c.candidateChunks(),c.k()),data,c.k()),
                            MovieEvaluator.evaluate("GLOBAL",q -> searcher.vectorGlobal(q,c.k()),data,c.k()),
                            MovieEvaluator.evaluate("RRF",q -> searcher.hybridRrf(q,c.candidateChunks(),c.k()),data,c.k()));
                    System.out.printf("%-10s %5s %8s %8s %8s %8s %8s%n","SISTEMA","N","P@k","R@k","F1@k","MAP@k","NDCG@k");
                    for (var s:summaries) System.out.printf(java.util.Locale.ROOT,"%-10s %5d %8.3f %8.3f %8.3f %8.3f %8.3f%n",
                            s.method(),s.queries(),s.p(),s.r(),s.f1(),s.map(),s.ndcg());
                }
            }
        }
    }
    private static void show(String label,List<RankedMovie> list) {
        System.out.println("\n"+label);
        for (int i=0;i<list.size();i++) {
            var m=list.get(i);
            System.out.printf(java.util.Locale.ROOT,"%2d %-22s score=%.4f id=%s chunk=%d%n",i+1,m.title(),m.score(),m.id(),m.chunkId());
        }
    }
}
