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

    static final String CANAL = "proximos3";
    static final int NOTIF_ID = 7;
    static final int ID_BASE = 100;
    static final String GRUPO = "alarmes";
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

    /** Cancela as nossas notificações que não valem mais, mantendo as n primeiras. */
    private static void limpar(NotificationManager nm, int n) {
        for (android.service.notification.StatusBarNotification sb : nm.getActiveNotifications()) {
            int id = sb.getId();
            boolean nosso = id == NOTIF_ID || (id >= ID_BASE && id < ID_BASE + 100);
            if (!nosso) continue;
            boolean vale = id == NOTIF_ID ? n > 1 : id - ID_BASE < n;
            if (!vale) nm.cancel(id);
        }
    }

    public static void atualizar(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        Escala e = new Escala(c);
        if (!e.mostrarContagem()) {
            limpar(nm, 0);
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
            limpar(nm, 0);
            return;
        }
        Collections.sort(itens, (x, y) -> x.quando.compareTo(y.quando));

        nm.deleteNotificationChannel("proximos");
        nm.deleteNotificationChannel("proximos2");
        NotificationChannel ch = new NotificationChannel(CANAL, "Próximos alarmes",
                NotificationManager.IMPORTANCE_DEFAULT);
        ch.setShowBadge(false);
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);

        PendingIntent abrir = PendingIntent.getActivity(c, 20, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        limpar(nm, itens.size());

        boolean varios = itens.size() > 1;
        for (int i = 0; i < itens.size(); i++) {
            Item it = itens.get(i);
            Notification.Builder b = new Notification.Builder(c, CANAL)
                    .setSmallIcon(R.drawable.ic_alarme)
                    .setContentTitle(it.nome)
                    .setContentText(quando(it.quando))
                    .setOngoing(true)
                    .setCategory(Notification.CATEGORY_STATUS)
                    .setOnlyAlertOnce(true)
                    .setShowWhen(false)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setContentIntent(abrir);
            if (varios) {
                b.setGroup(GRUPO)
                        .setSortKey(String.format(Locale.US, "%03d", i))
                        .setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY);
            }
            nm.notify(ID_BASE + i, b.build());
        }

        if (varios) {
            Notification.InboxStyle estilo = new Notification.InboxStyle();
            for (Item it : itens) estilo.addLine(it.nome + ": " + quando(it.quando));
            Item primeiro = itens.get(0);
            Notification resumo = new Notification.Builder(c, CANAL)
                    .setSmallIcon(R.drawable.ic_alarme)
                    .setContentTitle(itens.size() + " alarmes")
                    .setContentText("Próximo: " + primeiro.nome + " — " + quando(primeiro.quando))
                    .setStyle(estilo)
                    .setGroup(GRUPO)
                    .setGroupSummary(true)
                    .setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setShowWhen(false)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setContentIntent(abrir)
                    .build();
            nm.notify(NOTIF_ID, resumo);
        }
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
