package uk.gemwire.camelot.commands.information;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.jetbrains.annotations.NotNull;
import uk.gemwire.camelot.BotMain;
import uk.gemwire.camelot.commands.Commands;
import uk.gemwire.camelot.commands.PaginatableCommand;
import uk.gemwire.camelot.configuration.Common;
import uk.gemwire.camelot.util.ButtonManager;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class HelpCommand extends PaginatableCommand<PaginatableCommand.SimpleData> {

    public HelpCommand(ButtonManager buttonManager) {
        super(buttonManager);
        this.name = "help";
        this.help = "Show information about the bot's commands";
        this.itemsPerPage = 25;
    }

    @Override
    public SimpleData collectData(SlashCommandEvent event) {
        return new SimpleData(Commands.get().getSlashCommands().size());
    }

    @Override
    public CompletableFuture<MessageEditData> createMessage(int page, SimpleData data, Interaction interaction) {
        return CompletableFuture.completedFuture(new MessageEditBuilder()
                .setEmbeds(getHelpStartingAt(page * itemsPerPage).build())
                .build());
    }

    /**
     * Given a starting index, build an embed that we can display for users
     *  to summarise all available commands.
     * Intended to be used with pagination in the case of servers with LOTS of commands.
     */
    public EmbedBuilder getHelpStartingAt(int index) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setAuthor(Common.NAME, Common.REPO, BotMain.get().getSelfUser().getAvatarUrl());
        embed.setDescription("All registered commands:");

        List<Command> commandList = Commands.get().getCommands();
        commandList.addAll(Commands.get().getSlashCommands());

        // Embeds have a 25 field limit. We need to make sure we don't exceed that.
        if (commandList.size() < itemsPerPage) {
            for (Command c : commandList)
                embed.addField(c.getName(), c.getHelp(), true);
        } else {
            // Make sure we only go up to the limit.
            for (int i = index; i < index + itemsPerPage; i++)
                if (i < commandList.size())
                    embed.addField(commandList.get(i).getName(), commandList.get(i).getHelp(), true);
        }

        embed.setFooter("Camelot").setTimestamp(Instant.now());

        return embed;
    }

}
