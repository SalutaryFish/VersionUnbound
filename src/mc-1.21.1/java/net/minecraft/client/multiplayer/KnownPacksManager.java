package net.minecraft.client.multiplayer;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;

public class KnownPacksManager {
    private final PackRepository repository = ServerPacksSource.createVanillaTrustedRepository();
    private final Map<KnownPack, String> knownPackToId;

    public KnownPacksManager() {
        this.repository.reload();
        Builder<KnownPack, String> builder = ImmutableMap.builder();
        this.repository.getAvailablePacks().forEach(pack -> {
            PackLocationInfo packLocationInfo = pack.location();
            packLocationInfo.knownPackInfo().ifPresent(knownPack -> builder.put(knownPack, packLocationInfo.id()));
        });
        this.knownPackToId = builder.build();
    }

    public List<KnownPack> trySelectingPacks(List<KnownPack> packs) {
        List<KnownPack> list = new ArrayList<>(packs.size());
        List<String> list2 = new ArrayList<>(packs.size());

        for (KnownPack knownPack : packs) {
            String string = this.knownPackToId.get(knownPack);
            if (string != null) {
                list2.add(string);
                list.add(knownPack);
            }
        }

        this.repository.setSelected(list2);
        return list;
    }

    public CloseableResourceManager createResourceManager() {
        List<PackResources> list = this.repository.openAllSelected();
        return new MultiPackResourceManager(PackType.SERVER_DATA, list);
    }
}
