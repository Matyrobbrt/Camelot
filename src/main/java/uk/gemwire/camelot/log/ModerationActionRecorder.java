package uk.gemwire.camelot.log;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogChange;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.AuditLogKey;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.GuildAuditLogEntryCreateEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import uk.gemwire.camelot.BotMain;
import uk.gemwire.camelot.configuration.Config;
import uk.gemwire.camelot.db.schemas.ModLogEntry;
import uk.gemwire.camelot.db.transactionals.ModLogsDAO;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;

public class ModerationActionRecorder implements EventListener {
    @Override
    public void onEvent(@NotNull GenericEvent gevent) {
        if (!(gevent instanceof GuildAuditLogEntryCreateEvent event)) return;
        final AuditLogEntry entry = event.getEntry();
        final ActionType type = entry.getType();

        if (entry.getReason() != null && entry.getReason().startsWith("rec: ")) return; // If the reason starts with `rec:` it means that the bot moderated someone after a moderator used a command

        final ModLogEntry logEntry = switch (type) {
            case BAN -> ModLogEntry.ban(
                    entry.getTargetIdLong(), entry.getGuild().getIdLong(),
                    entry.getUserIdLong(), null, entry.getReason()
            );
            case KICK -> ModLogEntry.kick(
                    entry.getTargetIdLong(), entry.getGuild().getIdLong(),
                    entry.getUserIdLong(), entry.getReason()
            );
            case UNBAN -> ModLogEntry.unban(
                    entry.getTargetIdLong(), entry.getGuild().getIdLong(),
                    entry.getUserIdLong(), entry.getReason()
            );
            case MEMBER_UPDATE -> {
                final @Nullable AuditLogChange timeoutChange = entry.getChangeByKey(AuditLogKey.MEMBER_TIME_OUT);
                if (timeoutChange != null) {
                    final OffsetDateTime oldTimeoutEnd = parseDateTime(timeoutChange.getOldValue());
                    final OffsetDateTime newTimeoutEnd = parseDateTime(timeoutChange.getNewValue());

                    if ((oldTimeoutEnd == null || oldTimeoutEnd.isBefore(OffsetDateTime.now())) && newTimeoutEnd != null) {
                        // Somebody was timed out
                        yield ModLogEntry.mute(
                                entry.getTargetIdLong(), entry.getGuild().getIdLong(),
                                entry.getUserIdLong(), Duration.ofSeconds(newTimeoutEnd.toEpochSecond()).minusSeconds(entry.getTimeCreated().toEpochSecond()), entry.getReason()
                        );
                    } else if (oldTimeoutEnd != null && newTimeoutEnd == null) {
                        // Somebody's timeout was removed
                        yield ModLogEntry.unmute(
                                entry.getTargetIdLong(), entry.getGuild().getIdLong(),
                                entry.getUserIdLong(), entry.getReason()
                        );
                    }
                }
                yield null;
            }
            default -> null;
        };
        if (logEntry != null) {
            recordAndLog(logEntry, event.getJDA());
        }
    }

    private void recordAndLog(ModLogEntry entry, JDA jda) {
        entry.setId(BotMain.jdbi().withExtension(ModLogsDAO.class, db -> db.insert(entry)));
        log(entry, jda);
    }

    public static void log(ModLogEntry entry, JDA jda) {
        jda.retrieveUserById(entry.user())
                .queue(user -> log(entry, user));
    }

    public static void log(ModLogEntry entry, User user) {
        entry.format(user.getJDA())
                .thenAccept(caseData -> Config.MODERATION_LOGS.log(new EmbedBuilder()
                        .setTitle("%s has been %s".formatted(user.getAsTag(), entry.type().getAction()))
                        .setDescription("Case information below:")
                        .addField(caseData)
                        .setTimestamp(entry.timestamp())
                        .setFooter("User ID: " + user.getId(), user.getAvatarUrl())
                        .setColor(entry.type().getColor())
                        .build()))
                .exceptionally((ex) -> {
                    BotMain.LOGGER.error("Could not log moderation log entry {}: ", entry, ex);
                    return null;
                });
    }

    private static @Nullable OffsetDateTime parseDateTime(@Nullable String dateTimeString) {
        if (dateTimeString == null) return null;
        return OffsetDateTime.parse(dateTimeString);
    }
}
