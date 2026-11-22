package es.ugr.ri.vector;

public record RunConfig(String corpus, int limit, int chunkWords, int chunkOverlap, int k, int candidateChunks) {
    public static RunConfig fromProperties() {
        return new RunConfig(
                System.getProperty("corpus", "sample"),
                Integer.getInteger("limit", Integer.MAX_VALUE),
                Integer.getInteger("chunk.words", 120),
                Integer.getInteger("chunk.overlap", 20),
                Integer.getInteger("k", 10),
                Integer.getInteger("candidates", 100));
    }

    public TextChunker chunker() {
        return new TextChunker(chunkWords, chunkOverlap);
    }

    public String describe() {
        return String.format("chunk.words=%d chunk.overlap=%d k=%d candidates=%d",
                chunkWords, chunkOverlap, k, candidateChunks);
    }
}