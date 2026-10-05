package com.escala.alarme;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.time.LocalDateTime;

/** Recebe a hora do alarme, os botões "Desligar" / "Soneca" e a atualização diária da contagem. */
public class AlarmeReceiver extends BroadcastReceiver {

    static final String ACAO_TOCAR = "com.escala.alarme.TOCAR";
    static final String ACAO_PARAR = "com.escala.alarme.PARAR";
    static final String ACAO_SONECA = "com.escala.alarme.SONECA";
    static final String ACAO_CONTAGEM = "com.escala.alarme.CONTAGEM";

    @Override
    public void onReceive(Context c, Intent intent) {
        String acao = intent.getAction();
        if (ACAO_PARAR.equals(acao)) {
            parar(c);
        } else if (ACAO_SONECA.equals(acao)) {
            soneca(c);
        } else if (ACAO_CONTAGEM.equals(acao)) {
            Contagem.atualizar(c);
            Contagem.agendarDiario(c);
        } else if (ACAO_TOCAR.equals(acao)) {
            int req = intent.getIntExtra("req", Agendador.REQ_ESCALA);
            if (req == Agendador.REQ_ESCALA) {
                // Já deixa marcado o próximo toque.
                Agendador.agendarDepoisDe(c, LocalDateTime.now().plusMinutes(1));
            }
            Intent s = new Intent(c, AlarmeService.class);
            String nome = intent.getStringExtra("nome");
            if (nome != null) s.putExtra("nome", nome);
            c.startForegroundService(s);
        }
    }

    static void parar(Context c) {
        c.stopService(new Intent(c, AlarmeService.class));
    }

    static void soneca(Context c) {
        int min = new Escala(c).sonecaMinutos();
        Agendador.agendarAvulso(c, Agendador.REQ_SONECA, min * 60_000L, AlarmeService.nomeAtual);
        parar(c);
    }
}
