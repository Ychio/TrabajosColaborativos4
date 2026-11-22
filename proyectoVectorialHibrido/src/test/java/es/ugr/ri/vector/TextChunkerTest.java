package es.ugr.ri.vector;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Actividad 1: pruebas de segmentación con w = 10 y o = 2. */
class TextChunkerTest {
    private static final int W = 10, O = 2;
    private final TextChunker chunker = new TextChunker(W, O);

    /** Película falsa con sinopsis "w0 w1 w2 ..." para saber qué palabra es cada una. */
    private static Movie movie(int n) {
        String plot = IntStream.range(0, n).mapToObj(i -> "w" + i).collect(Collectors.joining(" "));
        return new Movie("m" + n, "Test " + n, plot);
    }

    private static List<String> words(MovieChunk c) { return List.of(TextChunker.words(c.text())); }

    @Test void longitud0_noGeneraFragmentos() {
        assertTrue(chunker.split(movie(0)).isEmpty());
        assertTrue(chunker.split(new Movie("x", "x", null)).isEmpty());
        assertTrue(chunker.split(new Movie("x", "x", "   ")).isEmpty());
    }

    @Test void longitudWmenos1_unFragmento() {
        var cs = chunker.split(movie(W - 1));            // 9 palabras
        assertEquals(1, cs.size());
        assertEquals(9, words(cs.get(0)).size());
    }

    @Test void longitudW_unFragmentoSinDuplicado() {
        var cs = chunker.split(movie(W));                // 10 palabras
        assertEquals(1, cs.size());
    }

    @Test void longitudWmas1_dosFragmentos() {
        var cs = chunker.split(movie(W + 1));            // 11 palabras: [0,10) y [8,11)
        assertEquals(2, cs.size());
        assertEquals(List.of("w8", "w9", "w10"), words(cs.get(1)));
    }

    @Test void longitud2Wmas1_tresFragmentos() {
        var cs = chunker.split(movie(2 * W + 1));        // 21 palabras: [0,10) [8,18) [16,21)
        assertEquals(3, cs.size());
        assertEquals("w0", words(cs.get(0)).get(0));
        assertEquals("w8", words(cs.get(1)).get(0));
        assertEquals("w16", words(cs.get(2)).get(0));
        assertEquals(5, words(cs.get(2)).size());        // el último es corto
    }

    @Test void coberturaOrdenEIdentificadores() {
        for (int n : new int[]{1, 9, 10, 11, 21, 57, 333}) {
            var cs = chunker.split(movie(n));
            List<String> vistas = new ArrayList<>();
            for (int i = 0; i < cs.size(); i++) {
                assertEquals(i, cs.get(i).chunkId());           // 0, 1, 2...
                assertTrue(words(cs.get(i)).size() <= W);       // ninguno pasa de w
                vistas.addAll(words(cs.get(i)));
            }
            for (int i = 0; i < n; i++)
                assertTrue(vistas.contains("w" + i), "falta w" + i + " con n=" + n);
        }
    }

    @Test void solapamientoEntreConsecutivos() {
        var cs = chunker.split(movie(57));
        for (int i = 1; i < cs.size(); i++) {
            var prev = words(cs.get(i - 1));
            var cur = words(cs.get(i));
            assertEquals(prev.subList(prev.size() - O, prev.size()), cur.subList(0, O));
        }
    }

    @Test void solapamientoMaximoTermina() {
        var cs = new TextChunker(10, 9).split(movie(15));   // paso 1
        assertEquals(6, cs.size());
    }

    @Test void parametrosInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> new TextChunker(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TextChunker(10, -1));
        assertThrows(IllegalArgumentException.class, () -> new TextChunker(10, 10));
    }
}