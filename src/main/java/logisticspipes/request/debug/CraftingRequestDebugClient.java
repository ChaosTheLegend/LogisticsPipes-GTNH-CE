package logisticspipes.request.debug;

import static javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.debug.CraftingRequestDebugRequest;
import logisticspipes.proxy.MainProxy;

public final class CraftingRequestDebugClient {

    private static final long AUTO_REFRESH_INTERVAL = 1000L;

    private static boolean requestKeyWasDown = false;
    private static boolean requestInFlight = false;
    private static volatile boolean refreshRequested = false;
    private static long requestSentAt = 0L;
    private static long nextAutoRefresh = 0L;
    private static DebugWindow window = null;
    private static boolean clearInfo = false;

    private CraftingRequestDebugClient() {}

    /**
     * Polls the client-side debug key binding and keeps an open debug window refreshed.
     */
    public static void clientTick(ClientTickEvent event) {
        if (event.phase != Phase.END || !Keyboard.isCreated()) {
            return;
        }
        if (FMLClientHandler.instance().getClient().theWorld == null) {
            requestInFlight = false;
            requestKeyWasDown = false;
            closeWindow();
            return;
        }

        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        boolean control = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        boolean requestKeyDown = shift && control && Keyboard.isKeyDown(Keyboard.KEY_T);
        if (requestKeyDown && !requestKeyWasDown) {
            refreshRequested = true;
        }
        requestKeyWasDown = requestKeyDown;

        DebugWindow currentWindow = window;
        long now = System.currentTimeMillis();
        if (currentWindow != null && currentWindow.isAutoRefresh() && now >= nextAutoRefresh) {
            refreshRequested = true;
            nextAutoRefresh = now + AUTO_REFRESH_INTERVAL;
        }

        if (refreshRequested) {
            sendRefreshRequest();
        }
    }

    /**
     * Opens or updates the Swing window containing the server-built crafting request debug snapshot.
     */
    public static void openWindow(String title, String payload) {
        requestInFlight = false;
        requestSentAt = 0L;
        refreshRequested = false;
        nextAutoRefresh = System.currentTimeMillis() + AUTO_REFRESH_INTERVAL;
        SwingUtilities.invokeLater(() -> {
            if (window == null || !window.isDisplayable()) {
                window = new DebugWindow(title);
            }
            window.update(title, payload);
        });
    }

    public static void closeWindow() {
        DebugWindow currentWindow = window;
        if (currentWindow == null) {
            return;
        }
        window = null;
        SwingUtilities.invokeLater(currentWindow::dispose);
    }

    private static void requestRefreshSoon() {
        refreshRequested = true;
    }

    private static void clearInfo() {
        clearInfo = true;
        requestRefreshSoon();
    }

    private static void sendRefreshRequest() {
        if (requestInFlight) {
            if (System.currentTimeMillis() - requestSentAt < 5000L) {
                return;
            }
            requestInFlight = false;
        }
        if (FMLClientHandler.instance().getClient().theWorld == null) {
            return;
        }
        requestInFlight = true;
        requestSentAt = System.currentTimeMillis();
        refreshRequested = false;
        var packet = PacketHandler.getPacket(CraftingRequestDebugRequest.class);
        if (clearInfo) {
            packet.clearInfo = true;
            clearInfo = false;
        }
        MainProxy.sendPacketToServer(packet);
    }

    /**
     * Sends a small chat fallback when the server could not build a snapshot response.
     */
    public static void notifyUnavailable(EntityPlayer player) {
        requestInFlight = false;
        requestSentAt = 0L;
        if (player != null) {
            player.addChatMessage(new ChatComponentText("No crafting request debug snapshot available."));
        }
    }

    private static class DebugWindow {

        private final JFrame frame;
        private final JCheckBox autoRefresh;
        private final JLabel status;
        private final JTextArea overview;
        private final JTextArea flow;
        private final JTextArea timeline;
        private final JTextArea verboseTimeline;
        private final JTextArea requests;
        private final JTextArea pipes;
        private final JTextArea raw;

        private DebugWindow(String title) {
            frame = new JFrame(normalizeTitle(title));
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {

                @Override
                public void windowClosed(WindowEvent e) {
                    if (window == DebugWindow.this) {
                        window = null;
                    }
                }
            });

            JButton refresh = new JButton("Refresh");
            refresh.addActionListener(e -> requestRefreshSoon());

            autoRefresh = new JCheckBox("Auto refresh", true);
            status = new JLabel("Waiting for snapshot");

            JButton clearInfo = new JButton("Clear Info");
            clearInfo.addActionListener(e -> clearInfo());

            JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
            toolbar.add(refresh);
            toolbar.add(autoRefresh);
            toolbar.add(status);
            toolbar.add(clearInfo);

            overview = createTextArea();
            flow = createTextArea();
            timeline = createTextArea();
            verboseTimeline = createTextArea();
            requests = createTextArea();
            pipes = createTextArea();
            raw = createTextArea();

            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Overview", new JScrollPane(overview));
            tabs.addTab("Crafting Flow", new JScrollPane(flow));
            tabs.addTab("Timeline", new JScrollPane(timeline));
            tabs.addTab("Verbose Timeline", new JScrollPane(verboseTimeline));
            tabs.addTab("Requests", new JScrollPane(requests));
            tabs.addTab("Pattern Pipes", new JScrollPane(pipes));
            tabs.addTab("Raw", new JScrollPane(raw));

            JPanel root = new JPanel(new BorderLayout());
            root.add(toolbar, BorderLayout.NORTH);
            root.add(tabs, BorderLayout.CENTER);

            addCloseBinding(frame);
            frame.getContentPane().add(root);
            frame.setPreferredSize(new Dimension(1250, 820));
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        }

        private boolean isDisplayable() {
            return frame.isDisplayable();
        }

        private boolean isAutoRefresh() {
            return autoRefresh.isSelected();
        }

        private void update(String title, String payload) {
            String text = payload == null ? "" : payload;
            frame.setTitle(normalizeTitle(title));
            Map<String, String> sections = splitSections(text);
            String header = sections.get("");
            String summary = sections.get("Summary");
            setTextPreservingCaret(overview, joinSections(header, summary));
            setTextPreservingCaret(flow, sections.get("Crafting Flow"));
            setTextPreservingCaret(timeline, sections.get("Timeline"));
            setTextPreservingCaret(verboseTimeline, sections.get("Verbose Timeline"));
            setTextPreservingCaret(requests, sections.get("Recorded Request Trees"));
            setTextPreservingCaret(pipes, sections.get("Active Pattern Crafting Pipes"));
            setTextPreservingCaret(raw, text);
            status.setText("Updated " + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()));
        }

        private void dispose() {
            frame.dispose();
        }

        private static JTextArea createTextArea() {
            JTextArea text = new JTextArea("");
            text.setEditable(false);
            text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            return text;
        }

        private static void setTextPreservingCaret(JTextArea textArea, String text) {
            int caret = textArea.getCaretPosition();
            textArea.setText(text == null ? "" : text);
            textArea.setCaretPosition(Math.min(caret, textArea.getDocument().getLength()));
        }

        private static String joinSections(String first, String second) {
            StringBuilder out = new StringBuilder();
            if (first != null && !first.isEmpty()) {
                out.append(first.trim()).append("\n\n");
            }
            if (second != null && !second.isEmpty()) {
                out.append(second.trim()).append("\n");
            }
            return out.toString();
        }

        private static Map<String, String> splitSections(String payload) {
            Map<String, String> sections = new LinkedHashMap<>();
            String current = "";
            StringBuilder currentText = new StringBuilder();
            String[] lines = (payload == null ? "" : payload).split("\\r?\\n");
            for (String line : lines) {
                if (line.startsWith("== ") && line.endsWith(" ==")) {
                    sections.put(current, currentText.toString());
                    current = line.substring(3, line.length() - 3);
                    currentText = new StringBuilder();
                } else {
                    currentText.append(line).append("\n");
                }
            }
            sections.put(current, currentText.toString());
            return sections;
        }

        private static String normalizeTitle(String title) {
            return title == null || title.isEmpty() ? "Crafting Request Debug" : title;
        }

        private static void addCloseBinding(JFrame frame) {
            frame.getRootPane().getActionMap().put("close-window", new CloseAction(frame));
            frame.getRootPane().getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .put(KeyStroke.getKeyStroke("control W"), "close-window");
            frame.getRootPane().getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .put(KeyStroke.getKeyStroke("meta W"), "close-window");
        }
    }

    private static class CloseAction extends AbstractAction {

        private final Window window;

        private CloseAction(Window window) {
            this.window = window;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (window == null) {
                return;
            }
            window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
        }
    }
}
