package com.escala.alarme;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.time.LocalDateTime;

/** Recebe a hora do alarme e os botões "Desligar" / "Soneca" da notificação. */
public class AlarmeReceiver extends BroadcastReceiver {

    static final String ACAO_TOCAR = "com.escala.alarme.TOCAR";
    static final String ACAO_PARAR = "com.escala.alarme.PARAR";
    static final String ACAO_SONECA = "com.escala.alarme.SONECA";

    @Override
    public void onReceive(Context c, Intent intent) {
        String acao = intent.getAction();
        if (ACAO_PARAR.equals(acao)) {
            parar(c);
        } else if (ACAO_SONECA.equals(acao)) {
            soneca(c);
        } else if (ACAO_TOCAR.equals(acao)) {
            int req = intent.getIntExtra("req", Agendador.REQ_ESCALA);
            if (req == Agendador.REQ_ESCALA) {
                // Já deixa marcado o próximo dia de trabalho.
                Agendador.agendarDepoisDe(c, LocalDateTime.now().plusMinutes(1));
            }
            Intent s = new Intent(c, AlarmeService.class);
            c.startForegroundService(s);
        }
    }

    static void parar(Context c) {
        c.stopService(new Intent(c, AlarmeService.class));
    }

    static void soneca(Context c) {
        int min = new Escala(c).sonecaMinutos();
        Agendador.agendarAvulso(c, Agendador.REQ_SONECA, min * 60_000L);
        parar(c);
    }
}
