import javax.swing.*;
import java.io.*;
import java.net.*;

public class ClientMain {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;
    private ClientGUI gui;

    public ClientMain(String host, int port, String username) throws IOException {
        this.username = username;
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

        // Build GUI
        gui = new ClientGUI(this, username);

        // Send JOIN
        send("JOIN:" + username);

        // Start listening thread
        new Thread(this::listenLoop).start();
    }

    public void send(String message) {
        out.println(message);
        out.flush();
    }

    public void listenLoop() {
        String line;
        try {
            while ((line = in.readLine()) != null) {
                // forward to GUI for processing on Swing thread
                final String msg = line;
                SwingUtilities.invokeLater(() -> gui.handleServerMessage(msg));
            }
        } catch (IOException e) {
            System.out.println("Connection lost: " + e.getMessage());
            SwingUtilities.invokeLater(() -> gui.showSystemMessage("Connection lost to server."));
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    public void disconnect() {
        try {
            send("LEFT:" + username);
            socket.close();
        } catch (IOException ignored) {}
    }

    public static void main(String[] args) {
        // quick start: java ClientMain localhost 5000 Alice
        if (args.length < 3) {
            System.out.println("Usage: java ClientMain <host> <port> <username>");
            System.exit(0);
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String username = args[2];

        SwingUtilities.invokeLater(() -> {
            try {
                new ClientMain(host, port, username);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Unable to connect: " + e.getMessage());
            }
        });
    }
}
