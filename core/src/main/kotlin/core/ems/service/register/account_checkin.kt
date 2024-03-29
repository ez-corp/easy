package core.ems.service.register

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import core.conf.security.EasyUser
import core.db.*
import mu.KotlinLogging
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.security.access.annotation.Secured
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

private val log = KotlinLogging.logger {}

@RestController
@RequestMapping("/v2")
class UpdateAccountController {

    data class PersonalDataBody(@JsonProperty("email", required = true)
                                @field:NotBlank @field:Size(max = 254) val email: String,
                                @JsonProperty("first_name", required = true)
                                @field:NotBlank @field:Size(max = 100) val firstName: String,
                                @JsonProperty("last_name", required = true)
                                @field:NotBlank @field:Size(max = 100) val lastName: String)

    data class Resp(@JsonProperty("messages")
                    @JsonInclude(JsonInclude.Include.NON_NULL) val messages: List<MessageResp>)

    data class MessageResp(@JsonProperty("message") val message: String)

    @Secured("ROLE_STUDENT", "ROLE_TEACHER", "ROLE_ADMIN")
    @PostMapping("/account/checkin")
    fun controller(@Valid @RequestBody dto: PersonalDataBody, caller: EasyUser): Resp {

        val account = AccountData(
                caller.id.toLowerCase(),
                dto.email.toLowerCase(),
                correctNameCapitalisation(dto.firstName),
                correctNameCapitalisation(dto.lastName))

        log.debug { "Update personal data for $account" }
        updateAccount(account)

        if (caller.isStudent()) {
            log.debug { "Update student ${caller.id}" }
            updateStudent(account)
        }
        if (caller.isTeacher()) {
            log.debug { "Update teacher ${caller.id}" }
            updateTeacher(account)
        }
        if (caller.isAdmin()) {
            log.debug { "Update admin ${caller.id}" }
            updateAdmin(account)
            // Admins should also have a teacher entity to add assessments, exercises etc
            updateTeacher(account)
        }

        return selectMessages()
    }
}

data class AccountData(val username: String, val email: String, val givenName: String, val familyName: String)

private fun correctNameCapitalisation(name: String) =
        name.split(Regex(" +"))
                .joinToString(separator = " ") {
                    it.split("-").joinToString(separator = "-") {
                        it.toLowerCase().capitalize()
                    }
                }

private fun updateAccount(accountData: AccountData) {
    transaction {
        Account.insertOrUpdate(Account.id, listOf(Account.id, Account.createdAt)) {
            it[id] = EntityID(accountData.username, Account)
            it[email] = accountData.email
            it[givenName] = accountData.givenName
            it[familyName] = accountData.familyName
            it[createdAt] = DateTime.now()
        }
    }
}

private fun updateStudent(student: AccountData) {
    transaction {
        addLogger(StdOutSqlLogger)
        Student.insertIgnore {
            it[id] = EntityID(student.username, Student)
            it[createdAt] = DateTime.now()
        }
    }
}

private fun updateTeacher(teacher: AccountData) {
    transaction {
        Teacher.insertIgnore {
            it[id] = EntityID(teacher.username, Teacher)
            it[createdAt] = DateTime.now()
        }
    }
}

private fun updateAdmin(admin: AccountData) {
    transaction {
        Admin.insertIgnore {
            it[id] = EntityID(admin.username, Admin)
            it[createdAt] = DateTime.now()
        }
    }
}

private fun selectMessages(): UpdateAccountController.Resp {
    return transaction {
        UpdateAccountController.Resp(
                ManagementNotification.selectAll()
                        .orderBy(ManagementNotification.id, SortOrder.DESC)
                        .map {
                            UpdateAccountController.MessageResp(
                                    it[ManagementNotification.message]
                            )
                        })
    }
}

