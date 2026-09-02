package com.tencent.trtcasr;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A minimal WebSocket (RFC 6455) server for hermetic recognizer tests.
 *
 * <p>Supports the subset the SDK exercises: HTTP Upgrade handshake, text /
 * binary / ping / pong / close frames, client-side masking.
 */
public class MiniWebSocketServer implements AutoCloseable {
    public interface Handler {
        void onConnection(Session session) throws Exception;
    }

    public static class Frame {
        public final int opcode;
        public final byte[] payload;

        Frame(int opcode, byte[] payload) {
            this.opcode = opcode;
            this.payload = payload;
        }

        public boolean isText() {
            return opcode == 0x1;
        }

        public boolean isBinary() {
            return opcode == 0x2;
        }

        public boolean isClose() {
            return opcode == 0x8;
        }

        public String text() {
            return new String(payload, StandardCharsets.UTF_8);
        }
    }

    public class Session {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;

        Session(Socket socket) throws IOException {
            this.socket = socket;
            this.in = socket.getInputStream();
            this.out = socket.getOutputStream();
        }

        /** Reads one frame; returns null on EOF/connection error. */
        public Frame read() {
            try {
                return readFrame();
            } catch (Exception e) {
                return null;
            }
        }

        private Frame readFrame() throws IOException {
            int b0 = in.read();
            if (b0 < 0) {
                return null;
            }
            int b1 = in.read();
            if (b1 < 0) {
                return null;
            }
            int opcode = b0 & 0x0F;
            boolean masked = (b1 & 0x80) != 0;
            long len = b1 & 0x7F;
            if (len == 126) {
                len = (long) readN(2);
            } else if (len == 127) {
                len = readN(8);
            }
            byte[] mask = masked ? readBytes(4) : null;
            byte[] payload = readBytes((int) len);
            if (masked) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= mask[i % 4];
                }
            }
            if (opcode == 0x9) { // ping → pong
                writeFrame(0xA, payload);
                return readFrame();
            }
            return new Frame(opcode, payload);
        }

        private long readN(int n) throws IOException {
            long v = 0;
            for (int i = 0; i < n; i++) {
                int b = in.read();
                if (b < 0) {
                    throw new EOFException();
                }
                v = (v << 8) | b;
            }
            return v;
        }

        private byte[] readBytes(int n) throws IOException {
            byte[] buf = new byte[n];
            int off = 0;
            while (off < n) {
                int r = in.read(buf, off, n - off);
                if (r < 0) {
                    throw new EOFException();
                }
                off += r;
            }
            return buf;
        }

        public synchronized void sendText(String text) throws IOException {
            writeFrame(0x1, text.getBytes(StandardCharsets.UTF_8));
        }

        public synchronized void sendBinary(byte[] data) throws IOException {
            writeFrame(0x2, data);
        }

        public synchronized void sendClose() throws IOException {
            writeFrame(0x8, new byte[0]);
        }

        private void writeFrame(int opcode, byte[] payload) throws IOException {
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            frame.write(0x80 | opcode);
            int len = payload.length;
            if (len < 126) {
                frame.write(len);
            } else if (len <= 0xFFFF) {
                frame.write(126);
                frame.write((len >> 8) & 0xFF);
                frame.write(len & 0xFF);
            } else {
                frame.write(127);
                for (int i = 7; i >= 0; i--) {
                    frame.write((int) ((len >> (8 * i)) & 0xFF));
                }
            }
            frame.write(payload);
            out.write(frame.toByteArray());
            out.flush();
        }

        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private final ServerSocket serverSocket;
    private final Thread thread;
    private final AtomicReference<String> requestTarget = new AtomicReference<>();
    private final AtomicReference<Throwable> handlerError = new AtomicReference<>();

    public MiniWebSocketServer(Handler handler) throws IOException {
        serverSocket = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
        thread = new Thread(() -> {
            try (Socket socket = serverSocket.accept()) {
                handshake(socket);
                handler.onConnection(new Session(socket));
            } catch (Throwable t) {
                handlerError.set(t);
            }
        }, "mini-ws-server");
        thread.setDaemon(true);
        thread.start();
    }

    private void handshake(Socket socket) throws Exception {
        InputStream in = socket.getInputStream();
        // Read request line + headers.
        StringBuilder head = new StringBuilder();
        int prev3 = -1, prev2 = -1, prev1 = -1;
        while (true) {
            int c = in.read();
            if (c < 0) {
                throw new EOFException("handshake EOF");
            }
            head.append((char) c);
            if (prev3 == '\r' && prev2 == '\n' && prev1 == '\r' && c == '\n') {
                break;
            }
            prev3 = prev2;
            prev2 = prev1;
            prev1 = c;
        }
        String[] lines = head.toString().split("\r\n");
        if (lines.length > 0) {
            String[] parts = lines[0].split(" ");
            if (parts.length >= 2) {
                requestTarget.set(parts[1]);
            }
        }
        String key = null;
        for (String line : lines) {
            int idx = line.indexOf(':');
            if (idx > 0 && line.substring(0, idx).trim().equalsIgnoreCase("Sec-WebSocket-Key")) {
                key = line.substring(idx + 1).trim();
            }
        }
        if (key == null) {
            throw new IOException("missing Sec-WebSocket-Key");
        }
        String accept = Base64.getEncoder().encodeToString(MessageDigest
                .getInstance("SHA-1")
                .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                        .getBytes(StandardCharsets.UTF_8)));
        String resp = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
        OutputStream out = socket.getOutputStream();
        out.write(resp.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    public String url() {
        return "ws://127.0.0.1:" + serverSocket.getLocalPort();
    }

    /** The handshake request target (path + query) for assertions. */
    public String requestTarget() {
        return requestTarget.get();
    }

    /** Waits for the handler thread to finish (bounded). */
    public void join(long millis) throws InterruptedException {
        thread.join(millis);
    }

    /** Error thrown by the handler, if any. */
    public Throwable handlerError() {
        return handlerError.get();
    }

    @Override
    public void close() {
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
    }
}
