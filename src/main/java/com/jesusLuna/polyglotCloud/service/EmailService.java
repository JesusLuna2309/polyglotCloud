package com.jesusLuna.polyglotCloud.service;

import java.io.UnsupportedEncodingException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    
    @Value("${app.email.from}")
    private String fromEmail;
    
    @Value("${app.email.verification-url}")
    private String verificationBaseUrl;
    
    @Value("${app.email.support-email}")
    private String supportEmail;

    /**
     * Envía email de verificación con template HTML bonito
     */
    public void sendEmailVerification(String toEmail, String username, String verificationToken) {
        log.info("Sending email verification to: {}", toEmail);
        
        try {
            // Crear contexto para template
            Context context = new Context();
            context.setVariable("username", username);
            context.setVariable("verificationUrl", verificationBaseUrl + "?token=" + verificationToken);
            context.setVariable("supportEmail", supportEmail);
            
            // Procesar template HTML
            String htmlContent = templateEngine.process("email", context);
            
            // Crear y enviar email
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail, "PolyglotCloud");
            helper.setTo(toEmail);
            helper.setSubject("Verifica tu cuenta en PolyglotCloud");
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            
            log.info("Email verification sent successfully to: {}", toEmail);
            
        } catch (MessagingException | UnsupportedEncodingException e) {  // ✅ AGREGAR UnsupportedEncodingException
            log.error("Failed to send email verification to: {}", toEmail, e);
            throw new RuntimeException("Failed to send verification email", e);
        }
    }
    
    /**
     * Envía email de bienvenida después de verificar
     */
    public void sendWelcomeEmail(String toEmail, String username) {
        log.info("Sending welcome email to: {}", toEmail);
        
        try {
            Context context = new Context();
            context.setVariable("username", username);
            context.setVariable("supportEmail", supportEmail);
            
            String htmlContent = templateEngine.process("email/welcome", context);
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail, "PolyglotCloud");
            helper.setTo(toEmail);
            helper.setSubject("¡Bienvenido a PolyglotCloud! 🎉");
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            
            log.info("Welcome email sent successfully to: {}", toEmail);
            
        } catch (MessagingException | UnsupportedEncodingException e) {  // ✅ AGREGAR UnsupportedEncodingException
            log.error("Failed to send welcome email to: {}", toEmail, e);
            // No throw exception aquí - welcome email no es crítico
        }
    }
}
