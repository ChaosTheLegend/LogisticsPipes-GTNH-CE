package network.rs485.debuggui;

import static javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import network.rs485.debuggui.api.IDataConnection;
import network.rs485.debuggui.api.IDebugGuiEntry;
import network.rs485.debuggui.api.IObjectIdentification;

public class DebugGuiEntry extends IDebugGuiEntry {

    private static final byte PACKET_REQUEST_SNAPSHOT = 1;
    private static final byte PACKET_CLOSE = 2;
    private static final byte PACKET_SNAPSHOT = 3;
    private static final byte PACKET_ERROR = 4;

    private static final long AUTO_REFRESH_INTERVAL = 1000L;

    private final List<ServerSession> serverSessions = new ArrayList<>();
    private final List<ClientSession> clientSessions = new ArrayList<>();

    @Override
    public IDataConnection startServerDebugging(Object object, IDataConnection outgoingData,
            IObjectIdentification objectIdent) {
        ServerSession session = new ServerSession(object, outgoingData, objectIdent);
        synchronized (serverSessions) {
            serverSessions.add(session);
        }
        session.sendSnapshot("initial");
        return session;
    }

    @Override
    public Future<IDataConnection> startClientDebugging(String name, IDataConnection outgoingData) {
        ClientSession session = new ClientSession(name, outgoingData);
        synchronized (clientSessions) {
            clientSessions.add(session);
        }
        return new CompletedFuture<>(session);
    }

    @Override
    public void exec() {
        synchronized (serverSessions) {
            Iterator<ServerSession> iterator = serverSessions.iterator();
            while (iterator.hasNext()) {
                ServerSession session = iterator.next();
                if (session.isClosed()) {
                    iterator.remove();
                }
            }
        }
        synchronized (clientSessions) {
            Iterator<ClientSession> iterator = clientSessions.iterator();
            while (iterator.hasNext()) {
                ClientSession session = iterator.next();
                if (session.isClosed()) {
                    iterator.remove();
                } else {
                    session.tick();
                }
            }
        }
    }

    private static final class ServerSession implements IDataConnection {

        private final Object object;
        private final IDataConnection outgoingData;
        private final IObjectIdentification objectIdent;
        private boolean closed;

        private ServerSession(Object object, IDataConnection outgoingData, IObjectIdentification objectIdent) {
            this.object = object;
            this.outgoingData = outgoingData;
            this.objectIdent = objectIdent;
        }

        @Override
        public void passData(byte[] packet) {
            if (closed) {
                return;
            }
            try {
                byte packetType = PacketCodec.readPacketType(packet);
                if (packetType == PACKET_REQUEST_SNAPSHOT) {
                    sendSnapshot("manual");
                } else if (packetType == PACKET_CLOSE) {
                    closeCon();
                }
            } catch (IOException e) {
                sendError("Could not read debug gui packet: " + e.getMessage());
            }
        }

        @Override
        public void closeCon() {
            closed = true;
            outgoingData.closeCon();
        }

        private boolean isClosed() {
            return closed;
        }

        private void sendSnapshot(String reason) {
            if (closed) {
                return;
            }
            try {
                SnapshotBuilder builder = new SnapshotBuilder(objectIdent);
                String title = object == null ? "null" : object.getClass().getName();
                String snapshot = builder.build(object, reason);
                outgoingData.passData(PacketCodec.snapshot(title, snapshot, System.currentTimeMillis()));
            } catch (Throwable t) {
                sendError("Could not build debug snapshot: " + t.getClass().getName() + ": " + t.getMessage());
            }
        }

        private void sendError(String message) {
            try {
                outgoingData.passData(PacketCodec.error(message));
            } catch (IOException ignored) {}
        }
    }

    private static final class ClientSession implements IDataConnection {

        private final String name;
        private final IDataConnection outgoingData;
        private volatile boolean closed;
        private volatile ClientDebugWindow window;
        private String pendingTitle;
        private String pendingText;
        private long pendingTimestamp;
        private long nextAutoRefresh;

        private ClientSession(String name, IDataConnection outgoingData) {
            this.name = name == null || name.isEmpty() ? "Debug Target" : name;
            this.outgoingData = outgoingData;
            this.nextAutoRefresh = System.currentTimeMillis() + AUTO_REFRESH_INTERVAL;
            openWindow();
        }

        @Override
        public void passData(byte[] packet) {
            if (closed) {
                return;
            }
            try {
                Packet packetData = PacketCodec.read(packet);
                if (packetData.type == PACKET_SNAPSHOT) {
                    showSnapshot(packetData.title, packetData.text, packetData.timestamp);
                } else if (packetData.type == PACKET_ERROR) {
                    showSnapshot(name, packetData.text, System.currentTimeMillis());
                } else if (packetData.type == PACKET_CLOSE) {
                    closeCon();
                }
            } catch (IOException e) {
                showSnapshot(name, "Could not read debug gui packet: " + e.getMessage(), System.currentTimeMillis());
            }
        }

        @Override
        public void closeCon() {
            if (closed) {
                return;
            }
            closed = true;
            ClientDebugWindow currentWindow = window;
            if (currentWindow != null) {
                currentWindow.dispose();
            }
            outgoingData.closeCon();
        }

        private boolean isClosed() {
            return closed;
        }

        private void tick() {
            if (closed) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now >= nextAutoRefresh) {
                nextAutoRefresh = now + AUTO_REFRESH_INTERVAL;
                ClientDebugWindow currentWindow = window;
                if (currentWindow != null && currentWindow.isAutoRefresh()) {
                    requestSnapshot();
                }
            }
        }

        private void requestSnapshot() {
            try {
                outgoingData.passData(PacketCodec.requestSnapshot());
            } catch (IOException e) {
                showSnapshot(name, "Could not request debug snapshot: " + e.getMessage(), System.currentTimeMillis());
            }
        }

        private void closeFromWindow() {
            if (closed) {
                return;
            }
            try {
                outgoingData.passData(PacketCodec.close());
            } catch (IOException ignored) {}
            closeCon();
        }

        private void openWindow() {
            if (GraphicsEnvironment.isHeadless()) {
                System.out.println("DebugGuiEntry: cannot open Swing debug window in headless mode for " + name);
                return;
            }
            SwingUtilities.invokeLater(() -> {
                if (closed) {
                    return;
                }
                window = new ClientDebugWindow(name, this);
                String title;
                String text;
                long timestamp;
                synchronized (this) {
                    title = pendingTitle;
                    text = pendingText;
                    timestamp = pendingTimestamp;
                    pendingTitle = null;
                    pendingText = null;
                }
                if (text != null) {
                    window.update(title, text, timestamp);
                }
                requestSnapshot();
            });
        }

        private void showSnapshot(String title, String text, long timestamp) {
            ClientDebugWindow currentWindow = window;
            if (currentWindow == null) {
                synchronized (this) {
                    pendingTitle = title;
                    pendingText = text;
                    pendingTimestamp = timestamp;
                }
                return;
            }
            SwingUtilities.invokeLater(() -> currentWindow.update(title, text, timestamp));
        }
    }

    private static final class ClientDebugWindow {

        private final JFrame frame;
        private final JTextArea textArea;
        private final JLabel status;
        private final JCheckBox autoRefresh;

        private ClientDebugWindow(String name, ClientSession session) {
            frame = new JFrame("LP Debug - " + name);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {

                @Override
                public void windowClosing(WindowEvent e) {
                    session.closeFromWindow();
                }
            });

            textArea = new JTextArea("");
            textArea.setEditable(false);
            textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

            JButton refresh = new JButton("Refresh");
            refresh.addActionListener(e -> session.requestSnapshot());

            autoRefresh = new JCheckBox("Auto refresh", true);

            JButton close = new JButton("Close");
            close.addActionListener(e -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)));

            status = new JLabel("Waiting for server snapshot");

            JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
            toolbar.add(refresh);
            toolbar.add(autoRefresh);
            toolbar.add(close);
            toolbar.add(status);

            JPanel root = new JPanel(new BorderLayout());
            root.add(toolbar, BorderLayout.NORTH);
            root.add(new JScrollPane(textArea), BorderLayout.CENTER);

            addCloseBinding(frame, session);
            frame.getContentPane().add(root);
            frame.setPreferredSize(new Dimension(1100, 750));
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        }

        private boolean isAutoRefresh() {
            return autoRefresh.isSelected();
        }

        private void update(String title, String text, long timestamp) {
            if (title != null && !title.isEmpty()) {
                frame.setTitle("LP Debug - " + title);
            }
            textArea.setText(text == null ? "" : text);
            textArea.setCaretPosition(0);
            status.setText("Updated " + new SimpleDateFormat("HH:mm:ss").format(new Date(timestamp)));
        }

        private void dispose() {
            if (SwingUtilities.isEventDispatchThread()) {
                frame.dispose();
            } else {
                SwingUtilities.invokeLater(frame::dispose);
            }
        }

        private static void addCloseBinding(JFrame frame, ClientSession session) {
            frame.getRootPane().getActionMap().put("close-window", new AbstractAction() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    Window window = SwingUtilities.getWindowAncestor(frame.getRootPane());
                    if (window != null) {
                        window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
                    } else {
                        session.closeFromWindow();
                    }
                }
            });
            frame.getRootPane().getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .put(KeyStroke.getKeyStroke("control W"), "close-window");
            frame.getRootPane().getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .put(KeyStroke.getKeyStroke("meta W"), "close-window");
        }
    }

    private static final class SnapshotBuilder {

        private static final int MAX_DEPTH = 4;
        private static final int MAX_COLLECTION_ENTRIES = 25;
        private static final int MAX_STRING_LENGTH = 500;
        private static final int MAX_OUTPUT_LENGTH = 250000;

        private final IObjectIdentification objectIdent;
        private final StringBuilder builder = new StringBuilder();
        private final IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();

        private SnapshotBuilder(IObjectIdentification objectIdent) {
            this.objectIdent = objectIdent;
        }

        private String build(Object object, String reason) {
            appendLine("Debug target snapshot");
            appendLine("Reason: " + reason);
            appendLine("Captured: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()));
            if (object == null) {
                appendLine("Object: null");
            } else {
                appendLine("Class: " + object.getClass().getName());
                appendLine("Identity: 0x" + Integer.toHexString(System.identityHashCode(object)));
                appendLine("");
                appendObject("object", object, 0);
            }
            return builder.toString();
        }

        private void appendObject(String label, Object value, int depth) {
            if (isOutputFull()) {
                return;
            }
            if (value == null) {
                appendLine(depth, label + " = null");
                return;
            }

            String handled = handleObject(value);
            if (handled != null) {
                appendLine(depth, label + " = " + value.getClass().getName() + " " + handled);
                return;
            }

            Class<?> valueClass = value.getClass();
            if (isSimpleValue(valueClass) || toStringObject(value)) {
                appendLine(depth, label + " = " + valueClass.getName() + " " + safeToString(value));
                return;
            }

            if (visited.containsKey(value)) {
                appendLine(
                        depth,
                        label + " = "
                                + className(valueClass)
                                + "@0x"
                                + Integer.toHexString(System.identityHashCode(value))
                                + " (already shown)");
                return;
            }

            if (valueClass.isArray()) {
                appendArray(label, value, depth);
                return;
            }
            if (value instanceof Collection) {
                appendCollection(label, (Collection<?>) value, depth);
                return;
            }
            if (value instanceof Map) {
                appendMap(label, (Map<?, ?>) value, depth);
                return;
            }

            appendFields(label, value, valueClass, depth);
        }

        private void appendArray(String label, Object array, int depth) {
            int length = Array.getLength(array);
            Class<?> componentType = array.getClass().getComponentType();
            appendLine(depth, label + " = " + componentType.getName() + "[" + length + "]");
            if (depth >= MAX_DEPTH) {
                appendLine(depth + 1, "(max depth reached)");
                return;
            }
            visited.put(array, Boolean.TRUE);
            int limit = Math.min(length, MAX_COLLECTION_ENTRIES);
            for (int i = 0; i < limit; i++) {
                appendObject("[" + i + "]", Array.get(array, i), depth + 1);
            }
            if (length > limit) {
                appendLine(depth + 1, "... " + (length - limit) + " more entries");
            }
        }

        private void appendCollection(String label, Collection<?> collection, int depth) {
            appendLine(depth, label + " = " + className(collection.getClass()) + " size=" + collection.size());
            if (depth >= MAX_DEPTH) {
                appendLine(depth + 1, "(max depth reached)");
                return;
            }
            visited.put(collection, Boolean.TRUE);
            int index = 0;
            for (Object value : collection) {
                if (index >= MAX_COLLECTION_ENTRIES) {
                    appendLine(depth + 1, "... " + (collection.size() - index) + " more entries");
                    break;
                }
                appendObject("[" + index + "]", value, depth + 1);
                index++;
            }
        }

        private void appendMap(String label, Map<?, ?> map, int depth) {
            appendLine(depth, label + " = " + className(map.getClass()) + " size=" + map.size());
            if (depth >= MAX_DEPTH) {
                appendLine(depth + 1, "(max depth reached)");
                return;
            }
            visited.put(map, Boolean.TRUE);
            int index = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (index >= MAX_COLLECTION_ENTRIES) {
                    appendLine(depth + 1, "... " + (map.size() - index) + " more entries");
                    break;
                }
                appendLine(depth + 1, "[" + index + "]");
                appendObject("key", entry.getKey(), depth + 2);
                appendObject("value", entry.getValue(), depth + 2);
                index++;
            }
        }

        private void appendFields(String label, Object value, Class<?> valueClass, int depth) {
            appendLine(
                    depth,
                    label + " = "
                            + className(valueClass)
                            + "@0x"
                            + Integer.toHexString(System.identityHashCode(value)));
            if (depth >= MAX_DEPTH) {
                appendLine(depth + 1, "(max depth reached)");
                return;
            }
            visited.put(value, Boolean.TRUE);

            Class<?> current = valueClass;
            while (current != null && current != Object.class) {
                Field[] fields = current.getDeclaredFields();
                Arrays.sort(fields, Comparator.comparing(Field::getName));
                appendLine(depth + 1, current.getName());
                for (Field field : fields) {
                    if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    appendField(value, field, depth + 2);
                }
                current = current.getSuperclass();
            }
        }

        private void appendField(Object owner, Field field, int depth) {
            String fieldLabel = Modifier.toString(field.getModifiers());
            if (!fieldLabel.isEmpty()) {
                fieldLabel += " ";
            }
            fieldLabel += field.getType().getSimpleName() + " " + field.getName();
            try {
                field.setAccessible(true);
                appendObject(fieldLabel, field.get(owner), depth);
            } catch (Throwable t) {
                appendLine(depth, fieldLabel + " = <inaccessible: " + t.getClass().getSimpleName() + ">");
            }
        }

        private String handleObject(Object value) {
            if (objectIdent == null || value == null) {
                return null;
            }
            try {
                return objectIdent.handleObject(value);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private boolean toStringObject(Object value) {
            if (objectIdent == null || value == null) {
                return false;
            }
            try {
                return objectIdent.toStringObject(value);
            } catch (Throwable ignored) {
                return false;
            }
        }

        private static boolean isSimpleValue(Class<?> valueClass) {
            return valueClass.isPrimitive() || valueClass == String.class
                    || Number.class.isAssignableFrom(valueClass)
                    || valueClass == Boolean.class
                    || valueClass == Character.class
                    || Enum.class.isAssignableFrom(valueClass);
        }

        private static String safeToString(Object value) {
            try {
                String text = String.valueOf(value);
                if (text.length() > MAX_STRING_LENGTH) {
                    return text.substring(0, MAX_STRING_LENGTH) + "...";
                }
                return text;
            } catch (Throwable t) {
                return "<toString failed: " + t.getClass().getSimpleName() + ">";
            }
        }

        private static String className(Class<?> clazz) {
            return clazz == null ? "null" : clazz.getName();
        }

        private void appendLine(String line) {
            appendLine(0, line);
        }

        private void appendLine(int depth, String line) {
            if (isOutputFull()) {
                return;
            }
            for (int i = 0; i < depth; i++) {
                builder.append("  ");
            }
            builder.append(line).append('\n');
            if (builder.length() > MAX_OUTPUT_LENGTH) {
                builder.setLength(MAX_OUTPUT_LENGTH);
                builder.append("\n... snapshot truncated ...\n");
            }
        }

        private boolean isOutputFull() {
            return builder.length() >= MAX_OUTPUT_LENGTH;
        }
    }

    private static final class PacketCodec {

        private PacketCodec() {}

        private static byte[] requestSnapshot() throws IOException {
            return singleBytePacket(PACKET_REQUEST_SNAPSHOT);
        }

        private static byte[] close() throws IOException {
            return singleBytePacket(PACKET_CLOSE);
        }

        private static byte[] snapshot(String title, String text, long timestamp) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(bytes);
            data.writeByte(PACKET_SNAPSHOT);
            data.writeLong(timestamp);
            writeString(data, title);
            writeString(data, text);
            data.close();
            return bytes.toByteArray();
        }

        private static byte[] error(String message) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(bytes);
            data.writeByte(PACKET_ERROR);
            writeString(data, message);
            data.close();
            return bytes.toByteArray();
        }

        private static byte[] singleBytePacket(byte packetType) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(bytes);
            data.writeByte(packetType);
            data.close();
            return bytes.toByteArray();
        }

        private static byte readPacketType(byte[] packet) throws IOException {
            if (packet == null || packet.length == 0) {
                throw new IOException("empty packet");
            }
            DataInputStream data = new DataInputStream(new ByteArrayInputStream(packet));
            return data.readByte();
        }

        private static Packet read(byte[] packet) throws IOException {
            if (packet == null || packet.length == 0) {
                throw new IOException("empty packet");
            }
            DataInputStream data = new DataInputStream(new ByteArrayInputStream(packet));
            byte packetType = data.readByte();
            if (packetType == PACKET_SNAPSHOT) {
                long timestamp = data.readLong();
                String title = readString(data);
                String text = readString(data);
                return new Packet(packetType, title, text, timestamp);
            }
            if (packetType == PACKET_ERROR) {
                return new Packet(packetType, null, readString(data), System.currentTimeMillis());
            }
            return new Packet(packetType, null, null, System.currentTimeMillis());
        }

        private static void writeString(DataOutputStream data, String text) throws IOException {
            byte[] bytes = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
            data.writeInt(bytes.length);
            data.write(bytes);
        }

        private static String readString(DataInputStream data) throws IOException {
            int length = data.readInt();
            if (length < 0 || length > 1024 * 1024) {
                throw new IOException("invalid string length " + length);
            }
            byte[] bytes = new byte[length];
            data.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static final class Packet {

        private final byte type;
        private final String title;
        private final String text;
        private final long timestamp;

        private Packet(byte type, String title, String text, long timestamp) {
            this.type = type;
            this.title = title;
            this.text = text;
            this.timestamp = timestamp;
        }
    }

    private static final class CompletedFuture<T> implements Future<T> {

        private final T value;

        private CompletedFuture(T value) {
            this.value = value;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public T get(long timeout, TimeUnit unit) {
            return value;
        }
    }
}
