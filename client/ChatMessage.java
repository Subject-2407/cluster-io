package client;
/**
 * ChatMessage.java
 *
 * Model class representing a chat message in Cluster.io.
 */
public class ChatMessage {
    private String username;
    private String message;
    private String color;
    private String shortId;
    private String userId;

    public ChatMessage() {
    }

    public ChatMessage(String username, String message, String color) {
        this.username = username;
        this.message = message;
        this.color = color;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getShortId() {
        return shortId;
    }

    public void setShortId(String shortId) {
        this.shortId = shortId;
    }
}
