package ru.tuganov.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private JavaMailSender mailSender;
    @InjectMocks private EmailService emailService;

    @Test
    void sendVerificationCode_success() {
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@test.com");

        emailService.sendVerificationCode("user@test.com", "123456");

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendNewVerificationCode_success() {
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@test.com");

        emailService.sendNewVerificationCode("user@test.com", "654321");

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendEmail_mailError_throwsServiceUnavailable() {
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@test.com");
        doThrow(new MailSendException("SMTP error")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> emailService.sendVerificationCode("user@test.com", "123456"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status.value").isEqualTo(503);
    }
}
