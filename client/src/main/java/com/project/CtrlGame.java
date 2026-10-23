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

import java.util.ArrayList;
import java.util.List;

import javafx.scene.input.MouseEvent;

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

    private final List<ObjectStatic> walls = new ArrayList<>();

    private final Target target = new Target();

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
        
        for (ObjectStatic wall : walls) {
            wall.draw(gc);
        }

        tank1.draw(gc);
        tank2.draw(gc);

        Tank myTank = getMyTank();
        target.drawGuide(gc, myTank, walls);
        target.draw(gc, myTank);
    }

    public void updateState(JsonObject msg) {
        for (JsonElement element : msg.getAsJsonArray("players")) {
            JsonObject player = element.getAsJsonObject();
            int id = player.get("id").getAsInt();
            Tank tank;

            if (id == 1) {
                tank = tank1;
            } else {
                tank = tank2;
            }
            
            tank.setPosition(new Point2D(player.get("x").getAsDouble(), player.get("y").getAsDouble()));
            tank.setAngle(player.get("angle").getAsDouble());
            tank.setTurretAngle(player.get("turretAngle").getAsDouble());
            
            // Marcador
            String text = buildPlayerText(player);
            if (id == 1) {
                lblPlayer1.setText(text);
            } else {
                lblPlayer2.setText(text);
            }
        }
    }

    private String buildPlayerText(JsonObject player) {
        String name = player.get("name").getAsString();
        int lives = player.get("lives").getAsInt();
        int activeBullets = player.get("activeBullets").getAsInt();

        String hearts = "";
        for (int i = 0; i < lives; i++) {
            hearts = hearts + "♥";
        }

        return name + " " + hearts + "\nProyectiles: " + activeBullets;
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

        canvas.setOnMouseMoved(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                onMouseMoved(event.getX(), event.getY());
            }
        });

        canvas.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                onMouseMoved(event.getX(), event.getY());
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

    public void startGame(JsonObject msg) {
        lblScenario.setText("Escenario: " + msg.get("scenario").getAsString());

        walls.clear();
        for (JsonElement element : msg.getAsJsonArray("walls")) {
            JsonObject wall = element.getAsJsonObject();

            double x = wall.get("x").getAsDouble();
            double y = wall.get("y").getAsDouble();
            double w = wall.get("w").getAsDouble();
            double h = wall.get("h").getAsDouble();

            walls.add(new ObjectStatic(x, y, w, h, Color.DIMGRAY));
        }
    }

        private void onMouseMoved(double x, double y) {
        target.setPosition(x, y);
        sendAim(x, y);
    }

    private void sendAim(double x, double y) {
        if (Main.client == null || !Main.client.isOpen()) {
            return;
        }

        JsonObject msg = new JsonObject();
        msg.addProperty("type", "AIM");
        msg.addProperty("x", x);
        msg.addProperty("y", y);

        Main.client.send(msg.toString());
    }

    // Devuelve el tanque de este jugador según el id recibido en WELCOME
    private Tank getMyTank() {
        if (Main.client != null && Main.client.getPlayerId() == 2) {
            return tank2;
        }
        return tank1;
    }
}
