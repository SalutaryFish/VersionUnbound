package net.minecraft.client.multiplayer.chat.report;

import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.client.multiplayer.chat.ChatLog;
import net.minecraft.client.multiplayer.chat.LoggedChatMessage;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.PlayerChatMessage;

public class ChatReportContextBuilder {
    final int leadingCount;
    private final List<ChatReportContextBuilder.Collector> activeCollectors = new ArrayList<>();

    public ChatReportContextBuilder(int leadingCount) {
        this.leadingCount = leadingCount;
    }

    public void collectAllContext(ChatLog chatLog, IntCollection reportedMessages, ChatReportContextBuilder.Handler handler) {
        IntSortedSet intSortedSet = new IntRBTreeSet(reportedMessages);

        for (int i = intSortedSet.lastInt(); i >= chatLog.start() && (this.isActive() || !intSortedSet.isEmpty()); i--) {
            if (chatLog.lookup(i) instanceof LoggedChatMessage.Player player) {
                boolean bl = this.acceptContext(player.message());
                if (intSortedSet.remove(i)) {
                    this.trackContext(player.message());
                    handler.accept(i, player);
                } else if (bl) {
                    handler.accept(i, player);
                }
            }
        }
    }

    public void trackContext(PlayerChatMessage lastChainMessage) {
        this.activeCollectors.add(new ChatReportContextBuilder.Collector(lastChainMessage));
    }

    public boolean acceptContext(PlayerChatMessage lastChainMessage) {
        boolean bl = false;
        Iterator<ChatReportContextBuilder.Collector> iterator = this.activeCollectors.iterator();

        while (iterator.hasNext()) {
            ChatReportContextBuilder.Collector collector = iterator.next();
            if (collector.accept(lastChainMessage)) {
                bl = true;
                if (collector.isComplete()) {
                    iterator.remove();
                }
            }
        }

        return bl;
    }

    public boolean isActive() {
        return !this.activeCollectors.isEmpty();
    }

    class Collector {
        private final Set<MessageSignature> lastSeenSignatures;
        private PlayerChatMessage lastChainMessage;
        private boolean collectingChain = true;
        private int count;

        Collector(final PlayerChatMessage lastChainMessage) {
            this.lastSeenSignatures = new ObjectOpenHashSet<>(lastChainMessage.signedBody().lastSeen().entries());
            this.lastChainMessage = lastChainMessage;
        }

        boolean accept(PlayerChatMessage message) {
            if (message.equals(this.lastChainMessage)) {
                return false;
            }

            boolean bl = this.lastSeenSignatures.remove(message.signature());
            if (this.collectingChain && this.lastChainMessage.sender().equals(message.sender())) {
                if (this.lastChainMessage.link().isDescendantOf(message.link())) {
                    bl = true;
                    this.lastChainMessage = message;
                } else {
                    this.collectingChain = false;
                }
            }

            if (bl) {
                this.count++;
            }

            return bl;
        }

        boolean isComplete() {
            return this.count >= ChatReportContextBuilder.this.leadingCount || !this.collectingChain && this.lastSeenSignatures.isEmpty();
        }
    }

    public interface Handler {
        void accept(int var1, LoggedChatMessage.Player var2);
    }
}
