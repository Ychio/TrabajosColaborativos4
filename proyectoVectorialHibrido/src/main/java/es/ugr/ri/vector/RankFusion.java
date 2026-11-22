package es.ugr.ri.vector;
import java.util.*;
/** RRF opera sobre rankings de películas (no sobre fragmentos). */
public final class RankFusion {
    private RankFusion() {}
    public static List<RankedMovie> rrf(List<RankedMovie> a, List<RankedMovie> b, int c, int k) {
        if (c < 1) throw new IllegalArgumentException("c debe ser positivo");
        Map<String, Double> scores = new HashMap<>();
        Map<String, RankedMovie> movies = new HashMap<>();
        add(a,c,scores,movies); add(b,c,scores,movies);
        return scores.entrySet().stream().sorted(Map.Entry.<String,Double>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .limit(k).map(e -> { RankedMovie m = movies.get(e.getKey());
                    return new RankedMovie(m.id(),m.title(),e.getValue(),-1,""); }).toList();
    }
    private static void add(List<RankedMovie> ranked, int c, Map<String,Double> scores, Map<String,RankedMovie> movies) {
        Set<String> seen = new HashSet<>();
        for (int i=0;i<ranked.size();i++) {
            RankedMovie r = ranked.get(i);
            if (!seen.add(r.id())) continue;
            scores.merge(r.id(),1.0/(c+i+1),Double::sum);
            movies.putIfAbsent(r.id(),r);
        }
    }
}
