package com.project;

import com.google.gson.JsonObject;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class CtrlResult {

    @FXML 
    private Label lblWinner;
    @FXML 
    private Label lblLoser;

    public void showResult(String winner, String loser) {
        lblWinner.setText("Ganador: " + winner);
        lblLoser.setText("Perdedor: " + loser);
    }

    @FXML
    private void actionBack() {
        if (Main.client == null || !Main.client.isOpen()) {
            UtilsViews.setView("ViewConnect");
            return;
        }

        JsonObject msg = new JsonObject();
        msg.addProperty("type", "READY");
        Main.client.send(msg.toString());

        UtilsViews.setView("ViewWaiting");
    }
}