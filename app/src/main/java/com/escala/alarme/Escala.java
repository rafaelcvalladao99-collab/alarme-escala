package com.escala.alarme;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Guarda as configurações e faz a conta da escala:
 * ciclo de 10 dias, sendo os 4 primeiros de trabalho e os 6 seguintes de folga.
 */
public class Escala {

    public static final int DIAS_CICLO = 10;
    public static final int DIAS_TRABALHO = 4;

    private final SharedPreferences p;

    public Escala(Context c) {
        p = c.getApplicationContext().getSharedPreferences("escala", Context.MODE_PRIVATE);
    }

    // ---------- configurações ----------

    public boolean ativo() { return p.getBoolean("ativo", true); }
    public void setAtivo(boolean v) { p.edit().putBoolean("ativo", v).apply(); }

    /** Uma data que foi (ou será) o 1º dia de trabalho de algum ciclo. */
    public LocalDate inicio() {
        long e = p.getLong("inicio", Long.MIN_VALUE);
        return e == Long.MIN_VALUE ? null : LocalDate.ofEpochDay(e);
    }
    public void setInicio(LocalDate d) { p.edit().putLong("inicio", d.toEpochDay()).apply(); }

    /** dia: 0 a 3 (Dia 1 a Dia 4 de trabalho). */
    public int hora(int dia) { return p.getInt("hora" + dia, 6); }
    public int minuto(int dia) { return p.getInt("min" + dia, 0); }
    public void setHorario(int dia, int h, int m) {
        p.edit().putInt("hora" + dia, h).putInt("min" + dia, m).apply();
    }

    public boolean diaLigado(int dia) { return p.getBoolean("ligado" + dia, true); }
    public void setDiaLigado(int dia, boolean v) { p.edit().putBoolean("ligado" + dia, v).apply(); }

    /** Som escolhido (texto de um Uri) ou null para o som de alarme padrão do celular. */
    public String toque() { return p.getString("toque", null); }
    public void setToque(String uri) { p.edit().putString("toque", uri).apply(); }

    public int sonecaMinutos() { return p.getInt("soneca", 10); }

    // ---------- conta da escala ----------

    /** Posição da data no ciclo: 0 a 9 (0-3 = trabalho, 4-9 = folga). -1 se não configurado. */
    public int posicaoNoCiclo(LocalDate d) {
        LocalDate ini = inicio();
        if (ini == null) return -1;
        long n = ChronoUnit.DAYS.between(ini, d);
        return (int) Math.floorMod(n, (long) DIAS_CICLO);
    }

    public boolean ehTrabalho(LocalDate d) {
        int pos = posicaoNoCiclo(d);
        return pos >= 0 && pos < DIAS_TRABALHO;
    }

    /** Próximo horário de alarme estritamente depois de "depoisDe", ou null se não houver. */
    public LocalDateTime proximo(LocalDateTime depoisDe) {
        if (!ativo() || inicio() == null) return null;
        LocalDate hoje = depoisDe.toLocalDate();
        for (int i = 0; i <= DIAS_CICLO + 1; i++) {
            LocalDate d = hoje.plusDays(i);
            int pos = posicaoNoCiclo(d);
            if (pos < DIAS_TRABALHO && diaLigado(pos)) {
                LocalDateTime t = d.atTime(hora(pos), minuto(pos));
                if (t.isAfter(depoisDe)) return t;
            }
        }
        return null;
    }

    public List<LocalDateTime> proximos(int quantos) {
        List<LocalDateTime> lista = new ArrayList<>();
        LocalDateTime t = LocalDateTime.now();
        for (int i = 0; i < quantos; i++) {
            t = proximo(t);
            if (t == null) break;
            lista.add(t);
        }
        return lista;
    }
}
