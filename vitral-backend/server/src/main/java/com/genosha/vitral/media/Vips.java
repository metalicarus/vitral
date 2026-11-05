package com.genosha.vitral.media;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Runs the libvips command-line tools as separate processes. A crash while decoding a
 * broken file then stays in that process and never takes the server down.
 *
 * <p>Two failure kinds are kept apart: {@link IOException} when the tool cannot run at all
 * (not installed, timed out), and {@link VipsException} when it ran and rejected the file.
 */
public class Vips {

    private final String vipsthumbnail;
    private final String vipsheader;
    private final Duration timeout;

    /** @param binDir folder with the tools, or null to find them on the PATH */
    public Vips(Path binDir, Duration timeout) {
        this.vipsthumbnail = command(binDir, "vipsthumbnail");
        this.vipsheader = command(binDir, "vipsheader");
        this.timeout = timeout;
    }

    private record Result(int exitCode, String stdout, String stderr) {
    }

    public boolean isAvailable() {
        try {
            return run(List.of(vipsthumbnail, "--vips-version")).exitCode() == 0;
        } catch (IOException e) {
            return false;
        }
    }

    /** For example "libvips 8.15.1". */
    public String version() throws IOException {
        return run(List.of(vipsthumbnail, "--vips-version")).stdout().strip();
    }

    /**
     * Writes a JPEG no larger than {@code maxSize} on its longest side, never enlarging.
     * The photo is turned upright using its EXIF orientation, converted to sRGB, and saved
     * without metadata, so the GPS position stays only in the original.
     */
    public void thumbnail(Path input, Path output, int maxSize, int quality) throws IOException, VipsException {
        Result result = run(List.of(vipsthumbnail, escape(input),
                "--size", maxSize + "x" + maxSize + ">",
                "--export-profile", "srgb",
                "-o", output + "[Q=" + quality + ",strip]"));
        if (result.exitCode() != 0) {
            throw new VipsException("vipsthumbnail failed (exit " + result.exitCode() + "): " + errors(result));
        }
    }

    /** The image's real size, as decoded. For HEIC this includes the crop the container declares. */
    public PixelSize size(Path file) throws IOException, VipsException {
        return new PixelSize(intField(file, "width"), intField(file, "height"));
    }

    private int intField(Path file, String field) throws IOException, VipsException {
        Result result = run(List.of(vipsheader, "-f", field, escape(file)));
        try {
            if (result.exitCode() == 0) {
                return Integer.parseInt(result.stdout().strip());
            }
        } catch (NumberFormatException ignored) {
            // Falls through to the error below.
        }
        throw new VipsException("vipsheader could not read " + field + ": " + errors(result));
    }

    /**
     * libvips reads a trailing [...] in a file name as loading options. Appending an empty
     * pair makes it take the whole name literally, whatever the user called the file.
     */
    private static String escape(Path file) {
        return file + "[]";
    }

    /** libvips prints warnings even on success (such as "ignoring nclx profile"); only real errors matter here. */
    private static String errors(Result result) {
        String text = result.stderr().lines()
                .filter(line -> !line.isBlank() && !line.contains("VIPS-WARNING"))
                .collect(Collectors.joining(" "));
        return text.isEmpty() ? "no details" : text;
    }

    private Result run(List<String> command) throws IOException {
        Path out = Files.createTempFile("vips-out-", ".log");
        Path err = Files.createTempFile("vips-err-", ".log");
        try {
            Process process = new ProcessBuilder(command)
                    .redirectOutput(out.toFile())
                    .redirectError(err.toFile())
                    .start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException(command.get(0) + " did not finish within " + timeout);
            }
            return new Result(process.exitValue(), read(out), read(err));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running " + command.get(0), e);
        } finally {
            Files.deleteIfExists(out);
            Files.deleteIfExists(err);
        }
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static String command(Path binDir, String name) {
        return binDir == null ? name : binDir.resolve(name).toString();
    }
}