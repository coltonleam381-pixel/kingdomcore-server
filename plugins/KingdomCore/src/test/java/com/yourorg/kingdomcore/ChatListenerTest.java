package com.yourorg.kingdomcore;

import com.yourorg.kingdomcore.gui.MenuFactory;
import com.yourorg.kingdomcore.listeners.ChatListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.*;

public class ChatListenerTest {
    @Test
    void defersReviveChatHandlingToSync() {
        Plugin plugin = mock(Plugin.class);
        MenuFactory menuFactory = mock(MenuFactory.class);
        Player player = mock(Player.class);
        AsyncPlayerChatEvent event = mock(AsyncPlayerChatEvent.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);

        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(event.getPlayer()).thenReturn(player);
        when(event.getMessage()).thenReturn("Target");
        when(menuFactory.isRevivePending(playerId)).thenReturn(true);

        AtomicReference<Runnable> captured = new AtomicReference<>();
        doAnswer(invocation -> {
            captured.set(invocation.getArgument(1));
            return null;
        }).when(scheduler).runTask(eq(plugin), any(Runnable.class));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

            ChatListener listener = new ChatListener(plugin, menuFactory);
            listener.onChat(event);

            verify(event).setCancelled(true);
            verify(menuFactory, never()).handleReviveChat(any(), any());

            captured.get().run();
            verify(menuFactory).handleReviveChat(player, "Target");
        }
    }
}
