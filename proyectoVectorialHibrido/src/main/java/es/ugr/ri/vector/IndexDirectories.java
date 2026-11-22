package es.ugr.ri.vector;

import java.io.IOException;
import java.nio.file.Path;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/** Tres índices Lucene, con la misma interfaz para RAM y disco. */
public final class IndexDirectories implements AutoCloseable {
    public final Directory lexical, chunks, global;
    private IndexDirectories(Directory lexical, Directory chunks, Directory global) {
        this.lexical = lexical; this.chunks = chunks; this.global = global;
    }
    public static IndexDirectories memory() {
        return new IndexDirectories(new ByteBuffersDirectory(), new ByteBuffersDirectory(), new ByteBuffersDirectory());
    }
    /** Abre (o crea, al indexar) tres subdirectorios de un directorio raíz. */
    public static IndexDirectories disk(Path root) throws IOException {
        Directory l = FSDirectory.open(root.resolve("lexical"));
        try {
            Directory c = FSDirectory.open(root.resolve("chunks"));
            try {
                return new IndexDirectories(l, c, FSDirectory.open(root.resolve("global")));
            } catch (IOException | RuntimeException e) { c.close(); throw e; }
        } catch (IOException | RuntimeException e) { l.close(); throw e; }
    }
    @Override public void close() throws IOException {
        IOException error = null;
        for (Directory d : new Directory[]{lexical, chunks, global}) {
            try { d.close(); } catch (IOException e) { if (error == null) error = e; else error.addSuppressed(e); }
        }
        if (error != null) throw error;
    }
}
