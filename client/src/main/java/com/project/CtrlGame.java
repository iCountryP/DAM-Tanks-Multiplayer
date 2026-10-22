package com.project;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;

import javafx.geometry.Point2D;

public class CtrlGame implements Initializable{
    
    @FXML 
    private Canvas canvas;
    
    @FXML 
    private Label lblPlayer1;
    
    @FXML 
    private Label lblPlayer2;
    
    @FXML 
    private Label lblScenario;

    private  GraphicsContext gc;

    private CnvTimer timer;

    // Tanques que dibujamos (más adelante los moverá el STATE)
    private final Tank tank1 = new Tank(new Point2D(100, 250), 0, 0, Color.DODGERBLUE);
    private final Tank tank2 = new Tank(new Point2D(700, 250), 180, 180, Color.DARKGOLDENROD);

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        gc = canvas.getGraphicsContext2D();
        
        timer = new CnvTimer(fps -> { }, this::redraw, 60);
        timer.start();
        
        // redraw();
    }

    private void redraw() {
        gc.setFill(Color.WHITESMOKE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        
        tank1.draw(gc);
        tank2.draw(gc);
    }
}
