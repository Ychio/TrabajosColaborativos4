package es.ugr.ri.vector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
/** Adaptador de entrada proporcionado: no es el objetivo de la práctica. */
public final class MovieRepository {
    private MovieRepository() {}
    public static List<Movie> load(String path, int limit) throws IOException {
        if (path == null || path.isBlank() || path.equals("sample")) return MovieData.sample();
        ObjectMapper mapper = new ObjectMapper();
        List<Movie> result = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(Path.of(path), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null && result.size() < limit) {
                if (line.isBlank()) continue;
                JsonNode j = mapper.readTree(line);
                String id = j.path("wikipedia_movie_id").asText("");
                String title = j.path("title").asText("");
                String plot = j.path("plot").asText("");
                if (!id.isBlank() && !title.isBlank() && !plot.isBlank()) result.add(new Movie(id,title,plot));
            }
        }
        return result;
    }
}
