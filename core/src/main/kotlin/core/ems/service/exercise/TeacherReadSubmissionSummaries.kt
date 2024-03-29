package core.ems.service.exercise

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import core.conf.security.EasyUser
import core.db.*
import core.ems.service.access.assertTeacherOrAdminHasAccessToCourse
import core.ems.service.idToLongOrInvalidReq
import core.exception.InvalidRequestException
import core.exception.ReqError
import core.util.DateTimeSerializer
import mu.KotlinLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.security.access.annotation.Secured
import org.springframework.web.bind.annotation.*

private val log = KotlinLogging.logger {}

private enum class OrderBy {
    // a - b - c (asc)
    // c - b - a (desc)
    FAMILY_NAME,
    // latest - earliest - no submission (desc)
    // no submission - earliest - latest (asc)
    SUBMISSION_TIME,
    // auto, teacher, missing (asc)
    // teacher, auto, missing (desc)
    GRADED_BY,
    // high, low, missing (desc)
    // missing, low, high (asc)
    GRADE
}

@RestController
@RequestMapping("/v2")
class TeacherReadSubmissionSummariesController {

    data class Resp(@JsonProperty("student_count") val studentCount: Int,
                    @JsonProperty("students") val students: List<StudentsResp>)

    data class StudentsResp(@JsonProperty("student_id") val studentId: String,
                            @JsonProperty("given_name") val studentGivenName: String,
                            @JsonProperty("family_name") val studentFamilyName: String,
                            @JsonSerialize(using = DateTimeSerializer::class)
                            @JsonProperty("submission_time") val submissionTime: DateTime?,
                            @JsonProperty("grade") val grade: Int?,
                            @JsonProperty("graded_by") val gradedBy: GraderType?)

    @Secured("ROLE_TEACHER", "ROLE_ADMIN")
    @GetMapping("/teacher/courses/{courseId}/exercises/{courseExerciseId}/submissions/latest/students")
    fun controller(@PathVariable("courseId") courseIdString: String,
                   @PathVariable("courseExerciseId") courseExerciseIdString: String,
                   @RequestParam("search", required = false, defaultValue = "") searchString: String,
                   @RequestParam("orderby", required = false) orderByString: String?,
                   @RequestParam("order", required = false) orderString: String?,
                   @RequestParam("limit", required = false) limitStr: String?,
                   @RequestParam("offset", required = false) offsetStr: String?,
                   caller: EasyUser): Resp {

        log.debug {
            "Getting submission summaries for ${caller.id} on course exercise $courseExerciseIdString " +
                    "on course $courseIdString (search string: '$searchString', orderby: $orderByString, order: $orderString)"
        }
        val courseId = courseIdString.idToLongOrInvalidReq()

        val orderBy = when (orderByString) {
            "name" -> OrderBy.FAMILY_NAME
            "time" -> OrderBy.SUBMISSION_TIME
            "gradedby" -> OrderBy.GRADED_BY
            "grade" -> OrderBy.GRADE
            null -> OrderBy.FAMILY_NAME
            else -> throw InvalidRequestException("Invalid value for orderby parameter", ReqError.INVALID_PARAMETER_VALUE)
        }
        val order = when (orderString) {
            "asc" -> SortOrder.ASC
            "desc" -> SortOrder.DESC
            null -> SortOrder.ASC
            else -> throw InvalidRequestException("Invalid value for order parameter", ReqError.INVALID_PARAMETER_VALUE)
        }

        assertTeacherOrAdminHasAccessToCourse(caller, courseId)

        val queryWords = searchString.trim().toLowerCase().split(" ").filter { it.isNotEmpty() }

        return selectTeacherSubmissionSummaries(courseId, courseExerciseIdString.idToLongOrInvalidReq(),
                queryWords, orderBy, order, offsetStr?.toIntOrNull(), limitStr?.toIntOrNull())
    }
}

private fun selectTeacherSubmissionSummaries(courseId: Long, courseExId: Long, queryWords: List<String>,
                                             orderBy: OrderBy, order: SortOrder, offset: Int?, limit: Int?):
        TeacherReadSubmissionSummariesController.Resp {
    return transaction {

        // Alias is needed on distinctOn for some reason
        val distinctStudentId = Student.id.distinctOn().alias("student_id")
        // Prevent teacher and auto grade name clash
        val autoGradeAlias = AutomaticAssessment.grade.alias("auto_grade")
        val validGradeAlias = Coalesce(TeacherAssessment.grade, AutomaticAssessment.grade).alias("valid_grade")

        val subQuery = (StudentCourseAccess innerJoin Student innerJoin Account leftJoin
                (Submission innerJoin CourseExercise leftJoin AutomaticAssessment leftJoin TeacherAssessment))
                .slice(distinctStudentId, Account.givenName, Account.familyName, Submission.createdAt,
                        autoGradeAlias, TeacherAssessment.grade, validGradeAlias)
                .select {
                    // CourseExercise.id & CourseExercise.course are null when the student has no submission
                    (CourseExercise.id eq courseExId or CourseExercise.id.isNull()) and
                            (CourseExercise.course eq courseId or CourseExercise.course.isNull()) and
                            (StudentCourseAccess.course eq courseId)
                }
                // These ORDER BY clauses are for selecting correct first rows in DISTINCT groups
                .orderBy(distinctStudentId to SortOrder.ASC,
                        Submission.createdAt to SortOrder.DESC,
                        AutomaticAssessment.createdAt to SortOrder.DESC,
                        TeacherAssessment.createdAt to SortOrder.DESC)

        queryWords.forEach {
            subQuery.andWhere {
                (Student.id like "%$it%") or
                        (Account.givenName.lowerCase() like "%$it%") or
                        (Account.familyName.lowerCase() like "%$it%")
            }
        }

        val subTable = subQuery.alias("t")
        val wrapQuery = subTable
                // Slice is needed because aliased columns are not included by default
                .slice(subTable[distinctStudentId], subTable[Account.givenName], subTable[Account.familyName],
                        subTable[Submission.createdAt], subTable[TeacherAssessment.grade], subTable[autoGradeAlias],
                        subTable[validGradeAlias])
                .selectAll()

        when (orderBy) {
            OrderBy.FAMILY_NAME -> wrapQuery.orderBy(subTable[Account.familyName] to order)
            OrderBy.SUBMISSION_TIME -> wrapQuery.orderBy(
                    subTable[Submission.createdAt].isNull() to order.complement(),
                    subTable[Submission.createdAt] to order)
            OrderBy.GRADED_BY -> wrapQuery.orderBy(
                    (subTable[autoGradeAlias].isNull() and subTable[TeacherAssessment.grade].isNull()) to SortOrder.ASC,
                    subTable[autoGradeAlias] to order,
                    subTable[TeacherAssessment.grade] to order)
            OrderBy.GRADE -> wrapQuery.orderBy(
                    subTable[validGradeAlias].isNull() to order.complement(),
                    subTable[validGradeAlias] to order)
        }

        val resultCount = wrapQuery.count()

        val studentsResp =
                wrapQuery.limit(limit ?: resultCount, offset ?: 0).map {
                    // Explicit nullable types because exposed's type system seems to fail here: it's assuming
                    // non-nullable types as they are in the table, does not account for left joins that create nulls
                    val autoGrade: Int? = it[subTable[autoGradeAlias]]
                    val teacherGrade: Int? = it[subTable[TeacherAssessment.grade]]
                    val validGrade: Int? = it[subTable[validGradeAlias]]

                    val validGradePair = when {
                        teacherGrade != null -> teacherGrade to GraderType.TEACHER
                        autoGrade != null -> autoGrade to GraderType.AUTO
                        else -> null
                    }

                    if (validGrade != validGradePair?.first) {
                        log.warn { "Valid grade is incorrect. From db: $validGrade, real: $validGradePair" }
                    }

                    TeacherReadSubmissionSummariesController.StudentsResp(
                            it[subTable[distinctStudentId]].value,
                            it[subTable[Account.givenName]],
                            it[subTable[Account.familyName]],
                            it[subTable[Submission.createdAt]],
                            validGradePair?.first,
                            validGradePair?.second
                    )
                }

        TeacherReadSubmissionSummariesController.Resp(resultCount, studentsResp)
    }
}
