package es.ugr.ri.busqueda;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Scanner;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FloatPoint;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import es.ugr.ri.comun.Analizadores;
import es.ugr.ri.comun.Campos;

public class BuscarPeliculas {

    private static final int MAX_HITS = 10;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Uso: BuscarPeliculas <ruta_indice>");
            System.exit(1);
        }

        Path rutaIndice = Paths.get(args[0]);
        if (!Files.isDirectory(rutaIndice)) {
            System.err.println("Error: No existe el directorio del índice en: " + rutaIndice.toAbsolutePath());
            System.err.println("Debe ejecutar primero el indexador.");
            System.exit(2);
        }

        try (Directory dir = FSDirectory.open(rutaIndice);
             IndexReader reader = DirectoryReader.open(dir);
             Analyzer analyzer = Analizadores.crear()) {

            IndexSearcher searcher = new IndexSearcher(reader);
            // Similitud BM25 por defecto
            searcher.setSimilarity(new BM25Similarity(1.2f, 0.75f));

            ejecutarMenuInteractivo(searcher, reader, analyzer);

        } catch (IOException e) {
            System.err.println("Error al acceder al índice: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void ejecutarMenuInteractivo(IndexSearcher searcher, IndexReader reader, Analyzer analyzer) {
        Scanner sc = new Scanner(System.in);
        boolean salir = false;

        while (!salir) {
            System.out.println("\n========================================================");
            System.out.println("                BUSCADOR DE PELÍCULAS");
            System.out.println("========================================================");
            System.out.println("1. Consulta textual en un campo (Título o Argumento)");
            System.out.println("2. Consulta textual en varios campos (Título y Argumento)");
            System.out.println("3. Consulta exacta sobre categóricos (Género y Director)");
            System.out.println("4. Consulta numérica exacta (Año y Votos)");
            System.out.println("5. Consulta numérica por rango (Años y Valoración IMDb)");
            System.out.println("6. Consulta booleana mixta (MUST, SHOULD, MUST_NOT, FILTER)");
            System.out.println("7. Ranking BM25 y desglose de puntuación (Explain)");
            System.out.println("8. Ordenación por campo (Año o Valoración) usando DocValues");
            System.out.println("9. Consulta compuesta del guion (detective + Mystery + rango)");
            System.out.println("10. Experimento BM25 (Variación de k1 y b)");
            System.out.println("0. Salir");
            System.out.print("Seleccione una opción: ");

            String opcion = sc.nextLine().trim();
            try {
                switch (opcion) {
                    case "1" -> ejercicio1(searcher, analyzer, sc);
                    case "2" -> ejercicio2(searcher, analyzer, sc);
                    case "3" -> ejercicio3(searcher, sc);
                    case "4" -> ejercicio4(searcher, sc);
                    case "5" -> ejercicio5(searcher, sc);
                    case "6" -> ejercicio6(searcher, analyzer);
                    case "7" -> ejercicio7(searcher, analyzer, sc);
                    case "8" -> ejercicio8(searcher, sc);
                    case "9" -> ejercicioCompuestoGuion(searcher, analyzer);
                    case "10" -> experimentoBM25(reader, analyzer, sc);
                    case "0" -> salir = true;
                    default -> System.out.println("Opción no válida.");
                }
            } catch (Exception e) {
                System.err.println("Error durante la búsqueda: " + e.getMessage());
            }
        }
    }

    // 1. Consultas textuales sobre un campo (términos y frases con parser)
    private static void ejercicio1(IndexSearcher searcher, Analyzer analyzer, Scanner sc) throws Exception {
        System.out.println("\n--- 1. Consulta textual en un campo ---");
        System.out.print("Campo a consultar (titulo / argumento) [defecto: titulo]: ");
        String campo = sc.nextLine().trim();
        if (campo.isEmpty() || (!campo.equalsIgnoreCase(Campos.TITULO) && !campo.equalsIgnoreCase(Campos.ARGUMENTO))) {
            campo = Campos.TITULO;
        }

        System.out.print("Introduzca texto o frase (ej. love story o star) [defecto: star]: ");
        String consulta = sc.nextLine().trim();
        if (consulta.isEmpty()) consulta = "star";

        QueryParser parser = new QueryParser(campo, analyzer);
        Query q = parser.parse(consulta);

        System.out.println("\nQuery interpretada: " + q.toString());
        mostrarResultados(searcher, q, MAX_HITS);
    }

    // 2. Consulta textual en varios campos (MultiFieldQueryParser)
    private static void ejercicio2(IndexSearcher searcher, Analyzer analyzer, Scanner sc) throws Exception {
        System.out.println("\n--- 2. Consulta textual en varios campos (Título y Argumento) ---");
        System.out.print("Texto a buscar en título y argumento (ej. secret agent) [defecto: secret agent]: ");
        String texto = sc.nextLine().trim();
        if (texto.isEmpty()) texto = "secret agent";

        String[] campos = { Campos.TITULO, Campos.ARGUMENTO };
        MultiFieldQueryParser parser = new MultiFieldQueryParser(campos, analyzer);
        Query q = parser.parse(texto);

        System.out.println("\nQuery generada: " + q.toString());
        System.out.println("Explicación: Combina las cláusulas en SHOULD (por defecto OR) sumando puntuaciones.");
        mostrarResultados(searcher, q, MAX_HITS);
    }

    // 3. Consultas exactas categóricas (TermQuery sobre StringField)
    private static void ejercicio3(IndexSearcher searcher, Scanner sc) throws IOException {
        System.out.println("\n--- 3. Consultas exactas sobre atributos categóricos ---");
        System.out.print("Género exacto (ej. Comedy, Action, Romantic comedy) [defecto: Comedy]: ");
        String genero = sc.nextLine().trim();
        if (genero.isEmpty()) genero = "Comedy";

        System.out.print("Director (dejar vacío si solo busca género, o ej. Steven Spielberg): ");
        String director = sc.nextLine().trim();

        BooleanQuery.Builder bq = new BooleanQuery.Builder();
        bq.add(new TermQuery(new Term(Campos.GENERO, genero)), BooleanClause.Occur.MUST);

        if (!director.isEmpty()) {
            bq.add(new TermQuery(new Term(Campos.DIRECTOR, director)), BooleanClause.Occur.MUST);
        }

        Query q = bq.build();
        System.out.println("\nQuery generada: " + q);
        mostrarResultados(searcher, q, MAX_HITS);
    }

    // 4. Numéricas exactas (IntPoint y FloatPoint)
    private static void ejercicio4(IndexSearcher searcher, Scanner sc) throws IOException {
        System.out.println("\n--- 4. Consultas numéricas exactas ---");
        System.out.println("Puede consultar por año, por valoración IMDb exacta, o combinar ambos:");
        System.out.print("Año exacto (ej. 1994, pulse Intro para omitir): ");
        String anioStr = sc.nextLine().trim();

        System.out.print("Valoración IMDb exacta (ej. 8.0, pulse Intro para omitir): ");
        String notaStr = sc.nextLine().trim();

        BooleanQuery.Builder bq = new BooleanQuery.Builder();
        boolean tieneFiltro = false;

        if (!anioStr.isEmpty()) {
            int anio = Integer.parseInt(anioStr);
            bq.add(IntPoint.newExactQuery(Campos.ANIO, anio), BooleanClause.Occur.MUST);
            tieneFiltro = true;
        }

        if (!notaStr.isEmpty()) {
            float nota = Float.parseFloat(notaStr);
            bq.add(FloatPoint.newExactQuery(Campos.VALORACION, nota), BooleanClause.Occur.MUST);
            tieneFiltro = true;
        }

        Query q;
        if (!tieneFiltro) {
            System.out.println("No se introdujo ningún criterio; buscando año 1994 por defecto.");
            q = IntPoint.newExactQuery(Campos.ANIO, 1994);
        } else if (anioStr.isEmpty() || notaStr.isEmpty()) {
            q = !anioStr.isEmpty()
                    ? IntPoint.newExactQuery(Campos.ANIO, Integer.parseInt(anioStr))
                    : FloatPoint.newExactQuery(Campos.VALORACION, Float.parseFloat(notaStr));
        } else {
            q = bq.build();
        }

        System.out.println("\nQuery generada: " + q);
        System.out.println("Nota técnica: Se emplean IntPoint y FloatPoint (estructuras BKD).");
        System.out.println("Tratamiento de ausentes: Las películas sin dicho valor (null) no contienen el Point y no generan falsos positivos.");
        mostrarResultados(searcher, q, MAX_HITS);
    }

    // 5. Numéricas por rango (IntPoint y FloatPoint)
    private static void ejercicio5(IndexSearcher searcher, Scanner sc) throws IOException {
        System.out.println("\n--- 5. Consultas numéricas por rango (Años y Valoración IMDb) ---");
        System.out.print("Año mínimo [defecto 1990]: ");
        String minAnioS = sc.nextLine().trim();
        int minA = minAnioS.isEmpty() ? 1990 : Integer.parseInt(minAnioS);

        System.out.print("Año máximo [defecto 2000]: ");
        String maxAnioS = sc.nextLine().trim();
        int maxA = maxAnioS.isEmpty() ? 2000 : Integer.parseInt(maxAnioS);

        System.out.print("Nota IMDb mínima (decimal) [defecto 8.0]: ");
        String minNS = sc.nextLine().trim();
        float minN = minNS.isEmpty() ? 8.0f : Float.parseFloat(minNS);

        System.out.print("Nota IMDb máxima (decimal) [defecto 10.0]: ");
        String maxNS = sc.nextLine().trim();
        float maxN = maxNS.isEmpty() ? 10.0f : Float.parseFloat(maxNS);

        Query qAnio = IntPoint.newRangeQuery(Campos.ANIO, minA, maxA);
        Query qNota = FloatPoint.newRangeQuery(Campos.VALORACION, minN, maxN);

        BooleanQuery q = new BooleanQuery.Builder()
                .add(qAnio, BooleanClause.Occur.FILTER)
                .add(qNota, BooleanClause.Occur.FILTER)
                .build();

        System.out.println("\nQuery generada: " + q);
        mostrarResultados(searcher, q, MAX_HITS);
    }

    // 6. Consultas booleanas mixtas (MUST, SHOULD, MUST_NOT, FILTER)
    private static void ejercicio6(IndexSearcher searcher, Analyzer analyzer) throws Exception {
        System.out.println("\n--- 6. Demostración de consulta booleana mixta ---");
        System.out.println("Condiciones:");
        System.out.println(" - MUST: argumento contiene 'war' (aporta relevancia)");
        System.out.println(" - SHOULD: título contiene 'world' (bonifica la puntuación si aparece)");
        System.out.println(" - MUST_NOT: NO ser del género 'Documentary'");
        System.out.println(" - FILTER: estrenada entre 1939 y 1945 (filtro puro, no puntúa)");

        Query qTexto = new QueryParser(Campos.ARGUMENTO, analyzer).parse("war");
        Query qTitulo = new QueryParser(Campos.TITULO, analyzer).parse("world");
        Query qSinDoc = new TermQuery(new Term(Campos.GENERO, "Documentary"));
        Query qRango = IntPoint.newRangeQuery(Campos.ANIO, 1939, 1945);

        BooleanQuery q = new BooleanQuery.Builder()
                .add(qTexto, BooleanClause.Occur.MUST)
                .add(qTitulo, BooleanClause.Occur.SHOULD)
                .add(qSinDoc, BooleanClause.Occur.MUST_NOT)
                .add(qRango, BooleanClause.Occur.FILTER)
                .build();

        System.out.println("\nQuery interpretada: " + q);
        mostrarResultados(searcher, q, MAX_HITS);
    }

    // 7. Ranking BM25 e interpretación de puntuaciones con Explain
    private static void ejercicio7(IndexSearcher searcher, Analyzer analyzer, Scanner sc) throws Exception {
        System.out.println("\n--- 7. Ranking BM25 y análisis de puntuaciones ---");
        System.out.print("Término a buscar en argumento [defecto 'vampire']: ");
        String term = sc.nextLine().trim();
        if (term.isEmpty()) term = "vampire";

        Query q = new QueryParser(Campos.ARGUMENTO, analyzer).parse(term);

        System.out.println("\nQuery interpretada: " + q);
        mostrarResultados(searcher, q, MAX_HITS);

        TopDocs hitsParaExplain = searcher.search(q, 2);
        if (hitsParaExplain.scoreDocs.length >= 2) {
            System.out.println("\n=== Explicación detallada de diferencias de score ===");
            System.out.println("Documento #1 (DocID Lucene: " + hitsParaExplain.scoreDocs[0].doc + "):");
            Explanation exp1 = searcher.explain(q, hitsParaExplain.scoreDocs[0].doc);
            System.out.println(exp1.toString().substring(0, Math.min(500, exp1.toString().length())) + "\n");

            System.out.println("Documento #2 (DocID Lucene: " + hitsParaExplain.scoreDocs[1].doc + "):");
            Explanation exp2 = searcher.explain(q, hitsParaExplain.scoreDocs[1].doc);
            System.out.println(exp2.toString().substring(0, Math.min(500, exp2.toString().length())) + "\n");
        }
    }

    // 8. Ordenación por DocValues (Año y Valoración IMDb)
    private static void ejercicio8(IndexSearcher searcher, Scanner sc) throws IOException {
        System.out.println("\n--- 8. Ordenación con DocValues ---");
        System.out.println("1. Ordenación combinada: por Año (descendente) y desempate por Valoración (descendente)");
        System.out.println("2. Ordenar exclusivamente por Valoración IMDb (descendente)");
        System.out.println("3. Ordenar exclusivamente por Año de estreno (descendente)");
        System.out.print("Seleccione opción [defecto 1]: ");
        String op = sc.nextLine().trim();

        SortField sfAnio = new SortField(Campos.ANIO_ORDEN, SortField.Type.LONG, true);
        // Si falta el año, se envía al final de la lista
        sfAnio.setMissingValue(Long.MIN_VALUE);

        SortField sfNota = new SortField(Campos.VALORACION_ORDEN, SortField.Type.FLOAT, true);
        // Las películas sin valoración (null) se envían al final de la lista
        sfNota.setMissingValue(Float.NEGATIVE_INFINITY);

        Sort sort;
        if ("2".equals(op)) {
            sort = new Sort(sfNota);
            System.out.println("\nCriterio: Valoración IMDb descendente.");
        } else if ("3".equals(op)) {
            sort = new Sort(sfAnio);
            System.out.println("\nCriterio: Año de estreno descendente.");
        } else {
            // Criterio combinado: Año + Valoración para empates
            sort = new Sort(sfAnio, sfNota);
            System.out.println("\nCriterio: Año descendente con desempate por Valoración descendente.");
        }

        // Búsqueda de todas las películas ordenadas por DocValues
        TopDocs results = searcher.search(new MatchAllDocsQuery(), MAX_HITS, sort);

        System.out.println("Resultados obtenidos:");
        for (ScoreDoc sd : results.scoreDocs) {
            Document d = searcher.storedFields().document(sd.doc);
            String anio = d.get(Campos.ANIO) != null ? d.get(Campos.ANIO) : "Sin año";
            String nota = d.get(Campos.VALORACION) != null ? d.get(Campos.VALORACION) : "Sin nota";
            System.out.printf("[ID: %-8s] Año: %-8s | IMDb: %-8s | %s\n",
                    d.get(Campos.ID), anio, nota, d.get(Campos.TITULO));
        }

    }


    // Consulta compuesta del enunciado de la práctica (Sección 5 y 5.1)
    private static void ejercicioCompuestoGuion(IndexSearcher searcher, Analyzer analyzer) throws Exception {
        System.out.println("\n--- Consulta Compuesta del Enunciado ---");
        System.out.println("Filtros: Sinopsis contiene 'detective', Género 'Mystery', Año [1990-2020], IMDb [7.5-9.0]");

        Query qSinopsis = new QueryParser(Campos.ARGUMENTO, analyzer).parse("detective");
        Query qGenero = new TermQuery(new Term(Campos.GENERO, "Mystery"));
        Query qAnio = IntPoint.newRangeQuery(Campos.ANIO, 1990, 2020);
        Query qNota = FloatPoint.newRangeQuery(Campos.VALORACION, 7.5f, 9.0f);

        BooleanQuery consultaTotal = new BooleanQuery.Builder()
                .add(qSinopsis, BooleanClause.Occur.MUST)
                .add(qGenero, BooleanClause.Occur.FILTER)
                .add(qAnio, BooleanClause.Occur.FILTER)
                .add(qNota, BooleanClause.Occur.FILTER)
                .build();

        System.out.println("Query construida: " + consultaTotal);
        mostrarResultados(searcher, consultaTotal, MAX_HITS);
    }

    // 5.2 Experimento BM25: variación de k1 y b
    private static void experimentoBM25(IndexReader reader, Analyzer analyzer, Scanner sc) throws Exception {
        System.out.println("\n--- 10. Experimento BM25 (Modificación de k1 y b) ---");
        System.out.print("Consulta textual para el experimento [defecto: space battle]: ");
        String texto = sc.nextLine().trim();
        if (texto.isEmpty()) texto = "space battle";

        Query q = new QueryParser(Campos.ARGUMENTO, analyzer).parse(texto);

        // Caso base (por defecto: k1=1.2, b=0.75)
        evaluarConfiguracionBM25(reader, q, 1.2f, 0.75f, "Base (k1=1.2, b=0.75)");

        // Caso 1: Sin normalización por longitud (b = 0.0)
        evaluarConfiguracionBM25(reader, q, 1.2f, 0.0f, "Sin penalización de longitud (b=0.0)");

        // Caso 2: Máxima normalización por longitud (b = 1.0)
        evaluarConfiguracionBM25(reader, q, 1.2f, 1.0f, "Máxima penalización de longitud (b=1.0)");

        // Caso 3: Saturación rápida de término (k1 = 0.4)
        evaluarConfiguracionBM25(reader, q, 0.4f, 0.75f, "Baja saturación k1=0.4");
    }

    private static void evaluarConfiguracionBM25(IndexReader reader, Query q, float k1, float b, String desc) throws IOException {
        IndexSearcher s = new IndexSearcher(reader);
        s.setSimilarity(new BM25Similarity(k1, b));
        TopDocs hits = s.search(q, 3);

        System.out.printf(Locale.ROOT, "\nConfiguración: %s (k1=%.2f, b=%.2f)\n", desc, k1, b);
        for (int i = 0; i < hits.scoreDocs.length; i++) {
            ScoreDoc sd = hits.scoreDocs[i];
            Document d = s.storedFields().document(sd.doc);
            System.out.printf(Locale.ROOT, "  #%d [Score: %.4f] %s (ID: %s)\n",
                    (i + 1), sd.score, d.get(Campos.TITULO), d.get(Campos.ID));
        }
    }

    private static void mostrarResultados(IndexSearcher searcher, Query q, int limite) throws IOException {
        TopDocs topDocs = searcher.search(q, limite);
        System.out.printf("Total aciertos: %d documentos encontrados.\n", topDocs.totalHits.value());

        ScoreDoc[] hits = topDocs.scoreDocs;
        if (hits.length == 0) {
            System.out.println("No se han encontrado resultados.");
            return;
        }

        for (int i = 0; i < hits.length; i++) {
            ScoreDoc sd = hits[i];
            Document doc = searcher.storedFields().document(sd.doc);

            String[] generos = doc.getValues(Campos.GENERO);
            String listaGeneros = (generos != null && generos.length > 0) ? String.join(", ", generos) : "N/D";
            String[] directores = doc.getValues(Campos.DIRECTOR);
            String listaDirectores = (directores != null && directores.length > 0) ? String.join(", ", directores) : "N/D";
            String resumen = doc.get(Campos.RESUMEN);
            if (resumen == null) resumen = "Sin resumen disponible.";

            System.out.printf(Locale.ROOT, "\n[%d] Score: %.4f | ID: %s | %s (%s)\n",
                    (i + 1), sd.score, doc.get(Campos.ID), doc.get(Campos.TITULO), doc.get(Campos.ANIO));
            System.out.println("    Géneros: " + listaGeneros);
            System.out.println("    IMDb: " + (doc.get(Campos.VALORACION) != null ? doc.get(Campos.VALORACION) : "N/D") +
                    " | Votos: " + (doc.get(Campos.VOTOS) != null ? doc.get(Campos.VOTOS) : "N/D"));
            System.out.println("    Director/es: " + listaDirectores);
            System.out.println("    Argumento (extracto): " + resumen);
        }
    }
}