/**
 * Members NeoForge's patches add to vanilla classes, in a form Fabric can attach. Instance methods live in interfaces
 * that the class tweaker injects into the vanilla class, as default methods, so both our code and mods compiled against
 * NeoForge find them on the vanilla class. Static methods can't be added that way (nor by a mixin), so they live here
 * as plain static methods that our code calls instead.
 */
@NullMarked
package net.salutaryfish.versionunbound.neoforge.patches;

import org.jspecify.annotations.NullMarked;
