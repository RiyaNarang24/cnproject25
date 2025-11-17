import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
public class CanvasPanel extends JPanel {
    public enum ToolMode {
        PENCIL,
        ERASER,
        LINE,
        RECTANGLE
    }private ToolMode currentMode = ToolMode.PENCIL;
    private BufferedImage canvasImage;
    private Graphics2D canvasG;
    private Color currentColor = Color.BLACK;
    private int stroke = 3;
    private int eraserStroke = 10;
    private int startX, startY;
    private boolean isDragging = false;
    private int currentX, currentY; 
    private int prevX = -1, prevY = -1; // For pencil/eraser
    private List<String> history = new ArrayList<>();
    private Map<String, long[]> activeDrawers = new ConcurrentHashMap<>();
    public interface DrawListener {
        void sendDrawCommand(String cmd);
    } private DrawListener listener;
    public CanvasPanel(int width, int height) {
        setPreferredSize(new Dimension(width, height));
        setBackground(Color.WHITE);
        setOpaque(true);
        initCanvas(width, height);
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                isDragging = true;
                startX = e.getX();
                startY = e.getY();
                currentX = startX;
                currentY = startY;
                if (currentMode == ToolMode.PENCIL || currentMode == ToolMode.ERASER) {
                    prevX = startX;
                    prevY = startY;
                } }
            @Override
            public void mouseDragged(MouseEvent e) {
                currentX = e.getX();
                currentY = e.getY();
                if (currentMode == ToolMode.PENCIL) {
                    drawSegment(prevX, prevY, currentX, currentY, currentColor, stroke, true);
                    prevX = currentX;
                    prevY = currentY;
                } else if (currentMode == ToolMode.ERASER) {
                    drawSegment(prevX, prevY, currentX, currentY, Color.WHITE, eraserStroke, true);
                    prevX = currentX;
                    prevY = currentY;
                } else if (currentMode == ToolMode.LINE || currentMode == ToolMode.RECTANGLE) {
                    repaint();
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                isDragging = false;
                int endX = e.getX();
                int endY = e.getY();
                if (currentMode == ToolMode.LINE) {
                    drawFinalLine(startX, startY, endX, endY, currentColor, stroke, true);
                } else if (currentMode == ToolMode.RECTANGLE) {
                    drawFinalRect(startX, startY, endX, endY, currentColor, stroke, true);
                }
                prevX = -1;
                prevY = -1;
                startX = 0;
                startY = 0;
                currentX = 0;
                currentY = 0;
                
                repaint();  } };
                addMouseListener(ma);
                addMouseMotionListener(ma);
                javax.swing.Timer repaintTimer = new javax.swing.Timer(500, e -> repaint());
                repaintTimer.start(); }
    private void initCanvas(int w, int h) {
        canvasImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        canvasG = canvasImage.createGraphics();
        canvasG.setColor(Color.WHITE);
        canvasG.fillRect(0, 0, w, h);
        canvasG.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); }
    public void setDrawListener(DrawListener l) {
        this.listener = l; }
    public void setColor(Color c) {
        currentColor = c;}
    public void setToolMode(ToolMode mode) {
        this.currentMode = mode; }
    public void setStrokeWidth(int s) {
        this.stroke = s;}
    public void setEraserStrokeWidth(int s) {
        this.eraserStroke = s;}
    public void clearCanvas(boolean send) {
        canvasG.setColor(Color.WHITE);
        canvasG.fillRect(0, 0, canvasImage.getWidth(), canvasImage.getHeight());
        repaint();
        history.clear();
        if (send && listener != null) {
            listener.sendDrawCommand("CLEAR:"); } }
    public void undo() {
        if (!history.isEmpty()) {
            history.remove(history.size() - 1);
            canvasG.setColor(Color.WHITE);
            canvasG.fillRect(0, 0, canvasImage.getWidth(), canvasImage.getHeight());
            for (String cmd : history) {
                applyDrawCommandLocal(cmd, false);  }
            repaint(); } }
    public void applyDrawCommandLocal(String cmd, boolean storeInHistory) {
        try {
            String[] parts = cmd.split(":", 3);
            if (parts.length < 3) return;
            String commandType = parts[0];
            String username = parts[1];
            String payload = parts[2];
            switch (commandType) {
                case "DRAW": {
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
                    long[] info = new long[] { x2, y2, System.currentTimeMillis() };
                    activeDrawers.put(username, info);
                    drawSegment(x1, y1, x2, y2, new Color(r, g, b), st, false);
                    break; }
                case "LINE": {
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
                    drawFinalLine(x1, y1, x2, y2, new Color(r,g,b), st, false);
                    break;}
                case "RECT": {
                    String[] fields = payload.split(",");
                    if (fields.length < 8) return;
                    int x = Integer.parseInt(fields[0]);
                    int y = Integer.parseInt(fields[1]);
                    int w = Integer.parseInt(fields[2]);
                    int h = Integer.parseInt(fields[3]);
                    int r = Integer.parseInt(fields[4]);
                    int g = Integer.parseInt(fields[5]);
                    int b = Integer.parseInt(fields[6]);
                    int st = Integer.parseInt(fields[7]);
                    drawFinalRect(x, y, w, h, new Color(r,g,b), st, false);
                    break; }}
            if (storeInHistory) history.add(cmd);
        } catch (Exception ex) {
            System.out.println("Bad DRAW cmd: " + cmd + " -> " + ex.getMessage()); } }
    private void drawSegment(int x1, int y1, int x2, int y2, Color color, int st, boolean localSend) {
        if (canvasG == null) return;
        canvasG.setStroke(new BasicStroke(st, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        canvasG.setColor(color);
        canvasG.drawLine(x1, y1, x2, y2);
        repaint();
        if (localSend && listener != null) {
            String cmdPayload = x1 + "," + y1 + "," + x2 + "," + y2 + "," +
                    color.getRed() + "," + color.getGreen() + "," + color.getBlue() + "," + st;
            listener.sendDrawCommand("DRAW:" + cmdPayload);
            history.add("DRAW:local:" + cmdPayload);  }}
    private void drawFinalLine(int x1, int y1, int x2, int y2, Color color, int st, boolean localSend) {
        if (canvasG == null) return;
        canvasG.setStroke(new BasicStroke(st, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        canvasG.setColor(color);
        canvasG.drawLine(x1, y1, x2, y2);
        repaint();
        if (localSend && listener != null) {
            String cmdPayload = x1 + "," + y1 + "," + x2 + "," + y2 + "," +
                    color.getRed() + "," + color.getGreen() + "," + color.getBlue() + "," + st;
            listener.sendDrawCommand("LINE:" + cmdPayload);
            history.add("LINE:local:" + cmdPayload); }}
    private void drawFinalRect(int x1, int y1, int x2, int y2, Color color, int st, boolean localSend) {
        if (canvasG == null) return;
        int x = Math.min(x1, x2);
        int y = Math.min(y1, y2);
        int width = Math.abs(x1 - x2);
        int height = Math.abs(y1 - y2);
        canvasG.setStroke(new BasicStroke(st, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        canvasG.setColor(color);
        canvasG.drawRect(x, y, width, height);
        repaint();
        if (localSend && listener != null) {
            String cmdPayload = x + "," + y + "," + width + "," + height + "," +
                    color.getRed() + "," + color.getGreen() + "," + color.getBlue() + "," + st;
            listener.sendDrawCommand("RECT:" + cmdPayload);
            history.add("RECT:local:" + cmdPayload); }}
    @Override
    public void invalidate() {
        super.invalidate();
        resizeCanvas();}
    private void resizeCanvas() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        BufferedImage newImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = newImg.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, w, h);
        if (canvasImage != null) {
            g2.drawImage(canvasImage, 0, 0, null); }
        g2.dispose();
        canvasImage = newImg;
        canvasG = canvasImage.createGraphics();
        canvasG.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));}
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (canvasImage != null) {
            g.drawImage(canvasImage, 0, 0, getWidth(), getHeight(), null);
        }

        // --- THIS IS THE CORRECTED BLOCK ---
        // Cast g to Graphics2D to access setStroke
        Graphics2D g2d = (Graphics2D) g;

        // Draw the live preview shape
        if (isDragging) {
            g2d.setColor(currentColor);
            // Now this line will work correctly
            g2d.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            if (currentMode == ToolMode.LINE) {
                g2d.drawLine(startX, startY, currentX, currentY);
            } else if (currentMode == ToolMode.RECTANGLE) {
                int x = Math.min(startX, currentX);
                int y = Math.min(startY, currentY);
                int width = Math.abs(startX - currentX);
                int height = Math.abs(startY - currentY);
                g2d.drawRect(x, y, width, height);
            }
        }
        long now = System.currentTimeMillis();
        Graphics2D g2Usernames = (Graphics2D) g;
        g2Usernames.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2Usernames.setFont(new Font("Arial", Font.BOLD, 12));
        
        Iterator<Map.Entry<String, long[]>> iterator = activeDrawers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, long[]> entry = iterator.next();
            long[] info = entry.getValue();
            long time = info[2];

            if (now - time > 1500) {
                iterator.remove();
            } else {
                String username = entry.getKey();
                int x = (int) info[0];
                int y = (int) info[1];
                g2Usernames.setColor(Color.BLACK);
                g2Usernames.drawString(username, x + 6, y + 16);
                g2Usernames.setColor(Color.WHITE);
                g2Usernames.drawString(username, x + 5, y + 15);
            } }}}
        
    
