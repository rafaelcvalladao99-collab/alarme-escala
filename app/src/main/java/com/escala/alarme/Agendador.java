package com.escala.alarme;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** Marca o próximo alarme no sistema do Android. Só existe um alarme marcado por vez. */
public class Agendador {

    static final int REQ_ESCALA = 1;
    static final int REQ_SONECA = 2;
    static final int REQ_TESTE = 3;

    /** Marca o próximo alarme da escala (a partir de agora). */
    public static void agendar(Context c) {
        agendarDepoisDe(c, LocalDateTime.now());
    }

    public static void agendarDepoisDe(Context c, LocalDateTime depoisDe) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = piAlarme(c, REQ_ESCALA);
        LocalDateTime prox = new Escala(c).proximo(depoisDe);
        if (prox == null) {
            am.cancel(pi);
            return;
        }
        long ms = prox.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        marcar(c, am, ms, pi);
    }

    /** Alarme avulso (soneca ou teste) daqui a alguns segundos. */
    public static void agendarAvulso(Context c, int req, long daquiMs) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        marcar(c, am, System.currentTimeMillis() + daquiMs, piAlarme(c, req));
    }

    public static boolean podeAgendar(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        return am.canScheduleExactAlarms();
    }

    private static void marcar(Context c, AlarmManager am, long ms, PendingIntent pi) {
        if (!podeAgendar(c)) return;
        Intent abrir = new Intent(c, MainActivity.class);
        PendingIntent mostrar = PendingIntent.getActivity(c, 0, abrir,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(ms, mostrar), pi);
        } catch (SecurityException e) {
            // Sem permissão de alarme exato: o app mostra o aviso na tela principal.
        }
    }

    static PendingIntent piAlarme(Context c, int req) {
        Intent i = new Intent(c, AlarmeReceiver.class);
        i.setAction(AlarmeReceiver.ACAO_TOCAR);
        i.putExtra("req", req);
        return PendingIntent.getBroadcast(c, req, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
