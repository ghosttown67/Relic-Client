package relic.client.api.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public final class MediaService {

    private static final Identifier ART_ID = Identifier.of("relic", "media_album_art");
    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");
    private static final long POLL_MS = 2000;

    private static MediaService instance;

    private final ExecutorService control = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Relic-Media-Ctl");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean running;
    private volatile String title = "";
    private volatile String artist = "";
    private volatile boolean playing;
    private volatile String trackKey = "";

    private final AtomicReference<NativeImage> pendingArt = new AtomicReference<>();
    private volatile boolean hasArt;
    private volatile int artW, artH;

    private volatile NativeImageBackedTexture artTex;

    private volatile long durationMs;
    private volatile long positionBaseMs;
    private volatile long baseNano;

    private volatile long lastWarnNano;

    private MediaService() {}

    public static MediaService getInstance() {
        if (instance == null) instance = new MediaService();
        return instance;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        Thread t = new Thread(this::pollLoop, "Relic-Media");
        t.setDaemon(true);
        t.start();
    }

    public boolean isPlaying()  { return playing; }
    public boolean hasArt()     { return hasArt && artW > 0; }
    public int artWidth()       { return artW; }
    public int artHeight()      { return artH; }
    public Identifier artId()   { return ART_ID; }
    public long getDurationMs() { return durationMs; }

    public int artGlId() {
        NativeImageBackedTexture t = artTex;
        if (t == null) return 0;
        try {
            GpuTexture gpu = t.getGlTexture();
            return gpu instanceof GlTexture gl ? gl.getGlId() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public long getProgressMs() {
        long pos = positionBaseMs;
        if (playing) pos += (System.nanoTime() - baseNano) / 1_000_000L;
        long d = durationMs;
        return (d > 0 && pos > d) ? d : Math.max(0, pos);
    }

    public String getTitle() {
        if (!title.isEmpty()) return title;
        return WINDOWS ? "Nothing playing" : "Windows only";
    }

    public String getArtist() {
        if (!artist.isEmpty()) return artist;
        return WINDOWS ? "Play media to begin" : "";
    }

    public void next()     { mediaKey(0xB0); }
    public void previous() { mediaKey(0xB1); }

    public void playPause() {
        playing = !playing;
        baseNano = System.nanoTime();
        mediaKey(0xB3);
    }

    private void pollLoop() {
        while (running) {
            try {
                if (WINDOWS) pollSession();
            } catch (Exception e) {
                warnThrottled("poll failed: " + e);
            }
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void pollSession() throws Exception {
        String json = runPowerShellCapture(METADATA_SCRIPT).strip();
        if (json.isEmpty() || json.equals("{}")) {

            if (playing) { positionBaseMs = getProgressMs(); playing = false; }
            title = ""; artist = ""; durationMs = 0; trackKey = "";
            return;
        }
        JsonObject o;
        try {
            o = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception parse) {
            warnThrottled("media session returned non-JSON: " + snippet(json));
            return;
        }
        if (o.has("error")) { warnThrottled("media session: " + str(o, "error")); return; }

        String newTitle = str(o, "title");
        String newArtist = str(o, "artist");
        int status = o.has("status") ? o.get("status").getAsInt() : 0;
        long posMs = o.has("positionMs") ? o.get("positionMs").getAsLong() : 0L;
        long durMs = o.has("durationMs") ? o.get("durationMs").getAsLong() : 0L;

        title = newTitle;
        artist = newArtist;
        durationMs = durMs;
        positionBaseMs = posMs;
        baseNano = System.nanoTime();
        playing = status == 4;

        String key = newArtist + " — " + newTitle;
        if (!key.isBlank() && !key.equals(trackKey)) {
            trackKey = key;
            fetchThumbnail();
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private void fetchThumbnail() {
        control.submit(() -> {
            try {
                Path out = Path.of(System.getProperty("java.io.tmpdir"), "relic-media-art.img");
                String outFwd = out.toString().replace('\\', '/');
                runPowerShellCapture(THUMB_SCRIPT.replace("__OUT__", outFwd));
                if (!Files.exists(out) || Files.size(out) == 0) return;
                NativeImage img = NativeImage.read(Files.readAllBytes(out));
                NativeImage old = pendingArt.getAndSet(img);
                if (old != null) old.close();
            } catch (Exception e) {
                warnThrottled("thumbnail fetch failed: " + e);
            }
        });
    }

    public void uploadPendingArt() {
        NativeImage img = pendingArt.getAndSet(null);
        if (img == null) return;
        try {
            TextureManager tm = MinecraftClient.getInstance().getTextureManager();
            tm.destroyTexture(ART_ID);
            NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "relic_media_art", img);
            tm.registerTexture(ART_ID, tex);
            artTex = tex;
            artW = img.getWidth();
            artH = img.getHeight();
            hasArt = true;
        } catch (Exception e) {
            img.close();
            warnThrottled("art upload failed: " + e);
        }
    }

    private void mediaKey(int vk) {
        if (!WINDOWS) return;
        control.submit(() -> runPowerShellSilent(
                "$s='[DllImport(\"user32.dll\")]public static extern void keybd_event(byte b,byte k,uint f,int e);';" +
                "$t=Add-Type -MemberDefinition $s -Name MK -Namespace Relic -PassThru;" +
                String.format("$t::keybd_event(0x%02X,0,0,0);$t::keybd_event(0x%02X,0,2,0)", vk, vk)));
    }

    private String runPowerShellCapture(String script) throws Exception {
        String b64 = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive",
                "-WindowStyle", "Hidden", "-EncodedCommand", b64)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();
        return out;
    }

    private void runPowerShellSilent(String script) {
        try {
            String b64 = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
            new ProcessBuilder("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-EncodedCommand", b64)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (Exception e) {
            warnThrottled("control failed: " + e.getMessage());
        }
    }

    private void warnThrottled(String msg) {
        long now = System.nanoTime();
        if (now - lastWarnNano > 15_000_000_000L) {
            lastWarnNano = now;
            System.err.println("[Relic Client] Media: " + msg);
        }
    }

    private static String snippet(String s) {
        String flat = s.strip().replaceAll("\\s+", " ");
        return flat.length() > 80 ? flat.substring(0, 80) + "…" : flat;
    }

    private static final String PREAMBLE = """
            [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
            $ErrorActionPreference = 'Stop'
            Add-Type -AssemblyName System.Runtime.WindowsRuntime | Out-Null
            $asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
            function Await($op, $t) { $m = $asTaskGeneric.MakeGenericMethod($t); $tk = $m.Invoke($null, @($op)); $tk.Wait(-1) | Out-Null; $tk.Result }
            [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null
            $mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
            $s = $mgr.GetCurrentSession()
            """;

    private static final String METADATA_SCRIPT = PREAMBLE + """
            try {
              if (-not $s) { Write-Output '{}'; return }
              $props = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
              $pb = $s.GetPlaybackInfo()
              $tl = $s.GetTimelineProperties()
              $o = [ordered]@{
                title      = [string]$props.Title
                artist     = [string]$props.Artist
                status     = [int]$pb.PlaybackStatus
                positionMs = [long]$tl.Position.TotalMilliseconds
                durationMs = [long]$tl.EndTime.TotalMilliseconds
              }
              $o | ConvertTo-Json -Compress
            } catch {
              @{ error = $_.Exception.Message } | ConvertTo-Json -Compress
            }
            """;

    private static final String THUMB_SCRIPT = PREAMBLE + """
            try {
              if (-not $s) { return }
              [Windows.Storage.Streams.IInputStream, Windows.Storage.Streams, ContentType = WindowsRuntime] | Out-Null
              $props = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
              $thumb = $props.Thumbnail
              if (-not $thumb) { return }
              $stream = Await ($thumb.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
              $m = [System.IO.WindowsRuntimeStreamExtensions].GetMethod('AsStreamForRead', [type[]]@([Windows.Storage.Streams.IInputStream]))
              $net = $m.Invoke($null, @([object]$stream))
              $ms = New-Object System.IO.MemoryStream
              $net.CopyTo($ms)
              [IO.File]::WriteAllBytes('__OUT__', $ms.ToArray())
            } catch { }
            """;
}
