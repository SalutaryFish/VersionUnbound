package net.salutaryfish.versionunbound.neoforge.mixin.coremods;

import org.spongepowered.asm.mixin.Mixin;

/**
 * Carrier for NeoForge's {@code finalizeSpawn} coremod: {@code CoremodMixinPlugin} replaces every
 * {@code invokevirtual Mob.finalizeSpawn} in these classes with {@code EventHooks.finalizeMobSpawn}, which fires
 * {@code FinalizeSpawnEvent}. The targets are NeoForge's {@code finalize_spawn_targets.json}, verbatim: every 26.2 class
 * that calls {@code finalizeSpawn} with {@code invokevirtual}, except the two spawners, whose mixins fire the event
 * themselves ({@code BaseSpawnerMixin}, {@code TrialSpawnerMixin}). Calls through {@code super} are
 * {@code invokespecial} and stay. Regenerate the list for another Minecraft version (NeoForge's
 * {@code GenerateFinalizeSpawnTargets}).
 */
@Mixin(targets = {
        "net.minecraft.gametest.framework.GameTestEntityBuilder",
        "net.minecraft.server.commands.RaidCommand",
        "net.minecraft.server.commands.SummonCommand",
        "net.minecraft.world.entity.EntityType",
        "net.minecraft.world.entity.ai.village.VillageSiege",
        "net.minecraft.world.entity.animal.equine.SkeletonTrapGoal",
        "net.minecraft.world.entity.animal.equine.ZombieHorse",
        "net.minecraft.world.entity.animal.frog.Tadpole",
        "net.minecraft.world.entity.monster.Strider",
        "net.minecraft.world.entity.monster.illager.Evoker$EvokerSummonSpellGoal",
        "net.minecraft.world.entity.monster.spider.Spider",
        "net.minecraft.world.entity.monster.zombie.Drowned",
        "net.minecraft.world.entity.monster.zombie.Husk",
        "net.minecraft.world.entity.monster.zombie.Zombie",
        "net.minecraft.world.entity.monster.zombie.ZombieVillager",
        "net.minecraft.world.entity.npc.CatSpawner",
        "net.minecraft.world.entity.npc.villager.Villager",
        "net.minecraft.world.entity.raid.Raid",
        "net.minecraft.world.level.NaturalSpawner",
        "net.minecraft.world.level.levelgen.PatrolSpawner",
        "net.minecraft.world.level.levelgen.PhantomSpawner",
        "net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces$OceanMonumentPiece",
        "net.minecraft.world.level.levelgen.structure.structures.OceanRuinPieces$OceanRuinPiece",
        "net.minecraft.world.level.levelgen.structure.structures.SwampHutPiece",
        "net.minecraft.world.level.levelgen.structure.structures.WoodlandMansionPieces$WoodlandMansionPiece",
        "net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate"
})
abstract class FinalizeSpawnTargetsMixin {}
