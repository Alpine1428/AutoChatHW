package com.holyworld.autoreply.handler;

import com.holyworld.autoreply.HolyWorldAutoReply;
import com.holyworld.autoreply.ai.ResponseEngine;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.*;

public class ChatHandler {

    // Pattern to match [CHECK] messages
    // Format: §d§l[CHECK] §f<PlayerName> §5-> <message>
    private static final Pattern CHECK_PATTERN = Pattern.compile(
        "\\[CHECK\\]\\s*§f(\\S+)\\s*§5->\\s*(.*)"
    );
    
    // Alternative pattern without color codes (stripped)
    private static final Pattern CHECK_PATTERN_STRIPPED = Pattern.compile(
        "\\[CHECK\\]\\s+(\\S+)\\s+->\\s+(.*)"
    );

    private final ResponseEngine responseEngine;
    private final ScheduledExecutorService scheduler;
    
    // Cooldown tracking per player
    private final ConcurrentHashMap<String, Long> lastReplyTime = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 2000; // 2 second cooldown per player

    public ChatHandler() {
        this.responseEngine = new ResponseEngine();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AutoReply-Scheduler");
            t.setDaemon(true);
            return t;
        });
        registerListener();
    }

    private void registerListener() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!HolyWorldAutoReply.isEnabled()) return;
            if (overlay) return;
            
            try {
                String rawMessage = message.getString();
                processMessage(rawMessage);
            } catch (Exception e) {
                HolyWorldAutoReply.LOGGER.error("[AutoReply] Error processing message", e);
            }
        });
    }

    private void processMessage(String rawMessage) {
        // Try to find [CHECK] in the message
        if (!rawMessage.contains("[CHECK]")) return;

        String playerName = null;
        String playerMessage = null;

        // Try pattern with color codes
        Matcher matcher = CHECK_PATTERN.matcher(rawMessage);
        if (matcher.find()) {
            playerName = matcher.group(1);
            playerMessage = matcher.group(2);
        }
        
        // Try stripped pattern
        if (playerName == null) {
            matcher = CHECK_PATTERN_STRIPPED.matcher(rawMessage);
            if (matcher.find()) {
                playerName = matcher.group(1);
                playerMessage = matcher.group(2);
            }
        }

        // Fallback: manual parse
        if (playerName == null) {
            int checkIdx = rawMessage.indexOf("[CHECK]");
            if (checkIdx >= 0) {
                String after = rawMessage.substring(checkIdx + 7).trim();
                // Remove color codes
                after = after.replaceAll("§[0-9a-fk-or]", "").trim();
                int arrowIdx = after.indexOf("->");
                if (arrowIdx > 0) {
                    playerName = after.substring(0, arrowIdx).trim();
                    playerMessage = after.substring(arrowIdx + 2).trim();
                }
            }
        }

        if (playerName == null || playerMessage == null || playerMessage.isEmpty()) return;

        // Clean up color codes from message
        playerMessage = playerMessage.replaceAll("§[0-9a-fk-or]", "").trim();
        playerName = playerName.replaceAll("§[0-9a-fk-or]", "").trim();

        // Cooldown check
        long now = System.currentTimeMillis();
        Long lastTime = lastReplyTime.get(playerName);
        if (lastTime != null && (now - lastTime) < COOLDOWN_MS) {
            return;
        }
        lastReplyTime.put(playerName, now);

        String response = responseEngine.getResponse(playerMessage, playerName);
        if (response != null && !response.isEmpty()) {
            final String finalResponse = response;
            final String finalPlayerName = playerName;
            
            // Delay response by 0.5-1.5 seconds to seem more natural
            long delay = 500 + (long)(Math.random() * 1000);
            scheduler.schedule(() -> {
                sendReply(finalPlayerName, finalResponse);
            }, delay, TimeUnit.MILLISECONDS);
        }
    }

    private void sendReply(String playerName, String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;
        
        // Send as /r or direct message
        // Using /r to reply to the player on check
        client.execute(() -> {
            if (client.player != null) {
                String cmd = "/r " + message;
                client.player.networkHandler.sendChatCommand(cmd.substring(1)); // remove leading /
            }
        });
    }
}
