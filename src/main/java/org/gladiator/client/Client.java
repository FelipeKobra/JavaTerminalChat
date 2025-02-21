package org.gladiator.client;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import javax.net.SocketFactory;
import org.gladiator.client.config.ClientConfig;
import org.gladiator.client.config.ClientConfigProvider;
import org.gladiator.exception.EndApplicationException;
import org.gladiator.exception.InvalidMessageException;
import org.gladiator.util.chat.ChatUtils;
import org.gladiator.util.connection.Connection;
import org.gladiator.util.connection.message.ConnectionMessageFactory;
import org.gladiator.util.connection.message.model.Message;
import org.gladiator.util.connection.message.model.SimpleMessage;
import org.gladiator.util.thread.NamedVirtualThreadExecutorFactory;
import org.jline.reader.EndOfFileException;
import org.jline.reader.UserInterruptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Client class represents a client that connects to a server, exchanges messages, and handles
 * user interactions.
 */
public final class Client implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(Client.class);

  private final ClientConfig config;
  private final ExecutorService executor;
  private final ChatUtils chatUtils;

  private Client(final ClientConfig config, final ExecutorService executor,
      final ChatUtils chatUtils) {
    this.config = config;
    this.executor = executor;
    this.chatUtils = chatUtils;
  }

  /**
   * Creates a new Client instance by initializing the necessary components and establishing a
   * connection to the server.
   *
   * @return a new Client instance
   * @throws EndApplicationException if an error occurs during client creation
   */
  public static Client createClient(final ChatUtils chatUtils)
      throws EndApplicationException {

    final Client client;
    try {
      final ClientConfig clientConfig = new ClientConfigProvider(
          chatUtils).createClientConfig();

      final ExecutorService executor = NamedVirtualThreadExecutorFactory.create("client");

      client = new Client(clientConfig, executor, chatUtils);
    } catch (final UserInterruptException | EndOfFileException e) {
      throw new EndApplicationException(e);
    }

    Objects.requireNonNull(client);
    return client;
  }

  /**
   * Creates a socket connection to the specified server address and port.
   *
   * @param chatUtils     the ChatUtils instance for user interaction
   * @param serverAddress the address of the server to connect to
   * @param port          the port number to connect to
   * @return a Socket connected to the server
   * @throws EndApplicationException if an error occurs during socket creation
   */
  private static Socket createSocket(final ChatUtils chatUtils,
      final String serverAddress, final int port)
      throws EndApplicationException {

    Socket clientSocket = null;

    try {
      clientSocket = SocketFactory.getDefault()
          .createSocket(serverAddress, port);
    } catch (final UnknownHostException e) {
      handleException(chatUtils, "Server Address not found",
          "Server " + serverAddress + " not Found", e);
    } catch (final ConnectException e) {
      handleException(chatUtils, "Connection time out with server",
          "Timed out when trying to connect to server: " + serverAddress, e);
    } catch (final SocketException e) {
      handleException(chatUtils, "Invalid Server IP Address",
          "Server address not valid: " + serverAddress, e);
    } catch (final IOException e) {
      handleException(chatUtils, "Error connecting to server",
          "Error during connection with server: " + serverAddress, e);
    }

    Objects.requireNonNull(clientSocket);
    return clientSocket;
  }

  /**
   * Handles exceptions by logging the message, displaying a user message, and throwing an
   * EndApplicationException.
   *
   * @param chatUtils   the ChatUtils instance for user interaction
   * @param logMessage  the message to log
   * @param userMessage the message to display to the user
   * @param exception   the exception to handle
   * @throws EndApplicationException the wrapped exception
   */
  private static void handleException(final ChatUtils chatUtils, final String logMessage,
      final String userMessage,
      final Exception exception)
      throws EndApplicationException {
    LOGGER.debug(logMessage, exception);
    chatUtils.displayOnScreen(userMessage);
    throw new EndApplicationException(exception);
  }


  /**
   * Runs the client by establishing a connection, exchanging names with the server, and handling
   * message sending and receiving.
   *
   * @throws EndApplicationException if an error occurs during client execution
   */
  public void run() throws EndApplicationException {
    try {
      final Socket socket = createSocket(chatUtils, config.serverAddress(),
          config.port());

      final BufferedReader reader = new BufferedReader(
          new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      final PrintWriter writer = new PrintWriter(socket.getOutputStream(), true,
          StandardCharsets.UTF_8);

      final String serverName = exchangeNames(writer, reader);

      final Connection serverConnection = new Connection(serverName, reader, writer, socket);

      chatUtils.displayBanner("Connection Established with " + serverName);

      chatUtils.displayOnScreen("Type `quit` to exit");

      final CompletableFuture<Void> receiveMessagesFuture = CompletableFuture.runAsync(
          () -> receiveMessages(serverConnection), executor);

      final CompletableFuture<Void> sendMessagesFuture = CompletableFuture.runAsync(
          () -> sendMessages(serverConnection), executor);

      CompletableFuture.allOf(receiveMessagesFuture, sendMessagesFuture).join();

      serverConnection.close();
      reconnectPrompt();
    } catch (final IOException e) {
      throw new EndApplicationException("Error creating Socket IO" + e);
    }

  }


  /**
   * Sends messages to the server. Reads user input and sends it as messages to the server.
   * Terminates when the user types "quit".
   *
   * @param serverConnection The connection to the server.
   */
  private void sendMessages(final Connection serverConnection) {

    try {
      String line = chatUtils.getUserInput();

      while (null != line) {
        if ("quit".equals(line)) {
          break;
        }

        if (!line.isBlank()) {
          final Message msg = new SimpleMessage(config.name(), line);
          serverConnection.writeOutput(msg);
        }
        line = chatUtils.getUserInput();
      }
    } catch (final EndOfFileException | UserInterruptException e) {
      LOGGER.debug(ChatUtils.USER_INTERRUPT_MESSAGE, e);
    } finally {
      executor.shutdownNow();
    }
  }

  /**
   * Receives messages from the server and displays them using ChatUtils.
   *
   * @param serverConnection The connection to the server.
   */
  private void receiveMessages(final Connection serverConnection) {
    try {
      serverConnection.readStream()
          .map(transportMessage -> {
            try {
              return ConnectionMessageFactory.createFromString(transportMessage);
            } catch (final InvalidMessageException e) {
              LOGGER.debug(InvalidMessageException.DEFAULT_PROMPT, e);
              return null;
            }
          })
          .filter(Objects::nonNull)
          .forEach(chatUtils::showNewMessage);
    } catch (final UncheckedIOException e) {
      LOGGER.debug("The connection with the server has ended");
    } finally {
      executor.shutdownNow();
    }
  }

  /**
   * Exchanges names with the server by sending the client's name and receiving the server's name.
   *
   * @param writer the PrintWriter to send the client's name to the server
   * @param reader the BufferedReader to receive the server's name
   * @return the name of the server
   */
  private String exchangeNames(final PrintWriter writer, final BufferedReader reader) {

    final CompletableFuture<Void> sendNameFuture = CompletableFuture.runAsync(() -> {
      writer.println(config.name());
      final String logMessage = "Sent name (" + config.name() + ") to server.";
      LOGGER.debug(logMessage);
    }, executor);

    final CompletableFuture<String> receiveNameFuture = CompletableFuture.supplyAsync(() -> {
      String serverName = "";
      try {
        serverName = reader.readLine();
        final String logMessage = "Received name from server: " + serverName + ".";
        LOGGER.debug(logMessage);
      } catch (final IOException e) {
        LOGGER.error("Error receiving server name: {}", e, e);
      }

      return serverName;
    }, executor);

    CompletableFuture.allOf(sendNameFuture, receiveNameFuture).join();

    return receiveNameFuture.join();
  }

  /**
   * Prompts the user to reconnect to another server.
   *
   * @throws EndApplicationException if the user chooses not to reconnect or an error occurs
   */
  private void reconnectPrompt() throws EndApplicationException {
    try {
      final String choice = chatUtils.getUserInput("Connect to another server? y/N: ");
      if (!"Y".equalsIgnoreCase(choice)) {
        throw new EndApplicationException("User chose to not reconnect");
      }
    } catch (final UserInterruptException | EndOfFileException e) {
      throw new EndApplicationException(e);
    }
  }


  @Override
  public void close() {
    LOGGER.debug("Closing connection");
    executor.shutdownNow();
  }
}
