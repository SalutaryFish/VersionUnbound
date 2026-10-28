package net.salutaryfish.versionunbound.testmod.lifecycle;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.neoforged.bus.api.Event;

/** A game-bus event this mod posts itself, recording which listeners heard it. */
public class ProbeEvent extends Event {
    public final List<String> heardBy = new CopyOnWriteArrayList<>();
}
