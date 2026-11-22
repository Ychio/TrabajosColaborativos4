package es.ugr.ri.vector;

import static org.junit.jupiter.api.Assertions.*;

import es.ugr.ri.vector.ChunkAggregator.Aggregation;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Actividad 2: agregación de fragmentos con notas inventadas, calculables a mano. */
class ChunkAggregatorTest {

    /** Crea un trozo falso: película, número de trozo y nota. */
    private static ChunkHit h(String movie, int chunk, double score) {
        return new ChunkHit(movie, "T" + movie, chunk, score, "texto " + movie + "#" + chunk);
    }

    // Lucene devuelve 4 trozos, pero A ocupa los 3 primeros: solo hay 2 películas.
    private final List<ChunkHit> hits = List.of(
            h("A", 3, 0.90), h("A", 1, 0.85), h("A", 0, 0.80), h("B", 2, 0.70));

    @Test void cuatroFragmentosSonSoloDosPeliculas() {
        var r = ChunkAggregator.aggregate(hits, Aggregation.MAX, 4);
        assertEquals(2, r.size());                                     // pido 4, salen 2
    }

    @Test void maxSinDuplicadosYConFragmentoGanador() {
        var r = ChunkAggregator.aggregate(hits, Aggregation.MAX, 10);
        assertEquals(List.of("A", "B"), r.stream().map(RankedMovie::id).toList());
        assertEquals(0.90, r.get(0).score(), 1e-9);                    // mejor trozo de A
        assertEquals(3, r.get(0).chunkId());                           // y es el trozo 3
        assertEquals("texto A#3", r.get(0).excerpt());
    }

    @Test void top2MediaDeLosDosMejores() {
        var r = ChunkAggregator.aggregate(hits, Aggregation.TOP2, 10);
        assertEquals((0.90 + 0.85) / 2, r.get(0).score(), 1e-9);       // A = 0,875
    }

    @Test void top2UnSoloFragmentoUsaLaNotaMinima() {
        // A: (0,9 + 0,4)/2 = 0,65   B: (0,5 + 0,4)/2 = 0,45   mínima = 0,4
        var hs = List.of(h("A", 0, 0.9), h("B", 0, 0.5), h("B", 1, 0.4));
        var r = ChunkAggregator.aggregate(hs, Aggregation.TOP2, 10);
        assertEquals(0.65, r.get(0).score(), 1e-9);
        assertEquals(0.45, r.get(1).score(), 1e-9);
    }

    @Test void top2PuedeCambiarElOrdenRespectoAMax() {
        // C: un trozo muy bueno. D: dos trozos buenos.
        var hs = List.of(h("C", 0, 0.95), h("D", 0, 0.90), h("D", 1, 0.88), h("E", 0, 0.50));
        assertEquals("C", ChunkAggregator.aggregate(hs, Aggregation.MAX, 3).get(0).id());   // 0,95
        assertEquals("D", ChunkAggregator.aggregate(hs, Aggregation.TOP2, 3).get(0).id());  // 0,89 > 0,725
    }

    @Test void cortaEnKYDesempataPorId() {
        var hs = List.of(h("Z", 0, 0.5), h("Y", 0, 0.5), h("X", 0, 0.4));
        var r = ChunkAggregator.aggregate(hs, Aggregation.MAX, 2);
        assertEquals(List.of("Y", "Z"), r.stream().map(RankedMovie::id).toList());
    }

    @Test void casosLimite() {
        assertTrue(ChunkAggregator.aggregate(List.of(), Aggregation.MAX, 10).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ChunkAggregator.aggregate(hits, Aggregation.MAX, 0));
        assertEquals(Aggregation.TOP2, Aggregation.parse("TOP2"));
        assertThrows(IllegalArgumentException.class, () -> Aggregation.parse("media"));
    }
}