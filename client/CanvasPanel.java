import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class CanvasPanel extends JPanel {

    private BufferedImage canvasImage;
    private Graphics2D canvasG;
    private Color currentColor = Color.BLACK;
    private int stroke = 3;

    private int prevX = -1, prevY = -1;

    private List<String> history = new ArrayList<>();

    public interface DrawListener {
        void sendDrawCommand(String cmd);
    }

    private DrawListener listener;

    public CanvasPanel(int width, int height) {

        // Let layout resize this panel
        setPreferredSize(new Dimension(width, height));
        setBackground(Color.WHITE);
        setOpaque(true);

        initCanvas(width, height);

        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                prevX = e.getX();
                prevY = e.getY();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                int x = e.getX();
                int y = e.getY();
                drawSegment(prevX, prevY, x, y, currentColor, stroke, true);
                prevX = x;
                prevY = y;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                prevX = -1;
                prevY = -1;
            }
        };

        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    private void initCanvas(int w, int h) {
        canvasImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        canvasG = canvasImage.createGraphics();
        canvasG.setColor(Color.WHITE);
        canvasG.fillRect(0, 0, w, h);
        canvasG.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    }

    public void setDrawListener(DrawListener l) {
        this.listener = l;
    }

    public void setColor(Color c) {
        currentColor = c;
    }

    public void setStrokeWidth(int s) {
        this.stroke = s;
        canvasG.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    }

    public void clearCanvas(boolean send) {
        canvasG.setColor(Color.WHITE);
        canvasG.fillRect(0, 0, canvasImage.getWidth(), canvasImage.getHeight());
        repaint();
        history.clear();

        if (send && listener != null) {
            listener.sendDrawCommand("CLEAR:");
        }
    }

    public void undo() {
        if (!history.isEmpty()) {
            history.remove(history.size() - 1);

            canvasG.setColor(Color.WHITE);
            canvasG.fillRect(0, 0, canvasImage.getWidth(), canvasImage.getHeight());

            for (String cmd : history) {
                applyDrawCommandLocal(cmd, false);
            }

            repaint();
        }
    }

    /** APPLY COMMAND RECEIVED FROM SERVER **/
    public void applyDrawCommandLocal(String cmd, boolean storeInHistory) {
        try {
            String[] parts = cmd.split(":", 3);
            if (parts.length < 3) return;

            String payload = parts[2];
            String[] fields = payload.split(",");
            if (fields.length < 8) return;

            int x1 = Integer.parseInt(fields[0]);
            int y1 = Integer.parseInt(fields[1]);
            int x2 = Integer.parseInt(fields[2]);
            int y2 = Integer.parseInt(fields[3]);
            int r = Integer.parseInt(fields[4]);
            int g = Integer.parseInt(fields[5]);
            int b = Integer.parseInt(fields[6]);
            int st = Integer.parseInt(fields[7]);

            drawSegment(x1, y1, x2, y2, new Color(r, g, b), st, false);

            if (storeInHistory) history.add(cmd);

        } catch (Exception ex) {
            System.out.println("Bad DRAW cmd: " + cmd + " -> " + ex.getMessage());
        }
    }

    /** DRAW LOCALLY + SEND TO SERVER IF LOCAL USER **/
    private void drawSegment(int x1, int y1, int x2, int y2, Color color, int st, boolean localSend) {
        if (canvasG == null) return;

        canvasG.setStroke(new BasicStroke(st, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        canvasG.setColor(color);
        canvasG.drawLine(x1, y1, x2, y2);

        repaint();

        if (localSend && listener != null) {
            String cmd = "DRAW:" + x1 + "," + y1 + "," + x2 + "," + y2 + "," +
                    color.getRed() + "," + color.getGreen() + "," + color.getBlue() + "," + st;

            history.add(cmd);
            listener.sendDrawCommand(cmd);
        }
    }

    /** AUTO RESIZE CANVAS WHEN PANEL RESIZES **/
    @Override
    public void invalidate() {
        super.invalidate();
        resizeCanvas();
    }

    private void resizeCanvas() {
        int w = getWidth();
        int h = getHeight();

        if (w <= 0 || h <= 0) return;

        BufferedImage newImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = newImg.createGraphics();

        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, w, h);

        if (canvasImage != null) {
            g2.drawImage(canvasImage, 0, 0, null);
        }

        g2.dispose();

        canvasImage = newImg;
        canvasG = canvasImage.createGraphics();
        canvasG.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (canvasImage != null) {
            g.drawImage(canvasImage, 0, 0, getWidth(), getHeight(), null);
        }
    }
}
