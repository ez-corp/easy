package ee.urgas.aas.conf.security

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority


class EasyUser(val email: String, val givenName: String, val familyName: String, val roles: Set<EasyGrantedAuthority>) :
        AbstractAuthenticationToken(roles) {

    // We have no credentials
    override fun getCredentials(): Any? = null

    override fun getPrincipal(): Any = email

    fun isStudent(): Boolean = roles.contains(EasyGrantedAuthority(EasyRole.STUDENT))

    fun isTeacher(): Boolean = roles.contains(EasyGrantedAuthority(EasyRole.TEACHER))
}

class EasyGrantedAuthority(private val role: EasyRole) : GrantedAuthority {

    override fun getAuthority(): String = role.roleWithPrefix

    override fun equals(other: Any?): Boolean {
        return if (other is EasyGrantedAuthority) {
            role == other.role
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        return role.hashCode()
    }
}

enum class EasyRole(val roleWithPrefix: String) {
    STUDENT("ROLE_STUDENT"),
    TEACHER("ROLE_TEACHER")
}