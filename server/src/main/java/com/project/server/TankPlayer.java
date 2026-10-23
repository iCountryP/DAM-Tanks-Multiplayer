
package com.project.server;

import org.java_websocket.WebSocket;

public class TankPlayer {

    public int id;
    public String name;
    public WebSocket conexion;

    public double x;
    public double y;
    public double angle;
    public double turretAngle;

    public int lives = 3;

    // Teclas que tiene pulsadas el jugador
    public boolean up;
    public boolean down;
    public boolean left;
    public boolean right;

    // Tamaño del escenario
    private static final int ANCHO = 800;
    private static final int ALTO = 500;

    // Velocidad del tanque
    private static final double VELOCIDAD = 120;
    private static final double GIRO = 150;

    public TankPlayer(int id, WebSocket conexion) {
        this.id = id;
        this.conexion = conexion;
        this.name = "";

        // Colocamos los tanques en lados diferentes
        if (id == 1) {
            x = 100;
            y = 250;
            angle = 0;
        } else {
            x = 700;
            y = 250;
            angle = 180;
        }

        turretAngle = angle;
    }

    // Actualizamos la posicion y el giro del tanque
    public void actualizar(double tiempo) {

        if (left) {
            angle -= GIRO * tiempo;
        }

        if (right) {
            angle += GIRO * tiempo;
        }

        double movimiento = 0;

        if (up) {
            movimiento += VELOCIDAD * tiempo;
        }

        if (down) {
            movimiento -= VELOCIDAD * tiempo;
        }

        // Calculamos hacia donde se mueve el tanque
        double radianes = Math.toRadians(angle);

        x += Math.cos(radianes) * movimiento;
        y += Math.sin(radianes) * movimiento;

        // Evitamos salir del escenario
        x = Math.max(20, Math.min(ANCHO - 20, x));
        y = Math.max(20, Math.min(ALTO - 20, y));

        // Dejamos el angulo entre 0 y 360 grados
        angle = (angle % 360 + 360) % 360;
    }

    // Orientamos la torreta hacia el raton
    public void apuntar(double mouseX, double mouseY) {

        double diferenciaX = mouseX - x;
        double diferenciaY = mouseY - y;

        turretAngle = Math.toDegrees(
                Math.atan2(diferenciaY, diferenciaX));

        turretAngle = (turretAngle % 360 + 360) % 360;
    }

    // Soltamos todas las teclas
    public void detener() {
        up = false;
        down = false;
        left = false;
        right = false;
    }
}
