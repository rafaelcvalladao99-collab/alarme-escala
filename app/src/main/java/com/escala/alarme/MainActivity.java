package com.escala.alarme;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int PEDIR_NOTIF = 1;
    private static final Locale BR = Locale.forLanguageTag("pt-BR");

    private Escala escala;
    private LinearLayout avisos, lista;
    private TextView txtVersao, subtitulo;
    private Atualizador.Info novaVersao;
    private boolean baixando = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        escala = new Escala(this);

        avisos = findViewById(R.id.avisos);
        lista = findViewById(R.id.lista);
        subtitulo = findViewById(R.id.subtitulo);
        subtitulo.setTextColor(Ui.cor(this, R.color.mute));
        ((TextView) findViewById(R.id.titulo)).setTextColor(Ui.cor(this, R.color.ink));

        ImageButton config = findViewById(R.id.btnConfig);
        Drawable engrenagem = getDrawable(R.drawable.ic_engrenagem).mutate();
        engrenagem.setTint(Ui.cor(this, R.color.ink));
        config.setImageDrawable(engrenagem);
        config.setBackground(Ui.ondulado(Ui.forma(this, Ui.cor(this, R.color.card),
                Ui.cor(this, R.color.line), 14, 2)));
        config.setOnClickListener(v -> startActivity(new Intent(this, ConfigActivity.class)));

        Button novo = findViewById(R.id.btnNovo);
        novo.setAllCaps(false);
        novo.setStateListAnimator(null);
        novo.setTextSize(18);
        novo.setTypeface(null, android.graphics.Typeface.BOLD);
        novo.setTextColor(Ui.cor(this, R.color.on_primary));
        novo.setBackground(Ui.ondulado(Ui.forma(this, Ui.cor(this, R.color.primary), 0, 20, 0)));
        novo.setOnClickListener(v -> startActivity(new Intent(this, EditarActivity.class)));

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, PEDIR_NOTIF);
        }

        // Rodapé: versão instalada e botão de atualização.
        LinearLayout conteudo = findViewById(R.id.conteudo);
        txtVersao = Ui.texto(this, "", 14, false, Ui.cor(this, R.color.mute));
        txtVersao.setGravity(Gravity.CENTER);
        txtVersao.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 4));
        conteudo.addView(txtVersao, Ui.largura());
        Button btnVerificar = Ui.botao(this, "Verificar atualização", Ui.CONTORNO);
        btnVerificar.setOnClickListener(v -> verificarAtualizacao(true));
        conteudo.addView(btnVerificar, Ui.largura());
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
        String data = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd/MM", BR));
        subtitulo.setText(data.substring(0, 1).toUpperCase(BR) + data.substring(1));
        montarAvisos();
        montarLista();
    }

    private void montarLista() {
        lista.removeAllViews();
        List<Alarme> todos = escala.alarmes();
        if (todos.isEmpty()) {
            TextView vazio = Ui.texto(this,
                    "Nenhum alarme ainda. Toque em \"+ Novo alarme\" para criar o primeiro.",
                    16, false, Ui.cor(this, R.color.mute));
            lista.addView(vazio, Ui.largura());
            return;
        }
        for (Alarme a : todos) lista.addView(cartao(a));
    }

    // ---------- cartão de cada alarme ----------

    private View cartao(Alarme a) {
        boolean ciclo = a.tipo == Alarme.CICLO;
        int corTipo = Ui.cor(this, ciclo ? R.color.ciclo : R.color.mensal);
        int corSoft = Ui.cor(this, ciclo ? R.color.ciclo_soft : R.color.mensal_soft);
        int ink = Ui.cor(this, R.color.ink);
        int mute = Ui.cor(this, R.color.mute);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        card.setPadding(p, p, p, p);
        card.setBackground(Ui.ondulado(Ui.forma(this, Ui.cor(this, R.color.card),
                Ui.cor(this, R.color.line), 20, 2)));
        card.setAlpha(a.ativo ? 1f : 0.62f);

        // linha de cima: nome + interruptor
        LinearLayout topo = new LinearLayout(this);
        topo.setGravity(Gravity.CENTER_VERTICAL);
        topo.addView(Ui.texto(this, a.nome, 20, true, ink),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = Ui.interruptor(this, a.ativo, (b, v) -> {
            a.ativo = v;
            escala.salvarUm(a);
            atualizar();
        });
        topo.addView(sw);
        card.addView(topo, Ui.largura());

        LocalDateTime prox = a.proximo(LocalDateTime.now());
        TextView hora = Ui.texto(this, prox != null ? Alarme.hhmm(prox.getHour() * 60 + prox.getMinute()) : "—:—",
                44, true, ink);
        hora.setFontFeatureSettings("tnum");
        card.addView(hora, Ui.largura(this, 4));

        LinearLayout quando = new LinearLayout(this);
        quando.setGravity(Gravity.CENTER_VERTICAL);
        if (!a.ativo) {
            quando.addView(Ui.texto(this, "Desligado", 15, false, mute));
        } else if (prox == null) {
            quando.addView(Ui.texto(this, "Sem próximo alarme", 15, false, mute));
        } else {
            long dias = ChronoUnit.DAYS.between(LocalDate.now(), prox.toLocalDate());
            String rotulo = dias <= 0 ? "hoje" : dias == 1 ? "amanhã" : "em " + dias + " dias";
            TextView chip = Ui.texto(this, rotulo, 13, true, Ui.cor(this, R.color.bg));
            chip.setBackground(Ui.forma(this, ink, 0, 8, 0));
            chip.setPadding(Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 8), Ui.dp(this, 2));
            quando.addView(chip);
            String data = prox.format(DateTimeFormatter.ofPattern("EEE, dd/MM", BR));
            if (!ciclo) data += " · todo dia " + a.diaMes;
            TextView dt = Ui.texto(this, data, 15, false, mute);
            dt.setPadding(Ui.dp(this, 8), 0, 0, 0);
            quando.addView(dt);
        }
        card.addView(quando, Ui.largura());

        if (ciclo) card.addView(faixa(a), Ui.largura(this, 12));

        // rótulo do tipo, lá embaixo
        TextView tag = Ui.texto(this, ciclo ? "Escala de trabalho" : "Todo mês", 13, true, corTipo);
        Drawable icone = getDrawable(ciclo ? R.drawable.ic_ciclo : R.drawable.ic_calendario).mutate();
        icone.setTint(corTipo);
        int d16 = Ui.dp(this, 16);
        icone.setBounds(0, 0, d16, d16);
        tag.setCompoundDrawables(icone, null, null, null);
        tag.setCompoundDrawablePadding(Ui.dp(this, 6));
        tag.setBackground(Ui.forma(this, corSoft, 0, 999, 0));
        tag.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4));
        LinearLayout.LayoutParams ltag = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ltag.topMargin = Ui.dp(this, 12);
        card.addView(tag, ltag);

        card.setOnClickListener(v -> startActivity(
                new Intent(this, EditarActivity.class).putExtra("id", a.id)));

        LinearLayout.LayoutParams lp = Ui.largura();
        lp.bottomMargin = Ui.dp(this, 12);
        card.setLayoutParams(lp);
        return card;
    }

    /** Faixa com um quadradinho por dia do ciclo: cheio = trabalho, contorno forte = hoje. */
    private View faixa(Alarme a) {
        int hoje = a.posicao(LocalDate.now());
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        if (a.ciclo <= 14) {
            LinearLayout fila = new LinearLayout(this);
            LocalDate inicioCiclo = LocalDate.now().minusDays(hoje);
            for (int i = 0; i < a.ciclo; i++) {
                LocalDate dia = inicioCiclo.plusDays(i);
                int turno = a.turnoEm(dia);
                int sem = dia.getDayOfWeek().getValue();
                View cel = Ui.celula(this, String.valueOf(i + 1), Ui.SEMANA[sem - 1], turno != Alarme.NADA,
                        i == hoje, sem >= 6, false, Ui.nomeTurno(turno), 30);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMargins(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
                fila.addView(cel, lp);
            }
            col.addView(fila, Ui.largura());
        }

        String esquerda = "Dia " + (hoje + 1) + " de " + a.ciclo;
        String direita;
        if (hoje < a.trabalho) {
            int resta = a.trabalho - 1 - hoje;
            direita = resta == 0 ? "Último dia de trabalho" : "Trabalha mais " + resta + (resta == 1 ? " dia" : " dias");
        } else {
            int volta = a.ciclo - hoje;
            direita = "Folga: volta em " + volta + (volta == 1 ? " dia" : " dias");
        }
        LinearLayout legenda = new LinearLayout(this);
        legenda.addView(Ui.texto(this, esquerda, 13, false, Ui.cor(this, R.color.mute)),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        legenda.addView(Ui.texto(this, direita, 13, false, Ui.cor(this, R.color.mute)));
        col.addView(legenda, Ui.largura(this, 6));
        return col;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        atualizar();
    }

    // ---------- avisos ----------

    private void montarAvisos() {
        avisos.removeAllViews();
        String pkg = "package:" + getPackageName();

        if (novaVersao != null) {
            aviso("Tem uma versão nova do app (" + novaVersao.versao + ").",
                    "Atualizar agora", this::iniciarAtualizacao, true);
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
                    }, false);
        }

        if (!Agendador.podeAgendar(this) && Build.VERSION.SDK_INT >= 31) {
            aviso("O app precisa de permissão para \"Alarmes e lembretes\". Sem ela o alarme NÃO toca.",
                    "Dar permissão", () -> abrir(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse(pkg))), false);
        }

        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (!nm.canUseFullScreenIntent()) {
                aviso("Permita \"notificações em tela cheia\" para o alarme aparecer com o celular bloqueado.",
                        "Permitir tela cheia", () -> abrir(new Intent(
                                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse(pkg))), false);
            }
        }

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
            aviso("Recomendado: tirar o app da economia de bateria, para o celular não bloquear o alarme.",
                    "Liberar bateria", () -> abrir(new Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse(pkg))), false);
        }
    }

    private void aviso(String texto, String botao, Runnable acao, boolean novidade) {
        LinearLayout caixa = new LinearLayout(this);
        caixa.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 14);
        caixa.setPadding(p, p, p, p);
        caixa.setBackground(Ui.forma(this,
                Ui.cor(this, novidade ? R.color.ciclo_soft : R.color.mensal_soft), 0, 16, 0));

        caixa.addView(Ui.texto(this, texto, 15, false, Ui.cor(this, R.color.ink)), Ui.largura());

        Button b = Ui.botao(this, botao, novidade ? Ui.PRIMARIO : Ui.CONTORNO);
        b.setOnClickListener(v -> acao.run());
        caixa.addView(b, Ui.largura(this, 10));

        LinearLayout.LayoutParams lp = Ui.largura();
        lp.bottomMargin = Ui.dp(this, 10);
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
}
