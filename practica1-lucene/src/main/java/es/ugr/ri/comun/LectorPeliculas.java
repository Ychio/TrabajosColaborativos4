package es.ugr.ri.comun;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class LectorPeliculas {

    private LectorPeliculas() { }

    public static Pelicula parsear(String linea) {
        JsonObject o = JsonParser.parseString(linea).getAsJsonObject();

        // Generos: union de CMU e IMDb, sin repetidos y conservando el orden
        Set<String> generos = new LinkedHashSet<>();
        generos.addAll(lista(o, "genres_cmu"));
        generos.addAll(lista(o, "genres_imdb"));

        Double anio = numero(o, "year");
        Double valoracion = numero(o, "rating_imdb");
        Double votos = numero(o, "votes_imdb");

        return new Pelicula(
                texto(o, "wikipedia_movie_id"),
                texto(o, "title"),
                texto(o, "plot"),
                anio == null ? null : anio.intValue(),
                new ArrayList<>(generos),
                lista(o, "directors"),
                valoracion == null ? null : valoracion.floatValue(),
                votos == null ? null : votos.intValue());
    }

    /** Devuelve la cadena, o null si falta o esta vacia. */
    private static String texto(JsonObject o, String clave) {
        JsonElement e = o.get(clave);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) {
            return null;
        }
        String s = e.getAsString().strip();
        return s.isEmpty() ? null : s;
    }

    /** Devuelve el numero, o null si falta. */
    private static Double numero(JsonObject o, String clave) {
        JsonElement e = o.get(clave);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) {
            return null;
        }
        return e.getAsDouble();
    }

    /** Devuelve la lista de cadenas; vacia si falta. */
    private static List<String> lista(JsonObject o, String clave) {
        List<String> valores = new ArrayList<>();
        JsonElement e = o.get(clave);
        if (e == null || !e.isJsonArray()) {
            return valores;
        }
        for (JsonElement item : e.getAsJsonArray()) {
            if (item.isJsonPrimitive()) {
                String s = item.getAsString().strip();
                if (!s.isEmpty()) {
                    valores.add(s);
                }
            }
        }
        return valores;
    }
}