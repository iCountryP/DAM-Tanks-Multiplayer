
package com.project.server;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class TankServer extends WebSocketServer {

    // Jugadores, balas y explosiones de la partida
    private final List<TankPlayer> jugadores = new ArrayList<>();
    private final List<TankBullet> balas = new ArrayList<>();
    private final List<JsonObject> explosiones = new ArrayList<>();

    private final Gson gson = new Gson();

    private boolean partidaIniciada = false;
    private boolean cuentaAtrasActiva = false;
    private int versionCuentaAtras = 0;

    private static final int MAX_BALAS = 4;
    private static final double RADIO_TANQUE = 20;

    public TankServer(int port) {
        super(new InetSocketAddress("0.0.0.0", port));
    }

    @Override
    public void onStart() {
        System.out.println("Servidor Tanks iniciado en el puerto 3000");
        System.out.println("Esperando jugadores...");

        // Actualizamos el juego cada 50 milisegundos
        Thread hiloJuego = new Thread(() -> {

            while (!Thread.currentThread().isInterrupted()) {

                synchronized (TankServer.this) {

                    if (partidaIniciada && jugadores.size() == 2) {

                        // Actualizamos los tanques
                        for (TankPlayer jugador : jugadores) {
                            jugador.actualizar(0.05);
                        }

                        // Actualizamos las balas y comprobamos impactos
                        actualizarBalas();

                        // Enviamos el estado a los jugadores
                        enviarEstado();

                        // Reducimos la duracion de las explosiones
                        actualizarExplosiones();
                    }
                }

                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });

        hiloJuego.setDaemon(true);
        hiloJuego.start();
    }

    @Override
    public synchronized void onOpen(WebSocket conn, ClientHandshake handshake) {

        // Solo dejamos entrar a dos jugadores
        if (jugadores.size() >= 2) {
            conn.send("{\"type\":\"ERROR\",\"message\":\"Partida completa\"}");
            conn.close();
            return;
        }

        int id = jugadores.size() + 1;

        TankPlayer jugador = new TankPlayer(id, conn);
        jugadores.add(jugador);

        System.out.println("Jugador " + id + " conectado");

        conn.send("{\"type\":\"WELCOME\",\"playerId\":" + id + "}");

        // Esperamos a que los dos envien JOIN
        enviarATodos("{\"type\":\"WAITING\"}");
    }

    // Buscamos el jugador que ha enviado un mensaje
    private TankPlayer buscarJugador(WebSocket conn) {

        for (TankPlayer jugador : jugadores) {
            if (jugador.conexion == conn) {
                return jugador;
            }
        }

        return null;
    }

    // Enviamos el mismo mensaje a los dos jugadores
    private void enviarATodos(String mensaje) {

        for (TankPlayer jugador : jugadores) {
            if (jugador.conexion.isOpen()) {
                jugador.conexion.send(mensaje);
            }
        }
    }

    // Comprobamos si los dos jugadores tienen nombre
    private boolean jugadoresPreparados() {

        if (jugadores.size() != 2) {
            return false;
        }

        for (TankPlayer jugador : jugadores) {
            if (jugador.name.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    // Iniciamos la cuenta atras
    private void iniciarCuentaAtras() {

        if (cuentaAtrasActiva || partidaIniciada) {
            return;
        }

        cuentaAtrasActiva = true;

        versionCuentaAtras++;
        int versionActual = versionCuentaAtras;

        Thread hilo = new Thread(() -> {

            for (int i = 3; i >= 1; i--) {

                synchronized (TankServer.this) {

                    if (versionActual != versionCuentaAtras
                            || !jugadoresPreparados()) {
                        return;
                    }

                    enviarATodos("{\"type\":\"COUNTDOWN\",\"seconds\":" + i + "}");
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            synchronized (TankServer.this) {

                if (versionActual != versionCuentaAtras
                        || !jugadoresPreparados()) {
                    return;
                }

                // Limpiamos los datos de la partida anterior
                balas.clear();
                explosiones.clear();

                partidaIniciada = true;
                cuentaAtrasActiva = false;

                for (TankPlayer jugador : jugadores) {
                    jugador.detener();
                }

                enviarATodos("{\"type\":\"START\"}");

                System.out.println("Partida iniciada");
            }
        });

        hilo.setDaemon(true);
        hilo.start();
    }

    // Contamos las balas activas de un jugador
    private int contarBalas(int id) {

        int cantidad = 0;

        for (TankBullet bala : balas) {
            if (bala.ownerId == id) {
                cantidad++;
            }
        }

        return cantidad;
    }

    // Creamos una bala si no ha llegado al limite
    private void disparar(TankPlayer jugador) {

        if (contarBalas(jugador.id) >= MAX_BALAS) {
            return;
        }

        TankBullet bala = new TankBullet(jugador);
        balas.add(bala);

        System.out.println(jugador.name + " ha disparado");
    }

    // Actualizamos las balas y comprobamos si impactan
    private void actualizarBalas() {

        for (int i = balas.size() - 1; i >= 0; i--) {

            TankBullet bala = balas.get(i);

            // Guardamos la posicion anterior para comprobar el recorrido
            double anteriorX = bala.x;
            double anteriorY = bala.y;

            bala.actualizar(0.05);

            boolean impacto = false;

            for (TankPlayer jugador : jugadores) {

                // Una bala no puede dañar a su propietario
                if (jugador.id == bala.ownerId || jugador.lives <= 0) {
                    continue;
                }

                if (balaImpactaTanque(anteriorX, anteriorY, bala, jugador)) {

                    impacto = true;

                    // Restamos una vida al jugador
                    jugador.lives--;

                    System.out.println(jugador.name
                            + " ha recibido un impacto. Vidas: "
                            + jugador.lives);

                    crearExplosion(bala.x, bala.y);

                    // Si no le quedan vidas, termina la partida
                    if (jugador.lives <= 0) {
                        terminarPartida(jugador);
                    }

                    break;
                }
            }

            // Eliminamos las balas que impactan o terminan su recorrido
            if (impacto || bala.haTerminado()) {
                balas.remove(i);
            }

            if (!partidaIniciada) {
                balas.clear();
                break;
            }
        }
    }

    // Comprobamos si el recorrido de una bala toca un tanque
    private boolean balaImpactaTanque(double anteriorX, double anteriorY,
            TankBullet bala, TankPlayer jugador) {

        double dx = bala.x - anteriorX;
        double dy = bala.y - anteriorY;

        double longitudCuadrado = dx * dx + dy * dy;

        double t = 0;

        if (longitudCuadrado > 0) {
            t = ((jugador.x - anteriorX) * dx
                    + (jugador.y - anteriorY) * dy) / longitudCuadrado;
        }

        // Limitamos el punto al recorrido real de la bala
        t = Math.max(0, Math.min(1, t));

        double cercanoX = anteriorX + t * dx;
        double cercanoY = anteriorY + t * dy;

        double distanciaX = cercanoX - jugador.x;
        double distanciaY = cercanoY - jugador.y;

        double distanciaCuadrado = distanciaX * distanciaX + distanciaY * distanciaY;

        return distanciaCuadrado <= RADIO_TANQUE * RADIO_TANQUE;
    }

    // Guardamos una explosion cuando hay un impacto
    private void crearExplosion(double x, double y) {

        JsonObject explosion = new JsonObject();

        explosion.addProperty("x", x);
        explosion.addProperty("y", y);
        explosion.addProperty("radius", 25);
        explosion.addProperty("ticks", 8);

        explosiones.add(explosion);
    }

    // Quitamos las explosiones despues de unos instantes
    private void actualizarExplosiones() {

        for (int i = explosiones.size() - 1; i >= 0; i--) {

            JsonObject explosion = explosiones.get(i);

            int ticks = explosion.get("ticks").getAsInt() - 1;

            if (ticks <= 0) {
                explosiones.remove(i);
            } else {
                explosion.addProperty("ticks", ticks);
            }
        }
    }

    // Comprobamos quien ha ganado
    private void terminarPartida(TankPlayer perdedor) {

        TankPlayer ganador = null;

        for (TankPlayer jugador : jugadores) {
            if (jugador != perdedor) {
                ganador = jugador;
                break;
            }
        }

        if (ganador == null) {
            return;
        }

        partidaIniciada = false;

        for (TankPlayer jugador : jugadores) {
            jugador.detener();
        }

        JsonObject resultado = new JsonObject();
        resultado.addProperty("type", "GAME_OVER");
        resultado.addProperty("winner", ganador.name);
        resultado.addProperty("loser", perdedor.name);

        enviarATodos(gson.toJson(resultado));

        System.out.println("Partida terminada. Ganador: " + ganador.name);
    }

    // Enviamos el estado de la partida
    private void enviarEstado() {

        JsonObject estado = new JsonObject();
        estado.addProperty("type", "STATE");

        JsonArray tanques = new JsonArray();

        for (TankPlayer jugador : jugadores) {

            JsonObject tanque = new JsonObject();

            tanque.addProperty("id", jugador.id);
            tanque.addProperty("name", jugador.name);
            tanque.addProperty("x", jugador.x);
            tanque.addProperty("y", jugador.y);
            tanque.addProperty("angle", jugador.angle);
            tanque.addProperty("turretAngle", jugador.turretAngle);
            tanque.addProperty("lives", jugador.lives);
            tanque.addProperty("activeBullets", contarBalas(jugador.id));

            tanques.add(tanque);
        }

        estado.add("players", tanques);

        // Enviamos las balas
        JsonArray proyectiles = new JsonArray();

        for (TankBullet bala : balas) {

            JsonObject proyectil = new JsonObject();

            proyectil.addProperty("x", bala.x);
            proyectil.addProperty("y", bala.y);
            proyectil.addProperty("angle", bala.angle);
            proyectil.addProperty("ownerId", bala.ownerId);

            proyectiles.add(proyectil);
        }

        estado.add("bullets", proyectiles);
        estado.add("walls", new JsonArray());

        // Enviamos las explosiones que siguen activas
        JsonArray listaExplosiones = new JsonArray();

        for (JsonObject explosion : explosiones) {

            JsonObject datos = new JsonObject();

            datos.addProperty("x", explosion.get("x").getAsDouble());
            datos.addProperty("y", explosion.get("y").getAsDouble());
            datos.addProperty("radius", explosion.get("radius").getAsDouble());

            listaExplosiones.add(datos);
        }

        estado.add("explosions", listaExplosiones);

        enviarATodos(gson.toJson(estado));
    }

    @Override
    public synchronized void onMessage(WebSocket conn, String message) {

        TankPlayer jugador = buscarJugador(conn);

        if (jugador == null) {
            return;
        }

        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String tipo = json.get("type").getAsString();

            // Registramos el nombre del jugador
            if (tipo.equals("JOIN")) {

                String nombre = json.get("name").getAsString().trim();

                if (!nombre.isEmpty() && nombre.length() <= 30) {

                    if (!partidaIniciada && !cuentaAtrasActiva) {

                        jugador.name = nombre;

                        System.out.println("Jugador " + jugador.id + ": " + nombre);

                        if (jugadoresPreparados()) {
                            iniciarCuentaAtras();
                        }
                    }

                } else {
                    conn.send("{\"type\":\"ERROR\",\"message\":\"Nombre incorrecto\"}");
                }
            }

            // Guardamos las teclas pulsadas
            if (tipo.equals("INPUT") && partidaIniciada) {

                jugador.up = json.get("up").getAsBoolean();
                jugador.down = json.get("down").getAsBoolean();
                jugador.left = json.get("left").getAsBoolean();
                jugador.right = json.get("right").getAsBoolean();
            }

            // Orientamos la torreta
            if (tipo.equals("AIM") && partidaIniciada) {

                double mouseX = json.get("x").getAsDouble();
                double mouseY = json.get("y").getAsDouble();

                if (Double.isFinite(mouseX) && Double.isFinite(mouseY)) {
                    jugador.apuntar(mouseX, mouseY);
                }
            }

            // Disparamos
            if (tipo.equals("FIRE") && partidaIniciada) {
                disparar(jugador);
            }

        } catch (Exception e) {
            conn.send("{\"type\":\"ERROR\",\"message\":\"Mensaje incorrecto\"}");
        }
    }

    @Override
    public synchronized void onClose(WebSocket conn, int code, String reason, boolean remote) {

        TankPlayer jugador = buscarJugador(conn);

        if (jugador == null) {
            return;
        }

        jugadores.remove(jugador);

        System.out.println("Jugador " + jugador.id + " desconectado");

        // Reiniciamos el juego si alguien se desconecta
        versionCuentaAtras++;
        cuentaAtrasActiva = false;
        partidaIniciada = false;

        balas.clear();
        explosiones.clear();

        if (jugadores.size() == 1) {

            TankPlayer restante = jugadores.get(0);

            restante.id = 1;
            restante.name = "";
            restante.detener();

            restante.x = 100;
            restante.y = 250;
            restante.angle = 0;
            restante.turretAngle = 0;
            restante.lives = 3;

            restante.conexion.send("{\"type\":\"WELCOME\",\"playerId\":1}");
            enviarATodos("{\"type\":\"WAITING\"}");
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("Error en el servidor: " + ex.getMessage());
    }

    public static void main(String[] args) {
        TankServer server = new TankServer(3000);
        server.start();
    }
}
