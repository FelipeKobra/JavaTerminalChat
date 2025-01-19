package app.server.config;

import static environment.Port.PORT_DEFAULT;
import static environment.Port.PORT_MAX;
import static environment.Port.PORT_MIN;

import java.util.Objects;
import org.apache.commons.lang3.Validate;

/**
 * Represents the configuration for a server, including its name and port.
 */
public record ServerConfig(String name, int port) {

  private static final String DEFAULT_NAME = "Server";

  /**
   * Constructs a new ServerConfig with the specified name and port.
   *
   * @param name the name of the server
   * @param port the port number of the server
   */
  public ServerConfig {
    validateArgs(name, port);
  }

  /**
   * Constructs a new ServerConfig with default name and port.
   */
  public ServerConfig() {
    this(getDefaultName(), PORT_DEFAULT);
  }


  public static String getDefaultName() {
    return DEFAULT_NAME;
  }


  private void validateArgs(final String name, final int port) {
    Objects.requireNonNull(name);
    Validate.notBlank(name);
    Validate.inclusiveBetween(PORT_MIN, PORT_MAX, port);
  }


}
