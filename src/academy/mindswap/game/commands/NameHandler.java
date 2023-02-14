package academy.mindswap.game.commands;

import academy.mindswap.game.Game;
import academy.mindswap.game.messages.Messages;


public class NameHandler implements CommandHandler{
    @Override
    public void execute(Game game, Game.ClientConnectionHandler clientConnectionHandler) {
        String message = clientConnectionHandler.getMessage();
        String name = message.substring(6);
        String oldName = clientConnectionHandler.getName();
        game.getClientByName(name).ifPresentOrElse(
                client -> clientConnectionHandler.send(Messages.CLIENT_ALREADY_EXISTS),
                () -> {
                    clientConnectionHandler.setName(name);
                    clientConnectionHandler.send(Messages.SELF_NAME_CHANGED.formatted(name));
                    game.broadcast(name, Messages.NAME_CHANGED.formatted(oldName, name));
                }
        );
    }
}