package es.ugr.ri.vector;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Actividad 4: verificación independiente de P@k, R@k, F1@k, AP@k, MAP@k y NDCG@k. */
class RetrievalMetricsTest {

    private static double log2(double x) {
        return Math.log(x) / Math.log(2.0);
    }

    @Test void relevanteEnPrimeraPosicion() {
        // Ranking: [A, X, Y, Z], relevante={A}, k=4
        var s = RetrievalMetrics.calculate(List.of("A", "X", "Y", "Z"), Set.of("A"), 4);
        assertEquals(0.25, s.precision(), 1e-12); // 1/4
        assertEquals(1.0, s.recall(), 1e-12);     // 1/1
        assertEquals(0.4, s.f1(), 1e-12);         // 2*0.25*1 / 1.25
        assertEquals(1.0, s.ap(), 1e-12);         // (1/1)/1
        assertEquals(1.0, s.ndcg(), 1e-12);       // DCG=1, IDCG=1
    }

    @Test void relevanteEnUltimaPosicion() {
        // Ranking: [X, Y, Z, A], relevante={A}, k=4
        var s = RetrievalMetrics.calculate(List.of("X", "Y", "Z", "A"), Set.of("A"), 4);
        assertEquals(0.25, s.precision(), 1e-12);
        assertEquals(1.0, s.recall(), 1e-12);
        assertEquals(0.4, s.f1(), 1e-12);
        assertEquals(0.25, s.ap(), 1e-12);           // (1/4)/1
        assertEquals(1.0 / log2(5), s.ndcg(), 1e-12); // DCG=1/log2(5), IDCG=1
    }

    @Test void variosRelevantesEjemploSeccion79() {
        // Sección 7.9 del guion: (0, 1, 0, 1, 0) con 2 relevantes en la colección
        var s = RetrievalMetrics.calculate(List.of("X1", "A", "X2", "B", "X3"), Set.of("A", "B"), 5);
        assertEquals(2.0 / 5.0, s.precision(), 1e-12);
        assertEquals(1.0, s.recall(), 1e-12);
        assertEquals(4.0 / 7.0, s.f1(), 1e-12);
        assertEquals(0.5, s.ap(), 1e-12); // (1/2 + 2/4) / 2
        double dcg = 1.0 / log2(3) + 1.0 / log2(5);
        double idcg = 1.0 / log2(2) + 1.0 / log2(3);
        assertEquals(dcg / idcg, s.ndcg(), 1e-12);
    }

    @Test void variosRelevantesEjemploSeccion75() {
        // Sección 7.5 del guion: (1, 0, 1, 0, 0) con 3 relevantes en la colección
        var s = RetrievalMetrics.calculate(List.of("A", "X1", "B", "X2", "X3"), Set.of("A", "B", "C"), 5);
        assertEquals(0.4, s.precision(), 1e-12);
        assertEquals(2.0 / 3.0, s.recall(), 1e-12);
        assertEquals(0.5, s.f1(), 1e-12);
        assertEquals(5.0 / 9.0, s.ap(), 1e-12); // (1 + 2/3) / 3
        double dcg = 1.0 + 1.0 / log2(4);
        double idcg = 1.0 + 1.0 / log2(3) + 1.0 / log2(4);
        assertEquals(dcg / idcg, s.ndcg(), 1e-12);
    }

    @Test void ausenciaDeRelevantesYNingunAciertoSinDivisionPorCero() {
        // Sin relevantes en qrels -> todo 0, sin NaN ni excepciones
        var sinRel = RetrievalMetrics.calculate(List.of("A", "B"), Set.of(), 5);
        assertEquals(0.0, sinRel.precision(), 1e-12);
        assertEquals(0.0, sinRel.recall(), 1e-12);
        assertEquals(0.0, sinRel.f1(), 1e-12);
        assertEquals(0.0, sinRel.ap(), 1e-12);
        assertEquals(0.0, sinRel.ndcg(), 1e-12);

        // Con relevantes pero ningún acierto -> todo 0
        var sinAciertos = RetrievalMetrics.calculate(List.of("A", "B"), Set.of("C", "D"), 2);
        assertEquals(0.0, sinAciertos.precision(), 1e-12);
        assertEquals(0.0, sinAciertos.recall(), 1e-12);
        assertEquals(0.0, sinAciertos.f1(), 1e-12);
        assertEquals(0.0, sinAciertos.ap(), 1e-12);
        assertEquals(0.0, sinAciertos.ndcg(), 1e-12);

        // Ranking vacío
        var vacio = RetrievalMetrics.calculate(List.of(), Set.of("A"), 5);
        assertEquals(0.0, vacio.precision(), 1e-12);
        assertEquals(0.0, vacio.ndcg(), 1e-12);
    }

    @Test void todosLosAciertosYMenosDeKResultados() {
        var todos = RetrievalMetrics.calculate(List.of("A", "B", "C"), Set.of("A", "B", "C"), 3);
        assertEquals(1.0, todos.precision(), 1e-12);
        assertEquals(1.0, todos.recall(), 1e-12);
        assertEquals(1.0, todos.f1(), 1e-12);
        assertEquals(1.0, todos.ap(), 1e-12);
        assertEquals(1.0, todos.ndcg(), 1e-12);

        // Menos de k resultados: [A, X] con k=5 -> P@5 divide entre k=5
        var corto = RetrievalMetrics.calculate(List.of("A", "X"), Set.of("A"), 5);
        assertEquals(0.2, corto.precision(), 1e-12);
        assertEquals(1.0, corto.recall(), 1e-12);
        assertEquals(1.0 / 3.0, corto.f1(), 1e-12);
        assertEquals(1.0, corto.ap(), 1e-12);
        assertEquals(1.0, corto.ndcg(), 1e-12);
    }

    @Test void convencionApDividePorTotalRelevantesNoPorMinKR() {
        // 4 relevantes en colección, k=2, los 2 primeros aciertan -> AP = (1 + 1) / 4 = 0.5
        var s = RetrievalMetrics.calculate(List.of("A", "B"), Set.of("A", "B", "C", "D"), 2);
        assertEquals(1.0, s.precision(), 1e-12);
        assertEquals(0.5, s.recall(), 1e-12);
        assertEquals(0.5, s.ap(), 1e-12);
        assertEquals(1.0, s.ndcg(), 1e-12);
    }

    @Test void rechazaDuplicadosYKInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> RetrievalMetrics.calculate(List.of("A", "A"), Set.of("A"), 2));
        assertThrows(IllegalArgumentException.class,
                () -> RetrievalMetrics.calculate(List.of("A"), Set.of("A"), 0));
    }

    @Test void mediaDeVariasConsultasMapYNdcg() {
        var q1 = RetrievalMetrics.calculate(List.of("A", "X"), Set.of("A"), 2); // AP=1.0, NDCG=1.0
        var q2 = RetrievalMetrics.calculate(List.of("X", "A"), Set.of("A"), 2); // AP=0.5, NDCG=1/log2(3)
        double map = RetrievalMetrics.mean(List.of(q1, q2), RetrievalMetrics.Scores::ap);
        double meanNdcg = RetrievalMetrics.mean(List.of(q1, q2), RetrievalMetrics.Scores::ndcg);

        assertEquals(0.75, map, 1e-12);
        assertEquals((1.0 + 1.0 / log2(3)) / 2.0, meanNdcg, 1e-12);
        assertEquals(0.0, RetrievalMetrics.mean(List.of(), RetrievalMetrics.Scores::ap), 1e-12);
    }
}