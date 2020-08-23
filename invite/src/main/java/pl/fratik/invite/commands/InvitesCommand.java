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

package pl.fratik.invite.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import pl.fratik.core.command.Command;
import pl.fratik.core.command.CommandCategory;
import pl.fratik.core.command.CommandContext;
import pl.fratik.core.entity.Uzycie;
import pl.fratik.core.util.UserUtil;
import pl.fratik.invite.entity.InviteConfig;
import pl.fratik.invite.entity.InviteDao;

import java.time.Instant;

public class InvitesCommand extends Command {

    private InviteDao inviteDao;

    public InvitesCommand(InviteDao inviteDao) {
        name = "invites";
        category = CommandCategory.INVITES;
        aliases = new String[] {"zaproszenia"};
        permissions.add(Permission.MESSAGE_EMBED_LINKS);
        uzycie = new Uzycie("osoba", "user");
        cooldown = 5;
        this.inviteDao = inviteDao;
    }

    @Override
    public boolean execute(CommandContext context) {
        User osoba = null;
        if (context.getArgs().length != 0) osoba = (User) context.getArgs()[0];
        if (osoba == null) osoba = context.getSender();

        InviteConfig dao = inviteDao.get(osoba.getId(), context.getGuild().getId());

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(UserUtil.getPrimColor(osoba));
        eb.setThumbnail(UserUtil.getAvatarUrl(osoba));
        eb.setTitle(UserUtil.formatDiscrim(osoba));
        eb.setTimestamp(Instant.now());
        if (!context.getGuild().getSelfMember().hasPermission(Permission.MANAGE_SERVER)) {
            eb.setDescription(context.getTranslated("invites.maybie.doesnt.work"));
        }

        eb.addField(context.getTranslated("invites.stats"),
                context.getTranslated("invites.fieldvalue", 2137,
                        dao.getTotalInvites() - dao.getLeaveInvites(),
                        dao.getLeaveInvites(), dao.getTotalInvites()
                ), false);


        context.send(eb.build());

        return true;
    }

}
