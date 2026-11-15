package client;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

/**
 * Client.java
 *
 * Frontend Swing GUI Client for Cluster.io.
 * Designed to be clean, simple, and developable in under 30 minutes for student practice.
 * Features colored chat messages, top-left username input, and decoupled WebSocket networking.
 */
public class Client extends JFrame {

    // =========================================================================
    // SECTION 1: UI COMPONENTS & CONFIGURATION
    // =========================================================================

    private JTextField usernameField;
    private JComboBox<String> colorBox;
    private JLabel statusLabel;

    // [OPTIONAL - SKIP FOR <30 MIN BUILD]: Reconnect button
    // private JButton reconnectButton;

    private JTextPane chatPane;
    private JTextField messageField;
    private JButton sendButton;

    private WebSocketService chatService;

    // Available colors matching server.js defaults
    private static final String[] COLOR_NAMES = {
            "Teal", "Crimson", "Navy", "Terracotta", "Purple", "Ocean Blue", "Emerald", "Amber"
    };
    private static final String[] COLOR_HEX = {
            "#2A9D8F", "#E63946", "#1D3557", "#E76F51", "#6A4C93", "#1982C4", "#38B000", "#D97706"
    };

    // =========================================================================
    // SECTION 2: CONSTRUCTOR & LAYOUT
    // =========================================================================

    public Client() {
        super("Cluster.io");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Main container with 10px padding
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 1. Header Panel (Top: Username, Color, Status)
        JPanel headerPanel = new JPanel(new BorderLayout());

        JPanel leftHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftHeader.add(new JLabel("Username:"));
        usernameField = new JTextField(12);
        leftHeader.add(usernameField);

        leftHeader.add(new JLabel("Color:"));
        colorBox = new JComboBox<>(COLOR_NAMES);
        leftHeader.add(colorBox);

        JPanel rightHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        statusLabel = new JLabel("Server: Connecting...");
        statusLabel.setForeground(Color.GRAY);
        rightHeader.add(statusLabel);

        // [OPTIONAL - SKIP FOR <30 MIN BUILD]: Reconnect button
        // reconnectButton = new JButton("Reconnect");
        // rightHeader.add(reconnectButton);

        headerPanel.add(leftHeader, BorderLayout.WEST);
        headerPanel.add(rightHeader, BorderLayout.EAST);

        // 2. Center Panel (Chat Area with Colored Text support)
        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        chatPane.setMargin(new Insets(8, 8, 8, 8));
        JScrollPane scrollPane = new JScrollPane(chatPane);

        // 3. Bottom Panel (Input & Send Button)
        JPanel inputPanel = new JPanel(new BorderLayout(8, 0));
        messageField = new JTextField();
        messageField.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        sendButton = new JButton("Send");
        sendButton.setEnabled(false); // Disabled until username is entered!

        // [OPTIONAL - SKIP FOR <30 MIN BUILD]: Restrict tab index strictly to chat text field
        /*
        colorBox.setFocusable(false);
        chatPane.setFocusable(false);
        sendButton.setFocusable(false);

        setFocusTraversalPolicy(new FocusTraversalPolicy() {
            @Override public Component getComponentAfter(Container c, Component comp) { return messageField; }
            @Override public Component getComponentBefore(Container c, Component comp) { return messageField; }
            @Override public Component getFirstComponent(Container c) { return messageField; }
            @Override public Component getLastComponent(Container c) { return messageField; }
            @Override public Component getDefaultComponent(Container c) { return messageField; }
        });
        */

        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        // Assemble all panels
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(inputPanel, BorderLayout.SOUTH);
        setContentPane(mainPanel);

        // Setup Event Listeners and Networking
        setupEvents();
        initNetwork();
    }

    // =========================================================================
    // SECTION 3: EVENT HANDLING
    // =========================================================================

    private void setupEvents() {
        // Enable 'Send' button only when username is not empty
        usernameField.getDocument().addDocumentListener(new DocumentListener() {
            private void check() {
                boolean hasUsername = !usernameField.getText().trim().isEmpty();
                sendButton.setEnabled(hasUsername);
            }
            @Override public void insertUpdate(DocumentEvent e) { check(); }
            @Override public void removeUpdate(DocumentEvent e) { check(); }
            @Override public void changedUpdate(DocumentEvent e) { check(); }
        });

        // Send message on Send button click or pressing Enter in message field
        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());

        // [OPTIONAL - SKIP FOR <30 MIN BUILD]: Reconnect button listener
        /*
        reconnectButton.addActionListener(e -> {
            if (!chatService.isConnected()) {
                statusLabel.setText("Server: Reconnecting...");
                statusLabel.setForeground(Color.GRAY);
                chatService.connect();
            } else {
                JOptionPane.showMessageDialog(this, "Already connected to server!", "Information", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        */

        // [OPTIONAL - SKIP FOR <30 MIN BUILD]: Clean disconnect on window close
        // (JVM automatically closes sockets upon process termination)
        /*
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (chatService != null) chatService.disconnect();
            }
        });
        */
    }

    // =========================================================================
    // SECTION 4: INPUT & OUTPUT PROCESSING
    // =========================================================================

    /**
     * Validates input and sends message through WebSocketService.
     */
    private void sendMessage() {
        String username = usernameField.getText().trim();
        String text = messageField.getText().trim();

        // Simple validation with dialog messages
        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter your username on the top-left first!", "Warning", JOptionPane.WARNING_MESSAGE);
            usernameField.requestFocusInWindow();
            return;
        }

        if (text.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Message cannot be empty!", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!chatService.isConnected()) {
            JOptionPane.showMessageDialog(this, "Not connected to server! Please ensure server.js is running.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Send to server with selected hex color
        String color = COLOR_HEX[colorBox.getSelectedIndex()];
        chatService.sendMessage(username, text, color);

        // Clear input field and restore focus
        messageField.setText("");
        messageField.requestFocusInWindow();
    }

    /**
     * Appends a chat message to JTextPane.
     * Username and user ID are rendered in bold, while the message text is regular.
     * Both parts use the user's specified color.
     */
    private void appendMessage(ChatMessage msg) {
        Color color = Color.BLACK;
        try {
            if (msg.getColor() != null && !msg.getColor().isEmpty()) {
                color = Color.decode(msg.getColor());
            }
        } catch (Exception ignored) {}

        StyledDocument doc = chatPane.getStyledDocument();

        // 1. Style for Username + ID (Bold)
        SimpleAttributeSet boldStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(boldStyle, color);
        StyleConstants.setFontFamily(boldStyle, "Segoe UI");
        StyleConstants.setFontSize(boldStyle, 14);
        StyleConstants.setBold(boldStyle, true);

        // 2. Style for Message text (Regular)
        SimpleAttributeSet regularStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(regularStyle, color);
        StyleConstants.setFontFamily(regularStyle, "Segoe UI");
        StyleConstants.setFontSize(regularStyle, 14);
        StyleConstants.setBold(regularStyle, false);

        try {
            // Get user identifier (shortId or userId)
            String idTag = "";
            if (msg.getShortId() != null && !msg.getShortId().isEmpty()) {
                idTag = " #" + msg.getShortId();
            } else if (msg.getUserId() != null && !msg.getUserId().isEmpty()) {
                idTag = " #" + msg.getUserId();
            }

            String header = msg.getUsername() + idTag + ": ";
            doc.insertString(doc.getLength(), header, boldStyle);
            doc.insertString(doc.getLength(), msg.getMessage() + "\n", regularStyle);
            chatPane.setCaretPosition(doc.getLength()); // Auto scroll
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    // =========================================================================
    // SECTION 5: WEBSOCKET SERVICE INTEGRATION
    // =========================================================================

    private void initNetwork() {
        chatService = new WebSocketService("http://localhost:3000");

        chatService.setListener(new WebSocketService.ChatEventListener() {
            @Override
            public void onConnected() {
                statusLabel.setText("Server: Online");
                statusLabel.setForeground(new Color(34, 139, 34)); // Green
            }

            @Override
            public void onDisconnected() {
                statusLabel.setText("Server: Offline");
                statusLabel.setForeground(Color.RED);
            }

            @Override
            public void onMessageReceived(ChatMessage msg) {
                appendMessage(msg);
            }

            @Override
            public void onHistoryReceived(List<ChatMessage> history) {
                for (ChatMessage msg : history) {
                    appendMessage(msg);
                }
            }

            @Override
            public void onError(String error) {
                statusLabel.setText("Server: Offline");
                statusLabel.setForeground(Color.RED);

                // [OPTIONAL - SKIP FOR <30 MIN BUILD]: Popup error dialog
                // JOptionPane.showMessageDialog(Client.this, error, "Server Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        chatService.connect();
    }

    // =========================================================================
    // SECTION 6: ENTRY POINT (main)
    // =========================================================================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Client client = new Client();
            client.setVisible(true);
        });
    }
}
