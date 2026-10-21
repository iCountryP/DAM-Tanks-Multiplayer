
package com.project.server;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

public class TankServer extends WebSocketServer {

    // Lista de jugadores conectados
    private final List<WebSocket> jugadores = new ArrayList<>();

    // Indica si la partida ha empezado
    private boolean partidaIniciada = false;

    // Sirve para controlar y cancelar la cuenta atras
    private int versionCuentaAtras = 0;

    // Constructor del servidor
    public TankServer(int port) {
        super(new InetSocketAddress("0.0.0.0", port));
    }

    // Se ejecuta cuando arranca el servidor
    @Override
    public void onStart() {
        System.out.println("Servidor Tanks iniciado en el puerto 8887");
        System.out.println("Esperando jugadores...");
    }

    // Se ejecuta cuando se conecta un jugador
    @Override
    public synchronized void onOpen(WebSocket conn, ClientHandshake handshake) {

        // Solo pueden conectarse dos jugadores
        if (jugadores.size() >= 2) {
            conn.send("{\"type\":\"ERROR\",\"message\":\"Partida completa\"}");
            conn.close();
            return;
        }

        // Añadimos el jugador a la lista
        jugadores.add(conn);

        // Asignamos el numero de jugador segun el orden de conexion
        int id = jugadores.size();

        System.out.println("Jugador " + id + " conectado");

        // Enviamos al jugador su identificador
        conn.send("{\"type\":\"WELCOME\",\"playerId\":" + id + "}");

        // Si solo hay un jugador, esperamos al segundo
        if (jugadores.size() == 1) {
            enviarATodos("{\"type\":\"WAITING\"}");
        }

        // Cuando hay dos jugadores empezamos la cuenta atras
        if (jugadores.size() == 2) {
            iniciarCuentaAtras();
        }
    }

    // Envia un mensaje a todos los jugadores conectados
    private void enviarATodos(String mensaje) {
        for (WebSocket jugador : jugadores) {
            if (jugador.isOpen()) {
                jugador.send(mensaje);
            }
        }
    }

    // Inicia la cuenta atras antes de empezar la partida
    private void iniciarCuentaAtras() {

        versionCuentaAtras++;
        int versionActual = versionCuentaAtras;

        // Creamos un hilo para no bloquear el servidor
        Thread hilo = new Thread(() -> {

            // Cuenta atras de 3 segundos
            for (int i = 3; i >= 1; i--) {

                synchronized (TankServer.this) {

                    // Si alguien se desconecta, cancelamos la cuenta atras
                    if (versionActual != versionCuentaAtras
                            || jugadores.size() != 2) {
                        return;
                    }

                    // Enviamos los segundos restantes a los jugadores
                    enviarATodos("{\"type\":\"COUNTDOWN\",\"seconds\":" + i + "}");
                }

                // Esperamos un segundo entre cada numero
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            synchronized (TankServer.this) {

                // Comprobamos que los dos jugadores siguen conectados
                if (versionActual != versionCuentaAtras
                        || jugadores.size() != 2) {
                    return;
                }

                // Iniciamos la partida
                partidaIniciada = true;
                enviarATodos("{\"type\":\"START\"}");

                System.out.println("Partida iniciada");
            }
        });

        // Ejecutamos la cuenta atras en un hilo separado
        hilo.setDaemon(true);
        hilo.start();
    }

    // Se ejecuta cuando recibimos un mensaje de un jugador
    @Override
    public synchronized void onMessage(WebSocket conn, String message) {

        // Ignoramos mensajes de conexiones que no sean jugadores
        if (!jugadores.contains(conn)) {
            return;
        }

        System.out.println("Mensaje recibido: " + message);

        // De momento solo confirmamos que hemos recibido el mensaje
        conn.send("{\"type\":\"ACK\",\"message\":\"Mensaje recibido\"}");
    }

    // Se ejecuta cuando un jugador se desconecta
    @Override
    public synchronized void onClose(WebSocket conn, int code, String reason, boolean remote) {

        // Eliminamos al jugador de la lista
        if (!jugadores.remove(conn)) {
            return;
        }

        System.out.println("Jugador desconectado");

        // Cancelamos la cuenta atras y reiniciamos el estado de la partida
        versionCuentaAtras++;
        partidaIniciada = false;

        // Si queda un jugador, vuelve a ser el jugador 1
        if (jugadores.size() == 1) {
            jugadores.get(0).send("{\"type\":\"WELCOME\",\"playerId\":1}");
            enviarATodos("{\"type\":\"WAITING\"}");
        }
    }

    // Se ejecuta si ocurre algun error en el servidor
    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("Error en el servidor: " + ex.getMessage());
    }

    // Punto de entrada del programa
    public static void main(String[] args) {

        // Creamos el servidor en el puerto 8887 y lo arrancamos
        TankServer server = new TankServer(8887);
        server.start();
    }
}
