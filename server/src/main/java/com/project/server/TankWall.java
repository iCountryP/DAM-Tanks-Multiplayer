
package com.project.server;

public class TankWall {

    public double x;
    public double y;
    public double w;
    public double h;

    public TankWall(double x, double y, double w, double h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    // Comprobamos si un circulo toca esta pared
    public boolean colisiona(double centroX, double centroY, double radio) {

        double cercanoX = Math.max(x, Math.min(centroX, x + w));
        double cercanoY = Math.max(y, Math.min(centroY, y + h));

        double distanciaX = centroX - cercanoX;
        double distanciaY = centroY - cercanoY;

        return distanciaX * distanciaX + distanciaY * distanciaY <= radio * radio;
    }

    // Comprobamos todo el recorrido de una bala
    public boolean colisionaBala(double anteriorX, double anteriorY,
            double nuevaX, double nuevaY) {

        double distanciaX = nuevaX - anteriorX;
        double distanciaY = nuevaY - anteriorY;

        double distancia = Math.sqrt(
                distanciaX * distanciaX + distanciaY * distanciaY);

        // Revisamos puntos intermedios para no atravesar paredes finas
        int pasos = Math.max(1, (int) Math.ceil(distancia / 4.0));

        for (int i = 0; i <= pasos; i++) {

            double proporcion = (double) i / pasos;

            double puntoX = anteriorX + distanciaX * proporcion;
            double puntoY = anteriorY + distanciaY * proporcion;

            if (colisiona(puntoX, puntoY, 4)) {
                return true;
            }
        }

        return false;
    }
}
