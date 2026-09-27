/**
 * Cross-cutting building blocks: security, JWT, permissions, exceptions, audit, ID generation,
 * pagination, PDF and storage.
 *
 * <p>Declared as an {@link org.springframework.modulith.ApplicationModule.Type#OPEN open} module so
 * every feature module may depend on it (including its nested packages) without Spring Modulith
 * flagging the reference. Feature modules must still talk to each other only through the other
 * module's {@code app} services or via application events.
 */
@org.springframework.modulith.ApplicationModule(
		type = org.springframework.modulith.ApplicationModule.Type.OPEN,
		displayName = "Common")
package com.school.common;
