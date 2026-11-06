package dev.fuken404.opspilot.incidents;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {

    Optional<Ticket> findByIdAndOrganizationId(UUID id, UUID organizationId);
}

interface TicketCommentRepository extends JpaRepository<TicketComment, UUID> {

    List<TicketComment> findByTicketIdOrderByCreatedAt(UUID ticketId);

    List<TicketComment> findByTicketIdAndInternalFalseOrderByCreatedAt(UUID ticketId);
}

interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, UUID> {

    List<TicketAttachment> findByTicketIdOrderByCreatedAt(UUID ticketId);

    List<TicketAttachment> findByTicketIdAndInternalFalseOrderByCreatedAt(UUID ticketId);

    Optional<TicketAttachment> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
