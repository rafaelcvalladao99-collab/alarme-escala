package com.escala.alarme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Pequenas peças de interface compartilhadas pelas telas do app. */
final class Ui {

    static final int PRIMARIO = 0;
    static final int CONTORNO = 1;
    static final int PERIGO = 2;

    private Ui() { }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    static int cor(Context c, int id) {
        return c.getColor(id);
    }

    static GradientDrawable forma(Context c, int fundo, int borda, float raioDp, float tracoDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fundo);
        g.setCornerRadius(dp(c, raioDp));
        if (borda != 0 && tracoDp > 0) g.setStroke(dp(c, tracoDp), borda);
        return g;
    }

    static Drawable ondulado(Drawable fundo) {
        return new RippleDrawable(ColorStateList.valueOf(0x33808080), fundo, null);
    }

    static TextView texto(Context c, CharSequence t, float sp, boolean negrito, int cor) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(cor);
        if (negrito) v.setTypeface(null, Typeface.BOLD);
        return v;
    }

    static LinearLayout.LayoutParams largura() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams largura(Context c, float topoDp) {
        LinearLayout.LayoutParams lp = largura();
        lp.topMargin = dp(c, topoDp);
        return lp;
    }

    static Button botao(Context c, String t, int estilo) {
        Button b = new Button(c);
        b.setText(t);
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        b.setTypeface(null, Typeface.BOLD);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setMinHeight(dp(c, 56));
        b.setMinimumHeight(dp(c, 56));
        int fundo;
        int borda;
        int txt;
        if (estilo == PRIMARIO) {
            fundo = cor(c, R.color.primary);
            borda = fundo;
            txt = cor(c, R.color.on_primary);
        } else if (estilo == PERIGO) {
            fundo = 0;
            borda = 0;
            txt = cor(c, R.color.danger);
        } else {
            fundo = cor(c, R.color.card);
            borda = cor(c, R.color.line);
            txt = cor(c, R.color.ink);
        }
        b.setBackground(ondulado(forma(c, fundo, borda, 16, 2)));
        b.setTextColor(txt);
        return b;
    }

    static Switch interruptor(Context c, boolean ligado, CompoundButton.OnCheckedChangeListener l) {
        Switch s = new Switch(c);
        s.setChecked(ligado);
        int[][] estados = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(estados,
                new int[]{cor(c, R.color.on_primary), cor(c, R.color.mute)}));
        s.setTrackTintList(new ColorStateList(estados,
                new int[]{cor(c, R.color.primary), cor(c, R.color.line)}));
        s.setScaleX(1.2f);
        s.setScaleY(1.2f);
        s.setOnCheckedChangeListener(l);
        return s;
    }

    interface Mudou {
        void valor(int v);
    }

    /** Número com botões − e +, e o campo no meio também aceita digitar. */
    static class Passo extends LinearLayout {
        final EditText campo;
        int min;
        int max;

        Passo(Context c, int min, int max, Mudou mudou) {
            super(c);
            this.min = min;
            this.max = max;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);

            Button menos = botaoPasso(c, "−");
            Button mais = botaoPasso(c, "+");
            campo = new EditText(c);
            campo.setInputType(InputType.TYPE_CLASS_NUMBER);
            campo.setSingleLine(true);
            campo.setGravity(Gravity.CENTER);
            campo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
            campo.setTypeface(null, Typeface.BOLD);
            campo.setTextColor(cor(c, R.color.ink));

            addView(menos);
            addView(campo, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            addView(mais);

            menos.setOnClickListener(v -> definir(limitar(valor(min) - 1)));
            mais.setOnClickListener(v -> definir(limitar(valor(min) + 1)));
            campo.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int a, int b, int d) { }

                @Override
                public void onTextChanged(CharSequence s, int a, int b, int d) { }

                @Override
                public void afterTextChanged(Editable s) {
                    int v = valor(-1);
                    if (v >= Passo.this.min && v <= Passo.this.max) mudou.valor(v);
                }
            });
        }

        private static Button botaoPasso(Context c, String t) {
            Button b = botao(c, t, CONTORNO);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setPadding(0, 0, 0, 0);
            b.setLayoutParams(new LayoutParams(dp(c, 56), dp(c, 56)));
            return b;
        }

        private int limitar(int v) {
            return Math.max(min, Math.min(max, v));
        }

        int valor(int padrao) {
            try {
                return Integer.parseInt(campo.getText().toString().trim());
            } catch (Exception e) {
                return padrao;
            }
        }

        void definir(int v) {
            campo.setText(String.valueOf(v));
        }
    }
}
