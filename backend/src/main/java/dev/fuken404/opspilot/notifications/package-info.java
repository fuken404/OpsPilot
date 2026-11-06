/**
 * Avisos por correo con bandeja de salida (outbox transaccional): el correo se escribe en la misma
 * transacción que el cambio y un despachador lo envía después, con reintentos. Si el servidor de
 * correo falla, el cambio se guarda igual y el correo sale cuando vuelva.
 */
package dev.fuken404.opspilot.notifications;
