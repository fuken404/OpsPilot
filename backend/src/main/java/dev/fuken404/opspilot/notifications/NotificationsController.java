package dev.fuken404.opspilot.notifications;

import dev.fuken404.opspilot.shared.InternalJobs;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class NotificationsController {

    private final EmailDispatcher dispatcher;
    private final InternalJobs jobs;

    NotificationsController(EmailDispatcher dispatcher, InternalJobs jobs) {
        this.dispatcher = dispatcher;
        this.jobs = jobs;
    }

    @PostMapping("/api/internal/mail/dispatch")
    ResponseEntity<EmailDispatcher.Result> dispatch(@RequestHeader(name = "X-Internal-Token", required = false) String token) {
        return jobs.run(token, dispatcher::dispatch);
    }
}
