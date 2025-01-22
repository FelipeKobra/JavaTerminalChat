package org.gladiator.app.util.connection;

import java.util.Objects;
import org.apache.commons.lang3.Validate;

/**
 * Represents a message exchanged between connections. This class is immutable and uses the record
 * feature of Java.
 */
public record ConnectionMessage(String senderName, String content) {

  /**
   * Constructs a new ConnectionMessage.
   *
   * @param senderName The name of the sender.
   * @param content    The content of the message.
   * @throws NullPointerException     if any of the parameters are null.
   * @throws IllegalArgumentException if the senderName is blank.
   */
  public ConnectionMessage {
    Validate.notBlank(senderName);
    Objects.requireNonNull(content);
  }

  /**
   * Converts the message to a raw string format. This format is necessary during the transmission
   * of the message, so it is easier to split the sender name to its content
   *
   * @return The raw string representation of the message.
   */
  public String toRawString() {
    return senderName + "," + content;
  }

  /**
   * Returns a string representation of the message.
   *
   * @return The string representation of the message.
   */
  @Override
  public String toString() {
    return senderName + ": " + content;
  }
}