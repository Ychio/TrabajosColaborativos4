package es.ugr.ri.vector;

import java.util.Arrays;
import java.util.List;

/** Comprobación obligatoria (p. 9): muestra los fragmentos de las películas de ejemplo. */
public final class ChunkInspectMain {
    public static void main(String[] args) {
        RunConfig config = RunConfig.fromProperties();
        TextChunker chunker = config.chunker();
        System.out.println("Configuración: " + config.describe());

        for (Movie m : MovieData.sample()) {
            String[] all = TextChunker.words(m.plot());
            List<MovieChunk> chunks = chunker.split(m);
            System.out.printf("%n%s (%s): %d palabras -> %d fragmentos%n",
                    m.title(), m.id(), all.length, chunks.size());

            for (MovieChunk c : chunks) {
                String[] w = TextChunker.words(c.text());
                System.out.printf("  #%d %3d pal | %s ... %s%n",
                        c.chunkId(), w.length, head(w, 8), tail(w, 6));
            }

            String[] last = TextChunker.words(chunks.get(chunks.size() - 1).text());
            boolean ok = tail(last, 10).equals(tail(all, 10));
            System.out.printf("  Final de la sinopsis:        ... %s%n", tail(all, 10));
            System.out.printf("  Final del último fragmento:  ... %s%n", tail(last, 10));
            System.out.println("  ¿Se conserva el final? " + (ok ? "SÍ" : "NO"));
        }
    }

    private static String head(String[] w, int n) {
        return String.join(" ", Arrays.copyOfRange(w, 0, Math.min(n, w.length)));
    }
    private static String tail(String[] w, int n) {
        return String.join(" ", Arrays.copyOfRange(w, Math.max(0, w.length - n), w.length));
    }
}