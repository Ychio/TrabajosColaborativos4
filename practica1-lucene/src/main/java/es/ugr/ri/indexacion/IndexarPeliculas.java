package es.ugr.ri.indexacion;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.stream.Stream;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.document.FloatPoint;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import es.ugr.ri.comun.Analizadores;
import es.ugr.ri.comun.Campos;
import es.ugr.ri.comun.LectorPeliculas;
import es.ugr.ri.comun.Pelicula;

public class IndexarPeliculas {

    private static final int LONGITUD_RESUMEN = 300;

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Uso: IndexarPeliculas <ruta_jsonl> <ruta_indice>");
            System.exit(1);
        }
        Path rutaJsonl = Paths.get(args[0]);
        Path rutaIndice = Paths.get(args[1]);

        if (!Files.isRegularFile(rutaJsonl)) {
            System.err.println("Error: no existe el fichero de la coleccion: " + rutaJsonl.toAbsolutePath());
            System.exit(2);
        }

        int lineas = 0;
        int descartadas = 0;
        int indexadas = 0;
        int sinAnio = 0;
        int sinGeneros = 0;
        int sinDirector = 0;
        int sinValoracion = 0;
        int sinVotos = 0;
        long inicio = System.nanoTime();

        try (Directory dir = FSDirectory.open(rutaIndice);
             Analyzer analizador = Analizadores.crear()) {

            IndexWriterConfig config = new IndexWriterConfig(analizador);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            config.setRAMBufferSizeMB(128.0);
            config.setSimilarity(new BM25Similarity(1.2f, 0.75f));

            try (IndexWriter writer = new IndexWriter(dir, config);
                 BufferedReader lector = Files.newBufferedReader(rutaJsonl, StandardCharsets.UTF_8)) {

                String linea;
                while ((linea = lector.readLine()) != null) {
                    if (linea.isBlank()) {
                        continue;
                    }
                    lineas++;

                    Pelicula p;
                    try {
                        p = LectorPeliculas.parsear(linea);
                    } catch (RuntimeException e) {
                        descartadas++;
                        System.err.println("Aviso: la linea " + lineas + " no es JSON valido y se omite.");
                        continue;
                    }
                    if (p.id() == null) {
                        descartadas++;
                        System.err.println("Aviso: la linea " + lineas + " no tiene identificador y se omite.");
                        continue;
                    }

                    if (p.anio() == null) sinAnio++;
                    if (p.generos().isEmpty()) sinGeneros++;
                    if (p.directores().isEmpty()) sinDirector++;
                    if (p.valoracion() == null) sinValoracion++;
                    if (p.votos() == null) sinVotos++;

                    writer.addDocument(crearDocumento(p));
                }

                writer.commit();
                indexadas = writer.getDocStats().numDocs;
            }
        }

        double segundos = (System.nanoTime() - inicio) / 1e9;
        long bytes = tamanoDirectorio(rutaIndice);

        System.out.println("=== Indexacion finalizada ===");
        System.out.println("Coleccion: " + rutaJsonl.toAbsolutePath());
        System.out.println("Indice creado en: " + rutaIndice.toAbsolutePath());
        System.out.println("Lineas leidas: " + lineas);
        System.out.println("Lineas descartadas: " + descartadas);
        System.out.println("Peliculas indexadas: " + indexadas);
        System.out.println(String.format(Locale.ROOT, "Tiempo empleado: %.2f segundos", segundos));
        System.out.println(String.format(Locale.ROOT, "Tamano del indice: %.1f MB", bytes / (1024.0 * 1024.0)));
        System.out.println("--- Peliculas con datos ausentes ---");
        System.out.println("Sin fecha de estreno: " + sinAnio);
        System.out.println("Sin ningun genero: " + sinGeneros);
        System.out.println("Sin direccion conocida: " + sinDirector);
        System.out.println("Sin nota de IMDb: " + sinValoracion);
        System.out.println("Sin numero de votos: " + sinVotos);
    }

    static Document crearDocumento(Pelicula p) {
        Document doc = new Document();

        doc.add(new StringField(Campos.ID, p.id(), Field.Store.YES));

        if (p.titulo() != null) {
            doc.add(new TextField(Campos.TITULO, p.titulo(), Field.Store.YES));
        }

        if (p.argumento() != null) {
            doc.add(new TextField(Campos.ARGUMENTO, p.argumento(), Field.Store.NO));
            doc.add(new StoredField(Campos.RESUMEN, resumir(p.argumento())));
        }

        if (p.anio() != null) {
            doc.add(new IntPoint(Campos.ANIO, p.anio()));
            doc.add(new StoredField(Campos.ANIO, p.anio()));
            doc.add(new NumericDocValuesField(Campos.ANIO_ORDEN, p.anio()));
        }

        for (String genero : p.generos()) {
            doc.add(new StringField(Campos.GENERO, genero, Field.Store.YES));
        }

        for (String director : p.directores()) {
            doc.add(new StringField(Campos.DIRECTOR, director, Field.Store.YES));
        }

        if (p.valoracion() != null) {
            doc.add(new FloatPoint(Campos.VALORACION, p.valoracion()));
            doc.add(new StoredField(Campos.VALORACION, p.valoracion()));
            doc.add(new FloatDocValuesField(Campos.VALORACION_ORDEN, p.valoracion()));
        }

        if (p.votos() != null) {
            doc.add(new IntPoint(Campos.VOTOS, p.votos()));
            doc.add(new StoredField(Campos.VOTOS, p.votos()));
            doc.add(new NumericDocValuesField(Campos.VOTOS_ORDEN, p.votos()));
        }

        return doc;
    }

    static String resumir(String texto) {
        if (texto.length() <= LONGITUD_RESUMEN) {
            return texto;
        }
        int corte = texto.lastIndexOf(' ', LONGITUD_RESUMEN);
        if (corte < LONGITUD_RESUMEN / 2) {
            corte = LONGITUD_RESUMEN;
        }
        return texto.substring(0, corte) + "...";
    }

    private static long tamanoDirectorio(Path carpeta) throws IOException {
        try (Stream<Path> ficheros = Files.walk(carpeta)) {
            return ficheros.filter(Files::isRegularFile).mapToLong(f -> {
                try {
                    return Files.size(f);
                } catch (IOException e) {
                    return 0L;
                }
            }).sum();
        }
    }
}