package com.escala.alarme;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Remarca o alarme quando o celular liga, o app é atualizado ou a hora muda. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Agendador.agendar(c);
    }
}
