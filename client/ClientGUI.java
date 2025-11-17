import javax.swing.*;
import java.awt.*;

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
        frame.setResizable(false);   // IMPORTANT FIX — prevents canvas resizing
        frame.setSize(1100, 700);

        // FIXED SIZE CANVAS (exact same for all clients)
        canvas = new CanvasPanel(800, 600);

        canvas.setPreferredSize(new Dimension(800, 600));
        canvas.setMinimumSize(new Dimension(800, 600));
        canvas.setMaximumSize(new Dimension(800, 600));

        canvas.setDrawListener(cmd -> {
            String[] parts = cmd.split(":", 2);
            String commandType = parts[0];
            String payload = (parts.length > 1) ? parts[1] : "";

            if (commandType.equals("CLEAR")) {
                client.send("CLEAR:" + username);
            } else {
                client.send(commandType + ":" + username + ":" + payload);
            }
        });

        // Wrap canvas inside FIXED panel — THIS FIXES ALL SIZING ISSUES
        JPanel canvasHolder = new JPanel(new BorderLayout());
        canvasHolder.setPreferredSize(new Dimension(800, 600));
        canvasHolder.setMinimumSize(new Dimension(800, 600));
        canvasHolder.setMaximumSize(new Dimension(800, 600));
        canvasHolder.add(canvas, BorderLayout.CENTER);

        // ----- TOOLBAR -----
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));

        ButtonGroup toolGroup = new ButtonGroup();

        JToggleButton pencilBtn = new JToggleButton("Pencil");
        pencilBtn.setSelected(true);
        toolGroup.add(pencilBtn);
        toolbar.add(pencilBtn);

        JToggleButton eraserBtn = new JToggleButton("Eraser");
        toolGroup.add(eraserBtn);
        toolbar.add(eraserBtn);

        JToggleButton lineBtn = new JToggleButton("Line");
        toolGroup.add(lineBtn);
        toolbar.add(lineBtn);

        JToggleButton rectBtn = new JToggleButton("Rectangle");
        toolGroup.add(rectBtn);
        toolbar.add(rectBtn);

        toolbar.add(new JSeparator(SwingConstants.VERTICAL));

        pencilBtn.addActionListener(e -> canvas.setToolMode(CanvasPanel.ToolMode.PENCIL));
        eraserBtn.addActionListener(e -> canvas.setToolMode(CanvasPanel.ToolMode.ERASER));
        lineBtn.addActionListener(e -> canvas.setToolMode(CanvasPanel.ToolMode.LINE));
        rectBtn.addActionListener(e -> canvas.setToolMode(CanvasPanel.ToolMode.RECTANGLE));

        JButton colorBtn = new JButton("Color");
        toolbar.add(colorBtn);

        toolbar.add(new JSeparator(SwingConstants.VERTICAL));

        JButton undoBtn = new JButton("Undo");
        toolbar.add(undoBtn);

        JButton clearBtn = new JButton("Clear");
        toolbar.add(clearBtn);

        toolbar.add(new JLabel(" Size:"));

        JSlider strokeSlider = new JSlider(JSlider.HORIZONTAL, 1, 30, 3);
        strokeSlider.setMajorTickSpacing(10);
        strokeSlider.setMinorTickSpacing(1);
        strokeSlider.setPaintTicks(true);
        toolbar.add(strokeSlider);

        colorBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(frame, "Pick Color", Color.BLACK);
            if (c != null) canvas.setColor(c);
        });

        undoBtn.addActionListener(e -> canvas.undo());
        clearBtn.addActionListener(e -> canvas.clearCanvas(true));

        strokeSlider.addChangeListener(e -> {
            int width = strokeSlider.getValue();
            canvas.setStrokeWidth(width);
            canvas.setEraserStrokeWidth(width * 2);
        });

        // LEFT = toolbar + canvasHolder
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(toolbar, BorderLayout.NORTH);
        leftPanel.add(canvasHolder, BorderLayout.CENTER);   // FIXED CANVAS PANEL

        // ----- CHAT PANEL -----
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
        rightPanel.add(disconnectBtn, BorderLayout.NORTH);
        rightPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        rightPanel.add(chatBottom, BorderLayout.SOUTH);

        // ----- FRAME LAYOUT -----
        frame.getContentPane().add(leftPanel, BorderLayout.CENTER);
        frame.getContentPane().add(rightPanel, BorderLayout.EAST);

        frame.setVisible(true);
    }

    private void sendChat() {
        String text = chatInput.getText().trim();
        if (!text.isEmpty()) {
            chatArea.append(username + ": " + text + "\n");
            client.send("CHAT:" + username + ":" + text);
            chatInput.setText("");
        }
    }

    public void handleServerMessage(String msg) {
        if (msg.startsWith("CHAT:")) {
            String[] parts = msg.split(":", 3);
            if (parts.length >= 3) {
                chatArea.append(parts[1] + ": " + parts[2] + "\n");
            }
        } else if (msg.startsWith("DRAW:") || msg.startsWith("LINE:") || msg.startsWith("RECT:")) {
            canvas.applyDrawCommandLocal(msg, true);
        } else if (msg.startsWith("CLEAR:")) {
            canvas.clearCanvas(false);
        } else if (msg.startsWith("JOIN:")) {
            chatArea.append("[System] " + msg.substring(5) + " joined.\n");
        } else if (msg.startsWith("LEFT:")) {
            chatArea.append("[System] " + msg.substring(5) + " left.\n");
        }
    }

    public void showSystemMessage(String msg) {
        JOptionPane.showMessageDialog(frame, msg);
    }
}
