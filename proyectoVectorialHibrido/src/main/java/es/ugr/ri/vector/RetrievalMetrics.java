package es.ugr.ri.vector;
import java.util.*;
/** Métricas docentes. AP@k se normaliza por R (total relevantes del corpus). */
public final class RetrievalMetrics {
    public record Scores(double precision, double recall, double f1, double ap, double ndcg) {}
    private RetrievalMetrics() {}
    public static Scores calculate(List<String> ranking, Set<String> relevant, int k) {
        if (k < 1) throw new IllegalArgumentException("k debe ser positivo");
        Set<String> seen = new HashSet<>();
        int hits=0;
        double sumPrecision=0, dcg=0;
        for (int i=0;i<Math.min(k,ranking.size());i++) {
            String id = ranking.get(i);
            if (!seen.add(id)) throw new IllegalArgumentException("Ranking duplicado: " + id);
            if (relevant.contains(id)) {
                hits++;
                sumPrecision += (double)hits/(i+1);
                dcg += 1.0/(Math.log(i+2)/Math.log(2));
            }
        }
        double p=(double)hits/k;
        double r=relevant.isEmpty()?0:(double)hits/relevant.size();
        double f=(p+r)==0?0:2*p*r/(p+r);
        double ap=relevant.isEmpty()?0:sumPrecision/relevant.size();
        double ideal=0;
        for (int i=0;i<Math.min(k,relevant.size());i++) ideal += 1.0/(Math.log(i+2)/Math.log(2));
        double ndcg=ideal==0?0:dcg/ideal;
        return new Scores(p,r,f,ap,ndcg);
    }
    public static double mean(List<Scores> values, java.util.function.ToDoubleFunction<Scores> metric) {
        return values.stream().mapToDouble(metric).average().orElse(0);
    }
}
