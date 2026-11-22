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
        var parser = new org.apache.lucene.queryparser.classic.MultiFieldQueryParser(new String[]{"title_text", "plot"}, new StandardAnalyzer());
        Query q = parser.parse(QueryParser.escape(query));
        var docs = lexical.search(q, k);
        List<RankedMovie> out = new ArrayList<>();
        for (var hit : docs.scoreDocs) {
            var d = lexical.storedFields().document(hit.doc);
            out.add(new RankedMovie(d.get("movie_id"), d.get("title"), hit.score, -1, ""));
        }
        return out;
    }
    public List<RankedMovie> vectorChunks(String query, int candidates, int k) throws IOException {
        int n = Math.min(candidates, chunkReader.numDocs());
        if (n == 0) return List.of();
        var docs = chunks.search(new KnnFloatVectorQuery("vector", embeddings.embed(query), n), n);
        Map<String, RankedMovie> best = new HashMap<>();
        for (var hit : docs.scoreDocs) {
            var d = chunks.storedFields().document(hit.doc);
            var r = new RankedMovie(d.get("movie_id"), d.get("title"), hit.score,
                    d.getField("chunk_id").numericValue().intValue(), d.get("chunk_text"));
            best.merge(r.id(), r, (a,b) -> a.score() >= b.score() ? a : b);
        }
        return best.values().stream().sorted(Comparator.comparingDouble(RankedMovie::score).reversed().thenComparing(RankedMovie::id)).limit(k).toList();
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
        // EJERCICIO: sustituir esta fusión de ejemplo por una implementación propia y comparar variantes.
        return RankFusion.rrf(bm25(query, Math.max(k, 20)), vectorChunks(query, candidates, Math.max(k, 20)), 60, k);
    }
    @Override public void close() throws IOException { lexicalReader.close(); chunkReader.close(); globalReader.close(); }
}
