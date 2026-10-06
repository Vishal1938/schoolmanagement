/**
 * Application services (the module's public entry point) for the auth module.
 *
 * <p>Exposed as a Spring Modulith named interface, because this is the only way other modules may
 * touch logins: {@code UserService}, never {@code UserRepository} and never the {@code User}
 * document. B4 validates teaching assignments against {@code isTeacher}, and B5 and B6 create a
 * login alongside a student or employee through {@code createWithUniqueId}.
 *
 * <p>Nothing credential-related crosses this boundary. The types that leave are {@code NewUser} and
 * {@code TeacherRef}; password hashes, lockout state and refresh tokens stay inside.
 */
@org.springframework.modulith.NamedInterface("app")
package com.school.auth.app;
