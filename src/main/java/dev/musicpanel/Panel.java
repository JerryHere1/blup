package dev.musicpanel;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/** Renders the control panel: one embed + two rows of buttons. Button ids start with "mp:". */
final class Panel {

    private static final String DIVIDER = "˖ ⊹ ────────────────────────── ⊹ ˖";
    private static final int DEFAULT_COLOR = 0xF5A9C7;      // soft pink
    private static final int IDLE_COLOR = 0x9B8FB5;         // muted lilac

    private Panel() { }

    // ───────────────────────── embed ─────────────────────────

    static MessageEmbed embed(GuildMusic gm) {
        FileConfiguration cfg = gm.service().cfg();
        AudioTrack track = gm.player().getPlayingTrack();
        boolean paused = gm.player().isPaused();

        EmbedBuilder eb = new EmbedBuilder();

        if (track == null) {
            String prefix = cfg.getString("discord.prefix", "+");
            eb.setColor(IDLE_COLOR);
            eb.setAuthor("♪  Music Panel", null, null);
            eb.setDescription("**Nothing is playing right now.**\n" + DIVIDER
                    + "\nStart something with `/play` or `" + prefix + "play`\n"
                    + "a song name, a YouTube link, or a Spotify track.");
        } else {
            AudioTrackInfo info = track.getInfo();
            eb.setColor(color(cfg));
            eb.setAuthor(paused ? "⏸  Paused" : "♪  Now Playing", null, null);

            String title = esc(cut(info.title, 110));
            eb.setDescription("### " + (isUrl(info.uri) ? "[" + title + "](" + info.uri + ")" : title)
                    + "\n*" + esc(cut(info.author, 80)) + "*\n"
                    + progress(track) + "\n" + DIVIDER);

            if (isUrl(info.artworkUrl)) eb.setThumbnail(info.artworkUrl);

            eb.addField("🔊 Volume", volumeBar(gm.volume()) + "\n`" + gm.volume() + "%`", true);
            eb.addField("🔁 Loop", switch (gm.loop()) {
                case OFF -> "`off`";
                case TRACK -> "`this song`";
                case QUEUE -> "`whole queue`";
            }, true);
            eb.addField("📜 Queue", "`" + gm.queueSize() + " waiting`", true);

            List<AudioTrack> next = gm.upcoming(3);
            if (!next.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < next.size(); i++) {
                    sb.append("`").append(i + 1).append("`  ")
                            .append(esc(cut(next.get(i).getInfo().title, 58))).append('\n');
                }
                eb.addField("✧ Up next", sb.toString().trim(), false);
            }

            String requester = track.getUserData(String.class);
            if (requester != null) eb.setFooter("♡ asked for by " + requester);
        }

        String gif = cfg.getString("panel.gif-url", "");
        if (isUrl(gif)) eb.setImage(gif);

        if (gm.lastError() != null) {
            eb.addField("⚠️ Last error", cut(gm.lastError(), 300), false);
        }
        return eb.build();
    }

    static MessageEmbed stopped(String reason) {
        return new EmbedBuilder()
                .setColor(IDLE_COLOR)
                .setAuthor("⏹  Stopped", null, null)
                .setDescription(reason + "\n" + DIVIDER + "\nUse `/play` to start the music again.")
                .build();
    }

    // ───────────────────────── buttons ─────────────────────────

    static List<ActionRow> rows(GuildMusic gm) {
        boolean idle = gm.player().getPlayingTrack() == null;
        boolean paused = gm.player().isPaused();

        Button restart = Button.secondary("mp:restart", "Replay").withEmoji(Emoji.fromUnicode("⏮️"));
        Button pause = paused
                ? Button.success("mp:pause", "Resume").withEmoji(Emoji.fromUnicode("▶️"))
                : Button.primary("mp:pause", "Pause").withEmoji(Emoji.fromUnicode("⏸️"));
        Button skip = Button.secondary("mp:skip", "Skip").withEmoji(Emoji.fromUnicode("⏭️"));
        Button stop = Button.danger("mp:stop", "Stop").withEmoji(Emoji.fromUnicode("⏹️"));

        GuildMusic.Loop loop = gm.loop();
        Button loopBtn = (loop == GuildMusic.Loop.OFF
                ? Button.secondary("mp:loop", "Loop")
                : Button.success("mp:loop", loop == GuildMusic.Loop.TRACK ? "Loop: song" : "Loop: queue"))
                .withEmoji(Emoji.fromUnicode(loop == GuildMusic.Loop.TRACK ? "🔂" : "🔁"));
        Button shuffle = Button.secondary("mp:shuffle", "Shuffle").withEmoji(Emoji.fromUnicode("🔀"));
        Button volDown = Button.secondary("mp:voldown", "−").withEmoji(Emoji.fromUnicode("🔉"));
        Button volUp = Button.secondary("mp:volup", "+").withEmoji(Emoji.fromUnicode("🔊"));
        Button queue = Button.secondary("mp:queue", "Queue").withEmoji(Emoji.fromUnicode("📜"));

        return List.of(
                ActionRow.of(restart.withDisabled(idle), pause.withDisabled(idle), skip.withDisabled(idle), stop),
                ActionRow.of(loopBtn, shuffle.withDisabled(gm.queueSize() < 2), volDown, volUp, queue));
    }

    // ───────────────────────── helpers ─────────────────────────

    static String esc(String s) {
        return MarkdownSanitizer.escape(s == null ? "" : s);
    }

    static String cut(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "…";
    }

    /** panel.color as "#rrggbb" or "rrggbb"; falls back to the default pink. */
    private static int color(FileConfiguration cfg) {
        String raw = cfg.getString("panel.color", "").trim().replace("#", "");
        if (raw.isEmpty()) return DEFAULT_COLOR;
        try {
            return Integer.parseInt(raw, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return DEFAULT_COLOR;
        }
    }

    private static boolean isUrl(String s) {
        return s != null && (s.startsWith("https://") || s.startsWith("http://")) && !s.contains(")") && !s.contains(" ");
    }

    private static String progress(AudioTrack track) {
        if (track.getInfo().isStream) return "`🔴 live`";
        long position = track.getPosition();
        long duration = Math.max(1, track.getDuration());
        int slots = 16;
        int marker = (int) Math.min(slots - 1, position * slots / duration);

        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < slots; i++) {
            bar.append(i == marker ? "🩷" : (i < marker ? "▬" : "▭"));
        }
        return bar + "\n`" + time(position) + " / " + time(duration) + "`";
    }

    private static String volumeBar(int volume) {
        int filled = Math.max(0, Math.min(10, volume / 10));
        return "▰".repeat(filled) + "▱".repeat(10 - filled);
    }

    private static String time(long millis) {
        long total = millis / 1000;
        long h = total / 3600, m = (total % 3600) / 60, s = total % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }
}
