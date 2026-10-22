package com.project;

import java.net.URI;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import com.google.gson.JsonObject;

public class GameClient extends WebSocketClient {

    private final String playerName;

    public GameClient(String url, String playerName) throws Exception {
        super(new URI(url));
        this.playerName = playerName;
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        System.out.println("Conectado al servidor");
    }

    @Override
    public void onMessage(String message) {
        System.out.println("Recibido: " + message);

        JsonObject msg = new JsonObject();
        msg.addProperty("type", "JOIN");
        msg.addProperty("name", playerName);
        send(msg.toString());
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("Conexión cerrada: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        System.out.println("Error: " + ex.getMessage());
    }
}