package com.escala.alarme;

import android.app.Activity;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Tela de configurações: som, notificação de contagem e teste do alarme. */
public class ConfigActivity extends Activity {

    private static final int ESCOLHER_SOM = 2;

    private Escala escala;
    private Button btnSom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        escala = new Escala(this);

        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        raiz.setPadding(pad, pad, pad, pad);

        ScrollView sv = new ScrollView(this);
        sv.setFitsSystemWindows(true);
        sv.addView(raiz);
        setContentView(sv);

        TextView cabecalho = new TextView(this);
        cabecalho.setText("Configurações");
        cabecalho.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        cabecalho.setTypeface(null, android.graphics.Typeface.BOLD);
        raiz.addView(cabecalho);

        raiz.addView(titulo("Som"));
        btnSom = new Button(this);
        btnSom.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        btnSom.setOnClickListener(v -> escolherSom());
        raiz.addView(btnSom, larguraTotal());

        raiz.addView(titulo("Notificação"));
        Switch sw = new Switch(this);
        sw.setText("Mostrar quantos dias faltam para cada alarme");
        sw.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        sw.setPadding(0, dp(8), 0, dp(8));
        sw.setChecked(escala.mostrarContagem());
        sw.setOnCheckedChangeListener((b, v) -> {
            escala.setMostrarContagem(v);
            Contagem.atualizar(this);
        });
        raiz.addView(sw, larguraTotal());

        raiz.addView(titulo("Teste"));
        Button testar = new Button(this);
        testar.setText("Testar alarme (toca em 10 segundos)");
        testar.setOnClickListener(v -> {
            Agendador.agendarAvulso(this, Agendador.REQ_TESTE, 10_000L, "Teste");
            Toast.makeText(this, "Pode bloquear a tela. O alarme toca em 10 segundos.",
                    Toast.LENGTH_LONG).show();
        });
        raiz.addView(testar, larguraTotal());

        atualizarSom();
    }

    private void escolherSom() {
        Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Som do alarme");
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        String atual = escala.toque();
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                atual != null ? Uri.parse(atual) : Settings.System.DEFAULT_ALARM_ALERT_URI);
        try {
            startActivityForResult(i, ESCOLHER_SOM);
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível abrir a lista de sons.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ESCOLHER_SOM && resultCode == RESULT_OK && data != null) {
            Uri u = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            boolean padrao = u == null || u.equals(Settings.System.DEFAULT_ALARM_ALERT_URI);
            escala.setToque(padrao ? null : u.toString());
            atualizarSom();
        }
    }

    private void atualizarSom() {
        btnSom.setText("Som: " + nomeDoSom());
    }

    private String nomeDoSom() {
        String t = escala.toque();
        if (t == null) return "padrão do celular";
        try {
            Ringtone r = RingtoneManager.getRingtone(this, Uri.parse(t));
            if (r != null) return r.getTitle(this);
        } catch (Exception ignored) { }
        return "personalizado";
    }

    private TextView titulo(String texto) {
        TextView t = new TextView(this, null, 0, R.style.Titulo);
        t.setText(texto);
        LinearLayout.LayoutParams lp = larguraTotal();
        lp.topMargin = dp(24);
        lp.bottomMargin = dp(4);
        t.setLayoutParams(lp);
        return t;
    }

    private LinearLayout.LayoutParams larguraTotal() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}
