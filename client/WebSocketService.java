package client;
import javax.swing.SwingUtilities;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WebSocketService.java
 *
 * Backend WebSocket connection wrapper for Cluster.io (Socket.IO backend).
 * Pure Java (JDK 11+) without any external JAR dependencies.
 *
 */
public class WebSocketService {

    public interface ChatEventListener {
        void onConnected();
        void onDisconnected();
        void onMessageReceived(ChatMessage message);
        void onHistoryReceived(List<ChatMessage> history);
        void onError(String error);
    }

    private final String wsUrl;
    private WebSocket webSocket;
    private ChatEventListener listener;
    private boolean connected = false;
    private final StringBuilder incomingBuffer = new StringBuilder();

    public WebSocketService(String serverHost) {
        String cleanHost = serverHost.replaceAll("/+$", "").replaceFirst("(?i)^http", "ws");
        this.wsUrl = cleanHost + "/socket.io/?EIO=4&transport=websocket";
    }

    public void setListener(ChatEventListener listener) {
        this.listener = listener;
    }

    public boolean isConnected() {
        return connected && webSocket != null && !webSocket.isInputClosed() && !webSocket.isOutputClosed();
    }

    public void connect() {
        if (isConnected()) return;

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            client.newWebSocketBuilder()
                    .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {

                        @Override
                        public void onOpen(WebSocket ws) {
                            webSocket = ws;
                            incomingBuffer.setLength(0);
                            ws.request(1);
                        }

                        @Override
                        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                            incomingBuffer.append(data);
                            if (last) {
                                String packet = incomingBuffer.toString();
                                incomingBuffer.setLength(0);
                                handlePacket(packet);
                            }
                            ws.request(1);
                            return null;
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                            connected = false;
                            dispatch(() -> { if (listener != null) listener.onDisconnected(); });
                            return null;
                        }

                        @Override
                        public void onError(WebSocket ws, Throwable error) {
                            connected = false;
                            dispatch(() -> {
                                if (listener != null) {
                                    listener.onDisconnected();
                                    listener.onError("Connection error: " + error.getMessage());
                                }
                            });
                        }
                    }).exceptionally(ex -> {
                        connected = false;
                        dispatch(() -> {
                            if (listener != null) {
                                listener.onDisconnected();
                                listener.onError("Cannot connect to server: " + ex.getMessage());
                            }
                        });
                        return null;
                    });
        } catch (Exception e) {
            connected = false;
            dispatch(() -> {
                if (listener != null) {
                    listener.onDisconnected();
                    listener.onError("Init error: " + e.getMessage());
                }
            });
        }
    }

    public void disconnect() {
        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Bye").join();
            } catch (Exception ignored) {}
        }
        connected = false;
        dispatch(() -> { if (listener != null) listener.onDisconnected(); });
    }

    public void sendMessage(String username, String message, String color) {
        if (!isConnected()) {
            dispatch(() -> { if (listener != null) listener.onError("Not connected to server."); });
            return;
        }

        String json = "{\"username\":\"" + escapeJson(username) + "\","
                + "\"message\":\"" + escapeJson(message) + "\","
                + "\"color\":\"" + escapeJson(color) + "\"}";

        webSocket.sendText("42[\"chat\"," + json + "]", true);
    }

    private void handlePacket(String packet) {
        if (packet == null || packet.isEmpty()) return;

        // Engine.IO handshake -> respond with Socket.IO CONNECT (40)
        if (packet.startsWith("0") && webSocket != null) {
            webSocket.sendText("40", true);
            return;
        }

        // Engine.IO heartbeat ping -> reply with pong (3)
        if (packet.startsWith("2") && webSocket != null) {
            webSocket.sendText("3", true);
            return;
        }

        // Socket.IO connected
        if (packet.startsWith("40")) {
            connected = true;
            dispatch(() -> { if (listener != null) listener.onConnected(); });
            return;
        }

        // Socket.IO events (starts with 42)
        if (packet.startsWith("42")) {
            String eventData = packet.substring(2);

            if (eventData.startsWith("[\"history\",")) {
                // Parse message history
                int start = eventData.indexOf('[');
                int second = eventData.indexOf('[', start + 1);
                int last = eventData.lastIndexOf(']');
                int innerEnd = eventData.lastIndexOf(']', last - 1);

                if (second != -1 && innerEnd > second) {
                    List<ChatMessage> list = parseMessageArray(eventData.substring(second, innerEnd + 1));
                    dispatch(() -> { if (listener != null) listener.onHistoryReceived(list); });
                }
            } else if (eventData.startsWith("[\"message\",")) {
                // Parse single new message
                int objStart = eventData.indexOf('{');
                int objEnd = eventData.lastIndexOf('}');
                if (objStart != -1 && objEnd > objStart) {
                    ChatMessage msg = parseMessage(eventData.substring(objStart, objEnd + 1));
                    dispatch(() -> { if (listener != null) listener.onMessageReceived(msg); });
                }
            } else if (eventData.startsWith("[\"error_message\",")) {
                Map<String, String> map = parseJsonFields(eventData);
                String err = map.getOrDefault("error", "Server error");
                dispatch(() -> { if (listener != null) listener.onError(err); });
            }
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private Map<String, String> parseJsonFields(String json) {
        Map<String, String> map = new HashMap<>();
        Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(?:\"((?:\\\\\"|[^\"])*)\"|([0-9a-zA-Z_\\.\\-]+))");
        Matcher matcher = pattern.matcher(json);
        while (matcher.find()) {
            String key = matcher.group(1);
            String val = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            if (val != null) {
                val = val.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n");
            }
            map.put(key, val);
        }
        return map;
    }

    private ChatMessage parseMessage(String json) {
        Map<String, String> map = parseJsonFields(json);
        ChatMessage msg = new ChatMessage();
        msg.setUsername(map.get("username"));
        msg.setMessage(map.get("message"));
        msg.setColor(map.get("color"));
        msg.setShortId(map.get("shortId"));
        msg.setUserId(map.get("userId"));
        return msg;
    }

    private List<ChatMessage> parseMessageArray(String jsonArray) {
        List<ChatMessage> list = new ArrayList<>();
        int start = -1;
        int depth = 0;
        boolean inQuote = false;

        for (int i = 0; i < jsonArray.length(); i++) {
            char c = jsonArray.charAt(i);
            if (c == '\\' && inQuote) { i++; continue; }
            if (c == '"') inQuote = !inQuote;
            else if (!inQuote) {
                if (c == '{') {
                    if (depth == 0) start = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && start != -1) {
                        list.add(parseMessage(jsonArray.substring(start, i + 1)));
                        start = -1;
                    }
                }
            }
        }
        return list;
    }

    private void dispatch(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }
}
