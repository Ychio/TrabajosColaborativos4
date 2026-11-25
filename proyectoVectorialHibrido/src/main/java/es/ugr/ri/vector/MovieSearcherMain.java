package es.ugr.ri.vector;

import java.nio.file.Path;
import java.util.*;

/** Búsqueda: sample <consulta...> o <directorio-indices> <consulta...>. */
public final class MovieSearcherMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Uso: MovieSearcherMain <sample|directorio-indices> <consulta...>");
        }
        boolean sample = args[0].equalsIgnoreCase("sample");
        RunConfig config = RunConfig.fromProperties();
        try (IndexDirectories indexes = sample ? IndexDirectories.memory() : IndexDirectories.disk(Path.of(args[0]))) {
            if (sample) {
                new MovieIndexer().build(MovieData.sample(), indexes, config.chunker(), new EmbeddingService());
            }
            String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            System.out.println("Consulta: \"" + query + "\"");
            System.out.println("Parámetros: " + config.describe());

            int k = config.k();
            int pool = Math.max(k, config.fusionDepth());

            try (MovieSearcher searcher = new MovieSearcher(indexes, new EmbeddingService())) {
                List<RankedMovie> bm25Pool = searcher.bm25(query, pool);
                List<RankedMovie> vecPool = searcher.vectorChunks(query, config.candidateChunks(), pool);

                show("BM25", bm25Pool.subList(0, Math.min(k, bm25Pool.size())));
                show("VECTOR", vecPool.subList(0, Math.min(k, vecPool.size())));
                show("VECTOR (top2)", searcher.vectorChunks(
                        query, config.candidateChunks(), k, ChunkAggregator.Aggregation.TOP2));
                show("GLOBAL", searcher.vectorGlobal(query, k));

                Map<String, Integer> bm25Ranks = indexRanks(bm25Pool);
                Map<String, Integer> vecRanks = indexRanks(vecPool);

                showHybrid("RRF (c=" + config.rrfC() + ")",
                        RankFusion.rrf(bm25Pool, vecPool, config.rrfC(), k), bm25Ranks, vecRanks);
                showHybrid(String.format(Locale.ROOT, "RRF_WEIGHTED (alpha=%.2f, c=%d)", config.fusionAlpha(), config.rrfC()),
                        RankFusion.weightedRrf(bm25Pool, vecPool, config.fusionAlpha(), config.rrfC(), k), bm25Ranks, vecRanks);
                showHybrid(String.format(Locale.ROOT, "FUSION_LINEAL (alpha=%.2f)", config.fusionAlpha()),
                        RankFusion.linearFusion(bm25Pool, vecPool, config.fusionAlpha(), k), bm25Ranks, vecRanks);
            }
        }
    }

    private static Map<String, Integer> indexRanks(List<RankedMovie> list) {
        Map<String, Integer> ranks = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            ranks.putIfAbsent(list.get(i).id(), i + 1);
        }
        return ranks;
    }

    private static void show(String method, List<RankedMovie> results) {
        System.out.println("\n" + method);
        for (int i = 0; i < results.size(); i++) {
            RankedMovie r = results.get(i);
            System.out.printf(Locale.ROOT, "%2d %-30s score=%.4f id=%s chunk=%d%n",
                    i + 1, r.title(), r.score(), r.id(), r.chunkId());
        }
    }

    private static void showHybrid(String method, List<RankedMovie> results,
                                   Map<String, Integer> bm25Ranks, Map<String, Integer> vecRanks) {
        System.out.println("\n" + method);
        for (int i = 0; i < results.size(); i++) {
            RankedMovie r = results.get(i);
            String rBm25 = bm25Ranks.containsKey(r.id()) ? "#" + bm25Ranks.get(r.id()) : "-";
            String rVec = vecRanks.containsKey(r.id()) ? "#" + vecRanks.get(r.id()) : "-";
            System.out.printf(Locale.ROOT, "%2d %-30s score=%.4f id=%-9s [bm25=%-3s vec=%-3s]%n",
                    i + 1, r.title(), r.score(), r.id(), rBm25, rVec);
        }
    }
}