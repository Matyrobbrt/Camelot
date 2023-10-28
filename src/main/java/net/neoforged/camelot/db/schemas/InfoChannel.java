package net.neoforged.camelot.db.schemas;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.Hashing;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.commands.information.InfoChannelCommand;
import net.neoforged.camelot.db.transactionals.InfoChannelsDAO;
import net.neoforged.camelot.db.transactionals.RulesDAO;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.UnaryOperator;

/**
 * A database object representing an info channel.
 *
 * @param channel       the ID of the channel
 * @param location      the location in a GitHub repository of the channel contents directory
 * @param forceRecreate if the channel contents should be forcibly recreated when they are updated
 * @param hash          the last known {@link Hashing#sha256() sha256} hash of the channel contents file. Used to check if the contents were updated
 * @param type          the type of this info channel
 * @see InfoChannelsDAO
 */
public record InfoChannel(long channel, GithubLocation location, boolean forceRecreate, @Nullable String hash, Type type) {
    public static final class Mapper implements RowMapper<InfoChannel> {

        @Override
        public InfoChannel map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new InfoChannel(
                    rs.getLong(1),
                    GithubLocation.parse(rs.getString(2)),
                    rs.getBoolean(3),
                    rs.getString(4),
                    Type.values()[rs.getInt(5)]
            );
        }
    }

    public enum Type {
        NORMAL,
        RULES {
            private static final float MIN_BRIGHTNESS = 0.8f;
            private static final Random RANDOM = new Random();

            public static Color createRandomBrightColor() {
                float h = RANDOM.nextFloat();
                float s = RANDOM.nextFloat();
                float b = MIN_BRIGHTNESS + ((1f - MIN_BRIGHTNESS) * RANDOM.nextFloat());
                return Color.getHSBColor(h, s, b);
            }

            @Override
            public List<InfoChannelCommand.MessageData> read(byte[] content, ObjectMapper mapper, long channel) throws IOException {
                final var data = super.read(content, mapper, channel);
                final List<InfoChannelCommand.MessageData> newMessages = new ArrayList<>();

                final long guildId = BotMain.get().getChannelById(GuildChannel.class, channel).getGuild().getIdLong();

                final RuleUpdater updater = new RuleUpdater(guildId, channel);

                data.forEach(msg -> newMessages.add(new InfoChannelCommand.MessageData(
                        MessageCreateBuilder.from(msg.data())
                                .setEmbeds(msg.data().getEmbeds().stream().map(updater).toList())
                                .build(),
                        msg.authorName(),
                        msg.avatarUrl()
                )));

                updater.run();

                return newMessages;
            }

            @Override
            public String write(List<Message> messages, ObjectMapper mapper, long channel) throws IOException {
                final long guildId = BotMain.get().getChannelById(GuildChannel.class, channel).getGuild().getIdLong();
                final RuleUpdater updater = new RuleUpdater(guildId, channel);
                messages.forEach(msg -> msg.getEmbeds().forEach(updater::apply));
                updater.run();
                return super.write(messages, mapper, channel);
            }

            private static class RuleUpdater implements UnaryOperator<MessageEmbed>, Runnable {
                private final Map<Integer, MessageEmbed> rules = new HashMap<>();
                private final long guildId, channelId;

                private RuleUpdater(long guildId, long channelId) {
                    this.guildId = guildId;
                    this.channelId = channelId;
                }

                @Override
                public MessageEmbed apply(MessageEmbed messageEmbed) {
                    if (messageEmbed.getTitle() != null && messageEmbed.getTitle().startsWith("Rule ")) {
                        final var builder = new EmbedBuilder(messageEmbed);
                        if (messageEmbed.getColorRaw() == Role.DEFAULT_COLOR_RAW) {
                            builder.setColor(createRandomBrightColor());
                        }
                        final int number = Integer.parseInt(messageEmbed.getTitle().substring(5).split("\\.", 2)[0]);
                        final var newEmbed = builder.build();
                        rules.put(number, newEmbed);
                        return newEmbed;
                    }
                    return messageEmbed;
                }

                @Override
                public void run() {
                    Database.main().useExtension(RulesDAO.class, db -> {
                        db.deleteRules(channelId);
                        rules.forEach((nr, embed) -> db.insert(new Rule(guildId, channelId, nr, embed)));
                    });
                }
            }
        };

        public List<InfoChannelCommand.MessageData> read(byte[] content, ObjectMapper mapper, long channel) throws IOException {
            return mapper.readValue(content, new TypeReference<>() {});
        }

        public String write(List<Message> messages, ObjectMapper mapper, long channel) throws IOException {
            return mapper.writer().writeValueAsString(messages);
        }
    }
}
