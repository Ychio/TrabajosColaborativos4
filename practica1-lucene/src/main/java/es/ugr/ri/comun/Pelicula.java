package es.ugr.ri.comun;

import java.util.List;

public record Pelicula(
    String id,
    String titulo,
    String argumento,
    Integer anio,
    List<String> generos,
    List<String> directores,
    Float valoracion,
    Integer votos) {
}