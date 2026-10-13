package es.ugr.ri;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;

import org.apache.tika.langdetect.optimaize.OptimaizeLangDetector;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

import org.apache.tika.sax.Link;
import org.apache.tika.sax.LinkContentHandler;
import org.apache.tika.sax.TeeContentHandler;

import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Main {
    private static void analizarContenedor(Path fichero, LanguageDetector langDetector) throws Exception {
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(new AutoDetectParser());
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1));// utilizamos estos dos para tratar los recursos del zip por separado en vez de tratarlo junto como uno único

        try (TikaInputStream tis = TikaInputStream.get(fichero)) {
            wrapper.parse(tis, handler, new Metadata(), new ParseContext());
        }

        List<Metadata> recursos = handler.getMetadataList();
        System.out.println("\nContenedor: " + fichero.getFileName());
        System.out.println("Recursos encontrados (contenedor incluido): " + recursos.size());
        for (Metadata m : recursos) {
            String ruta = m.get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH);
            String texto = m.get(TikaCoreProperties.TIKA_CONTENT);
            String idioma = "-";
            if (!texto.isBlank()) {
                String muestra = texto.substring(0, Math.min(texto.length(), 100_000));
                LanguageResult r = langDetector.detect(muestra);
                idioma = r.isUnknown() ? "?" : r.getLanguage();
            }
            String enc = m.get("Content-Encoding");
            System.out.println("  " + (ruta == null ? "(contenedor)" : ruta)
                    + "  |  " + m.get("Content-Type")
                    + "  |  codificacion: " + (enc == null ? "-" : enc)
                    + "  |  idioma: " + idioma
                    + "  |  caracteres: " + (texto == null ? 0 : texto.length()));
        }
    }//parte 7.3 analizando recursivamente
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Uso: mvn exec:java -Dexec.args=\"documentos\"");
            return;
        }

        Path dir = Path.of(args[0]);
        if (!Files.isDirectory(dir)) {
            System.err.println("No es un directorio " + dir);
            return;
        }

        Tika tika = new Tika();
        AutoDetectParser parser = new AutoDetectParser(); //Lee el contenido del documento con el parser adecuado
        LanguageDetector langDetector = new OptimaizeLangDetector().loadModels();//Detecta idiomas

        List<Path> ficheros;
        try(Stream<Path> s = Files.walk(dir)){
            ficheros = s.filter(Files::isRegularFile).sorted().collect(Collectors.toList()); //Lo ultimo basicamente recolecta todos los elementos y lo pone en un sitio y con el toList lo metemos en lista
        } //dicho bloque recorre la carpeta de forma recursiva para sacar todos los documentos que archivos regulares.

        System.out.println("Ficheros encontrados: " + ficheros.size());
        
        Path dirTexto = Path.of("salida","texto");
        Path dirMeta = Path.of("salida","metadatos");
        Path dirEnlaces = Path.of("salida","enlaces");
        Files.createDirectories(dirTexto);
        Files.createDirectories(dirMeta);
        Files.createDirectories(dirEnlaces); //creamos los directorios para guardar los contenidos de texto, metadatos y enlaces

        List<String> filas = new ArrayList<>();
        filas.add("Fichero;MIME (contenido);MIME (solo extension);Codificacion;Idioma;Caracteres;Estado");

        ObjectMapper mapper = new ObjectMapper();
        List<String> jsonl = new ArrayList<>();
        int contador = 0;
        for (Path p : ficheros) {
            contador++;
            String id = String.format("doc%04d", contador);
            String nombre = dir.relativize(p).toString();
            String mimeContenido = "-";
            String mimeNombre = tika.detect(p.getFileName().toString());
            String codificacion = "-";
            String idioma = "-";
            int caracteres = 0;
            String estado = "OK";

            String titulo = null;
            String autor = null;
            String texto = "";
            List<String> destinos = new ArrayList<>();
            //Preparamos el estado inicial de cada fichero 
            try{
                try(TikaInputStream tis = TikaInputStream.get(p)){
                    mimeContenido = tika.detect(tis);
                }//Usamos tika para el tipo por contenido que tiene

                Metadata md = new Metadata();
                BodyContentHandler handler = new BodyContentHandler(-1);
                LinkContentHandler linkHandler = new LinkContentHandler();
                TeeContentHandler tee = new TeeContentHandler(handler, linkHandler);
                try (TikaInputStream tis = TikaInputStream.get(p)) {
                    parser.parse(tis, tee, md, new ParseContext()); //el parser actua de locutor y va soltando contenido, mientras el tee unifica los dos habndler y cada handler pilla lo que necesite
                }
                texto = handler.toString();
                caracteres = texto.length(); //abrimos un flujo y vamos leyendo poco a poco e introduciendo los metadatos y el contenido en el sitio correspondiente
                Files.writeString(dirTexto.resolve(nombre + ".txt"), texto,StandardCharsets.UTF_8);

                String enc = md.get("Content-Encoding");
                if (enc == null) enc = md.get("X-TIKA:detectedEncoding");//tika a lo mejor lo deja aqui si no lo encuentra arriba
                if (enc != null) codificacion = enc;//pillamos el codificado que dejo en md en el bloque de arriba

                if (!texto.isBlank()) {
                    String muestra = texto.substring(0, Math.min(texto.length(), 100_000));
                    LanguageResult r = langDetector.detect(muestra);
                    idioma = r.isUnknown() ? "?" : r.getLanguage(); //Identifica el lenguaje mediante como maximo los primeros 100000 palabras
                }
                
                List<String> lineasMd = new ArrayList<>();
                for (String n : md.names()) {
                    lineasMd.add(n + " = " + String.join(" | ", md.getValues(n)));
                }
                Files.write(dirMeta.resolve(nombre + ".txt"), lineasMd, StandardCharsets.UTF_8);

                // Enlaces -> salida/enlaces/<fichero>.txt
                List<Link> enlaces = linkHandler.getLinks();
                List<String> lineasEnl = new ArrayList<>();
                for (Link l : enlaces) {
                    lineasEnl.add(l.getType() + "\t" + l.getUri() + "\t" + l.getText().trim());
                }
                Files.write(dirEnlaces.resolve(nombre + ".txt"), lineasEnl, StandardCharsets.UTF_8);
                titulo = md.get("dc:title");
                if (titulo == null) titulo = md.get("og:title");
                if (titulo == null) titulo = md.get("pdf:docinfo:title");

                autor = md.get("dc:creator");
                if (autor == null) autor = md.get("meta:author");

                for (Link l : enlaces) {
                    if ("a".equals(l.getType()) && l.getUri() != null && !l.getUri().isBlank() && !l.getUri().startsWith("#")) {
                        destinos.add(l.getUri());
                    }
                }
            }catch (Exception e) {
                estado = "Error: " + e.getClass().getSimpleName();
            } // try catch para cada archivo para que si un archivo falla no reviente el programa directamente

            if ("OK".equals(estado) && texto.isBlank()) estado = "SIN_TEXTO";
            String lang = "-".equals(idioma) ? null : idioma;

            ObjectNode reg = mapper.createObjectNode();
            reg.put("id", id);
            reg.put("path", p.toString());
            reg.put("mime_type", mimeContenido);
            reg.put("language", lang);
            reg.put("title", titulo);
            reg.put("author", autor);
            reg.put("text", texto);
            reg.put("num_characters", caracteres);
            ArrayNode arr = reg.putArray("links");
            destinos.forEach(arr::add);
            reg.put("status", estado);

            jsonl.add(mapper.writeValueAsString(reg));
            filas.add(String.join(";", nombre, mimeContenido, mimeNombre,codificacion, idioma, String.valueOf(caracteres), estado));//Ponemos cada uno de estos como una fila de datos
        }
        for (String f : filas) {
            System.out.println(f);
        }
        try (PrintWriter pw = new PrintWriter("inventario.csv", StandardCharsets.UTF_8)) {
            filas.forEach(pw::println);
        }//Utilizando la biblioteca printwriter fija UTF-8 y para cada linea de la lista filas lo mete en el csv
        Path zip = dir.resolve("comprimido.zip");
        if (Files.isRegularFile(zip)) {
            analizarContenedor(zip, langDetector);
        }
        Files.write(Path.of("coleccion.jsonl"), jsonl, StandardCharsets.UTF_8);
        System.out.println("Guardado coleccion.jsonl con " + jsonl.size() + " registros");
        System.out.println("\nGuardado correctamente en inventario.csv");
    }
}
