/**
 * Tiempo real: un WebSocket por pestaña, suscrito a una organización. Cuando un cambio se confirma en
 * la base, los demás lo ven sin recargar. Con varias instancias, los eventos viajan por Redis.
 */
package dev.fuken404.opspilot.realtime;
