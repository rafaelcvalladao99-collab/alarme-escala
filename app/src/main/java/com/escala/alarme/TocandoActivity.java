package com.escala.alarme;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Tela cheia que aparece quando o alarme toca, inclusive com o celular bloqueado. */
public class TocandoActivity extends Activity {

    private static WeakReference<TocandoActivity> aberta = new WeakReference<>(null);

    static void fecharSeAberta() {
        TocandoActivity a = aberta.get();
        if (a != null && !a.isFinishing()) a.finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        aberta = new WeakReference<>(this);

        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_tocando);

        TextView hora = findViewById(R.id.hora);
        TextView info = findViewById(R.id.info);
        Button desligar = findViewById(R.id.btnDesligar);
        Button soneca = findViewById(R.id.btnSoneca);

        hora.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        String nome = getIntent().getStringExtra("nome");
        info.setText(nome != null ? nome : "Alarme");
        soneca.setText("Soneca (" + new Escala(this).sonecaMinutos() + " min)");

        desligar.setOnClickListener(v -> {
            AlarmeReceiver.parar(this);
            finish();
        });
        soneca.setOnClickListener(v -> {
            AlarmeReceiver.soneca(this);
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        if (aberta.get() == this) aberta = new WeakReference<>(null);
        super.onDestroy();
    }
}
