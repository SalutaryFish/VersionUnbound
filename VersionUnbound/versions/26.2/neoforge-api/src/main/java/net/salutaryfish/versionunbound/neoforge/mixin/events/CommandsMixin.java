package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.EventHooks;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code Commands}: {@code RegisterCommandsEvent} once vanilla's commands are registered (Fabric's
 * {@code CommandRegistrationCallback} fires at the same point), and {@code CommandEvent} before a command runs.
 */
@Mixin(Commands.class)
abstract class CommandsMixin {
    @Shadow
    @Final
    private static Logger LOGGER;

    @Shadow
    @Final
    private CommandDispatcher<CommandSourceStack> dispatcher;

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/CommandDispatcher;setConsumer(Lcom/mojang/brigadier/ResultConsumer;)V"))
    private void versionunbound$registerCommands(Commands.CommandSelection commandSelection, CommandBuildContext context, CallbackInfo ci) {
        EventHooks.onCommandRegister(this.dispatcher, commandSelection, context);
    }

    // NeoForge's code at the head of performCommand; listeners may cancel the command or replace its parse results.
    @WrapMethod(method = "performCommand")
    private void versionunbound$commandEvent(ParseResults<CommandSourceStack> command, String commandString, Operation<Void> original) {
        CommandSourceStack sender = command.getContext().getSource();
        CommandEvent event = new CommandEvent(command);
        if (NeoForge.EVENT_BUS.post(event).isCanceled()) {
            if (event.getException() != null) {
                sender.sendFailure(Component.literal(Util.describeError(event.getException())));
                LOGGER.error("'/{}' threw an exception", commandString, event.getException());
            }
            return;
        }
        command = event.getParseResults();

        original.call(command, commandString);
    }
}
