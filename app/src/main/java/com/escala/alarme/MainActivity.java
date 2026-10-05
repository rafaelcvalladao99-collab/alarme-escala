package com.escala.alarme;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int PEDIR_NOTIF = 1;
    private static final Locale BR = Locale.forLanguageTag("pt-BR");

    private Escala escala;
    private LinearLayout avisos, lista;
    private TextView txtVersao;
    private Atualizador.Info novaVersao;
    private boolean baixando = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        escala = new Escala(this);

        avisos = findViewById(R.id.avisos);
        lista = findViewById(R.id.lista);

        findViewById(R.id.btnNovo).setOnClickListener(v ->
                startActivity(new Intent(this, EditarActivity.class)));
        findViewById(R.id.btnConfig).setOnClickListener(v ->
                startActivity(new Intent(this, ConfigActivity.class)));

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, PEDIR_NOTIF);
        }

        // Rodapé: versão instalada e botão de atualização.
        LinearLayout raiz = (LinearLayout) findViewById(R.id.btnNovo).getParent();
        txtVersao = new TextView(this);
        txtVersao.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        txtVersao.setAlpha(0.7f);
        txtVersao.setPadding(0, dp(20), 0, dp(4));
        raiz.addView(txtVersao);
        Button btnVerificar = new Button(this);
        btnVerificar.setText("Verificar atualização");
        btnVerificar.setOnClickListener(v -> verificarAtualizacao(true));
        raiz.addView(btnVerificar);
        txtVersao.setText("Versão " + Atualizador.versaoInstalada(this));
        verificarAtualizacao(false);
    }

    private void verificarAtualizacao(boolean manual) {
        String base = "Versão " + Atualizador.versaoInstalada(this);
        if (manual) txtVersao.setText(base + " — verificando...");
        Atualizador.verificar(this, (nova, erro) -> {
            if (isDestroyed()) return;
            novaVersao = nova;
            if (nova != null) {
                txtVersao.setText(base + " — versão " + nova.versao + " disponível");
            } else if (erro != null) {
                txtVersao.setText(manual ? base + " — não foi possível verificar (" + erro + ")" : base);
            } else {
                txtVersao.setText(base + " — você está na última versão");
            }
            montarAvisos();
        });
    }

    private void iniciarAtualizacao() {
        if (baixando || novaVersao == null) return;
        if (!getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(this, "Permita instalar apps por aqui e depois toque em Atualizar de novo.",
                    Toast.LENGTH_LONG).show();
            abrir(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        baixando = true;
        Atualizador.baixarEInstalar(this, novaVersao, (texto, terminou) -> {
            if (isDestroyed()) return;
            txtVersao.setText(texto);
            if (terminou) baixando = false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        atualizar();
    }

    /** Redesenha a tela e remarca o próximo alarme. */
    private void atualizar() {
        Agendador.agendar(this);
        montarAvisos();
        montarLista();
    }

    private void montarLista() {
        lista.removeAllViews();
        List<Alarme> todos = escala.alarmes();
        if (todos.isEmpty()) {
            TextView vazio = new TextView(this);
            vazio.setText("Nenhum alarme ainda. Toque em \"+ Novo alarme\" para criar o primeiro.");
            vazio.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            vazio.setAlpha(0.7f);
            lista.addView(vazio);
            return;
        }
        for (Alarme a : todos) lista.addView(cartao(a));
    }

    private View cartao(Alarme a) {
        LinearLayout linha = new LinearLayout(this);
        linha.setOrientation(LinearLayout.HORIZONTAL);
        linha.setGravity(Gravity.CENTER_VERTICAL);
        int p = dp(14);
        linha.setPadding(p, p, p, p);
        GradientDrawable fundo = new GradientDrawable();
        fundo.setColor(Color.parseColor("#22808080"));
        fundo.setCornerRadius(dp(12));
        linha.setBackground(fundo);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView nome = new TextView(this);
        nome.setText(a.nome);
        nome.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        nome.setTypeface(null, Typeface.BOLD);
        col.addView(nome);

        TextView resumo = new TextView(this);
        resumo.setText(a.resumo());
        resumo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        resumo.setAlpha(0.7f);
        col.addView(resumo);

        LocalDateTime prox = a.proximo(LocalDateTime.now());
        TextView proximo = new TextView(this);
        if (!a.ativo) proximo.setText("Desligado");
        else if (prox == null) proximo.setText("Sem próximo alarme");
        else proximo.setText("Próximo: " + Contagem.quando(prox));
        proximo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        proximo.setPadding(0, dp(4), 0, 0);
        col.addView(proximo);

        linha.addView(col, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(this);
        sw.setChecked(a.ativo);
        sw.setOnCheckedChangeListener((b, v) -> {
            a.ativo = v;
            escala.salvarUm(a);
            atualizar();
        });
        linha.addView(sw);

        linha.setOnClickListener(v -> startActivity(
                new Intent(this, EditarActivity.class).putExtra("id", a.id)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        linha.setLayoutParams(lp);
        return linha;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        atualizar();
    }

    // ---------- avisos de permissão ----------

    private void montarAvisos() {
        avisos.removeAllViews();
        String pkg = "package:" + getPackageName();

        if (novaVersao != null) {
            aviso("Tem uma versão nova do app (" + novaVersao.versao + ").",
                    "Atualizar agora", this::iniciarAtualizacao);
        }

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            aviso("Sem permissão de notificação, o alarme pode não aparecer na tela.",
                    "Permitir notificações", () -> {
                        if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, PEDIR_NOTIF);
                        } else {
                            abrir(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                        }
                    });
        }

        if (!Agendador.podeAgendar(this) && Build.VERSION.SDK_INT >= 31) {
            aviso("O app precisa de permissão para \"Alarmes e lembretes\". Sem ela o alarme NÃO toca.",
                    "Dar permissão", () -> abrir(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse(pkg))));
        }

        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (!nm.canUseFullScreenIntent()) {
                aviso("Permita \"notificações em tela cheia\" para o alarme aparecer com o celular bloqueado.",
                        "Permitir tela cheia", () -> abrir(new Intent(
                                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse(pkg))));
            }
        }

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
            aviso("Recomendado: tirar o app da economia de bateria, para o celular não bloquear o alarme.",
                    "Liberar bateria", () -> abrir(new Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse(pkg))));
        }
    }

    private void aviso(String texto, String botao, Runnable acao) {
        LinearLayout caixa = new LinearLayout(this);
        caixa.setOrientation(LinearLayout.VERTICAL);
        int p = dp(12);
        caixa.setPadding(p, p, p, p);
        GradientDrawable fundo = new GradientDrawable();
        fundo.setColor(Color.parseColor("#33FF9800"));
        fundo.setCornerRadius(dp(12));
        caixa.setBackground(fundo);

        TextView t = new TextView(this);
        t.setText(texto);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        caixa.addView(t);

        Button b = new Button(this);
        b.setText(botao);
        b.setOnClickListener(v -> acao.run());
        caixa.addView(b);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        avisos.addView(caixa, lp);
    }

    private void abrir(Intent i) {
        try {
            startActivity(i);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) { }
        }
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}
