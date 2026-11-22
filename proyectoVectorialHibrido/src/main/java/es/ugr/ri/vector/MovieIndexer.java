package es.ugr.ri.vector;
import java.io.IOException;
import java.util.*;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.util.VectorUtil;
/** Indexación separada de la búsqueda; índices RAM compartidos por el orquestador. */
public final class MovieIndexer {
    public record Stats(int movies, int chunks) {}
    public Stats build(List<Movie> movies, IndexDirectories dirs, TextChunker chunker, EmbeddingService embeddings) throws IOException {
        int total = 0;
        try (IndexWriter lexical = new IndexWriter(dirs.lexical, new IndexWriterConfig(new StandardAnalyzer()).setOpenMode(IndexWriterConfig.OpenMode.CREATE));
             IndexWriter fragments = new IndexWriter(dirs.chunks, new IndexWriterConfig().setOpenMode(IndexWriterConfig.OpenMode.CREATE));
             IndexWriter global = new IndexWriter(dirs.global, new IndexWriterConfig().setOpenMode(IndexWriterConfig.OpenMode.CREATE))) {
            for (Movie movie : movies) {
                Document ld = new Document();
                ld.add(new StringField("movie_id", movie.id(), Field.Store.YES));
                ld.add(new StoredField("title", movie.title()));
                ld.add(new TextField("title_text", movie.title(), Field.Store.NO));
                ld.add(new TextField("plot", movie.plot(), Field.Store.NO));
                lexical.addDocument(ld);
                List<MovieChunk> chunks = chunker.split(movie);
                if (chunks.isEmpty()) continue;
                float[] sum = null;
                for (MovieChunk c : chunks) {
                    float[] v = embeddings.embed(c.text());
                    if (sum == null) sum = new float[v.length];
                    VectorUtil.add(sum, v);
                    Document d = new Document();
                    d.add(new StringField("movie_id", c.movieId(), Field.Store.YES));
                    d.add(new StoredField("title", c.title()));
                    d.add(new StoredField("chunk_id", c.chunkId()));
                    d.add(new StoredField("chunk_text", c.text()));
                    d.add(new KnnFloatVectorField("vector", v, VectorSimilarityFunction.DOT_PRODUCT));
                    fragments.addDocument(d);
                    total++;
                }
                VectorUtil.l2normalize(sum);
                Document gd = new Document();
                gd.add(new StringField("movie_id", movie.id(), Field.Store.YES));
                gd.add(new StoredField("title", movie.title()));
                gd.add(new KnnFloatVectorField("vector", sum, VectorSimilarityFunction.DOT_PRODUCT));
                global.addDocument(gd);
            }
        }
        return new Stats(movies.size(), total);
    }
}
