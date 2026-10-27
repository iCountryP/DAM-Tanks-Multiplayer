
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

    // Datos de la partida
    private final List<TankPlayer> jugadores = new ArrayList<>();
    private final List<TankBullet> balas = new ArrayList<>();
    private final List<TankWall> paredes = new ArrayList<>();
    private final List<JsonObject> explosiones = new ArrayList<>();

    private final Gson gson = new Gson();

    private boolean partidaIniciada = false;
    private boolean cuentaAtrasActiva = false;
    private boolean partidaTerminada = false;

    private int versionCuentaAtras = 0;

    // Jugadores que han solicitado una revancha
    private final List<Integer> jugadoresListos = new ArrayList<>();

    private static final int MAX_BALAS = 4;
    private static final double RADIO_TANQUE = 20;

    private String escenario = "Arena";
    private int siguienteEscenario = 0;

    public TankServer(int port) {
        super(new InetSocketAddress("0.0.0.0", port));
    }

    @Override
    public void onStart() {

        System.out.println("Servidor Tanks iniciado en el puerto 3000");
        System.out.println("Esperando jugadores...");

        // Bucle principal del juego
        Thread hiloJuego = new Thread(() -> {

            while (!Thread.currentThread().isInterrupted()) {

                synchronized (TankServer.this) {

                    if (partidaIniciada && jugadores.size() == 2) {

                        actualizarJugadores();
                        actualizarBalas();

                        // Enviamos el estado actualizado
                        enviarEstado();
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

        // Solo permitimos dos jugadores
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

        enviarATodos("{\"type\":\"WAITING\"}");
    }

    // Buscamos el jugador asociado a una conexion
    private TankPlayer buscarJugador(WebSocket conn) {

        for (TankPlayer jugador : jugadores) {

            if (jugador.conexion == conn) {
                return jugador;
            }
        }

        return null;
    }

    // Enviamos un mensaje a los jugadores conectados
    private void enviarATodos(String mensaje) {

        for (TankPlayer jugador : jugadores) {

            if (jugador.conexion.isOpen()) {
                jugador.conexion.send(mensaje);
            }
        }
    }

    // Comprobamos que ambos jugadores tengan nombre
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

    // Seleccionamos el escenario de la partida
    private void cargarEscenario() {

        paredes.clear();

        if (siguienteEscenario == 0) {

            escenario = "Arena";

            // Pared central
            paredes.add(new TankWall(380, 150, 40, 200));

        } else if (siguienteEscenario == 1) {

            escenario = "Pasillos";

            // Cuatro paredes con pasillos centrales
            paredes.add(new TankWall(230, 50, 40, 160));
            paredes.add(new TankWall(230, 290, 40, 160));

            paredes.add(new TankWall(530, 50, 40, 160));
            paredes.add(new TankWall(530, 290, 40, 160));

        } else {

            escenario = "Obstaculos";

            // Obstaculos repartidos por el escenario
            paredes.add(new TankWall(260, 90, 60, 100));
            paredes.add(new TankWall(480, 310, 60, 100));
            paredes.add(new TankWall(370, 215, 60, 70));
        }

        // Preparamos el siguiente escenario
        siguienteEscenario++;

        if (siguienteEscenario >= 3) {
            siguienteEscenario = 0;
        }

        System.out.println("Escenario seleccionado: " + escenario);
    }

    // Restauramos las vidas y posiciones iniciales
    private void prepararJugadores() {

        for (TankPlayer jugador : jugadores) {

            jugador.detener();
            jugador.lives = 3;

            if (jugador.id == 1) {

                jugador.x = 100;
                jugador.y = 250;
                jugador.angle = 0;

            } else {

                jugador.x = 700;
                jugador.y = 250;
                jugador.angle = 180;
            }

            jugador.turretAngle = jugador.angle;
        }
    }

    // Cuenta atras antes de empezar una partida
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

                    enviarATodos(
                            "{\"type\":\"COUNTDOWN\",\"seconds\":" + i + "}");
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

                // Limpiamos los datos anteriores
                balas.clear();
                explosiones.clear();

                // Preparamos el nuevo escenario y los tanques
                cargarEscenario();
                prepararJugadores();

                // Reiniciamos el estado de la revancha
                jugadoresListos.clear();
                partidaTerminada = false;

                partidaIniciada = true;
                cuentaAtrasActiva = false;

                // Enviamos el escenario a los dos jugadores
                JsonObject inicio = new JsonObject();

                inicio.addProperty("type", "START");
                inicio.addProperty("scenario", escenario);
                inicio.add("walls", gson.toJsonTree(paredes));

                enviarATodos(gson.toJson(inicio));

                System.out.println("Partida iniciada");
            }
        });

        hilo.setDaemon(true);
        hilo.start();
    }

    // Registramos los jugadores que quieren volver a jugar
    private void prepararRevancha(TankPlayer jugador) {

        // Solo permitimos revancha cuando acaba una partida
        if (!partidaTerminada || cuentaAtrasActiva) {
            return;
        }

        // Evitamos registrar dos veces al mismo jugador
        if (!jugadoresListos.contains(jugador.id)) {

            jugadoresListos.add(jugador.id);

            System.out.println(jugador.name + " quiere volver a jugar");
        }

        // Informamos a ambos jugadores
        JsonObject mensaje = new JsonObject();

        mensaje.addProperty("type", "REMATCH_WAITING");
        mensaje.addProperty("ready", jugadoresListos.size());
        mensaje.addProperty("total", 2);

        enviarATodos(gson.toJson(mensaje));

        // Cuando los dos aceptan comienza la nueva cuenta atras
        if (jugadoresListos.size() == 2 && jugadoresPreparados()) {

            System.out.println("Revancha aceptada por ambos jugadores");

            iniciarCuentaAtras();
        }
    }

    // Comprobamos si un tanque toca una pared
    private boolean colisionaPared(double x, double y) {

        for (TankWall pared : paredes) {

            if (pared.colisiona(x, y, RADIO_TANQUE)) {
                return true;
            }
        }

        return false;
    }

    // Evitamos que los tanques se atraviesen entre ellos
    private boolean colisionaOtroTanque(TankPlayer jugador) {

        for (TankPlayer otro : jugadores) {

            if (otro == jugador) {
                continue;
            }

            double dx = jugador.x - otro.x;
            double dy = jugador.y - otro.y;

            double distanciaCuadrado = dx * dx + dy * dy;
            double radioTotal = RADIO_TANQUE * 2;

            if (distanciaCuadrado < radioTotal * radioTotal) {
                return true;
            }
        }

        return false;
    }

    // Movemos los tanques comprobando colisiones
    private void actualizarJugadores() {

        for (TankPlayer jugador : jugadores) {

            double anteriorX = jugador.x;
            double anteriorY = jugador.y;

            jugador.actualizar(0.05);

            // Si choca, vuelve a la posicion anterior
            if (colisionaPared(jugador.x, jugador.y)
                    || colisionaOtroTanque(jugador)) {

                jugador.x = anteriorX;
                jugador.y = anteriorY;
            }
        }
    }

    // Contamos los proyectiles activos de un jugador
    private int contarBalas(int id) {

        int cantidad = 0;

        for (TankBullet bala : balas) {

            if (bala.ownerId == id) {
                cantidad++;
            }
        }

        return cantidad;
    }

    // Creamos un proyectil si no se ha alcanzado el limite
    private void disparar(TankPlayer jugador) {

        if (contarBalas(jugador.id) >= MAX_BALAS) {
            return;
        }

        TankBullet bala = new TankBullet(jugador);

        balas.add(bala);

        System.out.println(jugador.name + " ha disparado");
    }

    // Actualizamos las balas y detectamos impactos
    private void actualizarBalas() {

        for (int i = balas.size() - 1; i >= 0; i--) {

            TankBullet bala = balas.get(i);

            double anteriorX = bala.x;
            double anteriorY = bala.y;

            bala.actualizar(0.05);

            boolean impactoPared = false;

            // Comprobamos primero las paredes
            for (TankWall pared : paredes) {

                if (pared.colisionaBala(
                        anteriorX, anteriorY, bala.x, bala.y)) {

                    impactoPared = true;

                    crearExplosion(bala.x, bala.y);
                    break;
                }
            }

            if (impactoPared) {

                balas.remove(i);
                continue;
            }

            boolean impactoTanque = false;

            // Comprobamos si la bala impacta contra un enemigo
            for (TankPlayer jugador : jugadores) {

                // No permitimos que un jugador se dispare a si mismo
                if (jugador.id == bala.ownerId || jugador.lives <= 0) {
                    continue;
                }

                if (balaImpactaTanque(
                        anteriorX, anteriorY, bala, jugador)) {

                    impactoTanque = true;

                    jugador.lives--;

                    System.out.println(jugador.name
                            + " ha recibido un impacto. Vidas: "
                            + jugador.lives);

                    crearExplosion(bala.x, bala.y);

                    // Terminamos la partida si se queda sin vidas
                    if (jugador.lives <= 0) {
                        terminarPartida(jugador);
                    }

                    break;
                }
            }

            // Eliminamos las balas que impactan o caducan
            if (impactoTanque || bala.haTerminado()) {
                balas.remove(i);
            }

            if (!partidaIniciada) {

                balas.clear();
                break;
            }
        }
    }

    // Detectamos si el recorrido de una bala toca un tanque
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

    // Creamos una explosion visual cuando hay un impacto
    private void crearExplosion(double x, double y) {

        JsonObject explosion = new JsonObject();

        explosion.addProperty("x", x);
        explosion.addProperty("y", y);
        explosion.addProperty("radius", 25);
        explosion.addProperty("ticks", 8);

        explosiones.add(explosion);
    }

    // Eliminamos las explosiones despues de unos instantes
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

    // Finalizamos la partida y comunicamos el ganador
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
        partidaTerminada = true;

        // Reiniciamos las solicitudes de revancha
        jugadoresListos.clear();

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

    // Enviamos el estado completo a ambos jugadores
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

        // Informacion de las balas activas
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

        // Enviamos las paredes del escenario
        estado.add("walls", gson.toJsonTree(paredes));

        // Enviamos las explosiones activas
        JsonArray listaExplosiones = new JsonArray();

        for (JsonObject explosion : explosiones) {

            JsonObject datos = new JsonObject();

            datos.addProperty("x", explosion.get("x").getAsDouble());
            datos.addProperty("y", explosion.get("y").getAsDouble());
            datos.addProperty(
                    "radius", explosion.get("radius").getAsDouble());

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

            // El jugador introduce su nombre
            if (tipo.equals("JOIN")) {

                String nombre = json.get("name").getAsString().trim();

                if (!nombre.isEmpty() && nombre.length() <= 30) {

                    if (!partidaIniciada && !cuentaAtrasActiva
                            && !partidaTerminada) {

                        jugador.name = nombre;

                        System.out.println("Jugador "
                                + jugador.id + ": " + nombre);

                        if (jugadoresPreparados()) {
                            iniciarCuentaAtras();
                        }
                    }

                } else {

                    conn.send(
                            "{\"type\":\"ERROR\",\"message\":\"Nombre incorrecto\"}");
                }
            }

            // Actualizamos las teclas de movimiento
            if (tipo.equals("INPUT") && partidaIniciada) {

                jugador.up = json.get("up").getAsBoolean();
                jugador.down = json.get("down").getAsBoolean();
                jugador.left = json.get("left").getAsBoolean();
                jugador.right = json.get("right").getAsBoolean();
            }

            // Orientamos la torreta hacia el raton
            if (tipo.equals("AIM") && partidaIniciada) {

                double mouseX = json.get("x").getAsDouble();
                double mouseY = json.get("y").getAsDouble();

                if (Double.isFinite(mouseX) && Double.isFinite(mouseY)) {
                    jugador.apuntar(mouseX, mouseY);
                }
            }

            // Creamos un nuevo disparo
            if (tipo.equals("FIRE") && partidaIniciada) {
                disparar(jugador);
            }

            // Solicitamos una nueva partida
            if (tipo.equals("READY")) {
                prepararRevancha(jugador);
            }

        } catch (Exception e) {

            conn.send(
                    "{\"type\":\"ERROR\",\"message\":\"Mensaje incorrecto\"}");
        }
    }

    @Override
    public synchronized void onClose(WebSocket conn, int code,
            String reason, boolean remote) {

        TankPlayer jugador = buscarJugador(conn);

        if (jugador == null) {
            return;
        }

        jugadores.remove(jugador);

        System.out.println("Jugador " + jugador.id + " desconectado");

        // Cancelamos cualquier partida o revancha pendiente
        versionCuentaAtras++;

        cuentaAtrasActiva = false;
        partidaIniciada = false;
        partidaTerminada = false;

        jugadoresListos.clear();
        balas.clear();
        paredes.clear();
        explosiones.clear();

        // El jugador restante vuelve a la sala de espera
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

            restante.conexion.send(
                    "{\"type\":\"WELCOME\",\"playerId\":1}");

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
