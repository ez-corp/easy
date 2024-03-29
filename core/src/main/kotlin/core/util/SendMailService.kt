package core.util

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.Instant

private val log = KotlinLogging.logger {}

@Service
class SendMailService(private val mailSender: JavaMailSender) {

    @Value("\${easy.core.mail.sys.from}")
    private lateinit var fromAddress: String
    @Value("\${easy.core.mail.sys.to}")
    private lateinit var toSysAddress: String
    @Value("\${easy.core.mail.sys.enabled}")
    private var enabled: Boolean = true

    @Async
    fun sendSystemNotification(message: String, id: String) {
        if (!enabled) {
            log.info { "System notifications disabled, ignoring" }
            return
        }

        val time = Instant.now().toString()
        log.info("Sending notification $id ($time) from $fromAddress to $toSysAddress")

        val bodyText = "$time\n$id\n\n$message"

        val mailMessage = SimpleMailMessage()
        mailMessage.setSubject("Easy:core system notification $id")
        mailMessage.setText(bodyText)
        mailMessage.setTo(toSysAddress)
        mailMessage.setFrom(fromAddress)
        mailSender.send(mailMessage)

        log.info("Sent notification $id")
    }
}