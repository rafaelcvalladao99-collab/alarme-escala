package com.escala.alarme;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Tela de configurações: som, notificação de contagem e teste do alarme. */
public class ConfigActivity extends Activity {

    private static final int ESCOLHER_SOM = 2;

    private Escala escala;
    private TextView subSom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        escala = new Escala(this);

        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        raiz.setPadding(pad, pad, pad, Ui.dp(this, 32));

        ScrollView sv = new ScrollView(this);
        sv.setFitsSystemWindows(true);
        sv.addView(raiz);
        setContentView(sv);

        LinearLayout topo = new LinearLayout(this);
        topo.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton voltar = new ImageButton(this);
        Drawable seta = getDrawable(R.drawable.ic_voltar).mutate();
        seta.setTint(Ui.cor(this, R.color.ink));
        voltar.setImageDrawable(seta);
        voltar.setContentDescription("Voltar");
        voltar.setBackground(Ui.ondulado(Ui.forma(this, Ui.cor(this, R.color.card),
                Ui.cor(this, R.color.line), 14, 2)));
        voltar.setOnClickListener(v -> finish());
        topo.addView(voltar, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52)));
        TextView titulo = Ui.texto(this, "Configurações", 24, true, Ui.cor(this, R.color.ink));
        titulo.setPadding(Ui.dp(this, 14), 0, 0, 0);
        topo.addView(titulo);
        raiz.addView(topo, Ui.largura());

        raiz.addView(rotulo("Alarme"), Ui.largura(this, 24));
        LinearLayout som = linha("Som", "");
        subSom = (TextView) ((LinearLayout) som.getChildAt(0)).getChildAt(1);
        Button trocar = Ui.botao(this, "Trocar", Ui.CONTORNO);
        trocar.setMinHeight(Ui.dp(this, 48));
        trocar.setMinimumHeight(Ui.dp(this, 48));
        trocar.setMinWidth(0);
        trocar.setMinimumWidth(0);
        trocar.setOnClickListener(v -> escolherSom());
        som.addView(trocar);
        raiz.addView(caixa(som), Ui.largura(this, 6));

        raiz.addView(rotulo("Notificação"), Ui.largura(this, 24));
        LinearLayout notif = linha("Mostrar próximos alarmes", "Fica visível também na tela de bloqueio");
        notif.addView(Ui.interruptor(this, escala.mostrarContagem(), (b, v) -> {
            escala.setMostrarContagem(v);
            Contagem.atualizar(this);
        }));
        raiz.addView(caixa(notif), Ui.largura(this, 6));

        raiz.addView(rotulo("Teste"), Ui.largura(this, 24));
        Button testar = Ui.botao(this, "Testar alarme (toca em 10 segundos)", Ui.CONTORNO);
        testar.setOnClickListener(v -> {
            Agendador.agendarAvulso(this, Agendador.REQ_TESTE, 10_000L, "Teste");
            Toast.makeText(this, "Pode bloquear a tela. O alarme toca em 10 segundos.",
                    Toast.LENGTH_LONG).show();
        });
        raiz.addView(testar, Ui.largura(this, 6));

        raiz.addView(rotulo("Sobre"), Ui.largura(this, 24));
        raiz.addView(caixa(linha("Alarme da Escala",
                "Versão " + Atualizador.versaoInstalada(this))), Ui.largura(this, 6));

        atualizarSom();
    }

    private TextView rotulo(String t) {
        return Ui.texto(this, t, 16, true, Ui.cor(this, R.color.ink));
    }

    /** Linha com título e subtítulo à esquerda; o que vier depois é adicionado à direita. */
    private LinearLayout linha(String titulo, String sub) {
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout textos = new LinearLayout(this);
        textos.setOrientation(LinearLayout.VERTICAL);
        textos.addView(Ui.texto(this, titulo, 17, true, Ui.cor(this, R.color.ink)));
        TextView s = Ui.texto(this, sub, 14, false, Ui.cor(this, R.color.mute));
        if (sub.isEmpty()) s.setVisibility(View.VISIBLE);
        textos.addView(s);
        l.addView(textos, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return l;
    }

    private LinearLayout caixa(LinearLayout conteudo) {
        LinearLayout c = new LinearLayout(this);
        int p = Ui.dp(this, 14);
        c.setPadding(p, p, p, p);
        c.setBackground(Ui.forma(this, Ui.cor(this, R.color.card), Ui.cor(this, R.color.line), 20, 2));
        c.addView(conteudo, Ui.largura());
        return c;
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
        subSom.setText(nomeDoSom());
    }

    private String nomeDoSom() {
        String t = escala.toque();
        if (t == null) return "Padrão do celular";
        try {
            Ringtone r = RingtoneManager.getRingtone(this, Uri.parse(t));
            if (r != null) return r.getTitle(this);
        } catch (Exception ignored) { }
        return "Personalizado";
    }
}
