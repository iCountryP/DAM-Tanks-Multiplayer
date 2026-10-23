package com.project;

import javafx.event.EventHandler;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javafx.geometry.Point2D;

import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

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

    private boolean up, down, left, right;

    // Tanques que dibujamos (más adelante los moverá el STATE)
    private final Tank tank1 = new Tank(new Point2D(100, 250), 0, 0, Color.DODGERBLUE);
    private final Tank tank2 = new Tank(new Point2D(700, 250), 180, 180, Color.DARKGOLDENROD);

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        gc = canvas.getGraphicsContext2D();
        
        timer = new CnvTimer(
            new Consumer<Double>() {
                @Override
                public void accept(Double fps) {
                    // La lógica la hace el servidor
                }
            },
            new Runnable() {
                @Override
                public void run() {
                    redraw();
                }
            },
            60
        );
        timer.start();
        
        // La escena todavía no existe aquí: esperamos a que esté creada
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                configureInput();
            }
        });
    }

    private void redraw() {
        gc.setFill(Color.WHITESMOKE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        
        tank1.draw(gc);
        tank2.draw(gc);
    }

    public void updateState(JsonObject msg) {
        for (JsonElement element : msg.getAsJsonArray("players")) {
            JsonObject player = element.getAsJsonObject();

            Tank tank;

            if (player.get("id").getAsInt() == 1) {
                tank = tank1;
            } else {
                tank = tank2;
            }
            
            tank.setPosition(new Point2D(player.get("x").getAsDouble(), player.get("y").getAsDouble()));
            tank.setAngle(player.get("angle").getAsDouble());
            tank.setTurretAngle(player.get("turretAngle").getAsDouble());
        }
    }

    private void configureInput() {
        canvas.getScene().setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent event) {
                onKey(event.getCode(), true);
            }
        });

        canvas.getScene().setOnKeyReleased(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent event) {
                onKey(event.getCode(), false);
            }
        });
    }

    private void onKey(KeyCode code, boolean pressed) {
        if (!UtilsViews.getCurrentView().equals("ViewGame")) return;

        boolean oldUp = up;
        boolean oldDown = down;
        boolean oldLeft = left;
        boolean oldRight = right;

        if (code == KeyCode.W || code == KeyCode.UP) {
            up = pressed;
        }
        if (code == KeyCode.S || code == KeyCode.DOWN) {
            down = pressed;
        }
        if (code == KeyCode.A || code == KeyCode.LEFT) {
            left = pressed;
        }
        if (code == KeyCode.D || code == KeyCode.RIGHT) {
            right = pressed;
        }

        boolean changed = false;

        if (up != oldUp) {
            changed = true;
        }
        if (down != oldDown) {
            changed = true;
        }
        if (left != oldLeft) {
            changed = true;
        }
        if (right != oldRight) {
            changed = true;
        }
        
        if (changed) {
            sendInput();
        }
    }

    private void sendInput() {
        if (Main.client == null || !Main.client.isOpen()) {
            return;
        }

        JsonObject msg = new JsonObject();
        msg.addProperty("type", "INPUT");
        msg.addProperty("up", up);
        msg.addProperty("down", down);
        msg.addProperty("left", left);
        msg.addProperty("right", right);

        Main.client.send(msg.toString());
    }
}
