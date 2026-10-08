package com.escala.alarme;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Toca o som e vibra até a pessoa desligar (ou até 10 minutos). */
public class AlarmeService extends Service {

    static final String CANAL = "alarme_tocando";
    static final int NOTIF_ID = 42;
    static final long LIMITE_MS = 10 * 60_000L;

    /** Nome do alarme que está tocando (usado pela soneca e pela tela do alarme). */
    static String nomeAtual = "Alarme";

    private MediaPlayer player;
    private Vibrator vibrador;
    private PowerManager.WakeLock wakeLock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int volumeOriginal = -1;
    private boolean tocando = false;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String nome = intent != null ? intent.getStringExtra("nome") : null;
        nomeAtual = nome != null ? nome : "Alarme";
        Notification n = criarNotificacao();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }
        if (!tocando) {
            tocando = true;
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlarmeEscala:tocando");
            wakeLock.acquire(LIMITE_MS + 60_000L);
            tocarSom();
            vibrar();
            handler.postDelayed(this::stopSelf, LIMITE_MS);
            // Se o celular estiver desbloqueado e em uso, tenta abrir a tela do alarme também.
            try {
                startActivity(intentTela());
            } catch (Exception ignored) { }
        }
        return START_NOT_STICKY;
    }

    private Intent intentTela() {
        Intent i = new Intent(this, TocandoActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        i.putExtra("nome", nomeAtual);
        return i;
    }

    private Notification criarNotificacao() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CANAL, "Alarme tocando",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(null, null); // o som é tocado pelo próprio app
        ch.enableVibration(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ch.setBypassDnd(true);
        nm.createNotificationChannel(ch);

        PendingIntent tela = PendingIntent.getActivity(this, 10, intentTela(),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent parar = PendingIntent.getBroadcast(this, 11,
                new Intent(this, AlarmeReceiver.class).setAction(AlarmeReceiver.ACAO_PARAR),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent soneca = PendingIntent.getBroadcast(this, 12,
                new Intent(this, AlarmeReceiver.class).setAction(AlarmeReceiver.ACAO_SONECA),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        int min = new Escala(this).sonecaMinutos();

        return new Notification.Builder(this, CANAL)
                .setSmallIcon(R.drawable.ic_alarme)
                .setContentTitle(nomeAtual + " — " + hora)
                .setContentText("Hora de levantar!")
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setContentIntent(tela)
                .setFullScreenIntent(tela, true)
                .addAction(new Notification.Action.Builder(null, "Desligar", parar).build())
                .addAction(new Notification.Action.Builder(null, "Soneca " + min + " min", soneca).build())
                .build();
    }

    private void tocarSom() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        try {
            int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            int atual = am.getStreamVolume(AudioManager.STREAM_ALARM);
            int alvo = Math.max(1, (int) Math.round(max * new Escala(this).volume() / 100.0));
            if (atual != alvo) {
                volumeOriginal = atual;
                am.setStreamVolume(AudioManager.STREAM_ALARM, alvo, 0);
            }
        } catch (Exception ignored) { }

        String escolhido = new Escala(this).toque();
        Uri[] opcoes = {
                escolhido != null ? Uri.parse(escolhido) : null,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                Settings.System.DEFAULT_ALARM_ALERT_URI,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        };
        for (Uri u : opcoes) {
            if (u == null) continue;
            try {
                MediaPlayer mp = new MediaPlayer();
                mp.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                mp.setDataSource(this, u);
                mp.setLooping(true);
                mp.prepare();
                mp.start();
                player = mp;
                return;
            } catch (Exception e) {
                // tenta o próximo som da lista
            }
        }
    }

    private void vibrar() {
        vibrador = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrador == null || !vibrador.hasVibrator()) return;
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build();
        vibrador.vibrate(VibrationEffect.createWaveform(new long[]{0, 800, 600}, 0), aa);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            player.release();
            player = null;
        }
        if (vibrador != null) vibrador.cancel();
        if (volumeOriginal >= 0) {
            try {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                am.setStreamVolume(AudioManager.STREAM_ALARM, volumeOriginal, 0);
            } catch (Exception ignored) { }
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        TocandoActivity.fecharSeAberta();
        super.onDestroy();
    }
}
