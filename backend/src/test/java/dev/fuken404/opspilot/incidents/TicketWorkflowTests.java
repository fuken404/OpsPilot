package dev.fuken404.opspilot.incidents;

import static dev.fuken404.opspilot.incidents.TicketStatus.CANCELLED;
import static dev.fuken404.opspilot.incidents.TicketStatus.CLOSED;
import static dev.fuken404.opspilot.incidents.TicketStatus.IN_PROGRESS;
import static dev.fuken404.opspilot.incidents.TicketStatus.NEW;
import static dev.fuken404.opspilot.incidents.TicketStatus.RESOLVED;
import static dev.fuken404.opspilot.incidents.TicketStatus.WAITING_CUSTOMER;
import static org.assertj.core.api.Assertions.assertThat;

import dev.fuken404.opspilot.organizations.Role;
import org.junit.jupiter.api.Test;

class TicketWorkflowTests {

    @Test
    void elEquipoRecorreTodoElFlujo() {
        assertThat(TicketWorkflow.allowed(NEW, Role.TECHNICIAN, false)).containsExactlyInAnyOrder(IN_PROGRESS, CANCELLED);
        assertThat(TicketWorkflow.allowed(IN_PROGRESS, Role.ADMIN, false))
            .containsExactlyInAnyOrder(WAITING_CUSTOMER, RESOLVED, CANCELLED);
        assertThat(TicketWorkflow.allowed(RESOLVED, Role.TECHNICIAN, false)).containsExactlyInAnyOrder(IN_PROGRESS, CLOSED);
    }

    @Test
    void losEstadosFinalesNoSeMueven() {
        assertThat(TicketWorkflow.allowed(CLOSED, Role.ADMIN, true)).isEmpty();
        assertThat(TicketWorkflow.allowed(CANCELLED, Role.ADMIN, true)).isEmpty();
        assertThat(TicketWorkflow.exists(NEW, CLOSED)).isFalse();
    }

    @Test
    void elClienteSoloCancelaAlInicioYConfirmaOReabreLaSolucion() {
        assertThat(TicketWorkflow.allowed(NEW, Role.CUSTOMER, true)).containsExactly(CANCELLED);
        assertThat(TicketWorkflow.allowed(IN_PROGRESS, Role.CUSTOMER, true)).isEmpty();
        assertThat(TicketWorkflow.allowed(WAITING_CUSTOMER, Role.CUSTOMER, true)).isEmpty();
        assertThat(TicketWorkflow.allowed(RESOLVED, Role.CUSTOMER, true)).containsExactlyInAnyOrder(IN_PROGRESS, CLOSED);
        assertThat(TicketWorkflow.allowed(RESOLVED, Role.CUSTOMER, false)).isEmpty();
    }
}
