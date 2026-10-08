package com.escala.alarme;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.Locale;
import java.util.TreeMap;

/**
 * Um alarme com a sua regra de repetição.
 * CICLO: ciclo de X dias; os primeiros dias são de trabalho (tocam) e o resto é folga.
 * MENSAL: todo mês no mesmo dia (se o mês for mais curto, toca no último dia do mês).
 */
public class Alarme {

    public static final int CICLO = 0;
    public static final int MENSAL = 1;

    /** Turnos de um dia: sem alarme, HA ou ZH (zero hora). */
    public static final int NADA = 0;
    public static final int HA = 1;
    public static final int ZH = 2;

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
    /** Horário dos turnos, em minutos desde 00:00. */
    public int ha = 290;
    public int zh = 1010;
    /** Turno (HA ou ZH) de cada dia de trabalho; null = automático (primeira metade HA, resto ZH). */
    public int[] turnos = null;
    /** Exceções por data (dia em epoch day): NADA = não toca nesse dia; HA ou ZH = toca nesse turno. */
    public TreeMap<Long, Integer> exc = new TreeMap<>();

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

    public int turnoDoDia(int i) {
        if (turnos != null && i >= 0 && i < turnos.length && (turnos[i] == HA || turnos[i] == ZH)) {
            return turnos[i];
        }
        return i < (trabalho + 1) / 2 ? HA : ZH;
    }

    /** Turno do dia pela escala normal, sem contar as exceções. */
    public int turnoNormal(LocalDate d) {
        int pos = posicao(d);
        if (pos < trabalho && horarioDoDia(pos) >= 0) return turnoDoDia(pos);
        return NADA;
    }

    /** Turno do dia já contando as exceções. */
    public int turnoEm(LocalDate d) {
        Integer e = exc.get(d.toEpochDay());
        return e != null ? e : turnoNormal(d);
    }

    /** Horário do alarme naquele dia, ou -1 se não toca. */
    public int minutosEm(LocalDate d) {
        Integer e = exc.get(d.toEpochDay());
        if (e != null) return e == HA ? ha : e == ZH ? zh : -1;
        int pos = posicao(d);
        return pos < trabalho ? horarioDoDia(pos) : -1;
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
                int m = minutosEm(d);
                if (m >= 0) {
                    LocalDateTime t = d.atTime(m / 60, m % 60);
                    if (t.isAfter(depois)) return t;
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
        o.put("ha", ha);
        o.put("zh", zh);
        if (turnos != null) {
            JSONArray tn = new JSONArray();
            for (int t : turnos) tn.put(t);
            o.put("turnos", tn);
        }
        JSONObject ex = new JSONObject();
        for (java.util.Map.Entry<Long, Integer> e : exc.entrySet()) ex.put(String.valueOf(e.getKey()), e.getValue());
        o.put("exc", ex);
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
        a.ha = o.optInt("ha", 290);
        a.zh = o.optInt("zh", 1010);
        JSONArray tn = o.optJSONArray("turnos");
        if (tn != null) {
            a.turnos = new int[tn.length()];
            for (int i = 0; i < tn.length(); i++) a.turnos[i] = tn.getInt(i);
        }
        JSONObject ex = o.optJSONObject("exc");
        if (ex != null) {
            Iterator<String> it = ex.keys();
            while (it.hasNext()) {
                String k = it.next();
                try {
                    a.exc.put(Long.parseLong(k), ex.getInt(k));
                } catch (Exception ignored) { }
            }
        }
        a.diaMes = o.optInt("diaMes", 1);
        a.minutos = o.optInt("minutos", 540);
        return a;
    }
}
