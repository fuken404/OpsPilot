package dev.fuken404.opspilot.realtime;

/** Reparte un evento a todas las instancias de la API, cada una lo entrega a sus conexiones. */
interface Broker {

    void publish(OrgMessage message);
}
