package academy.mindswap.game;

import academy.mindswap.game.commands.Command;
import academy.mindswap.game.messages.Instructions;
import academy.mindswap.game.messages.Messages;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Server {

    private final ServerSocket serverSocket;

    ExecutorService service;

    int numOfPlayers;

    protected final List<ConnectedPlayer> playersList;

    public Server(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        playersList = new CopyOnWriteArrayList<>();
    }

    public void start(int numOfPlayers) throws IOException, InterruptedException {
        this.numOfPlayers = numOfPlayers;
        service = Executors.newFixedThreadPool(numOfPlayers);
        System.out.printf(Messages.GAME_STARTED);
        while (playersList.size() < numOfPlayers) {
            acceptConnection();
        }
    }

    public void acceptConnection() throws IOException {
        Socket playerSocket = serverSocket.accept(); // blocking method
        ConnectedPlayer connectedPlayer = new ConnectedPlayer(playerSocket);
        service.submit(connectedPlayer);
    }

    private synchronized void addPlayer(ConnectedPlayer player) throws IOException, InterruptedException {
        verifyPlayerName(player);
        if (playersList.size() < numOfPlayers) {
            player.send(Messages.WAITING_ALL_PLAYERS.formatted(numOfPlayers - playersList.size()));
            this.wait();
        } else this.notifyAll();
    }

    private void verifyPlayerName(ConnectedPlayer player) throws IOException, InterruptedException {
        player.send(Messages.ASK_NAME);
        BufferedReader reader = new BufferedReader(new InputStreamReader(player.playerSocket.getInputStream()));
        String playerName = reader.readLine();
        validateName(player, playerName);
        if (playersList.stream().
                anyMatch(p -> p.getName().
                        equals(playerName))) {
            player.send(Messages.INVALID_NAME);
            verifyPlayerName(player);
        } else {
            player.name = playerName;
            playersList.add(player);
        }
        player.send(Messages.WELCOME.formatted(player.getName()));
    }

    private void validateName(ConnectedPlayer connectedPlayer, String name) throws IOException, InterruptedException {
        String regex = "^\\S+$";
        final Pattern pattern = Pattern.compile(regex);
        final Matcher matcher = pattern.matcher(name);
        if (!matcher.find()) {
            connectedPlayer.send(Messages.INVALID_FIRST_NAME);
            verifyPlayerName(connectedPlayer);
        }
    }

    public void removePlayer(ConnectedPlayer connectedPlayer) {
        playersList.remove(connectedPlayer);
    }

    public void broadcast(String name, String message) {
        playersList.stream()
                .filter(player -> !player.getName().equals(name))
                .forEach(player -> player.send(name.concat(message)));
    }

    public class ConnectedPlayer implements Runnable {

        private String name;

        private final Socket playerSocket;

        private final BufferedWriter out;

        private String message;

        Game game;

        public ConnectedPlayer(Socket playerSocket) throws IOException {
            this.playerSocket = playerSocket;
            this.out = new BufferedWriter(new OutputStreamWriter(playerSocket.getOutputStream()));
            game = new Game(this);
        }

        @Override
        public void run() {
            try {
                addPlayer(this);
                send(Instructions.readInstruction());
                game.play();
                restart();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                close();
            }
        }

        public String askForGuess() throws IOException {
            while (!playerSocket.isClosed()) {
                try {
                    Scanner in = new Scanner(playerSocket.getInputStream());
                    message = in.nextLine().toUpperCase();
                    if (isCommand(message)) {
                        dealWithCommand(message);
                        askForGuess();
                    }
                } catch (IOException e) {
                    System.err.println(Messages.PLAYER_ERROR + e.getMessage());
                }
                if (!validInput()) {
                    askForGuess();
                }
                return message;
            }
            return null;
        }

        private boolean validInput() {
            String regex = "^[OYBPG]{4}$";
            final Pattern pattern = Pattern.compile(regex);
            final Matcher matcher = pattern.matcher(message);
            if (!matcher.find()) {
                send(Messages.INVALID_TRY);
                return false;
            }
            return true;
        }

        private boolean isCommand(String message) {
            return message.startsWith("/");
        }

        private void dealWithCommand(String message) throws IOException {
            String description = message.split(" ")[0];
            Command command = Command.getCommandFromDescription(description);
            if (command == null) {
                out.write(Messages.NO_SUCH_COMMAND);
                out.newLine();
                out.flush();
                askForGuess();
            }
            command.getHandler().execute(Server.this, this);
        }

        public void send(String message) {
            try {
                out.write(message);
                out.newLine();
                out.flush();
            } catch (IOException e) {
                removePlayer(this);
                e.printStackTrace();
            }
        }

        public void close() {
            try {
                playerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public String getName() {
            return name;
        }

        public void restart() {
            send(Messages.QUIT_OR_NEW_GAME);
            try {
                Scanner in = new Scanner(playerSocket.getInputStream());
                message = in.nextLine();
                dealWithCommand(message);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}