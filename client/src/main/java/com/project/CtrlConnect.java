package com.project;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class CtrlConnect {

    @FXML
    private TextField txtServer;
    @FXML
    private TextField txtName;
    @FXML
    private Label lblError;

    @FXML 
    private void actionConnect() {
        String name = txtName.getText().trim();

        if (name.isEmpty()) {
            lblError.setText("Estribe un nombre");
            return;
        }
        lblError.setText("");

        UtilsViews.setView("ViewWaiting");
    }
}
