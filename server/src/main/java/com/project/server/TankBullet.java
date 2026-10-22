
package com.project.server;

public class TankBullet {

    public double x;
    public double y;
    public double angle;

    public int ownerId;

    private double tiempoVida = 0;

    // Velocidad de las balas
    private static final double VELOCIDAD = 300;

    public TankBullet(TankPlayer jugador) {

        ownerId = jugador.id;
        angle = jugador.turretAngle;

        double radianes = Math.toRadians(angle);

        // La bala aparece delante del cañon
        x = jugador.x + Math.cos(radianes) * 25;
        y = jugador.y + Math.sin(radianes) * 25;
    }

    // Movemos la bala segun su direccion
    public void actualizar(double tiempo) {

        double radianes = Math.toRadians(angle);

        x += Math.cos(radianes) * VELOCIDAD * tiempo;
        y += Math.sin(radianes) * VELOCIDAD * tiempo;

        tiempoVida += tiempo;
    }

    // Comprobamos si la bala debe desaparecer
    public boolean haTerminado() {

        if (x < 0 || x > 800 || y < 0 || y > 500) {
            return true;
        }

        if (tiempoVida >= 2.5) {
            return true;
        }

        return false;
    }
}
