package es.ugr.ri;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.HashSet;
import java.util.Set;

import org.apache.lucene.analysis.custom.CustomAnalyzer;

public class AnalisisDemo {
    public static void mostrar(Analyzer analyzer, String texto)
            throws IOException {
        try (TokenStream ts = analyzer.tokenStream("contenido", texto)) {
            CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
            OffsetAttribute offset = ts.addAttribute(OffsetAttribute.class);
            PositionIncrementAttribute inc =
                    ts.addAttribute(PositionIncrementAttribute.class);
            TypeAttribute tipo = ts.addAttribute(TypeAttribute.class);
            int pos = -1;
            ts.reset();
            while (ts.incrementToken()) {
                pos += inc.getPositionIncrement();
                System.out.printf("  %s\tpos=%d\toffset=[%d,%d)\ttipo=%s%n",
                        term.toString(), pos, offset.startOffset(),
                        offset.endOffset(), tipo.type());
            }
            ts.end();
        }
    }

    public static void contar(String nombre,Analyzer analyzer,String texto)
            throws IOException{
        int total = 0;
        Set<String> distintos = new HashSet<>();
        try (TokenStream ts = analyzer.tokenStream("contenido", texto)) {
            CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                total++;
                distintos.add(term.toString());
            }
            ts.end();
        }
        System.out.printf("%-20s tokens=%d distintos=%d%n",
                nombre, total, distintos.size()); 
    }

    static Analyzer analizadorAcentos() throws IOException {
        return CustomAnalyzer.builder().withTokenizer("standard").addTokenFilter("lowercase").addTokenFilter("asciiFolding").build();
    }

    static Analyzer analizadorShingles() throws IOException {
        return CustomAnalyzer.builder().withTokenizer("standard").addTokenFilter("lowercase").addTokenFilter("shingle","minShingleSize", "2", "maxShingleSize", "2").build();
    }

    public static void main(String[] args) throws IOException {
        String texto = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
        System.out.println("== Whitespaceanalyzer ==");
        try (Analyzer a = new WhitespaceAnalyzer()) {
            mostrar(a, texto);
        }
        /*System.out.println("== SimpleAnalyzer ==");
        try (Analyzer a = new SimpleAnalyzer()) {
            mostrar(a, texto);
        }*/
        System.out.println("== StandardAnalyzer ==");
        try (Analyzer a = new StandardAnalyzer()) {
            mostrar(a, texto);
        }

        System.out.println("== SpanishAnalyzer ==");
        try (Analyzer a = new SpanishAnalyzer()) {
            mostrar(a, texto);
        }
        System.out.println("== Standard + acentos ==");
        try (Analyzer a = analizadorAcentos()) {
            mostrar(a, texto);
        }
        System.out.println("== Standard + shingles ==");
        try (Analyzer a = analizadorShingles()) {
            mostrar(a, texto);
        }
        try (Analyzer a = new WhitespaceAnalyzer()) { contar("Whitespace", a, texto); }
        try (Analyzer a = new StandardAnalyzer())   { contar("Standard", a, texto); }
        try (Analyzer a = new SpanishAnalyzer())    { contar("Spanish", a, texto); }
        try (Analyzer a = analizadorAcentos())  { contar("Standard+acentos", a, texto); }
        try (Analyzer a = analizadorShingles()) { contar("Standard+shingles", a, texto); }
    }
}