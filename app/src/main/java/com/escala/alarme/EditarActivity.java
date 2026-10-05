package com.escala.alarme;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
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

    private EditText edNome, edCiclo, edTrabalho, edDia;
    private RadioButton rbCiclo, rbMensal;
    private LinearLayout boxCiclo, boxMensal, boxHorarios;
    private Button btnInicio, btnHoraMensal;

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
        int pad = dp(20);
        raiz.setPadding(pad, pad, pad, pad);

        ScrollView sv = new ScrollView(this);
        sv.setFitsSystemWindows(true);
        sv.addView(raiz);
        setContentView(sv);

        TextView cabecalho = new TextView(this);
        cabecalho.setText(novo ? "Novo alarme" : "Editar alarme");
        cabecalho.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        cabecalho.setTypeface(null, android.graphics.Typeface.BOLD);
        raiz.addView(cabecalho);

        raiz.addView(titulo("Nome"));
        edNome = new EditText(this);
        edNome.setSingleLine(true);
        edNome.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        edNome.setHint("Ex.: Trabalho, Aluguel");
        raiz.addView(edNome, larguraTotal());

        raiz.addView(titulo("Como ele repete"));
        RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(LinearLayout.VERTICAL);
        rbCiclo = new RadioButton(this);
        rbCiclo.setId(View.generateViewId());
        rbCiclo.setText("Ciclo de X dias (escala de trabalho e folga)");
        rbMensal = new RadioButton(this);
        rbMensal.setId(View.generateViewId());
        rbMensal.setText("Todo mês, sempre no mesmo dia (ex.: dia 14)");
        rg.addView(rbCiclo);
        rg.addView(rbMensal);
        rg.setOnCheckedChangeListener((g, checkedId) -> {
            a.tipo = checkedId == rbMensal.getId() ? Alarme.MENSAL : Alarme.CICLO;
            mostrarTipo();
        });
        raiz.addView(rg, larguraTotal());

        // --- opções do ciclo ---
        boxCiclo = new LinearLayout(this);
        boxCiclo.setOrientation(LinearLayout.VERTICAL);
        boxCiclo.addView(rotulo("Duração do ciclo, em dias"));
        edCiclo = numero();
        boxCiclo.addView(edCiclo);
        boxCiclo.addView(rotulo("Quantos dias de trabalho no começo do ciclo"));
        edTrabalho = numero();
        edTrabalho.addTextChangedListener(new Simples() {
            @Override
            public void afterTextChanged(Editable s) {
                int n = lerNumero(edTrabalho, -1);
                if (n >= 1 && n <= 31) {
                    ajustarDias(n);
                    montarHorarios();
                }
            }
        });
        boxCiclo.addView(edTrabalho);
        boxCiclo.addView(rotulo("1º dia de trabalho de um ciclo"));
        btnInicio = new Button(this);
        btnInicio.setOnClickListener(v -> escolherInicio());
        boxCiclo.addView(btnInicio);
        boxCiclo.addView(rotulo("Horário de cada dia de trabalho"));
        boxHorarios = new LinearLayout(this);
        boxHorarios.setOrientation(LinearLayout.VERTICAL);
        boxCiclo.addView(boxHorarios, larguraTotal());
        raiz.addView(boxCiclo, larguraTotal());

        // --- opções mensais ---
        boxMensal = new LinearLayout(this);
        boxMensal.setOrientation(LinearLayout.VERTICAL);
        boxMensal.addView(rotulo("Dia do mês (1 a 31)"));
        edDia = numero();
        boxMensal.addView(edDia);
        TextView dica = new TextView(this);
        dica.setText("Se o mês não tiver esse dia (ex.: 31 em abril), toca no último dia do mês.");
        dica.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        dica.setAlpha(0.7f);
        boxMensal.addView(dica);
        boxMensal.addView(rotulo("Horário"));
        btnHoraMensal = new Button(this);
        btnHoraMensal.setOnClickListener(v -> new TimePickerDialog(this, (tp, h, m) -> {
            minutosMensal = h * 60 + m;
            btnHoraMensal.setText(Alarme.hhmm(minutosMensal));
        }, minutosMensal / 60, minutosMensal % 60, true).show());
        boxMensal.addView(btnHoraMensal);
        raiz.addView(boxMensal, larguraTotal());

        // --- botões ---
        Button salvar = new Button(this);
        salvar.setText("Salvar");
        salvar.setOnClickListener(v -> salvar());
        LinearLayout.LayoutParams lpSalvar = larguraTotal();
        lpSalvar.topMargin = dp(28);
        raiz.addView(salvar, lpSalvar);

        if (!novo) {
            Button excluir = new Button(this);
            excluir.setText("Excluir alarme");
            excluir.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setMessage("Excluir o alarme \"" + a.nome + "\"?")
                    .setPositiveButton("Excluir", (d, w) -> {
                        escala.remover(a.id);
                        Agendador.agendar(this);
                        finish();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show());
            raiz.addView(excluir, larguraTotal());
        }

        Button cancelar = new Button(this);
        cancelar.setText("Cancelar");
        cancelar.setOnClickListener(v -> finish());
        raiz.addView(cancelar, larguraTotal());
    }

    private void preencher() {
        edNome.setText(novo ? "" : a.nome);
        edCiclo.setText(String.valueOf(a.ciclo));

        hs = new int[a.trabalho];
        lig = new boolean[a.trabalho];
        for (int i = 0; i < a.trabalho; i++) {
            int m = a.horarioDoDia(i);
            lig[i] = m >= 0;
            hs[i] = m >= 0 ? m : 360;
        }
        edTrabalho.setText(String.valueOf(a.trabalho));

        dataInicio = LocalDate.ofEpochDay(a.inicio);
        atualizarTextoInicio();

        edDia.setText(String.valueOf(a.diaMes));
        minutosMensal = a.minutos;
        btnHoraMensal.setText(Alarme.hhmm(minutosMensal));

        if (a.tipo == Alarme.MENSAL) rbMensal.setChecked(true);
        else rbCiclo.setChecked(true);
        montarHorarios();
        mostrarTipo();
    }

    private void mostrarTipo() {
        boolean mensal = a.tipo == Alarme.MENSAL;
        boxCiclo.setVisibility(mensal ? View.GONE : View.VISIBLE);
        boxMensal.setVisibility(mensal ? View.VISIBLE : View.GONE);
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
        boxHorarios.removeAllViews();
        for (int i = 0; i < hs.length; i++) {
            final int dia = i;
            LinearLayout linha = new LinearLayout(this);
            linha.setOrientation(LinearLayout.HORIZONTAL);
            linha.setGravity(Gravity.CENTER_VERTICAL);

            Button hora = new Button(this);
            hora.setText(Alarme.hhmm(hs[i]));
            hora.setEnabled(lig[i]);
            hora.setOnClickListener(v -> new TimePickerDialog(this, (tp, h, m) -> {
                hs[dia] = h * 60 + m;
                montarHorarios();
            }, hs[dia] / 60, hs[dia] % 60, true).show());

            CheckBox cb = new CheckBox(this);
            cb.setText("Dia " + (i + 1));
            cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            cb.setChecked(lig[i]);
            cb.setOnCheckedChangeListener((b, marcado) -> {
                lig[dia] = marcado;
                hora.setEnabled(marcado);
            });

            linha.addView(cb, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            linha.addView(hora);
            boxHorarios.addView(linha);
        }
    }

    private void escolherInicio() {
        new DatePickerDialog(this, (dp, ano, mes, dia) -> {
            dataInicio = LocalDate.of(ano, mes + 1, dia);
            atualizarTextoInicio();
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
            int ciclo = lerNumero(edCiclo, -1);
            int trab = lerNumero(edTrabalho, -1);
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
            int dia = lerNumero(edDia, -1);
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

    // ---------- ajudantes ----------

    private int lerNumero(EditText e, int padrao) {
        try {
            return Integer.parseInt(e.getText().toString().trim());
        } catch (Exception ex) {
            return padrao;
        }
    }

    private EditText numero() {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setSingleLine(true);
        e.setMinWidth(dp(120));
        return e;
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

    private TextView rotulo(String texto) {
        TextView t = new TextView(this);
        t.setText(texto);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setPadding(0, dp(14), 0, 0);
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

    /** TextWatcher com os métodos que não interessam já vazios. */
    private abstract static class Simples implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) { }
    }
}
