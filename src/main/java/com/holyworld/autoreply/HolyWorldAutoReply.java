package com.holyworld.autoreply;

import com.holyworld.autoreply.command.AICommand;
import com.holyworld.autoreply.handler.ChatHandler;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HolyWorldAutoReply implements ClientModInitializer {
    public static final String MOD_ID = "holyworld-autoreply";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    private static boolean enabled = false;
    private static ChatHandler chatHandler;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[HolyWorldAutoReply] Mod initialized!");
        chatHandler = new ChatHandler();
        AICommand.register();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean state) {
        enabled = state;
    }

    public static ChatHandler getChatHandler() {
        return chatHandler;
    }
}
