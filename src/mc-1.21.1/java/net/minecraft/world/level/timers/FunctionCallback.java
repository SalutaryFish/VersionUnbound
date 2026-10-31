package net.minecraft.world.level.timers;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;

public class FunctionCallback implements TimerCallback<MinecraftServer> {
    final ResourceLocation functionId;

    public FunctionCallback(ResourceLocation functionId) {
        this.functionId = functionId;
    }

    public void handle(MinecraftServer obj, TimerQueue<MinecraftServer> manager, long gameTime) {
        ServerFunctionManager serverFunctionManager = obj.getFunctions();
        serverFunctionManager.get(this.functionId)
            .ifPresent(
                commandFunction -> serverFunctionManager.execute(
                    (CommandFunction<CommandSourceStack>)commandFunction, serverFunctionManager.getGameLoopSender()
                )
            );
    }

    public static class Serializer extends TimerCallback.Serializer<MinecraftServer, FunctionCallback> {
        public Serializer() {
            super(ResourceLocation.withDefaultNamespace("function"), FunctionCallback.class);
        }

        public void serialize(CompoundTag compoundTag, FunctionCallback functionCallback) {
            compoundTag.putString("Name", functionCallback.functionId.toString());
        }

        public FunctionCallback deserialize(CompoundTag compoundTag) {
            ResourceLocation resourceLocation = ResourceLocation.parse(compoundTag.getString("Name"));
            return new FunctionCallback(resourceLocation);
        }
    }
}
