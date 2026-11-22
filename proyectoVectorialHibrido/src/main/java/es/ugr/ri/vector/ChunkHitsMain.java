package es.ugr.ri.vector;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Actividad 2, punto 1: fragmentos del k-NN sin agrupar. Uso: <directorio-indices> <consulta...> */
public final class ChunkHitsMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("Uso: ChunkHitsMain <directorio-indices> <consulta...>");
        String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        RunConfig config = RunConfig.fromProperties();
        int show = Integer.getInteger("show", 20);       // cuántos trozos imprimir
        String track = System.getProperty("track");      // movie_id a localizar (opcional)

        try (IndexDirectories indexes = IndexDirectories.disk(Path.of(args[0]));
             MovieSearcher searcher = new MovieSearcher(indexes, new EmbeddingService())) {

            List<ChunkHit> hits = searcher.chunkHits(query, config.candidateChunks());
            System.out.printf("Consulta: \"%s\" | candidates=%d | fragmentos en el índice=%d%n%n",
                    query, config.candidateChunks(), searcher.numChunks());

            // 1. Imprimir los primeros trozos tal cual
            for (int i = 0; i < Math.min(show, hits.size()); i++) {
                ChunkHit h = hits.get(i);
                System.out.printf(Locale.ROOT, "%3d %-30s chunk=%-3d score=%.4f%n",
                        i + 1, h.title(), h.chunkId(), h.score());
            }

            // 2. Contar películas distintas
            long top10 = hits.stream().limit(10).map(ChunkHit::movieId).distinct().count();
            long todas = hits.stream().map(ChunkHit::movieId).distinct().count();
            System.out.printf("%nLos 10 primeros fragmentos -> %d películas distintas%n", top10);
            System.out.printf("Los %d fragmentos -> %d películas distintas%n", hits.size(), todas);
                        // Tiempo: repetir la búsqueda varias veces y quedarse con la mediana
            int reps = Integer.getInteger("reps", 5);
            double[] ms = new double[reps];
            for (int r = 0; r < reps; r++) {
                long t0 = System.nanoTime();
                searcher.chunkHits(query, config.candidateChunks());
                ms[r] = (System.nanoTime() - t0) / 1e6;
            }
            Arrays.sort(ms);
            System.out.printf(Locale.ROOT, "Pedidos=%d, devueltos=%d, tiempo (mediana de %d)=%.1f ms%n",
                    config.candidateChunks(), hits.size(), reps, ms[reps / 2]);

            // Top-k de películas con cada agregación
            for (var agg : ChunkAggregator.Aggregation.values()) {
                var top = ChunkAggregator.aggregate(hits, agg, config.k());
                System.out.println(agg + ": " + top.stream().map(RankedMovie::title).toList());
            }

            // 3. Buscar una película concreta (opcional)
            if (track != null) {
                boolean found = false;
                for (int i = 0; i < hits.size(); i++) {
                    ChunkHit h = hits.get(i);
                    if (h.movieId().equals(track)) {
                        System.out.printf(Locale.ROOT, "  %s: posición %d, chunk=%d, score=%.4f%n",
                                h.title(), i + 1, h.chunkId(), h.score());
                        found = true;
                    }
                }
                if (!found) System.out.println("  La película " + track + " no está entre los candidatos.");
            }
        }
    }
}