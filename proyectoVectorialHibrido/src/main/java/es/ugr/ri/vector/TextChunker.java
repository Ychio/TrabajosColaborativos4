package es.ugr.ri.vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class TextChunker {
    private final int chunkWords;   // w: palabras por chunk (120)
    private final int overlap;      // o: palabras que comparten dos chunks seguidos (20)

    public TextChunker(int chunkWords) {
        this(chunkWords, 0);
    }

    public TextChunker(int chunkWords, int overlap) {
        if (chunkWords < 1)
            throw new IllegalArgumentException("chunk.words debe ser positivo: " + chunkWords);
        if (overlap < 0 || overlap >= chunkWords)
            throw new IllegalArgumentException("chunk.overlap debe cumplir 0 <= o < w (o=" + overlap + ", w=" + chunkWords + ")");
        this.chunkWords = chunkWords;
        this.overlap = overlap;
    }

    public int chunkWords() { return chunkWords; }
    public int overlap() { return overlap; }

    public List<MovieChunk> split(Movie movie) {
        if (movie == null) throw new IllegalArgumentException("movie es null");
        String[] words = words(movie.plot());
        List<MovieChunk> out = new ArrayList<>();
        int chunkId = 0;
        for (int[] w : windows(words.length)) {
            String text = String.join(" ", Arrays.copyOfRange(words, w[0], w[1]));
            out.add(new MovieChunk(movie.id(), movie.title(), chunkId++, text));
        }
        return out;
    }

    /** Devuelve [inicio, fin) de cada chunk para un texto de n palabras. */
    public List<int[]> windows(int n) {
        List<int[]> out = new ArrayList<>();
        if (n <= 0) return out;
        int step = chunkWords - overlap;
        for (int start = 0; ; start += step) {
            int end = Math.min(start + chunkWords, n);
            out.add(new int[]{start, end});
            if (end == n) break;
        }
        return out;
    }

    static String[] words(String text) {
        if (text == null || text.isBlank()) return new String[0];
        return text.trim().split("\\s+");
    }
}