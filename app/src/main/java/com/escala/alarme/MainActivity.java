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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int PEDIR_NOTIF = 1;
    private static final int ESCOLHER_SOM = 2;
    private static final Locale BR = Locale.forLanguageTag("pt-BR");

    private Escala escala;
    private Switch swAtivo;
    private TextView txtInicio, txtHoje, txtProximos;
    private Button btnSom;
    private LinearLayout avisos, dias;
    private TextView txtVersao;
    private Atualizador.Info novaVersao;
    private boolean baixando = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        escala = new Escala(this);

        swAtivo = findViewById(R.id.swAtivo);
        txtInicio = findViewById(R.id.txtInicio);
        txtHoje = findViewById(R.id.txtHoje);
        txtProximos = findViewById(R.id.txtProximos);
        btnSom = findViewById(R.id.btnSom);
        avisos = findViewById(R.id.avisos);
        dias = findViewById(R.id.dias);

        swAtivo.setChecked(escala.ativo());
        swAtivo.setOnCheckedChangeListener((b, v) -> {
            escala.setAtivo(v);
            atualizar();
        });

        findViewById(R.id.btnInicio).setOnClickListener(v -> escolherInicio());
        btnSom.setOnClickListener(v -> escolherSom());
        findViewById(R.id.btnTestar).setOnClickListener(v -> {
            Agendador.agendarAvulso(this, Agendador.REQ_TESTE, 10_000L);
            Toast.makeText(this, "Pode bloquear a tela. O alarme toca em 10 segundos.",
                    Toast.LENGTH_LONG).show();
        });

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, PEDIR_NOTIF);
        }

        // Rodapé: versão instalada e botão de atualização.
        LinearLayout raiz = (LinearLayout) findViewById(R.id.btnTestar).getParent();
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
        montarDias();

        LocalDate ini = escala.inicio();
        if (ini == null) {
            txtInicio.setText("Ainda não escolhido");
            txtHoje.setText("Escolha a data de um 1º dia de trabalho para o alarme começar a funcionar.");
        } else {
            txtInicio.setText(ini.format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", BR)));
            int pos = escala.posicaoNoCiclo(LocalDate.now());
            String hoje = pos < Escala.DIAS_TRABALHO
                    ? "Hoje: dia " + (pos + 1) + " de trabalho"
                    : "Hoje: folga (" + (pos - Escala.DIAS_TRABALHO + 1) + "º de 6 dias)";
            txtHoje.setText(hoje);
        }

        btnSom.setText("Som: " + nomeDoSom());

        List<LocalDateTime> prox = escala.proximos(8);
        if (!escala.ativo()) {
            txtProximos.setText("Alarme desligado.");
        } else if (prox.isEmpty()) {
            txtProximos.setText("Nenhum alarme marcado.");
        } else {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("EEE, dd/MM 'às' HH:mm", BR);
            StringBuilder sb = new StringBuilder();
            for (LocalDateTime t : prox) {
                int pos = escala.posicaoNoCiclo(t.toLocalDate());
                if (sb.length() > 0) sb.append('\n');
                sb.append("•  ").append(t.format(f)).append("   (dia ").append(pos + 1).append(")");
            }
            txtProximos.setText(sb.toString());
        }
    }

    private void montarDias() {
        dias.removeAllViews();
        for (int i = 0; i < Escala.DIAS_TRABALHO; i++) {
            final int dia = i;
            LinearLayout linha = new LinearLayout(this);
            linha.setOrientation(LinearLayout.HORIZONTAL);
            linha.setGravity(Gravity.CENTER_VERTICAL);

            CheckBox cb = new CheckBox(this);
            cb.setText("Dia " + (i + 1));
            cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            cb.setChecked(escala.diaLigado(i));
            cb.setOnCheckedChangeListener((b, v) -> {
                escala.setDiaLigado(dia, v);
                atualizar();
            });
            linha.addView(cb, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            Button hora = new Button(this);
            hora.setText(String.format(Locale.ROOT, "%02d:%02d", escala.hora(i), escala.minuto(i)));
            hora.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            hora.setEnabled(escala.diaLigado(i));
            hora.setOnClickListener(v -> new TimePickerDialog(this, (tp, h, m) -> {
                escala.setHorario(dia, h, m);
                atualizar();
            }, escala.hora(dia), escala.minuto(dia), true).show());
            linha.addView(hora);

            dias.addView(linha);
        }
    }

    private void escolherInicio() {
        LocalDate d = escala.inicio() != null ? escala.inicio() : LocalDate.now();
        new DatePickerDialog(this, (dp, ano, mes, dia) -> {
            escala.setInicio(LocalDate.of(ano, mes + 1, dia));
            atualizar();
        }, d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()).show();
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
            atualizar();
        }
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
