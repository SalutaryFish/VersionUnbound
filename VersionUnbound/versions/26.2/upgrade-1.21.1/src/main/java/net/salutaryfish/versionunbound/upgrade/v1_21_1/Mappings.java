package net.salutaryfish.versionunbound.upgrade.v1_21_1;

import java.util.List;

/**
 * What changed between NeoForge 1.21.1 (Minecraft 1.21.1) and NeoForge 26.2 (Minecraft 26.2), as far as the upgrader
 * knows it: hand-written for now, entry by entry for what the corpus needs (Chunky so far). The plan is to generate the
 * renames by diffing the two versions' sources and curate the rest by hand (HANDOFF.md §9).
 * <p>
 * Classes are renamed first, so the member entries name classes as 26.2 does ({@code Identifier}, not
 * {@code ResourceLocation}) and members as 1.21.1 did. A member entry's owner is the class that declared the member in
 * 1.21.1; it matches a reference whose owner is that class or, in 26.2's hierarchy, a subtype of it (a call of
 * {@code getMinBuildHeight} on a {@code ServerLevel} names {@code ServerLevel}).
 */
final class Mappings {
    private Mappings() {}

    private static final String BRIDGES = "net/salutaryfish/versionunbound/compat/v1_21_1/";

    /** A class 26.2 renamed or moved; its nested classes ({@code from$...}) follow it. */
    record ClassRename(String from, String to) {}

    /** A method 26.2 renamed, with the same descriptor. */
    record MethodRename(String owner, String name, String descriptor, String newName) {}

    /**
     * A method 26.2 removed or changed, whose calls go to {@code bridge.bridgeName}: for an instance method a static
     * method taking the receiver first ({@code (Lowner;...)}), for a static one a static method of the same descriptor.
     */
    record Redirect(String owner, String name, String descriptor, boolean isStatic, String bridge, String bridgeName) {
        String bridgeDescriptor() {
            return isStatic ? descriptor : "(L" + owner + ";" + descriptor.substring(1);
        }
    }

    /**
     * A constructor 26.2 gave leading parameters: {@code new owner(args)} becomes {@code new owner(prefix, args)},
     * {@code prefix} from the static no-argument {@code bridge.bridgeName}.
     */
    record ConstructorPrefix(String owner, String descriptor, String prefixDescriptor, String bridge, String bridgeName) {
        String newDescriptor() {
            return "(" + prefixDescriptor + descriptor.substring(1);
        }
    }

    static final List<ClassRename> CLASSES = List.of(
            new ClassRename("net/minecraft/resources/ResourceLocation", "net/minecraft/resources/Identifier"));

    static final List<MethodRename> METHOD_RENAMES = List.of(
            new MethodRename("net/minecraft/core/RegistryAccess", "registry", "(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;", "lookup"),
            new MethodRename("net/minecraft/resources/ResourceKey", "location", "()Lnet/minecraft/resources/Identifier;", "identifier"),
            new MethodRename("net/minecraft/world/level/LevelHeightAccessor", "getMinBuildHeight", "()I", "getMinY"),
            // 26.2's ServerPlayer.level() returns ServerLevel, as serverLevel() did.
            new MethodRename("net/minecraft/server/level/ServerPlayer", "serverLevel", "()Lnet/minecraft/server/level/ServerLevel;", "level"),
            new MethodRename("net/minecraft/world/level/ChunkPos", "toLong", "()J", "pack"));

    static final List<Redirect> REDIRECTS = List.of(
            new Redirect("net/minecraft/commands/CommandSourceStack", "hasPermission", "(I)Z", false, BRIDGES + "CommandSourceStackBridge", "hasPermission"),
            new Redirect("net/minecraft/world/entity/Entity", "hasPermissions", "(I)Z", false, BRIDGES + "EntityBridge", "hasPermissions"),
            new Redirect("net/minecraft/world/entity/player/Player", "displayClientMessage", "(Lnet/minecraft/network/chat/Component;Z)V", false,
                    BRIDGES + "PlayerBridge", "displayClientMessage"),
            new Redirect("net/minecraft/server/level/ServerPlayer", "teleportTo", "(Lnet/minecraft/server/level/ServerLevel;DDDFF)V", false,
                    BRIDGES + "ServerPlayerBridge", "teleportTo"),
            new Redirect("net/minecraft/world/level/Level", "getSharedSpawnPos", "()Lnet/minecraft/core/BlockPos;", false, BRIDGES + "LevelBridge", "getSharedSpawnPos"),
            new Redirect("net/minecraft/world/level/Level", "getSharedSpawnAngle", "()F", false, BRIDGES + "LevelBridge", "getSharedSpawnAngle"),
            new Redirect("net/minecraft/world/level/Level", "playSound",
                    "(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V", false,
                    BRIDGES + "LevelBridge", "playSound"),
            new Redirect("net/minecraft/nbt/CompoundTag", "contains", "(Ljava/lang/String;I)Z", false, BRIDGES + "CompoundTagBridge", "contains"),
            new Redirect("net/minecraft/nbt/CompoundTag", "getString", "(Ljava/lang/String;)Ljava/lang/String;", false, BRIDGES + "CompoundTagBridge", "getString"),
            new Redirect("net/minecraft/server/level/ServerChunkCache", "addRegionTicket",
                    "(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V", false,
                    BRIDGES + "ServerChunkCacheBridge", "addRegionTicket"),
            new Redirect("net/minecraft/server/level/ServerChunkCache", "removeRegionTicket",
                    "(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V", false,
                    BRIDGES + "ServerChunkCacheBridge", "removeRegionTicket"),
            new Redirect("net/minecraft/server/level/TicketType", "create", "(Ljava/lang/String;Ljava/util/Comparator;)Lnet/minecraft/server/level/TicketType;", true,
                    BRIDGES + "TicketTypeBridge", "create"),
            new Redirect("net/minecraft/server/level/TicketType", "create", "(Ljava/lang/String;Ljava/util/Comparator;I)Lnet/minecraft/server/level/TicketType;", true,
                    BRIDGES + "TicketTypeBridge", "create"));

    static final List<ConstructorPrefix> CONSTRUCTORS = List.of(
            new ConstructorPrefix("net/minecraft/server/level/ServerBossEvent",
                    "(Lnet/minecraft/network/chat/Component;Lnet/minecraft/world/BossEvent$BossBarColor;Lnet/minecraft/world/BossEvent$BossBarOverlay;)V",
                    "Ljava/util/UUID;", BRIDGES + "ServerBossEventBridge", "id"));

    /** Changes whenever an entry does, so jars upgraded with other mappings aren't taken from the cache. */
    static String fingerprint() {
        return Integer.toHexString(List.of(CLASSES, METHOD_RENAMES, REDIRECTS, CONSTRUCTORS).toString().hashCode());
    }
}
