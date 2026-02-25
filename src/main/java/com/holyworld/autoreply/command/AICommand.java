package com.holyworld.autoreply.command;

import com.holyworld.autoreply.HolyWorldAutoReply;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.text.Text;
import com.mojang.brigadier.arguments.StringArgumentType;

public class AICommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                ClientCommandManager.literal("ai")
                    .then(ClientCommandManager.literal("start")
                        .executes(context -> {
                            HolyWorldAutoReply.setEnabled(true);
                            context.getSource().sendFeedback(
                                Text.literal("§a§l[AutoReply] §eАвтоответчик §aВКЛЮЧЕН!")
                            );
                            HolyWorldAutoReply.LOGGER.info("[AutoReply] Enabled");
                            return 1;
                        })
                    )
                    .then(ClientCommandManager.literal("stop")
                        .executes(context -> {
                            HolyWorldAutoReply.setEnabled(false);
                            context.getSource().sendFeedback(
                                Text.literal("§c§l[AutoReply] §eАвтоответчик §cВЫКЛЮЧЕН!")
                            );
                            HolyWorldAutoReply.LOGGER.info("[AutoReply] Disabled");
                            return 1;
                        })
                    )
                    .then(ClientCommandManager.literal("status")
                        .executes(context -> {
                            boolean on = HolyWorldAutoReply.isEnabled();
                            context.getSource().sendFeedback(
                                Text.literal("§b§l[AutoReply] §eСтатус: " + (on ? "§aВКЛЮЧЕН" : "§cВЫКЛЮЧЕН"))
                            );
                            return 1;
                        })
                    )
            );
        });
    }
}
