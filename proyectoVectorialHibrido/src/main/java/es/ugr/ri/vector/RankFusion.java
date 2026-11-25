package es.ugr.ri.vector;

import java.util.*;

/**
 * Fusión de rankings a nivel de película (nunca sobre fragmentos sin agrupar).
 * Incluye RRF sin ponderar, RRF ponderado y Fusión Lineal con normalización min-max.
 */
public final class RankFusion {
    private RankFusion() {}

    /**
     * RRF sin ponderar: sum_j 1 / (c + rank_j(d)).
     * Es equivalente en orden a RRF ponderado con pesos iguales (0.5, 0.5).
     */
    public static List<RankedMovie> rrf(List<RankedMovie> a, List<RankedMovie> b, int c, int k) {
        if (c < 1) throw new IllegalArgumentException("c debe ser positivo: " + c);
        if (k < 1) throw new IllegalArgumentException("k debe ser positivo: " + k);
        Map<String, Double> scores = new HashMap<>();
        Map<String, RankedMovie> movies = new HashMap<>();
        addRrf(a, 1.0, c, scores, movies);
        addRrf(b, 1.0, c, scores, movies);
        return sortAndLimit(scores, movies, k);
    }

    /**
     * RRF ponderado para 2 rankings: alpha / (c + rank_a(d)) + (1 - alpha) / (c + rank_b(d)).
     */
    public static List<RankedMovie> weightedRrf(List<RankedMovie> a, List<RankedMovie> b,
                                                double alpha, int c, int k) {
        if (!Double.isFinite(alpha) || alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("alpha debe estar en [0, 1]: " + alpha);
        }
        return weightedRrf(List.of(a, b), List.of(alpha, 1.0 - alpha), c, k);
    }

    /**
     * RRF ponderado general: sum_j alpha_j / (c + rank_j(d)), con alpha_j >= 0 y sum(alpha_j) = 1.
     */
    public static List<RankedMovie> weightedRrf(List<List<RankedMovie>> rankings,
                                                List<Double> weights, int c, int k) {
        if (c < 1) throw new IllegalArgumentException("c debe ser positivo: " + c);
        if (k < 1) throw new IllegalArgumentException("k debe ser positivo: " + k);
        if (rankings == null || weights == null || rankings.isEmpty() || rankings.size() != weights.size()) {
            throw new IllegalArgumentException("Rankings y pesos deben ser no vacíos y tener el mismo tamaño");
        }
        double sumWeights = 0.0;
        for (double w : weights) {
            if (!Double.isFinite(w) || w < 0.0) {
                throw new IllegalArgumentException("Peso inválido (debe ser >= 0 y finito): " + w);
            }
            sumWeights += w;
        }
        if (Math.abs(sumWeights - 1.0) > 1e-6) {
            throw new IllegalArgumentException("Los pesos deben sumar 1.0 (suma actual=" + sumWeights + ")");
        }

        Map<String, Double> scores = new HashMap<>();
        Map<String, RankedMovie> movies = new HashMap<>();
        for (int j = 0; j < rankings.size(); j++) {
            addRrf(rankings.get(j), weights.get(j), c, scores, movies);
        }
        return sortAndLimit(scores, movies, k);
    }

    /**
     * Fusión lineal con normalización min-max en [0, 1]:
     * S(d) = alpha * S_norm_a(d) + (1 - alpha) * S_norm_b(d).
     * - Documentos ausentes en un ranking reciben puntuación normalizada 0.0 en ese ranking.
     * - Si en un ranking no vacío todos los scores son iguales (max == min), reciben 1.0.
     */
    public static List<RankedMovie> linearFusion(List<RankedMovie> a, List<RankedMovie> b,
                                                 double alpha, int k) {
        if (k < 1) throw new IllegalArgumentException("k debe ser positivo: " + k);
        if (!Double.isFinite(alpha) || alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("alpha debe estar en [0, 1]: " + alpha);
        }

        Map<String, RankedMovie> movies = new HashMap<>();
        Map<String, Double> normA = minMaxNormalize(a, movies);
        Map<String, Double> normB = minMaxNormalize(b, movies);

        Set<String> allIds = new HashSet<>(normA.keySet());
        allIds.addAll(normB.keySet());

        Map<String, Double> combined = new HashMap<>();
        for (String id : allIds) {
            double sA = normA.getOrDefault(id, 0.0);
            double sB = normB.getOrDefault(id, 0.0);
            combined.put(id, alpha * sA + (1.0 - alpha) * sB);
        }
        return sortAndLimit(combined, movies, k);
    }

    private static void addRrf(List<RankedMovie> ranked, double weight, int c,
                               Map<String, Double> scores, Map<String, RankedMovie> movies) {
        if (ranked == null) return;
        Set<String> seen = new HashSet<>();
        int rank = 1;
        for (RankedMovie r : ranked) {
            if (!seen.add(r.id())) continue;
            scores.merge(r.id(), weight / (c + rank), Double::sum);
            movies.putIfAbsent(r.id(), r);
            rank++;
        }
    }

    private static Map<String, Double> minMaxNormalize(List<RankedMovie> ranked,
                                                       Map<String, RankedMovie> movies) {
        if (ranked == null || ranked.isEmpty()) return Map.of();

        // Quedarse con la primera aparición de cada película (mejor posición)
        Map<String, Double> raw = new LinkedHashMap<>();
        for (RankedMovie r : ranked) {
            if (!raw.containsKey(r.id())) {
                raw.put(r.id(), r.score());
                movies.putIfAbsent(r.id(), r);
            }
        }

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double s : raw.values()) {
            if (s < min) min = s;
            if (s > max) max = s;
        }

        Map<String, Double> norm = new HashMap<>();
        double range = max - min;
        for (var e : raw.entrySet()) {
            double val = (range <= 1e-12) ? 1.0 : (e.getValue() - min) / range;
            norm.put(e.getKey(), val);
        }
        return norm;
    }

    private static List<RankedMovie> sortAndLimit(Map<String, Double> scores,
                                                  Map<String, RankedMovie> movies, int k) {
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(k)
                .map(e -> {
                    RankedMovie m = movies.get(e.getKey());
                    return new RankedMovie(m.id(), m.title(), e.getValue(), -1, "");
                })
                .toList();
    }
}