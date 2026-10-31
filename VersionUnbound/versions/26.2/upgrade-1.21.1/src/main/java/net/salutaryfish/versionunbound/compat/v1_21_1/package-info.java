/**
 * Bridges for NeoForge 1.21.1 mods: static methods that do what a 1.21.1 member did, with 26.2's API. The upgrader
 * ({@code upgrade/v1_21_1/Mappings}) points a mod's calls of members 26.2 removed or changed here: an instance method's
 * receiver becomes the bridge's first parameter. Each bridge says which 1.21.1 member it stands for and how it differs.
 */
package net.salutaryfish.versionunbound.compat.v1_21_1;
