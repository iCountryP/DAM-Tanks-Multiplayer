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
    private boolean stateShown = false;

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
        } else if (!stateShown) {
            System.out.println("Primer STATE: " + message);
            stateShown = true;
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
        CtrlResult ctrlResult = (CtrlResult) UtilsViews.getController("ViewResult");

        if (type.equals("WELCOME")) {
            playerId = msg.get("playerId").getAsInt();
        } else if (type.equals("WAITING")) {
            ctrlWaiting.showWaiting();
        } else if (type.equals("COUNTDOWN")) {
            ctrlWaiting.showCountdown(msg.get("seconds").getAsInt());
        } else if (type.equals("START")) {
            ctrlGame.startGame(msg);
            UtilsViews.setView("ViewGame");
        } else if (type.equals("STATE")) {
            ctrlGame.updateState(msg);
        } else if (type.equals("GAME_OVER")) {
            String winner = msg.get("winner").getAsString();
            String loser = msg.get("loser").getAsString();
            ctrlResult.showResult(winner, loser);
            UtilsViews.setView("ViewResult");
        } else if (type.equals("REMATCH_WAITING")) {
            int ready = msg.get("ready").getAsInt();
            int total = msg.get("total").getAsInt();
            ctrlWaiting.showRematch(ready, total);
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