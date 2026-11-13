package de.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Netzwerk-Teil von Swarm (ohne Minecraft): ein Spiel ist Host und schickt Textzeilen an alle
 * verbundenen Worker. Alles laeuft in Hintergrund-Threads; das Spiel liest empfangene Befehle
 * aus {@link #INBOX} und wartet nie auf das Netzwerk.
 */
final class SwarmNet {
    private SwarmNet() {
    }

    static final int AUTO = 0;
    static final int HOST = 1;
    static final int WORKER = 2;
    private static final int MAX_LINE = 600;

    /** Vom Host empfangene Befehle (nur beim Worker gefuellt). */
    static final Queue<String> INBOX = new ConcurrentLinkedQueue<>();

    private static volatile String status = "Aus";
    private static volatile boolean newWorker;
    private static volatile boolean isHost;
    private static Session session;

    static String status() {
        return status;
    }

    static boolean hosting() {
        return isHost;
    }

    /** true (einmalig), wenn sich seit dem letzten Aufruf ein Worker neu verbunden hat. */
    static boolean takeNewWorker() {
        boolean n = newWorker;
        newWorker = false;
        return n;
    }

    static synchronized void start(int mode, String address, int port, boolean lan) {
        stop();
        session = new Session(mode, address, Math.max(1024, Math.min(65535, port)), lan);
        Thread t = new Thread(session::run, "1912-swarm");
        t.setDaemon(true);
        t.start();
    }

    static synchronized void stop() {
        if (session != null) session.close();
        session = null;
        isHost = false;
        newWorker = false;
        INBOX.clear();
        status = "Aus";
    }

    /** Host: Zeile an alle Worker schicken. */
    static void broadcast(String line) {
        Session s = session;
        if (s == null || !isHost) return;
        String clean = line.replace('\n', ' ').replace('\r', ' ');
        if (clean.length() > MAX_LINE) clean = clean.substring(0, MAX_LINE);
        s.outbox.offer(clean);
    }

    private static void closeQuietly(java.io.Closeable c) {
        try {
            if (c != null) c.close();
        } catch (IOException e) {
            // egal
        }
    }

    /** Liest eine Zeile mit begrenzter Laenge; null = Verbindung zu Ende. */
    private static String readLine(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b < 0) return null;
            if (b == '\n') break;
            if (b != '\r') buf.write(b);
            if (buf.size() > MAX_LINE * 4) throw new IOException("Zeile zu lang");
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    private static void writeLine(OutputStream out, String line) throws IOException {
        out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static final class Session {
        final int mode;
        final String address;
        final int port;
        final boolean lan;
        volatile boolean running = true;
        volatile ServerSocket server;
        volatile Socket socket;
        final List<Socket> peers = new CopyOnWriteArrayList<>();
        final LinkedBlockingQueue<String> outbox = new LinkedBlockingQueue<>(2000);

        Session(int mode, String address, int port, boolean lan) {
            this.mode = mode;
            this.address = address == null || address.isBlank() ? "127.0.0.1" : address.trim();
            this.port = port;
            this.lan = lan;
        }

        void close() {
            running = false;
            closeQuietly(server);
            closeQuietly(socket);
            for (Socket p : peers) closeQuietly(p);
        }

        void run() {
            if (mode != WORKER) {
                try {
                    server = new ServerSocket(port, 16, lan ? null : InetAddress.getLoopbackAddress());
                } catch (BindException e) {
                    if (mode == HOST) {
                        status = "Port " + port + " ist schon belegt";
                        return;
                    }
                    // Auto: es gibt schon einen Host auf diesem PC -> selbst Worker werden
                } catch (IOException e) {
                    status = "Host konnte nicht starten";
                    return;
                }
            }
            if (!running) {
                closeQuietly(server);
                return;
            }
            if (server != null) host();
            else worker();
        }

        // ----- Host -----

        void host() {
            isHost = true;
            updateHostStatus();
            Thread sender = new Thread(this::sendLoop, "1912-swarm-send");
            sender.setDaemon(true);
            sender.start();
            while (running) {
                try {
                    Socket s = server.accept();
                    s.setTcpNoDelay(true);
                    peers.add(s);
                    newWorker = true;
                    updateHostStatus();
                    Thread r = new Thread(() -> drain(s), "1912-swarm-peer");
                    r.setDaemon(true);
                    r.start();
                } catch (IOException e) {
                    if (running) status = "Host: Fehler beim Annehmen";
                    break;
                }
            }
        }

        void updateHostStatus() {
            if (!running) return;
            int n = peers.size();
            status = "Host auf Port " + port + ": " + n + (n == 1 ? " Worker" : " Worker") + " verbunden";
        }

        /** Liest (und verwirft) alles, was ein Worker schickt; endet, wenn er weg ist. */
        void drain(Socket s) {
            try {
                InputStream in = s.getInputStream();
                while (running && readLine(in) != null) {
                    // Worker schicken nur Lebenszeichen
                }
            } catch (IOException e) {
                // Worker weg
            }
            drop(s);
        }

        void drop(Socket s) {
            if (peers.remove(s)) updateHostStatus();
            closeQuietly(s);
        }

        void sendLoop() {
            while (running) {
                String line;
                try {
                    line = outbox.poll(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    return;
                }
                if (line == null) line = "PING"; // Lebenszeichen, damit tote Verbindungen auffallen
                for (Socket p : peers) {
                    try {
                        writeLine(p.getOutputStream(), line);
                    } catch (IOException e) {
                        drop(p);
                    }
                }
            }
        }

        // ----- Worker -----

        void worker() {
            isHost = false;
            while (running) {
                status = "Worker: verbinde mit " + address + ":" + port;
                Socket s = new Socket();
                socket = s;
                try {
                    s.connect(new InetSocketAddress(address, port), 3000);
                    s.setTcpNoDelay(true);
                    s.setSoTimeout(20_000); // der Host meldet sich alle 5 Sekunden
                    writeLine(s.getOutputStream(), "HELLO");
                    status = "Worker: verbunden mit " + address + ":" + port;
                    InputStream in = s.getInputStream();
                    String line;
                    while (running && (line = readLine(in)) != null) {
                        if (!line.equals("PING") && INBOX.size() < 500) INBOX.add(line);
                    }
                } catch (IOException e) {
                    // Host nicht da oder Verbindung weg: gleich nochmal versuchen
                }
                closeQuietly(s);
                if (!running) break;
                status = "Worker: Host " + address + ":" + port + " nicht erreichbar, neuer Versuch";
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }
}
