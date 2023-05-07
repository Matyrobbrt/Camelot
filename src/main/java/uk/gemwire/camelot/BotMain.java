package uk.gemwire.camelot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gemwire.camelot.commands.Commands;
import uk.gemwire.camelot.commands.information.InfoChannelCommand;
import uk.gemwire.camelot.commands.utility.EvalCommand;
import uk.gemwire.camelot.commands.utility.ManageTrickCommand;
import uk.gemwire.camelot.configuration.Common;
import uk.gemwire.camelot.configuration.Config;
import uk.gemwire.camelot.db.transactionals.PendingUnbansDAO;
import uk.gemwire.camelot.listener.TrickListener;
import uk.gemwire.camelot.log.ModerationActionRecorder;
import uk.gemwire.camelot.util.jda.ButtonManager;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Bot program entry point.
 * <p>
 * Camelot is a utility and management bot designed for the Forge Project Discord server.
 * It provides entertainment systems like quotes, utility systems like tricks and pings.
 * <p>
 * The main feature is translation between Minecraft obfuscation mappings.
 * Defaulting to the latest Mojang mappings, it can translate mapped to SRG names,
 * and for versions where MCPBot exports exist, MCP to Mojmap translations.
 * <p>
 * Camelot is designed to be a successor to K9, and is developed in tandem with its sister Lost City, R'lyeh.
 *
 * @author Curle
 */
public class BotMain {
    /**
     * Discord Gateway Intents used by the bot.
     * These will probably be changed often, so they're prominent.
     */
    private static final List<GatewayIntent> INTENTS = Arrays.asList(
            GatewayIntent.GUILD_MESSAGES,               // For receiving messages.
            GatewayIntent.MESSAGE_CONTENT,              // For reading messages.
            GatewayIntent.GUILD_EMOJIS_AND_STICKERS,    // For receiving emoji updates.
            GatewayIntent.GUILD_MESSAGE_REACTIONS,      // For reading message reactions. This should be removed after Actions are implemented.
            GatewayIntent.GUILD_MEMBERS,                // For reading online members, such as for resolving moderators by ID.
            GatewayIntent.DIRECT_MESSAGES,              // For receiving direct messages.
            GatewayIntent.GUILD_MODERATION              // For receiving moderation-related events, such as bans, unbans and audit log changes.
    );

    /**
     * The static button manager, used for easy button handling.
     */
    public static final ButtonManager BUTTON_MANAGER = new ButtonManager();

    /**
     * The static {@link ScheduledExecutorService} for scheduling tasks.
     */
    public static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(2);

    /**
     * The static {@link HttpClient} instance used for http requests.
     */
    public static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    /**
     * Static instance of the bot. Can be accessed by any class with {@link #get()}
     */
    private static JDA instance;

    /**
     * Logger instance for the whole bot. Perhaps overkill.
     */

    public static final Logger LOGGER = LoggerFactory.getLogger(Common.NAME);

    /**
     * Fetch the static instance of the bot stored in this class.
     */
    public static JDA get() {
        return instance;
    }

    public static void main(String[] args) {
        // This throw shouldn't occur by any Earthly means but Java demands that i catch it.
        try {
            Config.readConfigs();
        } catch (Exception e) {
            LOGGER.error("Something is wrong with the universe. Error: " + e.getMessage());
            System.exit(-1);
        }

        instance = JDABuilder
                .create(Config.LOGIN_TOKEN, INTENTS)
                .disableCache(CacheFlag.VOICE_STATE, CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS)
                .setActivity(Activity.playing("the fiddle"))
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .addEventListeners(BUTTON_MANAGER, new ModerationActionRecorder(), InfoChannelCommand.EVENT_LISTENER)

                .addEventListeners((EventListener) ManageTrickCommand.Update::onEvent, (EventListener) ManageTrickCommand.Add::onEvent, (EventListener) EvalCommand::onEvent)

                .build();
        Config.populate(instance);

        try {
            Database.init();
        } catch (IOException exception) {
            throw new RuntimeException("Encountered exception setting up database connections:", exception);
        }

        Commands.init();
        instance.addEventListener(new TrickListener(Commands.get().getPrefix()));

        EXECUTOR.scheduleAtFixedRate(() -> {
            final PendingUnbansDAO db = Database.main().onDemand(PendingUnbansDAO.class);
            for (final Guild guild : instance.getGuilds()) {
                final List<Long> users = db.getUsersToUnban(guild.getIdLong());
                if (!users.isEmpty()) {
                    for (final long toUnban : users) {
                        // We do not use allOf because we do not want a deleted user to cause all unbans to fail
                        guild.unban(UserSnowflake.fromId(toUnban)).reason("rec: Ban expired")
                                .queue(suc -> db.delete(toUnban, guild.getIdLong()), new ErrorHandler()
                                        .handle(ErrorResponse.UNKNOWN_USER, e -> db.delete(toUnban, guild.getIdLong()))); // User doesn't exist, so don't care about the unban anymore
                    }
                }
            }
        }, 1, 1, TimeUnit.MINUTES);

        // Update info channels every couple of minutes
        EXECUTOR.scheduleAtFixedRate(InfoChannelCommand::run, 1, 2, TimeUnit.MINUTES);
    }
}
