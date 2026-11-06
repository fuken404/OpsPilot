package dev.fuken404.opspilot.incidents;

public enum TicketStatus {
    NEW, IN_PROGRESS, WAITING_CUSTOMER, RESOLVED, CLOSED, CANCELLED;

    public boolean isFinal() {
        return this == CLOSED || this == CANCELLED;
    }
}
