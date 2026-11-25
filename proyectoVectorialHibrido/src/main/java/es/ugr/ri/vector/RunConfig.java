package es.ugr.ri.vector;

import java.util.Locale;

public record RunConfig(
        String corpus,
        int limit,
        int chunkWords,
        int chunkOverlap,
        int k,
        int candidateChunks,
        int rrfC,
        int fusionDepth,
        double fusionAlpha) {

    public RunConfig(String corpus, int limit, int chunkWords, int chunkOverlap, int k, int candidateChunks) {
        this(corpus, limit, chunkWords, chunkOverlap, k, candidateChunks, 60, 20, 0.6);
    }

    public static RunConfig fromProperties() {
        return new RunConfig(
                System.getProperty("corpus", "sample"),
                Integer.getInteger("limit", Integer.MAX_VALUE),
                Integer.getInteger("chunk.words", 120),
                Integer.getInteger("chunk.overlap", 20),
                Integer.getInteger("k", 10),
                Integer.getInteger("candidates", 100),
                Integer.getInteger("rrf.c", 60),
                Integer.getInteger("fusion.depth", 20),
                Double.parseDouble(System.getProperty("fusion.alpha", "0.6")));
    }

    public TextChunker chunker() {
        return new TextChunker(chunkWords, chunkOverlap);
    }

    public String describe() {
        return String.format(Locale.ROOT,
                "chunk.words=%d chunk.overlap=%d k=%d candidates=%d rrf.c=%d fusion.depth=%d fusion.alpha=%.2f",
                chunkWords, chunkOverlap, k, candidateChunks, rrfC, fusionDepth, fusionAlpha);
    }
}