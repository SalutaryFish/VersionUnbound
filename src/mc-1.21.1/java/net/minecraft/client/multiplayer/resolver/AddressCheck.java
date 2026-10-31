package net.minecraft.client.multiplayer.resolver;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.mojang.blocklist.BlockListSupplier;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Predicate;

public interface AddressCheck {
    boolean isAllowed(ResolvedServerAddress var1);

    boolean isAllowed(ServerAddress var1);

    static AddressCheck createFromService() {
        final ImmutableList<Predicate<String>> immutableList = Streams.stream(ServiceLoader.load(BlockListSupplier.class))
            .map(BlockListSupplier::createBlockList)
            .filter(Objects::nonNull)
            .collect(ImmutableList.toImmutableList());
        return new AddressCheck() {
            @Override
            public boolean isAllowed(ResolvedServerAddress resolvedServerAddress) {
                String string = resolvedServerAddress.getHostName();
                String string2 = resolvedServerAddress.getHostIp();
                return immutableList.stream().noneMatch(filter -> filter.test(string) || filter.test(string2));
            }

            @Override
            public boolean isAllowed(ServerAddress serverAddress) {
                String string = serverAddress.getHost();
                return immutableList.stream().noneMatch(filter -> filter.test(string));
            }
        };
    }
}
