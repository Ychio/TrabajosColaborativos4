package es.ugr.ri;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.apache.tika.Tika;
import org.junit.jupiter.api.Test;

class TikaTest {

    private final Tika tika = new Tika();
    private final Path documento = Path.of("documentos", "prueba.txt");

    @Test
    void detectaTextoPlano() throws Exception {
        assertEquals("text/plain", tika.detect(documento));
    }

    @Test
    void extraeContenido() throws Exception {
        String texto = tika.parseToString(documento);
        assertTrue(texto.contains("Apache Tika"));
    }
}
