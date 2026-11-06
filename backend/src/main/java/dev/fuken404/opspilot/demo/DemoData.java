package dev.fuken404.opspilot.demo;

import dev.fuken404.opspilot.identity.AppUser;
import dev.fuken404.opspilot.identity.Users;
import dev.fuken404.opspilot.incidents.TicketCategory;
import dev.fuken404.opspilot.incidents.TicketPriority;
import dev.fuken404.opspilot.incidents.TicketStatus;
import dev.fuken404.opspilot.incidents.Tickets;
import dev.fuken404.opspilot.organizations.Organizations;
import dev.fuken404.opspilot.organizations.Role;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Siembra la demo. Las personas usan el mismo `sub` fijo que el realm de Keycloak, así que sus
 * tickets ya existen cuando alguien entra por primera vez. Solo crea tickets si la organización no
 * tiene ninguno: reiniciar la app no duplica nada.
 */
@Configuration
@Profile("demo")
class DemoData {

    private static final Logger log = LoggerFactory.getLogger(DemoData.class);

    @Bean
    ApplicationRunner demoSeed(Users users, Organizations organizations, Tickets tickets) {
        return args -> {
            AppUser ana = users.ensure("4f1d2c3a-0a11-4e2b-9a01-000000000001", "ana.admin@opspilot.demo", "Ana Rojas");
            AppUser tomas = users.ensure("4f1d2c3a-0a11-4e2b-9a01-000000000002", "tomas.tecnico@opspilot.demo", "Tomás Gil");
            AppUser carla = users.ensure("4f1d2c3a-0a11-4e2b-9a01-000000000003", "carla.cliente@opspilot.demo", "Carla Méndez");
            AppUser lucia = users.ensure("4f1d2c3a-0a11-4e2b-9a01-000000000004", "lucia.cliente@opspilot.demo", "Lucía Peña");

            UUID acme = organizations.createWithInvitations("Acme Logística", "acme", Map.of(
                "ana.admin@opspilot.demo", Role.ADMIN,
                "tomas.tecnico@opspilot.demo", Role.TECHNICIAN,
                "carla.cliente@opspilot.demo", Role.CUSTOMER));
            UUID nimbus = organizations.createWithInvitations("Nimbus Salud", "nimbus", Map.of(
                "ana.admin@opspilot.demo", Role.ADMIN,
                "lucia.cliente@opspilot.demo", Role.CUSTOMER));

            if (tickets.list(acme, ana, new Tickets.Filter(Tickets.View.ALL, Tickets.AssigneeFilter.ANY, null)).isEmpty()) {
                seedAcme(tickets, acme, ana, tomas, carla);
                log.info("Demo: tickets de Acme creados");
            }
            if (tickets.list(nimbus, ana, new Tickets.Filter(Tickets.View.ALL, Tickets.AssigneeFilter.ANY, null)).isEmpty()) {
                seedNimbus(tickets, nimbus, ana, lucia);
                log.info("Demo: tickets de Nimbus creados");
            }
        };
    }

    private static void seedAcme(Tickets tickets, UUID org, AppUser ana, AppUser tomas, AppUser carla) {
        Tickets.Detail vpn = tickets.create(org, carla, new Tickets.NewTicket("La VPN se desconecta cada diez minutos",
            "Desde el lunes la VPN de la bodega norte se cae cada diez minutos y hay que volver a conectarse. "
                + "Nos pasa a todo el equipo de despachos.", TicketCategory.NETWORK, null));
        vpn = tickets.assign(org, tomas, vpn.id(), vpn.version(), tomas.getId());
        vpn = tickets.changeStatus(org, tomas, vpn.id(), vpn.version(), TicketStatus.IN_PROGRESS);
        tickets.comment(org, tomas, vpn.id(), "Hola Carla, ya lo estoy revisando. ¿Les pasa también desde la red de la oficina?", false);
        tickets.comment(org, tomas, vpn.id(), "El firewall de la bodega está cortando sesiones UDP inactivas. Probar keepalive de 30 s.", true);

        tickets.create(org, carla, new Tickets.NewTicket("No puedo abrir los PDF de facturación",
            "Al descargar las facturas del portal sale \"el archivo está dañado\". Probé en Chrome y en Edge.",
            TicketCategory.SOFTWARE, null));

        Tickets.Detail ssl = tickets.create(org, ana, new Tickets.NewTicket("Renovar el certificado del portal de clientes",
            "El certificado vence en 10 días. Hay que renovarlo y avisar a los clientes del cambio de huella.",
            TicketCategory.ACCESS, TicketPriority.HIGH));
        ssl = tickets.assign(org, ana, ssl.id(), ssl.version(), tomas.getId());
        tickets.changeStatus(org, tomas, ssl.id(), ssl.version(), TicketStatus.IN_PROGRESS);

        Tickets.Detail printer = tickets.create(org, carla, new Tickets.NewTicket("La impresora del piso 2 imprime en blanco",
            "Salen las hojas, pero sin nada impreso. Ya cambiamos el tóner.", TicketCategory.HARDWARE, null));
        printer = tickets.assign(org, tomas, printer.id(), printer.version(), tomas.getId());
        printer = tickets.changeStatus(org, tomas, printer.id(), printer.version(), TicketStatus.IN_PROGRESS);
        tickets.comment(org, tomas, printer.id(), "Era el cabezal de impresión; ya lo limpié. ¿Puedes confirmar que salió bien?", false);
        tickets.changeStatus(org, tomas, printer.id(), printer.version(), TicketStatus.RESOLVED);
    }

    private static void seedNimbus(Tickets tickets, UUID org, AppUser ana, AppUser lucia) {
        Tickets.Detail agenda = tickets.create(org, lucia, new Tickets.NewTicket("El sistema de citas está muy lento",
            "Agendar una cita tarda más de un minuto y los pacientes esperan en recepción.", TicketCategory.SOFTWARE, null));
        tickets.triage(org, ana, agenda.id(), agenda.version(), TicketPriority.CRITICAL, TicketCategory.SOFTWARE);
    }
}
