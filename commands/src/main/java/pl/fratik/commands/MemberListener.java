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

package pl.fratik.commands;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberLeaveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import pl.fratik.core.cache.Cache;
import pl.fratik.core.cache.RedisCacheManager;
import pl.fratik.core.entity.GuildConfig;
import pl.fratik.core.entity.GuildDao;
import pl.fratik.core.event.DatabaseUpdateEvent;
import pl.fratik.core.util.CommonUtil;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

class MemberListener {
    private final GuildDao guildDao;
    private final Cache<GuildConfig> gcCache;

    MemberListener(GuildDao guildDao, RedisCacheManager redisCacheManager) {
        this.guildDao = guildDao;
        gcCache = redisCacheManager.new CacheRetriever<GuildConfig>(){}.getCache();
    }

    @Subscribe
    public void onMemberJoinEvent(GuildMemberJoinEvent e) {
        autorole(e);
        przywitanie(e);
    }

    @Subscribe
    public void onMemberLeaveEvent(GuildMemberLeaveEvent e) {
        GuildConfig gc = getGuildConfig(e.getGuild());
        for (Map.Entry<String, String> ch : gc.getPozegnania().entrySet()) {
            TextChannel cha = e.getGuild().getTextChannelById(ch.getKey());
            if (cha == null) continue;
            cha.sendMessage(ch.getValue()
                    .replaceAll("\\{\\{user}}", e.getMember().getUser().getAsTag().replaceAll("@(everyone|here)", "@\u200b$1"))
                    .replaceAll("\\{\\{server}}", e.getGuild().getName().replaceAll("@(everyone|here)", "@\u200b$1"))).queue();
        }
    }

    private void autorole(GuildMemberJoinEvent e) {
        GuildConfig gc = getGuildConfig(e.getGuild());
        List<Role> role = new ArrayList<>();
        if (gc.getAutoroleZa1szaWiadomosc()) return;
        for (String id : gc.getAutorole()) {
            Role rola = CommonUtil.supressException((Function<String, Role>) e.getGuild()::getRoleById, id);
            if (rola == null || !e.getGuild().getSelfMember().canInteract(rola)) continue;
            role.add(rola);
        }
        if (role.isEmpty()) return;
        e.getGuild().modifyMemberRoles(e.getMember(), role, new ArrayList<>()).queue();
    }

    private void przywitanie(GuildMemberJoinEvent e) {
        GuildConfig gc = getGuildConfig(e.getGuild());
        for (Map.Entry<String, String> ch : gc.getPowitania().entrySet()) {
            TextChannel cha = e.getGuild().getTextChannelById(ch.getKey());
            if (cha == null || !cha.canTalk()) continue;
            cha.sendMessage(ch.getValue()
                    .replaceAll("\\{\\{user}}", e.getMember().getUser().getAsTag().replaceAll("@(everyone|here)", "@\u200b$1"))
                    .replaceAll("\\{\\{mention}}", e.getMember().getAsMention())
                    .replaceAll("\\{\\{server}}", e.getGuild().getName().replaceAll("@(everyone|here)", "@\u200b$1"))).queue();
        }
    }

    private GuildConfig getGuildConfig(Guild guild) {
        return gcCache.get(guild.getId(), guildDao::get);
    }

    @Subscribe
    @AllowConcurrentEvents
    public void onMessage(MessageReceivedEvent m) {
        if (m.getChannelType() != ChannelType.TEXT || m.isWebhookMessage()) return;
        GuildConfig gc = getGuildConfig(m.getGuild());
        if (!gc.getAutoroleZa1szaWiadomosc()) return;
        List<Role> role = new ArrayList<>();
        for (String id : gc.getAutorole()) {
            Role rola = CommonUtil.supressException((Function<String, Role>) m.getGuild()::getRoleById, id);
            if (rola == null || !m.getGuild().getSelfMember().canInteract(rola)) continue;
            role.add(rola);
        }
        if (role.isEmpty()) return;
        if (role.stream().anyMatch(Objects.requireNonNull(m.getMember()).getRoles()::contains)) return;
        try {
            m.getGuild().modifyMemberRoles(m.getMember(), role, new ArrayList<>()).queue();
        } catch (Exception e) {
            // nie mamy permów, zawijamy się
        }
    }

    @Subscribe
    public void onRoleAdd(GuildMemberRoleAddEvent e) {
        updateNickname(e.getMember());
    }
    @Subscribe
    public void onRoleRemmove(GuildMemberRoleRemoveEvent e) {
        updateNickname(e.getMember());
    }

    private void updateNickname(Member mem) {
        GuildConfig gc = getGuildConfig(mem.getGuild());
        if (gc.getRolePrefix() == null) return;
        for (Role role : mem.getRoles()) {
            String prefix = gc.getRolePrefix().get(role.getId());
            if (prefix == null) continue;
            
            AtomicReference<String> nick = new AtomicReference<>(mem.getNickname());
            if (nick.get() == null) nick.set(mem.getUser().getName());
            else {
                gc.getRolePrefix().values().forEach(p -> {
                    if (nick.toString().startsWith(p)) {
                        nick.set(nick.toString().substring(0, p.length()));
                    }
                });
            }
            if (nick.toString().startsWith(" ")) nick.set(nick.toString().substring(1));
            else nick.set(" " + nick.toString());
            if ((prefix + nick).length() > 32) {
                int dlugosc = (prefix + nick).length() - 32;
                nick.set(nick.toString().substring(dlugosc));
            }
            try {
                mem.getGuild().modifyNickname(mem, prefix + nick.toString()).queue();
                return;
            } catch (Exception e) {
                /* brak permów */
            }
        }
    }
    
    @Subscribe
    public void onDatabaseUpdateEvent(DatabaseUpdateEvent e) {
        if (e.getEntity() instanceof GuildConfig)
            gcCache.put(((GuildConfig) e.getEntity()).getGuildId(), (GuildConfig) e.getEntity());
    }
}
