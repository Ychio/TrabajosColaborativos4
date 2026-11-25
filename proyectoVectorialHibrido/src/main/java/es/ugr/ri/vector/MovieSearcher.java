package es.ugr.ri.vector;

import java.io.IOException;
import java.util.*;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;

/** Recuperador independiente: BM25, vectores y fusión. */
public final class MovieSearcher implements AutoCloseable {
    private final DirectoryReader lexicalReader, chunkReader, globalReader;
    private final IndexSearcher lexical, chunks, global;
    private final EmbeddingService embeddings;

    public MovieSearcher(IndexDirectories indexes, EmbeddingService embeddings) throws IOException {
        this.embeddings = embeddings;
        lexicalReader = DirectoryReader.open(indexes.lexical);
        chunkReader = DirectoryReader.open(indexes.chunks);
        globalReader = DirectoryReader.open(indexes.global);
        lexical = new IndexSearcher(lexicalReader);
        chunks = new IndexSearcher(chunkReader);
        global = new IndexSearcher(globalReader);
    }

    public List<RankedMovie> bm25(String query, int k) throws Exception {
        var parser = new org.apache.lucene.queryparser.classic.MultiFieldQueryParser(
                new String[]{"title_text", "plot"}, new StandardAnalyzer());
        Query q = parser.parse(QueryParser.escape(query));
        var docs = lexical.search(q, k);
        List<RankedMovie> out = new ArrayList<>();
        for (var hit : docs.scoreDocs) {
            var d = lexical.storedFields().document(hit.doc);
            out.add(new RankedMovie(d.get("movie_id"), d.get("title"), hit.score, -1, ""));
        }
        return out;
    }

    public List<ChunkHit> chunkHits(String query, int candidates) throws IOException {
        int n = Math.min(candidates, chunkReader.numDocs());
        if (n == 0) return List.of();
        var docs = chunks.search(new KnnFloatVectorQuery("vector", embeddings.embed(query), n), n);
        List<ChunkHit> out = new ArrayList<>();
        for (var hit : docs.scoreDocs) {
            var d = chunks.storedFields().document(hit.doc);
            out.add(new ChunkHit(d.get("movie_id"), d.get("title"),
                    d.getField("chunk_id").numericValue().intValue(), hit.score, d.get("chunk_text")));
        }
        return out;
    }

    public int numChunks() { return chunkReader.numDocs(); }

    /** k-NN sobre fragmentos agrupado con MAX (comportamiento original). */
    public List<RankedMovie> vectorChunks(String query, int candidates, int k) throws IOException {
        return vectorChunks(query, candidates, k, ChunkAggregator.Aggregation.MAX);
    }

    /** k-NN sobre fragmentos agrupado con la agregación indicada (MAX o TOP2). */
    public List<RankedMovie> vectorChunks(String query, int candidates, int k,
                                          ChunkAggregator.Aggregation agg) throws IOException {
        List<ChunkHit> hits = chunkHits(query, candidates);
        List<RankedMovie> ranking = ChunkAggregator.aggregate(hits, agg, k);
        if (ranking.size() < k && hits.size() < chunkReader.numDocs())
            System.err.printf("AVISO: %d fragmentos candidatos solo dan %d películas distintas (k=%d)%n",
                    hits.size(), ranking.size(), k);
        return ranking;
    }

    public List<RankedMovie> vectorGlobal(String query, int k) throws IOException {
        int n = Math.min(k, globalReader.numDocs());
        if (n == 0) return List.of();
        var docs = global.search(new KnnFloatVectorQuery("vector", embeddings.embed(query), n), n);
        List<RankedMovie> out = new ArrayList<>();
        for (var hit : docs.scoreDocs) {
            var d = global.storedFields().document(hit.doc);
            out.add(new RankedMovie(d.get("movie_id"), d.get("title"), hit.score, -1, ""));
        }
        return out;
    }

    public List<RankedMovie> hybridRrf(String query, int candidates, int k) throws Exception {
        return hybridRrf(query, candidates, 20, 60, k);
    }

    public List<RankedMovie> hybridRrf(String query, int candidates, int depth, int c, int k) throws Exception {
        int pool = Math.max(k, depth);
        return RankFusion.rrf(bm25(query, pool), vectorChunks(query, candidates, pool), c, k);
    }

    public List<RankedMovie> hybridWeightedRrf(String query, int candidates, int depth,
                                               double alpha, int c, int k) throws Exception {
        int pool = Math.max(k, depth);
        return RankFusion.weightedRrf(bm25(query, pool), vectorChunks(query, candidates, pool), alpha, c, k);
    }

    public List<RankedMovie> hybridLinear(String query, int candidates, int depth,
                                          double alpha, int k) throws Exception {
        int pool = Math.max(k, depth);
        return RankFusion.linearFusion(bm25(query, pool), vectorChunks(query, candidates, pool), alpha, k);
    }

    @Override
    public void close() throws IOException {
        lexicalReader.close();
        chunkReader.close();
        globalReader.close();
    }
}