package es.ugr.ri.vector;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Formato TREC: queryId 0 movieId relevance; TSV: queryId TAB text. */
public final class EvaluationData {
    public final Map<String,String> queries;
    public final Map<String,Set<String>> relevant;
    private EvaluationData(Map<String,String> queries, Map<String,Set<String>> relevant) {
        this.queries = Collections.unmodifiableMap(queries); this.relevant = Collections.unmodifiableMap(relevant);
    }
    public static EvaluationData load() throws IOException {
        Map<String,String> q = new LinkedHashMap<>();
        Map<String,Set<String>> rel = new LinkedHashMap<>();
        try (var in = resource("/evaluation/queries.tsv")) {
            String line;
            while ((line=in.readLine())!=null) {
                if (line.isBlank()) continue;
                String[] p=line.split("\\t",2);
                if (p.length != 2) throw new IOException("Consulta inválida: " + line);
                q.put(p[0],p[1]);
            }
        }
        try (var in = resource("/evaluation/qrels.txt")) {
            String line;
            while ((line=in.readLine())!=null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] p=line.trim().split("\\s+");
                if (p.length != 4) throw new IOException("Qrel inválido: " + line);
                if (Integer.parseInt(p[3]) > 0) rel.computeIfAbsent(p[0],x -> new HashSet<>()).add(p[2]);
            }
        }
        return new EvaluationData(q,rel);
    }
    /** Carga archivos externos, sin necesidad de recompilar el proyecto. */
    public static EvaluationData load(java.nio.file.Path queriesPath, java.nio.file.Path qrelsPath) throws IOException {
        Map<String,String> q = new LinkedHashMap<>();
        Map<String,Set<String>> rel = new LinkedHashMap<>();
        try (var in = java.nio.file.Files.newBufferedReader(queriesPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\t", 2);
                if (fields.length != 2 || fields[0].isBlank() || fields[1].isBlank())
                    throw new IOException("Consulta inválida: " + line);
                if (q.putIfAbsent(fields[0], fields[1]) != null)
                    throw new IOException("ID de consulta repetido: " + fields[0]);
            }
        }
        try (var in = java.nio.file.Files.newBufferedReader(qrelsPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.trim().split("\\s+");
                if (fields.length != 4) throw new IOException("Qrel inválido: " + line);
                if (Integer.parseInt(fields[3]) > 0)
                    rel.computeIfAbsent(fields[0], x -> new HashSet<>()).add(fields[2]);
            }
        }
        if (q.isEmpty()) throw new IOException("No hay consultas en " + queriesPath);
        return new EvaluationData(q, rel);
    }
    private static BufferedReader resource(String name) throws IOException {
        InputStream in = EvaluationData.class.getResourceAsStream(name);
        if (in == null) throw new FileNotFoundException(name);
        return new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));
    }
    public EvaluationData restrictTo(Set<String> movieIds) {
        Map<String,Set<String>> filtered = new LinkedHashMap<>();
        for (var e: relevant.entrySet()) {
            Set<String> subset = new HashSet<>(e.getValue()); subset.retainAll(movieIds);
            filtered.put(e.getKey(),subset);
        }
        return new EvaluationData(new LinkedHashMap<>(queries),filtered);
    }
}
