package com.project;

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
        // TODO: cerrar la conexión (paso WebSocket)
        UtilsViews.setView("ViewConnect");
    }
}