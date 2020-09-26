/*
 * Copyright (C) 2019-2020 FratikB0T Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package pl.fratik.moderation.commands;

import com.google.common.eventbus.EventBus;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.fratik.core.command.CommandCategory;
import pl.fratik.core.command.CommandContext;
import pl.fratik.core.command.PermLevel;
import pl.fratik.core.entity.GuildConfig;
import pl.fratik.core.entity.GuildDao;
import pl.fratik.core.entity.Uzycie;
import pl.fratik.core.manager.ManagerKomend;
import pl.fratik.core.tlumaczenia.Language;
import pl.fratik.core.util.ClassicEmbedPaginator;
import pl.fratik.core.util.CommonUtil;
import pl.fratik.core.util.EventWaiter;
import pl.fratik.core.util.UserUtil;
import pl.fratik.moderation.entity.Case;
import pl.fratik.moderation.entity.CaseRow;
import pl.fratik.moderation.entity.CasesDao;
import pl.fratik.moderation.entity.Dowod;

import java.util.*;
import java.util.stream.Collectors;

public class DowodCommand extends CaseEditingCommand {

    private final GuildDao guildDao;
    private final EventWaiter eventWaiter;
    private final EventBus eventBus;

    public DowodCommand(GuildDao guildDao,
                        CasesDao casesDao,
                        ShardManager shardManager,
                        ManagerKomend managerKomend,
                        EventWaiter eventWaiter,
                        EventBus eventBus) {
        super(casesDao, shardManager, managerKomend);
        this.guildDao = guildDao;
        this.eventWaiter = eventWaiter;
        this.eventBus = eventBus;
        name = "dowod";
        category = CommandCategory.MODERATION;
        uzycieDelim = " ";
        LinkedHashMap<String, String> hmap = new LinkedHashMap<>();
        hmap.put("caseid", "integer");
        hmap.put("dowod", "string");
        hmap.put("[...]", "string");
        permissions.add(Permission.MESSAGE_MANAGE);
        permissions.add(Permission.MESSAGE_EMBED_LINKS);
        permissions.add(Permission.MESSAGE_ADD_REACTION);
        uzycie = new Uzycie(hmap, new boolean[] {true, false, false});
    }

    @Override
    public boolean execute(@NotNull CommandContext context) {
        Integer caseId = (Integer) context.getArgs()[0];
        String dowodCnt;
        if (context.getArgs().length > 1) dowodCnt = Arrays.stream(Arrays.copyOfRange(context.getArgs(), 1, context.getArgs().length))
                .map(e -> e == null ? "" : e).map(Objects::toString).collect(Collectors.joining(uzycieDelim));
        else dowodCnt = "";
        GuildConfig gc = guildDao.get(context.getGuild());
        CaseRow caseRow = casesDao.get(context.getGuild());
        if (caseRow.getCases().size() < caseId || (caseRow.getCases().size() >= caseId - 1 &&
                caseRow.getCases().get(caseId - 1) == null)) {
            context.send(context.getTranslated("dowod.invalid.case"));
            return false;
        }
        Case aCase = caseRow.getCases().get(caseId - 1);
        if (dowodCnt.isEmpty()) {
            List<EmbedBuilder> pages = new ArrayList<>();
            for (Dowod dowod : aCase.getDowody()) {
                User user = dowod.retrieveAttachedBy(shardManager).complete();
                EmbedBuilder builder = new EmbedBuilder()
                        .setImage(CommonUtil.getImageUrl(dowod.getContent()))
                        .setAuthor(user.getAsTag(), null, user.getEffectiveAvatarUrl())
                        .setColor(0x9cdff).setDescription(dowod.getContent())
                        .setFooter("ID: " + aCase.getCaseId() + "-" + dowod.getId());
                pages.add(builder);
            }
            new ClassicEmbedPaginator(eventWaiter, pages, context.getSender(), context.getLanguage(),
                    context.getTlumaczenia(), eventBus).create(context.getMessageChannel());
            return true;
        }
        if (context.getRawArgs().length >= 2) {
            List<String> usunAliasy = new ArrayList<>();
            usunAliasy.add("usuń");
            for (Language lang : Language.values()) {
                String[] aliasy = context.getTlumaczenia().get(lang, "dowod.usun").split("\\|");
                for (String alias : aliasy) {
                    if (alias.isEmpty() || alias.equals("dowod.usun")) continue;
                    usunAliasy.add(alias);
                }
            }
            if (usunAliasy.contains(context.getRawArgs()[0])) {
                String idR = context.getRawArgs()[1];
                String[] id = idR.split("-");
                Case aCase1 = caseRow.getCases().get(Integer.parseInt(id[0]));
                Dowod dowod = aCase1.getDowody().get(Integer.parseInt(id[1]));
                User user = dowod.retrieveAttachedBy(shardManager).complete();
                PermLevel attachedByPermlevel;
                try {
                    Member member = context.getGuild().retrieveMember(user).complete();
                    attachedByPermlevel = UserUtil.getPermlevel(member, guildDao, shardManager, PermLevel.OWNER);
                } catch (Exception err) {
                    attachedByPermlevel = UserUtil.getPermlevel(user, shardManager, PermLevel.OWNER);
                }
                PermLevel selfPermLevel = UserUtil.getPermlevel(context.getMember(), guildDao, shardManager, PermLevel.OWNER);
                if (selfPermLevel.getNum() < attachedByPermlevel.getNum()) {
                    context.send(context.getTranslated("dowod.usun.lower.permlevel"));
                    return false;
                }
                if (!aCase1.getDowody().remove(dowod)) throw new IllegalStateException("nie udało się usunąć!");
                context.send(context.getTranslated("dowod.usun.success"));
                return true;
            }
        }
        String content = dowodCnt;
        List<Message.Attachment> attachments = context.getMessage().getAttachments();
        if (!attachments.isEmpty()) {
            content += "\n";
            content += attachments.stream().map(Message.Attachment::getUrl).collect(Collectors.joining(" "));
        }
        aCase.getDowody().add(new Dowod(Dowod.getNextId(aCase.getDowody()), context.getSender().getId(), content.trim()));
        @Nullable TextChannel modLogChannel = gc.getModLog() != null && !Objects.equals(gc.getModLog(), "") ?
                context.getGuild().getTextChannelById(gc.getModLog()) : null;
        return updateCase(context, context.getTranslated("dowod.success"), context.getTranslated("dowod.failed"),
                caseRow, modLogChannel, aCase, gc);
    }

}
