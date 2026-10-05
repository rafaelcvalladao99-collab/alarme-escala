package com.escala.alarme;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Procura uma versão nova no GitHub e instala por cima, com a confirmação do Android. */
public class Atualizador {

    static final String REPO = "rafaelcvalladao99-collab/alarme-escala";

    public static class Info {
        public int versao;
        public String url;
    }

    public interface Resultado {
        /** nova != null: tem versão nova. nova == null e erro == null: já está na última. */
        void aoTerminar(Info nova, String erro);
    }

    public interface Progresso {
        void aoMudar(String texto, boolean terminou);
    }

    public static int versaoInstalada(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= 28) return (int) pi.getLongVersionCode();
            return pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    public static void verificar(Context c, Resultado r) {
        final Context app = c.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            Info nova = null;
            String erro = null;
            HttpURLConnection con = null;
            try {
                URL u = new URL("https://api.github.com/repos/" + REPO + "/releases/latest");
                con = (HttpURLConnection) u.openConnection();
                con.setConnectTimeout(10_000);
                con.setReadTimeout(15_000);
                con.setRequestProperty("Accept", "application/vnd.github+json");
                int codigo = con.getResponseCode();
                if (codigo != 200) throw new IOException("GitHub respondeu " + codigo);

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try (InputStream in = con.getInputStream()) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                }
                JSONObject j = new JSONObject(bos.toString("UTF-8"));
                int versao = Integer.parseInt(j.getString("tag_name").replaceAll("[^0-9]", ""));
                if (versao > versaoInstalada(app)) {
                    JSONArray assets = j.getJSONArray("assets");
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.getJSONObject(i);
                        if (a.getString("name").endsWith(".apk")) {
                            Info info = new Info();
                            info.versao = versao;
                            info.url = a.getString("browser_download_url");
                            nova = info;
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                erro = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            } finally {
                if (con != null) con.disconnect();
            }
            final Info resultado = nova;
            final String falha = erro;
            main.post(() -> r.aoTerminar(resultado, falha));
        }).start();
    }

    /** Baixa o APK direto para o instalador do Android. Depois disso o Android pede a confirmação. */
    public static void baixarEInstalar(Context c, Info info, Progresso p) {
        final Context app = c.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            HttpURLConnection con = null;
            try {
                main.post(() -> p.aoMudar("Baixando a versão " + info.versao + "...", false));
                con = (HttpURLConnection) new URL(info.url).openConnection();
                con.setConnectTimeout(15_000);
                con.setReadTimeout(30_000);
                int codigo = con.getResponseCode();
                if (codigo != 200) throw new IOException("download respondeu " + codigo);
                long total = con.getContentLengthLong();

                PackageInstaller pi = app.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                sp.setAppPackageName(app.getPackageName());
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    sp.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
                }
                int id = pi.createSession(sp);
                PackageInstaller.Session s = pi.openSession(id);
                try {
                    OutputStream out = s.openWrite("AlarmeEscala.apk", 0, total > 0 ? total : -1);
                    InputStream in = con.getInputStream();
                    byte[] buf = new byte[65_536];
                    long lido = 0;
                    int ultimo = -1;
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        lido += n;
                        if (total > 0) {
                            final int pct = (int) (lido * 100 / total);
                            if (pct != ultimo) {
                                ultimo = pct;
                                main.post(() -> p.aoMudar("Baixando a versão " + info.versao + "... " + pct + "%", false));
                            }
                        }
                    }
                    s.fsync(out);
                    out.close();
                    in.close();

                    Intent it = new Intent(app, InstalarReceiver.class).setAction(InstalarReceiver.ACAO);
                    PendingIntent pend = PendingIntent.getBroadcast(app, id, it,
                            PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                    s.commit(pend.getIntentSender());
                } catch (Exception e) {
                    s.abandon();
                    throw e;
                } finally {
                    s.close();
                }
                main.post(() -> p.aoMudar("Pronto. Confirme a instalação na tela que o Android abrir.", true));
            } catch (Exception e) {
                final String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                main.post(() -> p.aoMudar("Erro ao atualizar: " + msg, true));
            } finally {
                if (con != null) con.disconnect();
            }
        }).start();
    }
}
