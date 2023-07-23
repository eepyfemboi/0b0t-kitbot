package com.cocfire;

import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.CPacketClientStatus;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraft.client.settings.KeyBinding;

import org.apache.commons.io.IOUtils;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.reflect.Field;

@Mod(modid = KitBot.MODID, name = KitBot.NAME, version = KitBot.VERSION)
@EventBusSubscriber(modid = KitBot.MODID)
public class KitBot {
    public static final String MODID = "kitbot";
    public static final String NAME = "0b0t KitBot";
    public static final String VERSION = "1.0.3";
    private boolean waitingForRespawn = false;
    private boolean scheduledMoveForward = false;
    private boolean waitingForKill = false;
    private boolean suicideMessageScheduled = false;
    public static boolean enabledBot = true;
    public static boolean enabledMessages = true;
    private boolean killDelayed = false;
    private boolean waitingToKill = false;

    private int ticksWaited = 0;
    private int ticksElapsed = 0;
    private int killTicksWaited = 0;
    private int messageTicksWaited = 0;
    private int killDelayTicks = 0;
    public static int messageDelay = 5000;
    public static int killDelay = 10;
    private int killDelayWaited = 0;
    public static int respawnDelay = 40;
    private int respawnDelayWaited = 0;
    public static int moveDelay = 4;
    private int moveDelayWaited = 0;
    public static int moveTime = 60;
    private int moveTimeWaited = 0;

    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        String message = event.getMessage().getUnformattedText();

        Optional<Map.Entry<String, String>> parsedMessage = parseChatMessage(message);

        if (parsedMessage.isPresent()) {
            String username = parsedMessage.get().getKey();

            if (username.contains("ERROR_EEOC")) {
                if (message.toLowerCase().contains("=block") && getLastWord(message) != "=block") {
                    sendMessage("Now ignoring '" + getLastWord(message) + "'");
                    sendMessage("/ignore " + getLastWord(message));
                } else if (message.toLowerCase().contains("=unblock") && getLastWord(message) != "=unblock") {
                    sendMessage("Now unignoring `" + getLastWord(message) + "`");
                    sendMessage("/ignore " + getLastWord(message));
                } else if (message.toLowerCase().contains("=set")){
                    modifyVariable(message);
                }
            }

            if (!username.contains("WhatNow") && enabledBot) {
                if (message.toLowerCase().contains("kit")) {
                    doTpaRequest(username);
                } else if (message.toLowerCase().contains("clit")) {
                    doTpaRequest(username);
                }
            }
        }

        if (message.toLowerCase().contains("error_eeoc wants to teleport to you.")) {
            Minecraft.getMinecraft().player.sendChatMessage("/tpy ERROR_EEOC");
        }

        if (message.contains("teleporting to:") && enabledBot) {
            sendWebhookMessage(message);
            waitingToKill = true;
        } else {
            sendWebhookMessage("`" + message + "`");
        }

        if (message.contains("Death is too busy at the moment") && enabledBot && !waitingForKill && !killDelayed) {
            scheduledMoveForward = true;
            suicideMessageScheduled = true;
            waitingForKill = true;
            killDelayed = true;
        }
    }

    public void modifyVariable(String input) {
        String prefix = "<ERROR_EEOC> =set ";
        if (input.startsWith(prefix)) {
            String[] parts = input.substring(prefix.length()).split("\\s+", 2);
            if (parts.length == 2) {
                String variableName = parts[0];
                String variableValue = parts[1];
                setVariable(variableName, variableValue);
                sendMessage("Variable \"" + variableName + "\" set to \"" + variableValue + "\"");
            } else {
                sendMessage("Invalid input format");
            }
        } else {
            sendMessage("Invalid input");
        }
    }

    public void setVariable(String variableName, String variableValue) {
        try {
            Field field = KitBot.class.getDeclaredField(variableName);
            field.setAccessible(true);

            Class<?> fieldType = field.getType();
            Object parsedValue = parseValue(variableValue, fieldType);

            field.set(null, parsedValue);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            sendMessage("Setting \"" + variableName + "\" does not exist");
        }
    }

    public static Object parseValue(String variableValue, Class<?> fieldType) {
        if (fieldType == boolean.class || fieldType == Boolean.class) {
            return Boolean.parseBoolean(variableValue);
        } else if (fieldType == int.class || fieldType == Integer.class) {
            return Integer.parseInt(variableValue);
        } else if (fieldType == double.class || fieldType == Double.class) {
            return Double.parseDouble(variableValue);
        } else if (fieldType == String.class) {
            return variableValue;
        }

        return null;
    }

    private void sendMessage(String message) {
        Minecraft.getMinecraft().player.sendChatMessage(message);
    }

    private void doTpaRequest(String username) {
        sendWebhookMessage("Attempting to teleport to " + username);
        sendMessage("/tpa " + username);
    }

    private void sendWebhookMessage(String message) {
        try {
            URL url = new URL("");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("user-agent", "Ruby");
            connection.setDoOutput(true);

            String jsonMessage = "{\"content\":\"" + message + "\"}";

            DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
            outputStream.writeBytes(jsonMessage);
            outputStream.flush();
            outputStream.close();


            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                String response = IOUtils.toString(connection.getInputStream(), StandardCharsets.UTF_8);
            } else if (responseCode != 204) {
                System.out.println("discord webhook didn't send. L. Response code: " + responseCode);
            }
            connection.disconnect();
        } catch (Exception e) {
            System.out.println("error while sending discord webhook. L.");
        }
    }

    public void playerKill() {
        sendMessage("/kill");
        waitingForRespawn = true;
        waitingForKill = false;
        killTicksWaited = 0;
    }

    public void sendChatAd() {
        sendMessage("To get a free high-quality kit, simply use '=kit' and accept the TPA from me. If you're worried about coord loggers, just TPA to moooomoooo and use me there!");
    }

    public void playerMoveForward() {
        Minecraft.getMinecraft().player.setSprinting(true);
        KeyBinding.setKeyBindState(33, true);
        KeyBinding.setKeyBindState(57, true);
    }

    public void playerPerformRespawn() {
        Minecraft.getMinecraft().player.connection.sendPacket(new CPacketClientStatus(CPacketClientStatus.State.PERFORM_RESPAWN));
        waitingForRespawn = false;
        scheduledMoveForward = true;
        respawnDelayWaited = 0;
    }

    public void sendSuicideMessage() {
        sendMessage("Suicide hotline saved me... for now. (wait a few seconds)");
        suicideMessageScheduled = false;
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (killDelayed) {
            if (killDelayTicks >= 6000) {
                killDelayed = false;
                killDelayTicks = 0;
            } else {
                killDelayTicks++;
            }
        }

        if (waitingToKill) {
            if (killDelayWaited >= killDelay) {
                waitingToKill = false;
                playerKill();
            } else {
                killDelayWaited++;
            }
        }

        if (waitingForRespawn) {
            if (respawnDelayWaited >= respawnDelay) {
                playerPerformRespawn();
            } else {
                respawnDelayWaited++;
            }
        }

        if (waitingForKill) {
            if (killTicksWaited == 200 && Minecraft.getMinecraft().player != null && Minecraft.getMinecraft().world != null) {
                sendMessage("/kill");
            }

            if (killTicksWaited >= 200) {
                waitingForRespawn = true;
                waitingForKill = false;
                killTicksWaited = 0;
            } else {
                killTicksWaited++;
            }
        }

        if (scheduledMoveForward) {
            if (moveTimeWaited >= moveTime) {
                KeyBinding.setKeyBindState(33, false);
                KeyBinding.setKeyBindState(57, false);
                scheduledMoveForward = false;
                moveTimeWaited = 0;
            } else if (Minecraft.getMinecraft().player != null && Minecraft.getMinecraft().world != null) {
                playerMoveForward();
            }
            moveTimeWaited++;
        }

        if (suicideMessageScheduled && Minecraft.getMinecraft().world != null && killTicksWaited >= 60) {
            sendSuicideMessage();
        }

        if (!waitingForRespawn && Minecraft.getMinecraft().player != null && Minecraft.getMinecraft().player.isDead) {
            playerPerformRespawn();
        }

        if (messageTicksWaited >= messageDelay && Minecraft.getMinecraft().world != null && enabledMessages) {
            sendChatAd();
            messageTicksWaited = 0;
        } else if (Minecraft.getMinecraft().world != null && enabledMessages) {
            messageTicksWaited++;
        }
    }

    public String getLastWord(String input) {
        int lastSpaceIndex = input.lastIndexOf(' ');
        if (lastSpaceIndex != -1 && lastSpaceIndex < input.length() - 1) {
            return input.substring(lastSpaceIndex + 1);
        }
        return "";
    }


    public static Optional<Map.Entry<String, String>> parseChatMessage(String messageRaw) {
        Matcher matcher = Pattern.compile("^<(" + "[a-zA-Z0-9_]{3,16}" + ")> (.+)$").matcher(messageRaw);
        String senderName = null;
        String message = null;
        while (matcher.find()) {
            senderName = matcher.group(1);
            message = matcher.group(2);
        }

        if (senderName == null || senderName.isEmpty()) {
            return Optional.empty();
        }

        if (message == null || message.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new AbstractMap.SimpleEntry<>(senderName, message));
    }
}
