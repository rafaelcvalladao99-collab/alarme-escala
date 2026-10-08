package com.escala.alarme;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Tela para criar ou editar um alarme. */
public class EditarActivity extends Activity {

    private static final Locale BR = Locale.forLanguageTag("pt-BR");

    private Escala escala;
    private Alarme a;
    private boolean novo;

    private EditText edNome;
    private Ui.Passo pCiclo, pTrabalho, pDia;
    private Button btnCiclo, btnMensal, btnInicio, btnHoraMensal;
    private LinearLayout boxCiclo, boxMensal, boxHorarios;

    private LocalDate dataInicio;
    private int minutosMensal;
    private int[] hs = new int[0];
    private boolean[] lig = new boolean[0];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        escala = new Escala(this);
        long id = getIntent().getLongExtra("id", 0L);
        Alarme existente = id != 0L ? escala.buscar(id) : null;
        novo = existente == null;
        a = novo ? Alarme.novo() : existente;
        construirTela();
        preencher();
    }

    // ---------- montagem da tela ----------

    private void construirTela() {
        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        raiz.setPadding(pad, pad, pad, Ui.dp(this, 32));

        ScrollView sv = new ScrollView(this);
        sv.setFitsSystemWindows(true);
        sv.addView(raiz);
        setContentView(sv);

        // cabeçalho
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
        TextView titulo = Ui.texto(this, novo ? "Novo alarme" : "Editar alarme", 24, true,
                Ui.cor(this, R.color.ink));
        titulo.setPadding(Ui.dp(this, 14), 0, 0, 0);
        topo.addView(titulo);
        raiz.addView(topo, Ui.largura());

        // nome
        raiz.addView(rotulo("Nome"), Ui.largura(this, 20));
        edNome = new EditText(this);
        edNome.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        edNome.setSingleLine(true);
        edNome.setHint("Ex.: Trabalho, Aluguel");
        edNome.setHintTextColor(Ui.cor(this, R.color.mute));
        edNome.setTextColor(Ui.cor(this, R.color.ink));
        edNome.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        edNome.setMinHeight(Ui.dp(this, 56));
        edNome.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        edNome.setBackground(Ui.forma(this, Ui.cor(this, R.color.card),
                Ui.cor(this, R.color.line), 14, 2));
        raiz.addView(edNome, Ui.largura(this, 6));

        // tipo
        raiz.addView(rotulo("Como repete"), Ui.largura(this, 20));
        LinearLayout tipos = new LinearLayout(this);
        btnCiclo = botaoTipo("Ciclo de dias", R.drawable.ic_ciclo);
        btnMensal = botaoTipo("Todo mês", R.drawable.ic_calendario);
        btnCiclo.setOnClickListener(v -> escolherTipo(Alarme.CICLO));
        btnMensal.setOnClickListener(v -> escolherTipo(Alarme.MENSAL));
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, Ui.dp(this, 64), 1f);
        l1.rightMargin = Ui.dp(this, 4);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, Ui.dp(this, 64), 1f);
        l2.leftMargin = Ui.dp(this, 4);
        tipos.addView(btnCiclo, l1);
        tipos.addView(btnMensal, l2);
        raiz.addView(tipos, Ui.largura(this, 6));

        // --- opções do ciclo ---
        boxCiclo = new LinearLayout(this);
        boxCiclo.setOrientation(LinearLayout.VERTICAL);
        boxCiclo.addView(rotulo("Duração do ciclo (dias)"), Ui.largura(this, 20));
        pCiclo = new Ui.Passo(this, 2, 365, v -> {
            pTrabalho.max = Math.min(31, v);
            if (pTrabalho.valor(1) > pTrabalho.max) pTrabalho.definir(pTrabalho.max);
        });
        boxCiclo.addView(pCiclo, Ui.largura(this, 6));
        boxCiclo.addView(rotulo("Dias de trabalho no começo do ciclo"), Ui.largura(this, 20));
        pTrabalho = new Ui.Passo(this, 1, 31, v -> {
            ajustarDias(v);
            montarHorarios();
        });
        boxCiclo.addView(pTrabalho, Ui.largura(this, 6));
        boxCiclo.addView(rotulo("1º dia de trabalho de um ciclo"), Ui.largura(this, 20));
        btnInicio = Ui.botao(this, "", Ui.CONTORNO);
        btnInicio.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        btnInicio.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        btnInicio.setOnClickListener(v -> escolherInicio());
        boxCiclo.addView(btnInicio, Ui.largura(this, 6));
        boxCiclo.addView(rotulo("Horário de cada dia"), Ui.largura(this, 20));
        boxHorarios = new LinearLayout(this);
        boxHorarios.setOrientation(LinearLayout.VERTICAL);
        boxHorarios.setPadding(Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 4));
        boxHorarios.setBackground(Ui.forma(this, Ui.cor(this, R.color.card),
                Ui.cor(this, R.color.line), 20, 2));
        boxCiclo.addView(boxHorarios, Ui.largura(this, 6));
        raiz.addView(boxCiclo, Ui.largura());

        // --- opções mensais ---
        boxMensal = new LinearLayout(this);
        boxMensal.setOrientation(LinearLayout.VERTICAL);
        boxMensal.addView(rotulo("Dia do mês (1 a 31)"), Ui.largura(this, 20));
        pDia = new Ui.Passo(this, 1, 31, v -> { });
        boxMensal.addView(pDia, Ui.largura(this, 6));
        boxMensal.addView(Ui.texto(this,
                "Se o mês não tiver esse dia (ex.: 31 em abril), toca no último dia do mês.",
                14, false, Ui.cor(this, R.color.mute)), Ui.largura(this, 8));
        boxMensal.addView(rotulo("Horário"), Ui.largura(this, 20));
        btnHoraMensal = Ui.botao(this, "", Ui.CONTORNO);
        btnHoraMensal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        btnHoraMensal.setMinHeight(Ui.dp(this, 64));
        btnHoraMensal.setOnClickListener(v -> new TimePickerDialog(this, (tp, h, m) -> {
            minutosMensal = h * 60 + m;
            btnHoraMensal.setText(Alarme.hhmm(minutosMensal));
        }, minutosMensal / 60, minutosMensal % 60, true).show());
        boxMensal.addView(btnHoraMensal, Ui.largura(this, 6));
        raiz.addView(boxMensal, Ui.largura());

        // --- botões ---
        Button salvar = Ui.botao(this, "Salvar", Ui.PRIMARIO);
        salvar.setOnClickListener(v -> salvar());
        raiz.addView(salvar, Ui.largura(this, 28));

        if (!novo) {
            Button excluir = Ui.botao(this, "Excluir alarme", Ui.PERIGO);
            excluir.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setMessage("Excluir o alarme \"" + a.nome + "\"?")
                    .setPositiveButton("Excluir", (d, w) -> {
                        escala.remover(a.id);
                        Agendador.agendar(this);
                        finish();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show());
            raiz.addView(excluir, Ui.largura(this, 8));
        }

        Button cancelar = Ui.botao(this, "Cancelar", Ui.CONTORNO);
        cancelar.setOnClickListener(v -> finish());
        raiz.addView(cancelar, Ui.largura(this, 8));
    }

    private Button botaoTipo(String texto, int icone) {
        Button b = Ui.botao(this, texto, Ui.CONTORNO);
        Drawable d = getDrawable(icone).mutate();
        d.setTint(Ui.cor(this, R.color.ink));
        int s = Ui.dp(this, 22);
        d.setBounds(0, 0, s, s);
        b.setCompoundDrawables(d, null, null, null);
        b.setCompoundDrawablePadding(Ui.dp(this, 8));
        b.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        return b;
    }

    private void escolherTipo(int tipo) {
        a.tipo = tipo;
        mostrarTipo();
    }

    private void mostrarTipo() {
        boolean mensal = a.tipo == Alarme.MENSAL;
        boxCiclo.setVisibility(mensal ? View.GONE : View.VISIBLE);
        boxMensal.setVisibility(mensal ? View.VISIBLE : View.GONE);
        marcar(btnCiclo, !mensal);
        marcar(btnMensal, mensal);
    }

    private void marcar(Button b, boolean ligado) {
        int borda = Ui.cor(this, ligado ? R.color.primary : R.color.line);
        b.setBackground(Ui.ondulado(Ui.forma(this, Ui.cor(this, R.color.card), borda, 16,
                ligado ? 3 : 2)));
    }

    private void preencher() {
        edNome.setText(novo ? "" : a.nome);

        hs = new int[a.trabalho];
        lig = new boolean[a.trabalho];
        for (int i = 0; i < a.trabalho; i++) {
            int m = a.horarioDoDia(i);
            lig[i] = m >= 0;
            hs[i] = m >= 0 ? m : 360;
        }
        pCiclo.definir(a.ciclo);
        pTrabalho.definir(a.trabalho);

        dataInicio = LocalDate.ofEpochDay(a.inicio);
        atualizarTextoInicio();

        pDia.definir(a.diaMes);
        minutosMensal = a.minutos;
        btnHoraMensal.setText(Alarme.hhmm(minutosMensal));

        montarHorarios();
        mostrarTipo();
    }

    // ---------- horários do ciclo ----------

    private void ajustarDias(int n) {
        int[] nh = new int[n];
        boolean[] nl = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (i < hs.length) {
                nh[i] = hs[i];
                nl[i] = lig[i];
            } else {
                nh[i] = hs.length > 0 ? hs[hs.length - 1] : 360;
                nl[i] = true;
            }
        }
        hs = nh;
        lig = nl;
    }

    private void montarHorarios() {
        if (boxHorarios == null || dataInicio == null) return;
        boxHorarios.removeAllViews();
        for (int i = 0; i < hs.length; i++) {
            final int dia = i;
            LinearLayout linha = new LinearLayout(this);
            linha.setGravity(Gravity.CENTER_VERTICAL);
            linha.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));

            LinearLayout nomes = new LinearLayout(this);
            nomes.setOrientation(LinearLayout.VERTICAL);
            nomes.addView(Ui.texto(this, "Dia " + (i + 1), 16, true, Ui.cor(this, R.color.ink)));
            nomes.addView(Ui.texto(this,
                    dataInicio.plusDays(i).format(DateTimeFormatter.ofPattern("EEE, dd/MM", BR)),
                    13, false, Ui.cor(this, R.color.mute)));
            linha.addView(nomes, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            Button hora = Ui.botao(this, Alarme.hhmm(hs[i]), Ui.CONTORNO);
            hora.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            hora.setMinHeight(Ui.dp(this, 48));
            hora.setMinimumHeight(Ui.dp(this, 48));
            hora.setMinWidth(Ui.dp(this, 96));
            hora.setMinimumWidth(Ui.dp(this, 96));
            hora.setEnabled(lig[i]);
            hora.setAlpha(lig[i] ? 1f : 0.45f);
            hora.setOnClickListener(v -> new TimePickerDialog(this, (tp, h, m) -> {
                hs[dia] = h * 60 + m;
                montarHorarios();
            }, hs[dia] / 60, hs[dia] % 60, true).show());
            linha.addView(hora);

            Switch sw = Ui.interruptor(this, lig[i], (b, marcado) -> {
                lig[dia] = marcado;
                hora.setEnabled(marcado);
                hora.setAlpha(marcado ? 1f : 0.45f);
            });
            LinearLayout.LayoutParams lps = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lps.leftMargin = Ui.dp(this, 14);
            lps.rightMargin = Ui.dp(this, 6);
            linha.addView(sw, lps);

            boxHorarios.addView(linha, Ui.largura());
        }
    }

    private void escolherInicio() {
        new DatePickerDialog(this, (dp, ano, mes, dia) -> {
            dataInicio = LocalDate.of(ano, mes + 1, dia);
            atualizarTextoInicio();
            montarHorarios();
        }, dataInicio.getYear(), dataInicio.getMonthValue() - 1, dataInicio.getDayOfMonth()).show();
    }

    private void atualizarTextoInicio() {
        btnInicio.setText(dataInicio.format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", BR)));
    }

    // ---------- salvar ----------

    private void salvar() {
        String nome = edNome.getText().toString().trim();
        if (nome.isEmpty()) nome = "Alarme";

        if (a.tipo == Alarme.CICLO) {
            int ciclo = pCiclo.valor(-1);
            int trab = pTrabalho.valor(-1);
            if (ciclo < 2 || ciclo > 365) {
                erro("A duração do ciclo deve ficar entre 2 e 365 dias.");
                return;
            }
            if (trab < 1 || trab > ciclo || trab > 31) {
                erro("Os dias de trabalho devem ficar entre 1 e a duração do ciclo (no máximo 31).");
                return;
            }
            ajustarDias(trab);
            a.ciclo = ciclo;
            a.trabalho = trab;
            a.inicio = dataInicio.toEpochDay();
            a.horarios = new int[trab];
            for (int i = 0; i < trab; i++) a.horarios[i] = lig[i] ? hs[i] : -1;
        } else {
            int dia = pDia.valor(-1);
            if (dia < 1 || dia > 31) {
                erro("O dia do mês deve ficar entre 1 e 31.");
                return;
            }
            a.diaMes = dia;
            a.minutos = minutosMensal;
        }
        a.nome = nome;
        escala.salvarUm(a);
        Agendador.agendar(this);
        finish();
    }

    private void erro(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private TextView rotulo(String texto) {
        return Ui.texto(this, texto, 16, true, Ui.cor(this, R.color.ink));
    }
}
