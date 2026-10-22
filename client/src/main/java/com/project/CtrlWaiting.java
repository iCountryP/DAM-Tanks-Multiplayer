package com.project;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class CtrlWaiting {
    
    @FXML 
    private Label lblStatus;
    @FXML 
    private Label lblCountdown;

    public void showWaiting() {
        lblStatus.setText("Esperando rival...");
        lblCountdown.setText("");
    }

    public void showCountdown(int seconds) {
        lblStatus.setText("Rival encontrado! Empeiza en");
        lblCountdown.setText(String.valueOf(seconds));
    }
}
