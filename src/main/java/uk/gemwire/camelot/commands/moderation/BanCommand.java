package uk.gemwire.camelot.commands.moderation;

import com.google.common.base.Preconditions;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.Nullable;
import uk.gemwire.camelot.BotMain;
import uk.gemwire.camelot.db.schemas.ModLogEntry;
import uk.gemwire.camelot.db.transactionals.PendingUnbansDAO;
import uk.gemwire.camelot.util.DateUtils;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The command used to ban a user.
 */
public class BanCommand extends ModerationCommand<Integer> {

    public BanCommand() {
        this.name = "ban";
        this.help = "Bans an user";
        this.options = List.of(
                new OptionData(OptionType.USER, "user", "The user to ban", true),
                new OptionData(OptionType.STRING, "reason", "The reason for banning the user", true),
                new OptionData(OptionType.STRING, "duration", "How much to ban the user for. Defaults to indefinite", false),
                new OptionData(OptionType.INTEGER, "deldays", "The amount of days to delete messages for", false)
        );
        this.userPermissions = new Permission[] {
                Permission.BAN_MEMBERS
        };
    }

    @Nullable
    @Override
    @SuppressWarnings("DataFlowIssue")
    protected ModerationAction<Integer> createEntry(SlashCommandEvent event) {
        final long targetId;
        final Member target = event.optMember("user");
        if (target == null) {
            final User usr = event.optUser("user");
            if (usr == null) {
                event.reply("Unknown user!").setEphemeral(true).queue();
                return null;
            }
            targetId = usr.getIdLong();
        } else {
            Preconditions.checkArgument(canModerate(target, event.getMember()), "Cannot moderate user!");
            targetId = target.getIdLong();
        }
        final Duration time = event.getOption("duration", it -> DateUtils.getDurationFromInput(it.getAsString()));
        if (time != null) {
            BotMain.jdbi().useExtension(PendingUnbansDAO.class, db -> db.insert(targetId, event.getGuild().getIdLong(), Timestamp.from(Instant.now().plus(time))));
        }
        return new ModerationAction<>(
                ModLogEntry.ban(targetId, event.getGuild().getIdLong(), event.getUser().getIdLong(), time, event.optString("reason")),
                event.getOption("deldays", 0, OptionMapping::getAsInt)
        );
    }

    @Override
    @SuppressWarnings("DataFlowIssue")
    protected RestAction<?> handle(User user, ModerationAction<Integer> action) {
        final ModLogEntry entry = action.entry();
        return user.getJDA().getGuildById(entry.guild())
                .ban(UserSnowflake.fromId(entry.id()), action.additionalData(), TimeUnit.DAYS)
                .reason("rec: " + entry.reasonOrDefault());
    }

}
