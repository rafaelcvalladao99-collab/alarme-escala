package com.escala.alarme;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Notificação fixa que mostra quantos dias faltam para o próximo toque de cada alarme. */
public class Contagem {

    static final String CANAL = "proximos2";
    static final int NOTIF_ID = 7;
    private static final Locale BR = Locale.forLanguageTag("pt-BR");

    private static class Item {
        String nome;
        LocalDateTime quando;
    }

    /** Texto como "hoje às 05:30", "amanhã às 05:30 (qui, 08/10)" ou "em 9 dias, qua, 14/10 às 09:00". */
    public static String quando(LocalDateTime t) {
        long dias = ChronoUnit.DAYS.between(LocalDate.now(), t.toLocalDate());
        String hora = t.format(DateTimeFormatter.ofPattern("HH:mm"));
        String data = t.format(DateTimeFormatter.ofPattern("EEE, dd/MM", BR));
        if (dias <= 0) return "hoje às " + hora;
        if (dias == 1) return "amanhã às " + hora + " (" + data + ")";
        return "em " + dias + " dias, " + data + " às " + hora;
    }

    public static void atualizar(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        Escala e = new Escala(c);
        if (!e.mostrarContagem()) {
            nm.cancel(NOTIF_ID);
            return;
        }

        LocalDateTime agora = LocalDateTime.now();
        List<Item> itens = new ArrayList<>();
        for (Alarme a : e.alarmes()) {
            LocalDateTime t = a.proximo(agora);
            if (t != null) {
                Item it = new Item();
                it.nome = a.nome;
                it.quando = t;
                itens.add(it);
            }
        }
        if (itens.isEmpty()) {
            nm.cancel(NOTIF_ID);
            return;
        }
        Collections.sort(itens, (x, y) -> x.quando.compareTo(y.quando));

        nm.deleteNotificationChannel("proximos");
        NotificationChannel ch = new NotificationChannel(CANAL, "Próximos alarmes",
                NotificationManager.IMPORTANCE_DEFAULT);
        ch.setShowBadge(false);
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);

        Notification.InboxStyle estilo = new Notification.InboxStyle();
        for (Item it : itens) {
            estilo.addLine(it.nome + ": " + quando(it.quando));
        }
        Item primeiro = itens.get(0);

        PendingIntent abrir = PendingIntent.getActivity(c, 20, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification n = new Notification.Builder(c, CANAL)
                .setSmallIcon(R.drawable.ic_alarme)
                .setContentTitle(primeiro.nome + " — " + quando(primeiro.quando))
                .setContentText(itens.size() > 1
                        ? "Mais " + (itens.size() - 1) + " alarme" + (itens.size() > 2 ? "s" : "")
                                + " — toque na seta para ver todos"
                        : "Próximo alarme")
                .setStyle(estilo)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(abrir)
                .build();
        nm.notify(NOTIF_ID, n);
    }

    /** Marca uma atualização logo depois da meia-noite, para a contagem de dias acompanhar o calendário. */
    public static void agendarDiario(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(c, AlarmeReceiver.class).setAction(AlarmeReceiver.ACAO_CONTAGEM);
        PendingIntent pi = PendingIntent.getBroadcast(c, Agendador.REQ_CONTAGEM, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        long ms = LocalDate.now().plusDays(1).atTime(0, 5)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        am.setAndAllowWhileIdle(AlarmManager.RTC, ms, pi);
    }
}
