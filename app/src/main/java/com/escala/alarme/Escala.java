package com.escala.alarme;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Guarda a lista de alarmes e as configurações gerais do app. */
public class Escala {

    public static class Proximo {
        public Alarme alarme;
        public LocalDateTime quando;
    }

    private static final String CHAVE = "alarmes_json";

    private final SharedPreferences p;

    public Escala(Context c) {
        p = c.getApplicationContext().getSharedPreferences("escala", Context.MODE_PRIVATE);
    }

    // ---------- alarmes ----------

    public List<Alarme> alarmes() {
        String json = p.getString(CHAVE, null);
        if (json == null) {
            List<Alarme> migrados = migrarVersaoAntiga();
            salvar(migrados);
            return migrados;
        }
        List<Alarme> lista = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                lista.add(Alarme.deJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) { }
        return lista;
    }

    public void salvar(List<Alarme> lista) {
        try {
            JSONArray arr = new JSONArray();
            for (Alarme a : lista) arr.put(a.toJson());
            p.edit().putString(CHAVE, arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    public Alarme buscar(long id) {
        for (Alarme a : alarmes()) {
            if (a.id == id) return a;
        }
        return null;
    }

    public void salvarUm(Alarme novo) {
        List<Alarme> lista = alarmes();
        boolean achou = false;
        for (int i = 0; i < lista.size(); i++) {
            if (lista.get(i).id == novo.id) {
                lista.set(i, novo);
                achou = true;
                break;
            }
        }
        if (!achou) lista.add(novo);
        salvar(lista);
    }

    public void remover(long id) {
        List<Alarme> lista = alarmes();
        for (int i = 0; i < lista.size(); i++) {
            if (lista.get(i).id == id) {
                lista.remove(i);
                break;
            }
        }
        salvar(lista);
    }

    /** O próximo toque entre todos os alarmes, ou null. */
    public Proximo proximo(LocalDateTime depois) {
        Proximo melhor = null;
        for (Alarme a : alarmes()) {
            LocalDateTime t = a.proximo(depois);
            if (t != null && (melhor == null || t.isBefore(melhor.quando))) {
                melhor = new Proximo();
                melhor.alarme = a;
                melhor.quando = t;
            }
        }
        return melhor;
    }

    /** Traz para a lista nova a escala que existia na versão anterior do app. */
    private List<Alarme> migrarVersaoAntiga() {
        List<Alarme> lista = new ArrayList<>();
        if (!p.contains("inicio")) return lista;
        Alarme a = Alarme.novo();
        a.nome = "Trabalho";
        a.tipo = Alarme.CICLO;
        a.ativo = p.getBoolean("ativo", true);
        a.ciclo = 10;
        a.trabalho = 4;
        a.inicio = p.getLong("inicio", LocalDate.now().toEpochDay());
        a.horarios = new int[4];
        for (int i = 0; i < 4; i++) {
            boolean ligado = p.getBoolean("ligado" + i, true);
            int m = p.getInt("hora" + i, 6) * 60 + p.getInt("min" + i, 0);
            a.horarios[i] = ligado ? m : -1;
        }
        lista.add(a);
        return lista;
    }

    // ---------- configurações gerais ----------

    /** Som escolhido (texto de um Uri) ou null para o som de alarme padrão do celular. */
    public String toque() { return p.getString("toque", null); }
    public void setToque(String uri) { p.edit().putString("toque", uri).apply(); }

    public int sonecaMinutos() { return p.getInt("soneca", 10); }

    public boolean mostrarContagem() { return p.getBoolean("contagem", true); }
    public void setMostrarContagem(boolean v) { p.edit().putBoolean("contagem", v).apply(); }
}
