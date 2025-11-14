import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class ClientGUI {
    private JFrame frame;
    private JTextArea chatArea;
    private JTextField chatInput;
    private CanvasPanel canvas;
    private ClientMain client;
    private String username;

    public ClientGUI(ClientMain client, String username) {
        this.client = client;
        this.username = username;
        createAndShow();
    }

    private void createAndShow() {
        frame = new JFrame("Collaborative Board - " + username);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(900, 600);

        // Left: canvas and toolbar
        canvas = new CanvasPanel(600, 450);
canvas.setDrawListener(cmd -> {

    if (cmd.startsWith("DRAW:")) {
        // DRAW:x1,y1,x2,y2,r,g,b,stroke
        String payload = cmd.substring(5);
        client.send("DRAW:" + username + ":" + payload);

    } else if (cmd.startsWith("CLEAR:")) {
        client.send("CLEAR:" + username);
    }
});



        JPanel toolbar = new JPanel();
        JButton clearBtn = new JButton("Clear");
        JButton undoBtn = new JButton("Undo");
        JButton colorBtn = new JButton("Color");
        toolbar.add(clearBtn);
        toolbar.add(undoBtn);
        toolbar.add(colorBtn);

        clearBtn.addActionListener(e -> canvas.clearCanvas(true));
        undoBtn.addActionListener(e -> {
            canvas.undo();
            // no network undo broadcast in this simple version
        });
        colorBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(frame, "Pick Color", Color.BLACK);
            if (c != null) canvas.setColor(c);
        });

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(toolbar, BorderLayout.NORTH);
        leftPanel.add(canvas, BorderLayout.CENTER);

        // Right: chat
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatInput = new JTextField();
        JButton sendBtn = new JButton("Send");
        JPanel chatBottom = new JPanel(new BorderLayout());
        chatBottom.add(chatInput, BorderLayout.CENTER);
        chatBottom.add(sendBtn, BorderLayout.EAST);

        sendBtn.addActionListener(e -> sendChat());
        chatInput.addActionListener(e -> sendChat());

        JButton disconnectBtn = new JButton("Disconnect");
        disconnectBtn.addActionListener(e -> {
            client.disconnect();
            frame.dispose();
        });

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(260, 0));
        rightPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        rightPanel.add(chatBottom, BorderLayout.SOUTH);
        rightPanel.add(disconnectBtn, BorderLayout.NORTH);

        frame.getContentPane().add(leftPanel, BorderLayout.CENTER);
        frame.getContentPane().add(rightPanel, BorderLayout.EAST);

        frame.setVisible(true);
    }

    private void sendChat() {
        String text = chatInput.getText().trim();
        if (!text.isEmpty()) {
            client.send("CHAT:" + username + ":" + text);
            chatInput.setText("");
        }
    }

    // Called from ClientMain when a line arrives from server
    public void handleServerMessage(String msg) {
        if (msg.startsWith("CHAT:")) {
            // CHAT:username:message
            String[] parts = msg.split(":", 3);
            if (parts.length >= 3) {
                chatArea.append(parts[1] + ": " + parts[2] + "\n");
            }
        } else if (msg.startsWith("DRAW:")) {
            // DRAW:username:x1,y1,x2,y2,r,g,b,stroke
            canvas.applyDrawCommandLocal(msg, true);
        } else if (msg.startsWith("CLEAR:")) {
            // CLEAR:username
            canvas.clearCanvas(false); // false so we don't re-broadcast
        } else if (msg.startsWith("JOIN:")) {
            String u = msg.substring(5);
            chatArea.append("[System] " + u + " joined.\n");
        } else if (msg.startsWith("LEFT:")) {
            String u = msg.substring(5);
            chatArea.append("[System] " + u + " left.\n");
        } else {
            // unknown - show as system
            chatArea.append("[Server] " + msg + "\n");
        }
    }

    public void showSystemMessage(String m) {
        JOptionPane.showMessageDialog(frame, m);
    }
}
