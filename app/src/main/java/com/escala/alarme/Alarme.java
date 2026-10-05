package com.escala.alarme;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Um alarme com a sua regra de repetição.
 * CICLO: ciclo de X dias; os primeiros dias são de trabalho (tocam) e o resto é folga.
 * MENSAL: todo mês no mesmo dia (se o mês for mais curto, toca no último dia do mês).
 */
public class Alarme {

    public static final int CICLO = 0;
    public static final int MENSAL = 1;

    public long id;
    public String nome = "Novo alarme";
    public int tipo = CICLO;
    public boolean ativo = true;

    // CICLO
    public int ciclo = 10;
    public int trabalho = 4;
    public long inicio = LocalDate.now().toEpochDay();
    /** Minutos desde 00:00 para cada dia de trabalho; -1 = sem alarme naquele dia. */
    public int[] horarios = {360, 360, 360, 360};

    // MENSAL
    public int diaMes = 1;
    public int minutos = 540;

    public static Alarme novo() {
        Alarme a = new Alarme();
        a.id = System.currentTimeMillis();
        return a;
    }

    public int horarioDoDia(int i) {
        if (i >= 0 && i < horarios.length) return horarios[i];
        return horarios.length > 0 ? horarios[horarios.length - 1] : 360;
    }

    /** Posição da data dentro do ciclo: 0 até ciclo-1. */
    public int posicao(LocalDate d) {
        long n = ChronoUnit.DAYS.between(LocalDate.ofEpochDay(inicio), d);
        return (int) Math.floorMod(n, (long) ciclo);
    }

    /** Próximo toque estritamente depois de "depois", ou null se não houver. */
    public LocalDateTime proximo(LocalDateTime depois) {
        if (!ativo) return null;
        LocalDate hoje = depois.toLocalDate();
        if (tipo == CICLO) {
            if (ciclo < 1) return null;
            for (int i = 0; i <= ciclo + 1; i++) {
                LocalDate d = hoje.plusDays(i);
                int pos = posicao(d);
                if (pos < trabalho) {
                    int m = horarioDoDia(pos);
                    if (m >= 0) {
                        LocalDateTime t = d.atTime(m / 60, m % 60);
                        if (t.isAfter(depois)) return t;
                    }
                }
            }
            return null;
        }
        for (int k = 0; k <= 13; k++) {
            YearMonth ym = YearMonth.from(hoje).plusMonths(k);
            int dia = Math.min(diaMes, ym.lengthOfMonth());
            LocalDateTime t = ym.atDay(dia).atTime(minutos / 60, minutos % 60);
            if (t.isAfter(depois)) return t;
        }
        return null;
    }

    public String resumo() {
        if (tipo == MENSAL) {
            return "Todo dia " + diaMes + " do mês, " + hhmm(minutos);
        }
        return "Ciclo de " + ciclo + " dias: " + trabalho + " de trabalho e "
                + Math.max(0, ciclo - trabalho) + " de folga";
    }

    public static String hhmm(int m) {
        return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("nome", nome);
        o.put("tipo", tipo);
        o.put("ativo", ativo);
        o.put("ciclo", ciclo);
        o.put("trabalho", trabalho);
        o.put("inicio", inicio);
        JSONArray h = new JSONArray();
        for (int m : horarios) h.put(m);
        o.put("horarios", h);
        o.put("diaMes", diaMes);
        o.put("minutos", minutos);
        return o;
    }

    public static Alarme deJson(JSONObject o) throws JSONException {
        Alarme a = new Alarme();
        a.id = o.getLong("id");
        a.nome = o.optString("nome", "Alarme");
        a.tipo = o.optInt("tipo", CICLO);
        a.ativo = o.optBoolean("ativo", true);
        a.ciclo = o.optInt("ciclo", 10);
        a.trabalho = o.optInt("trabalho", 4);
        a.inicio = o.optLong("inicio", LocalDate.now().toEpochDay());
        JSONArray h = o.optJSONArray("horarios");
        if (h != null) {
            a.horarios = new int[h.length()];
            for (int i = 0; i < h.length(); i++) a.horarios[i] = h.getInt(i);
        }
        a.diaMes = o.optInt("diaMes", 1);
        a.minutos = o.optInt("minutos", 540);
        return a;
    }
}
