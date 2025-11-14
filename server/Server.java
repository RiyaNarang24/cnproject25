import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private ServerSocket serverSocket;
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();

    public Server(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("Server started on port " + port);
    }

    public void start() {
        try {
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket, this);
                clients.add(handler);
                handler.start();
            }
        } catch (IOException e) {
            System.out.println("Server accept loop ended: " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    public void broadcast(String message, ClientHandler exclude) {
        // Send message to all clients (including sender is fine too)
        for (ClientHandler ch : clients) {
            if (ch != exclude) {
                ch.send(message);
            }
        }
    }

    public void removeClient(ClientHandler ch) {
        clients.remove(ch);
    }

    public void shutdown() {
        try {
            serverSocket.close();
        } catch (IOException ignored) {}
        for (ClientHandler ch : clients) {
            ch.close();
        }
    }

    public static void main(String[] args) {
        int port = 5000;
        try {
            Server server = new Server(port);
            server.start();
        } catch (IOException e) {
            System.err.println("Unable to start server: " + e.getMessage());
        }
    }

    // Inner class for client handling
    private static class ClientHandler extends Thread {
        private Socket socket;
        private Server server;
        private PrintWriter out;
        private BufferedReader in;
        private String username = "unknown";

        public ClientHandler(Socket socket, Server server) {
            this.socket = socket;
            this.server = server;
        }

        public void send(String msg) {
            if (out != null) {
                out.println(msg);
                out.flush();
            }
        }

        public void close() {
            try { socket.close(); } catch (IOException ignored) {}
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // first messages could include JOIN:<username>
                String line;
                while ((line = in.readLine()) != null) {
                    // if join, set username and notify
                    if (line.startsWith("JOIN:")) {
                        username = line.substring(5);
                        System.out.println(username + " joined.");
                        server.broadcast("JOIN:" + username, this);
                        // do not continue (but broadcast JOIN also)
                    } else if (line.startsWith("LEFT:")) {
                        username = line.substring(5);
                        server.broadcast(line, this);
                        break; // will close
                    } else {
                        // other messages: broadcast to all
                        server.broadcast(line, this);
                    }
                }
            } catch (IOException e) {
                System.out.println("ClientHandler IO error: " + e.getMessage());
            } finally {
                server.removeClient(this);
                if (!username.equals("unknown")) {
                    server.broadcast("LEFT:" + username, this);
                    System.out.println(username + " left.");
                }
                close();
            }
        }
    }
}

