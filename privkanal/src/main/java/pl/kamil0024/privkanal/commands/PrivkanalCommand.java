/*
 * Copyright (C) 2019 FratikB0T Contributors
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

package pl.kamil0024.privkanal.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.VoiceChannel;
import org.jetbrains.annotations.NotNull;
import pl.fratik.core.command.*;
import pl.fratik.core.entity.ArgsMissingException;
import pl.fratik.core.entity.Uzycie;
import pl.fratik.core.manager.ManagerArgumentow;
import pl.fratik.core.util.CommonErrors;
import pl.kamil0024.privkanal.entity.Privkanal;
import pl.kamil0024.privkanal.entity.PrivkanalDao;

import java.util.LinkedHashMap;
import java.util.Map;

public class PrivkanalCommand extends Command {
    private final PrivkanalDao privkanalDao;
    private final ManagerArgumentow managerArgumentow;

    public PrivkanalCommand(PrivkanalDao privkanalDao, ManagerArgumentow managerArgumentow) {
        this.managerArgumentow = managerArgumentow;
        this.privkanalDao = privkanalDao;
        name = "privkanal";
        category = CommandCategory.FUN;
        permLevel = PermLevel.ADMIN;
        uzycieDelim = " ";
    }

    @Override
    public boolean execute(@NotNull CommandContext context) {
        CommonErrors.usage(context);
        return false;
    }

    @SubCommand(name="delete")
    private boolean delete(CommandContext context) {
        Object[] args;
        Privkanal pdao = privkanalDao.get(context.getGuild());
        try {
            args = new Uzycie("typ", "string", true).resolveArgs(context);
        } catch (ArgsMissingException e) {
            context.send(context.getTranslated("privkanal.delete.usage", context.getPrefix()));
            // Nie uzywam CommonErrors, ponieważ tak jest użytkownikowi będzie łatwiej się tym posługiwać
            return false;
        }
        String typ = (String) args[0];
        boolean amiToKozak = false;
        switch (typ) {
            case "category":
                pdao.setCategory("");
                amiToKozak = true;
                break;
            case "channel":
                pdao.setChannel("");
                amiToKozak = true;
                break;
        }
        if (!amiToKozak) {
            context.send(context.getTranslated("privkanal.delete.usage", context.getPrefix()));
            return false;
        }
        context.send(context.getTranslated("privkanal.delete.done", typ));
        return true;
    }

    @SubCommand(name="set")
    private boolean set(CommandContext context) {
        Object[] args;
        Privkanal pdao = privkanalDao.get(context.getGuild());

        try {
            LinkedHashMap<String, String> hmap = new LinkedHashMap<>();
            hmap.put("typKanalu", "string");
            hmap.put("kanal", "string");
            args = new Uzycie(hmap, new boolean[] {true, true}).resolveArgs(context);
        } catch (ArgsMissingException e) {
            context.send(context.getTranslated("privkanal.set.usage", context.getPrefix()));
            // Nie uzywam CommonErrors, ponieważ tak jest użytkownikowi będzie łatwiej się tym posługiwać
            return false;
        }

        String typ = (String) args[0];
        if (typ.equals("category")) {

            Category category = (Category) managerArgumentow.getArguments().get("member").execute((String) args[1],
                    context.getTlumaczenia(), context.getLanguage(), context.getGuild());
            if (category == null || category.getId().isEmpty()) {
                context.send(context.getTranslated("privkanal.set.usage"));
                return false;
            }
            if (category.getId().equals(pdao.getCategory())) {
                context.send(context.getTranslated("privkanal.set.category.alreadyset"));
                return false;
            }

            pdao.setCategory(category.getId());
            privkanalDao.save(pdao);
            context.send(context.getTranslated("privkanal.set.category.done", category.getName()));
            return true;
        }
        if (typ.equals("channel")) {
            VoiceChannel vc = (VoiceChannel) managerArgumentow.getArguments().get("voicechannel").execute((String) args[1],
                    context.getTlumaczenia(), context.getLanguage(), context.getGuild());

            if (vc == null || vc.getId().isEmpty()) {
                context.send(context.getTranslated("privkanal.set.channel.alreadyset"));
                return false;
            }

            if (pdao.getChannel().equals(vc.getId())) {
                context.send(context.getTranslated("privkanal.set.channel.alreadyset"));
                return false;
            }

            pdao.setChannel(vc.getId());
            privkanalDao.save(pdao);
            context.send(context.getTranslated("privkanal.set.channel.done", vc.getName()));
            return true;
        }
        context.send(context.getTranslated("privkanal.set.usage"));
        return false;
    }

    @SubCommand(name="info")
    private boolean info(CommandContext context) {
        Privkanal pdao = privkanalDao.get(context.getGuild());
        LinkedHashMap<String, String> jd = new LinkedHashMap<>();


        VoiceChannel vc = context.getGuild().getVoiceChannelById(pdao.getId());
        Category cat = context.getGuild().getCategoryById(pdao.getCategory());
        if (vc != null) { jd.put("vc", vc.getName()); }
        else jd.put("vc", context.getTranslated("generic.notset"));

        if (cat != null) { jd.put("cat", cat.getName()); }
        else jd.put("cat", context.getTranslated("generic.notset"));

        EmbedBuilder eb = context.getBaseEmbed();

        for (Map.Entry<String, String> kek : jd.entrySet()) {
            eb.addField(context.getTranslated("privkanal.info." + kek.getKey()), kek.getValue(), false);
        }
        context.send(eb.build());
        return true;
    }
}
