package es.ugr.ri.vector;
import java.util.*;
/** Ejecutar sin Maven: javac -d /tmp/metricstest ... && java -cp /tmp/metricstest ... */
public final class MetricsSmokeTest {
    public static void main(String[] args) {
        var s=RetrievalMetrics.calculate(List.of("A","X","B"),Set.of("A","B"),3);
        near(s.precision(),2.0/3); near(s.recall(),1.0); near(s.f1(),0.8);
        near(s.ap(),(1+2.0/3)/2);
        near(s.ndcg(),(1+1/ (Math.log(4)/Math.log(2)))/(1+1/(Math.log(3)/Math.log(2))));
        var f=RankFusion.rrf(List.of(new RankedMovie("A","A",2,-1,"") ),List.of(new RankedMovie("B","B",1,-1,"") ),60,2);
        if (f.size()!=2) throw new AssertionError();
        System.out.println("OK: métricas y RRF");
    }
    private static void near(double x,double y) { if(Math.abs(x-y)>1e-9) throw new AssertionError(x+" != "+y); }
}
