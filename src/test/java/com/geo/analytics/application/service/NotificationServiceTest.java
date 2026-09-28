package com.geo.analytics.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geo.analytics.domain.event.ProjectAuditCompletedEvent;
import com.geo.analytics.infrastructure.config.AppProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 宛先ごとに送る（#174）。Mailpit はどの宛先も受け付けるため、送信の失敗はここでモックを使って確かめる。
 * 実際の送信と RLS の経路は ProjectNotificationIntegrationTest で確かめる。
 */
class NotificationServiceTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID JOB_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final ProjectAuditCompletedEvent EVENT = new ProjectAuditCompletedEvent(PROJECT_ID, JOB_ID, WORKSPACE_ID);

    private ProjectAuditNoticeReader reader;
    private JavaMailSender mailSender;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        reader = mock(ProjectAuditNoticeReader.class);
        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mailSender);
        notificationService = new NotificationService(reader, provider, new AppProperties());
    }

    @Test
    void oneFailedRecipientDoesNotStopTheOthers() {
        when(reader.read(PROJECT_ID, JOB_ID)).thenReturn(Optional.of(notice(List.of("a@example.com", "b@example.com", "c@example.com"))));
        doThrow(new MailSendException("rejected")).doNothing().when(mailSender).send(any(MimeMessage.class));

        notificationService.deliver(EVENT);

        verify(mailSender, times(3)).send(any(MimeMessage.class));
    }

    @Test
    void sendsNothingWhenThereIsNoNotice() {
        when(reader.read(PROJECT_ID, JOB_ID)).thenReturn(Optional.empty());

        notificationService.deliver(EVENT);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendsOnePerRecipient() {
        when(reader.read(PROJECT_ID, JOB_ID)).thenReturn(Optional.of(notice(List.of("a@example.com", "b@example.com"))));
        doNothing().when(mailSender).send(any(MimeMessage.class));

        notificationService.deliver(EVENT);

        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    private static ProjectAuditNoticeReader.AuditNotice notice(List<String> recipients) {
        return new ProjectAuditNoticeReader.AuditNotice(
                "テスト案件", recipients, new ProjectAuditNoticeReader.AuditDigest(42.0, null, null, List.of()));
    }
}
