package es.ugr.ri.vector;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Actividad 3: pruebas unitarias de RRF, RRF ponderado y Fusión Lineal min-max. */
class RankFusionTest {

    private static RankedMovie m(String id, double score) {
        return new RankedMovie(id, "Title-" + id, score, -1, "");
    }

    @Test void rrfSinPonderarCalculadoAMano() {
        // A: m1(1), m2(2), m3(3) | B: m2(1), m4(2), m1(3), con c = 60
        var a = List.of(m("m1", 10.0), m("m2", 8.0), m("m3", 5.0));
        var b = List.of(m("m2", 0.9), m("m4", 0.8), m("m1", 0.7));

        var res = RankFusion.rrf(a, b, 60, 10);
        assertEquals(List.of("m2", "m1", "m4", "m3"), res.stream().map(RankedMovie::id).toList());

        assertEquals(1.0 / 62 + 1.0 / 61, res.get(0).score(), 1e-12); // m2
        assertEquals(1.0 / 61 + 1.0 / 63, res.get(1).score(), 1e-12); // m1
        assertEquals(1.0 / 62, res.get(2).score(), 1e-12);            // m4 (solo en B)
        assertEquals(1.0 / 63, res.get(3).score(), 1e-12);            // m3 (solo en A)
    }

    @Test void rrfDesempataDeFormaDeterministaPorIdYCortaEnK() {
        var a = List.of(m("Z", 5.0));
        var b = List.of(m("A", 0.9), m("B", 0.8));

        var res = RankFusion.rrf(a, b, 60, 2);
        // Z y A empatan en 1/61 -> A va antes por orden lexicográfico; se corta en k=2
        assertEquals(List.of("A", "Z"), res.stream().map(RankedMovie::id).toList());
    }

    @Test void rrfPonderadoCambiaOrdenYContieneAlSinPonderar() {
        var a = List.of(m("m1", 10.0), m("m2", 8.0), m("m3", 5.0));
        var b = List.of(m("m2", 0.9), m("m4", 0.8), m("m1", 0.7));

        // Con alpha = 0.5 mantiene exactamente el mismo orden que rrf sin ponderar y mitad de score
        var rrfBase = RankFusion.rrf(a, b, 60, 10);
        var rrfHalf = RankFusion.weightedRrf(a, b, 0.5, 60, 10);
        assertEquals(rrfBase.stream().map(RankedMovie::id).toList(),
                rrfHalf.stream().map(RankedMovie::id).toList());
        for (int i = 0; i < rrfBase.size(); i++) {
            assertEquals(0.5 * rrfBase.get(i).score(), rrfHalf.get(i).score(), 1e-12);
        }

        // Con alpha = 0.8 (prioriza ranking A), m1 supera a m2
        var rrfFavorA = RankFusion.weightedRrf(a, b, 0.8, 60, 10);
        assertEquals("m1", rrfFavorA.get(0).id());
        assertEquals(0.8 / 61 + 0.2 / 63, rrfFavorA.get(0).score(), 1e-12);
        assertEquals(0.8 / 62 + 0.2 / 61, rrfFavorA.get(1).score(), 1e-12);
    }

    @Test void rrfPonderadoValidaPesosYParametros() {
        var a = List.of(m("m1", 1.0));
        var b = List.of(m("m2", 1.0));

        assertThrows(IllegalArgumentException.class,
                () -> RankFusion.weightedRrf(List.of(a, b), List.of(0.5, 0.3), 60, 10)); // no suman 1
        assertThrows(IllegalArgumentException.class,
                () -> RankFusion.weightedRrf(List.of(a, b), List.of(1.2, -0.2), 60, 10)); // negativo
        assertThrows(IllegalArgumentException.class,
                () -> RankFusion.weightedRrf(a, b, 1.5, 60, 10));
        assertThrows(IllegalArgumentException.class,
                () -> RankFusion.rrf(a, b, 0, 10));
        assertThrows(IllegalArgumentException.class,
                () -> RankFusion.rrf(a, b, 60, 0));
    }

    @Test void fusionLinealMinMaxCalculadaAMano() {
        // A: m1=15, m2=10, m3=5 -> norm: m1=1.0, m2=0.5, m3=0.0
        // B: m2=0.8, m4=0.6, m1=0.4 -> norm: m2=1.0, m4=0.5, m1=0.0
        var a = List.of(m("m1", 15.0), m("m2", 10.0), m("m3", 5.0));
        var b = List.of(m("m2", 0.8), m("m4", 0.6), m("m1", 0.4));

        var res = RankFusion.linearFusion(a, b, 0.5, 10);
        // m2: 0.5*0.5 + 0.5*1.0 = 0.75
        // m1: 0.5*1.0 + 0.5*0.0 = 0.50
        // m4: 0.5*0.0 + 0.5*0.5 = 0.25 (ausente en A -> 0)
        // m3: 0.5*0.0 + 0.5*0.0 = 0.00 (mínimo en A y ausente en B)
        assertEquals(List.of("m2", "m1", "m4", "m3"), res.stream().map(RankedMovie::id).toList());
        assertEquals(0.75, res.get(0).score(), 1e-12);
        assertEquals(0.50, res.get(1).score(), 1e-12);
        assertEquals(0.25, res.get(2).score(), 1e-12);
        assertEquals(0.00, res.get(3).score(), 1e-12);
    }

    @Test void fusionLinealCasosLimiteEmpatesYListasVacias() {
        // Todos los scores iguales en cada lista (max == min -> norm = 1.0)
        var a = List.of(m("m1", 7.5));
        var b = List.of(m("m2", 0.8), m("m3", 0.8));

        var res = RankFusion.linearFusion(a, b, 0.6, 10);
        assertEquals(List.of("m1", "m2", "m3"), res.stream().map(RankedMovie::id).toList());
        assertEquals(0.6, res.get(0).score(), 1e-12);
        assertEquals(0.4, res.get(1).score(), 1e-12);
        assertEquals(0.4, res.get(2).score(), 1e-12);

        assertTrue(RankFusion.linearFusion(List.of(), List.of(), 0.5, 10).isEmpty());
    }
}