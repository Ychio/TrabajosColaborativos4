package es.ugr.ri.comun;

import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

public final class Analizadores {

    private Analizadores() { }

    public static Analyzer crear() {
        Analyzer porDefecto = new StandardAnalyzer();

        Map<String, Analyzer> porCampo = new HashMap<>();
        porCampo.put(Campos.ARGUMENTO, new EnglishAnalyzer());

        return new PerFieldAnalyzerWrapper(porDefecto, porCampo);
    }
}