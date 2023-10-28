package net.neoforged.camelot.commands.information;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.db.schemas.Rule;
import net.neoforged.camelot.db.transactionals.RulesDAO;

import java.util.List;

public class RuleCommand extends SlashCommand {
    private RuleCommand() {
        name = "rule";
        help = "Gets a rule by its number";
        options = List.of(new OptionData(
                OptionType.INTEGER, "rule", "The number of the rule to get", true
        ));
        guildOnly = true;
    }

    @Override
    protected void execute(final SlashCommandEvent event) {
        assert event.getGuild() != null;

        final int id = event.getOption("rule", 1, OptionMapping::getAsInt);
        final var rule = getRule(event.getGuild(), id);
        if (rule == null) {
            event.reply("Unknown rule nr. " + id)
                    .setEphemeral(true)
                    .queue();
        } else {
            event.reply(new MessageCreateBuilder()
                        .setEmbeds(new EmbedBuilder(rule.embed())
                                .setAuthor(event.getGuild().getName(), null, event.getGuild().getIconUrl())
                                .build())
                        .setContent(STR."See the server's rules in <#\{rule.channelId()}>.")
                        .build())
                    .queue();
        }
    }

    @Override
    protected void execute(final CommandEvent event) {
        final int id;
        try {
            id = Integer.parseInt(event.getArgs().trim());
        } catch (NumberFormatException ignored) {
            event.reply("Provided argument is not a number!");
            return;
        }
        final var rule = getRule(event.getGuild(), id);
        if (rule == null) {
            event.reply("Unknown rule nr. " + id);
        } else {
            event.reply(new MessageCreateBuilder()
                    .setEmbeds(new EmbedBuilder(rule.embed())
                            .setAuthor(event.getGuild().getName(), null, event.getGuild().getIconUrl())
                            .build())
                    .setContent(STR."See the server's rules in <#\{rule.channelId()}>.")
                    .build());
        }
    }

    protected Rule getRule(Guild guild, int id) {
        return Database.main().withExtension(RulesDAO.class, db -> db.getRule(guild.getIdLong(), id));
    }
}