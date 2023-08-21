package net.neoforged.camelot.commands;

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import net.neoforged.camelot.commands.information.InfoChannelCommand;
import net.neoforged.camelot.commands.moderation.BanCommand;
import net.neoforged.camelot.commands.moderation.KickCommand;
import net.neoforged.camelot.commands.moderation.ModLogsCommand;
import net.neoforged.camelot.commands.moderation.MuteCommand;
import net.neoforged.camelot.commands.moderation.NoteCommand;
import net.neoforged.camelot.commands.moderation.UnbanCommand;
import net.neoforged.camelot.commands.utility.CustomPingsCommand;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.commands.information.HelpCommand;
import net.neoforged.camelot.commands.moderation.PurgeCommand;
import net.neoforged.camelot.commands.moderation.UnmuteCommand;
import net.neoforged.camelot.commands.moderation.WarnCommand;
import net.neoforged.camelot.commands.utility.EvalCommand;
import net.neoforged.camelot.commands.utility.PingCommand;
import net.neoforged.camelot.commands.utility.ManageTrickCommand;
import net.neoforged.camelot.commands.utility.TrickCommand;
import net.neoforged.camelot.configuration.Config;

/**
 * The place where all control flow for commands converges.
 * This is where the routing and distributors are set up, such that when a command is invoked,
 *  the proper method is executed in the proper class.
 *
 * The intended structure of Commands are that each Command has its own class file, and it is registered here.
 * TODO: Perhaps automatic registration?
 *
 * @author Curle
 */
public class Commands {

    private static CommandClient commands;

    public static CommandClient get() { return commands; }

    /**
     * Register and setup every valid command.
     * To remove a command from the bot, simply comment the line where it is added.
     */
    public static void init() {
        commands = new CommandClientBuilder()
                .setOwnerId(String.valueOf(Config.OWNER_SNOWFLAKE))
                .setPrefix(Config.PREFIX)
                .useHelpBuilder(false) // We use the slash command instead

                .addSlashCommand(new PingCommand())
                .addSlashCommand(new HelpCommand(BotMain.BUTTON_MANAGER))

                // Moderation commands
                .addSlashCommands(
                        new ModLogsCommand(BotMain.BUTTON_MANAGER),
                        new NoteCommand(), new WarnCommand(),
                        new MuteCommand(), new UnmuteCommand(),
                        new KickCommand(), new PurgeCommand(),
                        new BanCommand(), new UnbanCommand()
                )

                // Information commands
                .addSlashCommands(new InfoChannelCommand())

                // Message context menus
                .addContextMenus(new InfoChannelCommand.UploadToDiscohookContextMenu())

                .addSlashCommands(new ManageTrickCommand(), new TrickCommand())
                .addCommand(new EvalCommand())

                .addSlashCommand(new CustomPingsCommand())

                .build();

        // Register the commands to the listener.
        BotMain.get().addEventListener(commands);
    }

}