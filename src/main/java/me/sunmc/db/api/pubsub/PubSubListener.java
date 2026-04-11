package me.sunmc.db.api.pubsub;

/**
 * Functional interface for receiving pub/sub messages.
 * Used with Redis pub/sub for cross-server communication.
 *
 * <pre>{@code
 * redis.subscribe("chat_channel", (channel, message) -> {
 *     System.out.println("[" + channel + "] " + message);
 * });
 * }</pre>
 */
@FunctionalInterface
public interface PubSubListener {

    /**
     * Called when a message is received on a subscribed channel.
     *
     * @param channel the channel name
     * @param message the message payload
     */
    void onMessage(String channel, String message);
}
