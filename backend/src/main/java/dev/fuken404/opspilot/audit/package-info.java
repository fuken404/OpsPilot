/**
 * Auditoría: registro de solo agregar con cada cambio de los demás módulos. Se escribe en la misma
 * transacción del cambio, así que no hay cambios sin registro ni registros de cambios que fallaron.
 */
package dev.fuken404.opspilot.audit;
