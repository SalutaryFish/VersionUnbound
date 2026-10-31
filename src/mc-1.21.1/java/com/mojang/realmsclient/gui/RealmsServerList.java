package com.mojang.realmsclient.gui;

import com.mojang.realmsclient.dto.RealmsServer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;

public class RealmsServerList implements Iterable<RealmsServer> {
    private final Minecraft minecraft;
    private final Set<RealmsServer> removedServers = new HashSet<>();
    private List<RealmsServer> servers = List.of();

    public RealmsServerList(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public void updateServersList(List<RealmsServer> servers) {
        List<RealmsServer> list = new ArrayList<>(servers);
        list.sort(new RealmsServer.McoServerComparator(this.minecraft.getUser().getName()));
        boolean bl = list.removeAll(this.removedServers);
        if (!bl) {
            this.removedServers.clear();
        }

        this.servers = list;
    }

    public void removeItem(RealmsServer server) {
        this.servers.remove(server);
        this.removedServers.add(server);
    }

    @Override
    public Iterator<RealmsServer> iterator() {
        return this.servers.iterator();
    }

    public boolean isEmpty() {
        return this.servers.isEmpty();
    }
}
