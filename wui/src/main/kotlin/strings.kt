// Top-level property to access strings
var Str: TranslatableStrings = EstStrings


abstract class TranslatableStrings {

    // General

    abstract fun noPermissionForPage(): String
    abstract fun somethingWentWrong(): String
    abstract fun errorDismiss(): String
    abstract fun yes(): String
    abstract fun no(): String

    fun translateBoolean(bool: Boolean) = if (bool) yes() else no()


    // Navbar

    abstract fun topMenuCourses(): String
    abstract fun accountData(): String
    abstract fun logOut(): String
    abstract fun roleChangeStudent(): String
    abstract fun roleChangeBack(): String
    abstract fun roleChangeStudentSuffix(): String


    // Sidenav

    abstract fun sidenavHeader(): String
    abstract fun newExercise(): String
    abstract fun addExistingExercise(): String
    abstract fun participants(): String
    abstract fun grades(): String


    // Courses page

    abstract fun coursesTitle(): String
    abstract fun coursesTitleAdmin(): String
    abstract fun fetchingCoursesFailed(): String
    abstract fun newCourseLink(): String
    abstract fun coursesStudents(): String
    abstract fun coursesStudent(): String


    // New course page

    abstract fun courseCreationFailed(): String
    abstract fun newCourseName(): String
    abstract fun addNewCourse(): String


    // Exercises page

    abstract fun courses(): String
    abstract fun autoAssessLabel(): String
    abstract fun teacherAssessLabel(): String
    abstract fun missingAssessLabel(): String
    abstract fun deadlineLabel(): String
    abstract fun completedLabel(): String
    abstract fun startedLabel(): String
    abstract fun ungradedLabel(): String
    abstract fun unstartedLabel(): String
}


private object EstStrings : TranslatableStrings() {
    override fun yes() = "jah"
    override fun no() = "ei"
    override fun errorDismiss() = "Sain aru"
    override fun topMenuCourses() = "Minu kursused"
    override fun noPermissionForPage() = "Teil puudub õigus selle lehe vaatamiseks."
    override fun newCourseLink() = "Uus kursus"
    override fun coursesStudents() = "õpilast"
    override fun coursesStudent() = "õpilane"
    override fun addNewCourse() = "Lisa uus kursus"
    override fun newCourseName() = "Uue kursuse nimi"
    override fun somethingWentWrong() = "Midagi läks valesti... Proovi lehte uuendada ja kui viga kordub, siis võta ühendust administraatoriga."
    override fun fetchingCoursesFailed() = "Kursuste laadimine ebaõnnestus."
    override fun courseCreationFailed() = "Uue kursuse loomine ebaõnnestus."
    override fun coursesTitleAdmin() = "Kõik kursused"
    override fun accountData() = "Konto andmed"
    override fun logOut() = "Logi välja"
    override fun roleChangeStudent() = "Digimuutu õpilaseks"
    override fun roleChangeBack() = "Taasmuutu põhirollile"
    override fun roleChangeStudentSuffix() = " (õpilane)"
    override fun sidenavHeader() = "Kursuse seaded"
    override fun newExercise() = "Uus ülesanne"
    override fun addExistingExercise() = "Lisa olemasolev ülesanne"
    override fun participants() = "Osalejad"
    override fun grades() = "Hinded"
    override fun coursesTitle() = "Minu kursused"
    override fun courses() = "Minu kursused"
    override fun deadlineLabel() = "Tähtaeg"
    override fun autoAssessLabel() = "Automaatselt hinnatud"
    override fun teacherAssessLabel() = "Käsitsi hinnatud"
    override fun missingAssessLabel() = "Pole veel hinnatud"
    override fun completedLabel() = "Lõpetanud"
    override fun startedLabel() = "Nässu läinud"
    override fun ungradedLabel() = "Hindamata"
    override fun unstartedLabel() = "Esitamata"
}
