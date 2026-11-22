package es.ugr.ri.vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Agrupa los fragmentos recuperados por el k-NN en un ranking de películas.
 * Trabaja solo con los fragmentos CANDIDATOS, no con todos los de cada película.
 */
public final class ChunkAggregator {
    private ChunkAggregator() {}

    public enum Aggregation {
        /** Nota de la película = su mejor fragmento. */
        MAX,
        /**
         * Nota de la película = media de sus 2 mejores fragmentos.
         * Si solo tiene 1 fragmento entre los candidatos, el 2º se sustituye por la nota
         * más baja de los candidatos: si existe y no ha entrado, su nota es como mucho esa.
         */
        TOP2;

        public static Aggregation parse(String s) {
            return switch (s.trim().toLowerCase(Locale.ROOT)) {
                case "max" -> MAX;
                case "top2" -> TOP2;
                default -> throw new IllegalArgumentException("agg desconocida: " + s + " (usar max | top2)");
            };
        }
    }

    public static List<RankedMovie> aggregate(List<ChunkHit> hits, Aggregation agg, int k) {
        if (k < 1) throw new IllegalArgumentException("k debe ser positivo");
        if (hits.isEmpty()) return List.of();

        // 1. Nota más baja entre todos los candidatos (para la opción C)
        double minCandidate = hits.stream().mapToDouble(ChunkHit::score).min().orElse(0);

        // 2. Agrupar: película -> sus fragmentos
        Map<String, List<ChunkHit>> byMovie = new LinkedHashMap<>();
        for (ChunkHit h : hits)
            byMovie.computeIfAbsent(h.movieId(), id -> new ArrayList<>()).add(h);

        // 3. Calcular la nota de cada película
        List<RankedMovie> out = new ArrayList<>();
        for (List<ChunkHit> group : byMovie.values()) {
            group.sort(Comparator.comparingDouble(ChunkHit::score).reversed());
            ChunkHit best = group.get(0);
            double score = switch (agg) {
                case MAX -> best.score();
                case TOP2 -> {
                    double second = group.size() > 1 ? group.get(1).score() : minCandidate;
                    yield (best.score() + second) / 2.0;
                }
            };
            out.add(new RankedMovie(best.movieId(), best.title(), score, best.chunkId(), best.text()));
        }

        // 4. Ordenar de mayor a menor nota (empate: por id) y quedarse con las k primeras
        out.sort(Comparator.comparingDouble(RankedMovie::score).reversed().thenComparing(RankedMovie::id));
        return out.size() > k ? List.copyOf(out.subList(0, k)) : List.copyOf(out);
    }
}