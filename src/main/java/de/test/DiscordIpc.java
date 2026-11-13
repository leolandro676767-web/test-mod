package de.test;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Discord Rich Presence ohne Zusatz-Bibliothek: spricht direkt mit dem Discord-Programm auf
 * diesem PC (lokale Verbindung "discord-ipc"). Alles laeuft in einem eigenen Hintergrund-Thread,
 * das Spiel wartet nie auf Discord.
 * <p>Protokoll: jede Nachricht = 4 Byte Art + 4 Byte Laenge (beides Little Endian) + JSON-Text.
 * Art 0 = Anmeldung, 1 = Befehl/Antwort, 2 = Schliessen, 3 = Ping, 4 = Pong.
 */
final class DiscordIpc {
    private DiscordIpc() {
    }

    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;
    private static final int OP_PING = 3;
    private static final int OP_PONG = 4;
    private static final int MAX_FRAME = 1 << 20;
    /** Discord erlaubt nur wenige Aktualisierungen pro Minute. */
    private static final long MIN_SEND_GAP_MS = 4000;

    // Gewuenschter Zustand (setzt der Spiel-Thread)
    private static volatile boolean wanted;
    private static volatile String appId = "";
    private static volatile String details = "";
    private static volatile String state = "";
    private static volatile long startMs;
    private static volatile String status = "Aus";
    private static Thread worker;

    /** Nur fuer Tests: Ordner, in dem die Verbindungsdatei gesucht wird. */
    static volatile String testDir;

    static String status() {
        return status;
    }

    /** Vom Spiel regelmaessig aufgerufen, solange das Modul an ist. */
    static synchronized void update(String id, String line1, String line2, long start) {
        appId = id == null ? "" : id.trim();
        details = line1 == null ? "" : line1;
        state = line2 == null ? "" : line2;
        startMs = start;
        wanted = true;
        if (worker == null || !worker.isAlive()) {
            worker = new Thread(DiscordIpc::run, "1912-discord");
            worker.setDaemon(true);
            worker.start();
        }
    }

    /** Modul aus: Anzeige in Discord entfernen und Verbindung schliessen. */
    static void stop() {
        wanted = false;
    }

    // ---------- Verbindung ----------

    private interface Transport {
        void write(byte[] data) throws IOException;

        /** Anzahl Bytes, die ohne Warten gelesen werden koennen. */
        int available() throws IOException;

        void readFully(byte[] target) throws IOException;

        void close();
    }

    /** Windows: benannte Pipe, gelesen/geschrieben wie eine Datei. */
    private static final class WinPipe implements Transport {
        private final RandomAccessFile file;

        WinPipe(RandomAccessFile file) {
            this.file = file;
        }

        @Override
        public void write(byte[] data) throws IOException {
            file.write(data);
        }

        @Override
        public int available() throws IOException {
            return (int) Math.min(Integer.MAX_VALUE, file.length());
        }

        @Override
        public void readFully(byte[] target) throws IOException {
            file.readFully(target);
        }

        @Override
        public void close() {
            try {
                file.close();
            } catch (IOException e) {
                // egal
            }
        }
    }

    /** Linux/macOS: Unix-Socket, nicht blockierend. */
    private static final class UnixSock implements Transport {
        private final SocketChannel ch;
        private final ByteBuffer in = ByteBuffer.allocate(16 * 1024); // im Schreib-Modus gehalten

        UnixSock(SocketChannel ch) {
            this.ch = ch;
        }

        @Override
        public void write(byte[] data) throws IOException {
            ByteBuffer buf = ByteBuffer.wrap(data);
            long deadline = System.currentTimeMillis() + 3000;
            while (buf.hasRemaining()) {
                if (ch.write(buf) == 0) {
                    if (System.currentTimeMillis() > deadline) throw new IOException("Schreiben dauert zu lange");
                    pause(5);
                }
            }
        }

        @Override
        public int available() throws IOException {
            if (in.hasRemaining() && ch.read(in) < 0) throw new EOFException();
            return in.position();
        }

        @Override
        public void readFully(byte[] target) throws IOException {
            int done = 0;
            long deadline = System.currentTimeMillis() + 5000;
            while (done < target.length) {
                // erst den Zwischenspeicher leeren, dann direkt weiterlesen
                if (in.position() > 0) {
                    in.flip();
                    int n = Math.min(in.remaining(), target.length - done);
                    in.get(target, done, n);
                    in.compact();
                    done += n;
                    continue;
                }
                int n = ch.read(ByteBuffer.wrap(target, done, target.length - done));
                if (n < 0) throw new EOFException();
                if (n == 0) {
                    if (System.currentTimeMillis() > deadline) throw new IOException("Lesen dauert zu lange");
                    pause(5);
                }
                done += n;
            }
        }

        @Override
        public void close() {
            try {
                ch.close();
            } catch (IOException e) {
                // egal
            }
        }
    }

    /** Sucht das laufende Discord und oeffnet die Verbindung; null = nicht gefunden. */
    private static Transport open() {
        String forced = testDir;
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        if (windows && forced == null) {
            for (int i = 0; i < 10; i++) {
                try {
                    return new WinPipe(new RandomAccessFile("\\\\?\\pipe\\discord-ipc-" + i, "rw"));
                } catch (IOException | SecurityException e) {
                    // naechste Nummer probieren
                }
            }
            return null;
        }
        List<String> dirs = new ArrayList<>();
        if (forced != null) {
            dirs.add(forced);
        } else {
            for (String key : new String[] {"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
                String v = System.getenv(key);
                if (v != null && !v.isBlank()) dirs.add(v);
            }
            dirs.add("/tmp");
        }
        String[] subs = {"", "app/com.discordapp.Discord/", "snap.discord/", ".flatpak/com.discordapp.Discord/xdg-run/"};
        for (String dir : dirs) {
            for (String sub : subs) {
                for (int i = 0; i < 10; i++) {
                    Path p;
                    try {
                        p = Path.of(dir, sub + "discord-ipc-" + i);
                    } catch (RuntimeException e) {
                        continue;
                    }
                    if (!Files.exists(p)) continue;
                    SocketChannel ch = null;
                    try {
                        ch = SocketChannel.open(StandardProtocolFamily.UNIX);
                        ch.connect(UnixDomainSocketAddress.of(p));
                        ch.configureBlocking(false);
                        return new UnixSock(ch);
                    } catch (IOException | RuntimeException e) {
                        if (ch != null) {
                            try {
                                ch.close();
                            } catch (IOException e2) {
                                // egal
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    // ---------- Nachrichten ----------

    private record Frame(int op, String json) {
    }

    private static void send(Transport t, int op, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(op).putInt(body.length).put(body);
        t.write(buf.array());
    }

    /** Liest eine wartende Nachricht; null = gerade nichts da. */
    private static Frame poll(Transport t) throws IOException {
        if (t.available() < 8) return null;
        byte[] head = new byte[8];
        t.readFully(head);
        ByteBuffer hb = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        int op = hb.getInt();
        int len = hb.getInt();
        if (len < 0 || len > MAX_FRAME) throw new IOException("Ungueltige Nachricht");
        byte[] body = new byte[len];
        t.readFully(body);
        return new Frame(op, new String(body, StandardCharsets.UTF_8));
    }

    private static Frame await(Transport t, long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline && wanted) {
            Frame f = poll(t);
            if (f != null) return f;
            pause(25);
        }
        return null;
    }

    /** Text sicher in JSON einbetten. */
    static String esc(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    /** Discord nimmt nur Zeilen mit 2 bis 128 Zeichen. */
    private static String line(String s) {
        String t = s == null ? "" : s.strip();
        if (t.length() > 128) t = t.substring(0, 128);
        return t.length() < 2 ? "" : t;
    }

    /** Der sichtbare Teil der Anzeige (ohne die bei jeder Nachricht neue Kennung). */
    static String activity(String line1, String line2, long start) {
        StringBuilder sb = new StringBuilder("{");
        String d = line(line1);
        String s = line(line2);
        if (!d.isEmpty()) sb.append("\"details\":\"").append(esc(d)).append("\",");
        if (!s.isEmpty()) sb.append("\"state\":\"").append(esc(s)).append("\",");
        if (start > 0) sb.append("\"timestamps\":{\"start\":").append(start).append("},");
        sb.append("\"instance\":false}");
        return sb.toString();
    }

    private static String command(String activityJson) {
        long pid = ProcessHandle.current().pid();
        return "{\"cmd\":\"SET_ACTIVITY\",\"args\":{\"pid\":" + pid
                + (activityJson == null ? "" : ",\"activity\":" + activityJson)
                + "},\"nonce\":\"" + UUID.randomUUID() + "\"}";
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------- Hintergrund-Thread ----------

    private static void run() {
        Transport t = null;
        String connectedId = null;
        String shown = null;
        long lastSend = 0;
        long nextTry = 0;
        try {
            while (wanted && !Thread.currentThread().isInterrupted()) {
                long now = System.currentTimeMillis();
                String id = appId;
                if (!id.matches("\\d{15,22}")) {
                    status = id.isEmpty() ? "Keine App-ID eingetragen" : "App-ID besteht nur aus 17 bis 20 Ziffern";
                    if (t != null) {
                        t.close();
                        t = null;
                    }
                    pause(500);
                    continue;
                }
                if (t != null && !id.equals(connectedId)) {
                    t.close(); // andere App-ID: neu anmelden
                    t = null;
                }
                if (t == null) {
                    if (now < nextTry) {
                        pause(250);
                        continue;
                    }
                    try {
                        t = open();
                        if (t == null) {
                            status = "Discord nicht gefunden (läuft Discord auf diesem PC?)";
                            nextTry = now + 10_000;
                            continue;
                        }
                        send(t, OP_HANDSHAKE, "{\"v\":1,\"client_id\":\"" + esc(id) + "\"}");
                        Frame reply = await(t, 5000);
                        if (reply == null) throw new IOException("keine Antwort");
                        if (reply.op() == OP_CLOSE || reply.json().contains("\"evt\":\"ERROR\"")) {
                            status = "Discord lehnt die App-ID ab";
                            t.close();
                            t = null;
                            nextTry = now + 15_000;
                            continue;
                        }
                        connectedId = id;
                        shown = null;
                        status = "Verbunden";
                    } catch (IOException e) {
                        if (t != null) t.close();
                        t = null;
                        status = "Verbindung zu Discord fehlgeschlagen";
                        nextTry = now + 10_000;
                        continue;
                    }
                }
                try {
                    Frame f;
                    while ((f = poll(t)) != null) {
                        if (f.op() == OP_PING) send(t, OP_PONG, f.json());
                        else if (f.op() == OP_CLOSE) throw new IOException("von Discord geschlossen");
                    }
                    String want = activity(details, state, startMs);
                    if (!want.equals(shown) && now - lastSend >= MIN_SEND_GAP_MS) {
                        send(t, OP_FRAME, command(want));
                        shown = want;
                        lastSend = now;
                    }
                } catch (IOException e) {
                    t.close();
                    t = null;
                    status = "Verbindung zu Discord getrennt";
                    nextTry = now + 5000;
                }
                pause(250);
            }
        } finally {
            if (t != null) {
                try {
                    send(t, OP_FRAME, command(null)); // Anzeige entfernen
                } catch (IOException e) {
                    // Discord ist schon weg
                }
                t.close();
            }
            status = "Aus";
        }
    }
}
