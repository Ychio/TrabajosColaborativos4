package es.ugr.ri.vector;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.apache.lucene.util.VectorUtil;

/** El mismo modelo se usa para indexar y consultar. */
public final class EmbeddingService {
    private final EmbeddingModel model = new AllMiniLmL6V2EmbeddingModel();

    public float[] embed(String text) {
        float[] v = model.embed(text).content().vector();
        if (v.length != 384) throw new IllegalStateException("Dimensionalidad inesperada: " + v.length);
        VectorUtil.l2normalize(v);
        return v;
    }
}
