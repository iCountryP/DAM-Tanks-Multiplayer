package com.project;

import java.net.URI;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;

public class GameClient extends WebSocketClient {

    private final String playerName;
    private int playerId;
    
    public int getPlayerId() {
        return playerId;
    }

    public GameClient(String url, String playerName) throws Exception {
        super(new URI(url));
        this.playerName = playerName;
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        System.out.println("Conectado al servidor");

        JsonObject msg = new JsonObject();
        msg.addProperty("type", "JOIN");
        msg.addProperty("name", playerName);
        send(msg.toString());
    }

    @Override
    public void onMessage(String message) {
        // System.out.println("Recibido: " + message);

        JsonObject msg = JsonParser.parseString(message).getAsJsonObject();
        String type = msg.get("type").getAsString();
        
        if (!type.equals("STATE")) {
            System.out.println("Recibido: " + message);
        }

        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                handleMessage(type, msg);
            }
        });
    }

    private void handleMessage(String type, JsonObject msg) {
        CtrlWaiting ctrlWaiting = (CtrlWaiting) UtilsViews.getController("ViewWaiting");
        CtrlGame ctrlGame = (CtrlGame) UtilsViews.getController("ViewGame");

        if (type.equals("WELCOME")) {
            playerId = msg.get("playerId").getAsInt();
        } else if (type.equals("WAITING")) {
            ctrlWaiting.showWaiting();
        } else if (type.equals("COUNTDOWN")) {
            ctrlWaiting.showCountdown(msg.get("seconds").getAsInt());
        } else if (type.equals("START")) {
            UtilsViews.setView("ViewGame");
        } else if (type.equals("STATE")) {
            ctrlGame.updateState(msg);
        }
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