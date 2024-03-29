package core.ems.service.course

import com.fasterxml.jackson.annotation.JsonProperty
import core.conf.security.EasyUser
import core.db.Course
import core.db.Teacher
import core.db.TeacherCourseAccess
import core.ems.service.idToLongOrInvalidReq
import core.exception.InvalidRequestException
import mu.KotlinLogging
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.security.access.annotation.Secured
import org.springframework.web.bind.annotation.*
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

private val log = KotlinLogging.logger {}

@RestController
@RequestMapping("/v2")
class AddTeachersToCourse {

    data class Req(@JsonProperty("teachers") @field:Valid val teacherIds: List<TeacherIdReq>)

    data class TeacherIdReq(@JsonProperty("teacher_id") @field:NotBlank @field:Size(max = 100) val teacherId: String)

    @Secured("ROLE_ADMIN")
    @PostMapping("/courses/{courseId}/teachers")
    fun controller(@PathVariable("courseId") courseIdStr: String,
                   @Valid @RequestBody teachers: Req,
                   caller: EasyUser) {

        log.debug { "Adding access to course $courseIdStr to teachers $teachers" }
        val courseId = courseIdStr.idToLongOrInvalidReq()

        insertTeacherCourseAccesses(courseId, teachers)
    }
}


private fun insertTeacherCourseAccesses(courseId: Long, teachers: AddTeachersToCourse.Req) {

    transaction {
        teachers.teacherIds.forEach { teacher ->
            val teacherExists =
                    Teacher.select { Teacher.id eq teacher.teacherId }
                            .count() == 1
            if (!teacherExists) {
                throw InvalidRequestException("Teacher not found: $teacher")
            }
        }

        val teachersWithoutAccess = teachers.teacherIds.filter {
            TeacherCourseAccess.select {
                TeacherCourseAccess.teacher eq it.teacherId and (TeacherCourseAccess.course eq courseId)
            }.count() == 0
        }

        log.debug { "Granting access to teacher (the rest already have access): $teachersWithoutAccess" }

        TeacherCourseAccess.batchInsert(teachersWithoutAccess) { teacher ->
            this[TeacherCourseAccess.teacher] = EntityID(teacher.teacherId, Teacher)
            this[TeacherCourseAccess.course] = EntityID(courseId, Course)
        }
    }
}
