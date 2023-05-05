package uk.gemwire.camelot.script.fs;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.IntBinaryOperator;
import java.util.stream.IntStream;

@ParametersAreNonnullByDefault
public final class ScriptPath implements Path {
    private final ScriptFileSystem fileSystem;
    private final boolean absolute;
    private final String[] pathParts;

    // Store the normalized path after it has been created first
    private ScriptPath normalized;

    ScriptPath(final ScriptFileSystem fileSystem, final String... pathParts) {
        this.fileSystem = fileSystem;
        if (pathParts.length == 0) {
            this.absolute = false;
            this.pathParts = new String[0];
        } else {
            StringBuilder joiner = new StringBuilder();
            for (int i = 0; i < pathParts.length; i++) {
                final String element = pathParts[i];
                if (!element.isEmpty()) {
                    joiner.append(element);
                    if (i < pathParts.length - 1) joiner.append('/');
                }
            }
            final var longstring = joiner.toString();
            this.absolute = longstring.startsWith("/");
            this.pathParts = getPathParts(longstring);
        }
        this.normalized = null;
    }

    // Private constructor only for known correct split and extra value for absolute
    ScriptPath(final ScriptFileSystem fileSystem, boolean absolute, final String... pathParts) {
        this(fileSystem, absolute, false, pathParts);
    }

    private ScriptPath(final ScriptFileSystem fileSystem, boolean absolute, boolean isNormalized, final String... pathParts) {
        this.fileSystem = fileSystem;
        this.absolute = absolute;
        this.pathParts = pathParts;
        if (isNormalized) this.normalized = this;
        else this.normalized = null;
    }

    private String[] getPathParts(final String longstring) {
        final String clean = longstring.replace('\\', '/');
        int startIndex = 0;
        final List<String> parts = new ArrayList<>();
        while (startIndex != longstring.length()) {
            final int index = clean.indexOf('/', startIndex);
            if (index == -1) {
                parts.add(clean.substring(startIndex));
                break;
            }
            // Skips double slash and slash and start/end
            if (index != startIndex) {
                parts.add(clean.substring(startIndex, index));
            }
            startIndex = (index + 1);
        }
        return parts.toArray(String[]::new);
    }

    @Override
    public ScriptFileSystem getFileSystem() {
        return this.fileSystem;
    }

    @Override
    public boolean isAbsolute() {
        return this.absolute;
    }

    @Override
    public Path getRoot() {
        return this.fileSystem.getRoot();
    }

    @Override
    public Path getFileName() {
        if (this.pathParts.length > 0) {
            return new ScriptPath(this.getFileSystem(), false, this.pathParts[this.pathParts.length - 1]);
        } else {
            return new ScriptPath(this.fileSystem, false);
        }
    }

    @Override
    public Path getParent() {
        if (this.pathParts.length > 0) {
            return new ScriptPath(this.fileSystem, this.absolute, Arrays.copyOf(this.pathParts, this.pathParts.length - 1));
        } else {
            return null;
        }
    }

    @Override
    public int getNameCount() {
        return this.pathParts.length;
    }

    @Override
    public Path getName(final int index) {
        if (index < 0 || index > this.pathParts.length - 1) throw new IllegalArgumentException();
        return new ScriptPath(this.fileSystem, false, this.pathParts[index]);
    }

    @Override
    public ScriptPath subpath(final int beginIndex, final int endIndex) {
        if (!this.absolute && this.pathParts.length == 0 && beginIndex == 0 && endIndex == 1)
            return new ScriptPath(this.fileSystem, false);
        if (beginIndex < 0 || beginIndex > this.pathParts.length - 1 || endIndex < 0 || endIndex > this.pathParts.length || beginIndex >= endIndex) {
            throw new IllegalArgumentException("Out of range " + beginIndex + " to " + endIndex + " for length " + this.pathParts.length);
        }
        if (!this.absolute && beginIndex == 0 && endIndex == this.pathParts.length) {
            return this;
        }
        return new ScriptPath(this.fileSystem, false, Arrays.copyOfRange(this.pathParts, beginIndex, endIndex));
    }

    @Override
    public boolean startsWith(final Path other) {
        if (other.getFileSystem() != this.getFileSystem()) {
            return false;
        }
        if (other instanceof ScriptPath bp) {
            if (this.absolute != bp.absolute) return false;
            return checkArraysMatch(this.pathParts, bp.pathParts, false);
        }
        return false;
    }


    @Override
    public boolean endsWith(final Path other) {
        if (other.getFileSystem() != this.getFileSystem()) {
            return false;
        }
        if (other instanceof ScriptPath bp) {
            if (!this.absolute && bp.absolute) return false;
            return checkArraysMatch(this.pathParts, bp.pathParts, true);
        }
        return false;
    }

    private static boolean checkArraysMatch(String[] array1, String[] array2, boolean reverse) {
        var length = Math.min(array1.length, array2.length);
        IntBinaryOperator offset = reverse ? (l, i) -> l - i - 1 : (l, i) -> i;
        for (int i = 0; i < length; i++) {
            if (!Objects.equals(array1[offset.applyAsInt(array1.length, i)], array2[offset.applyAsInt(array2.length, i)]))
                return false;
        }
        return true;
    }

    @Override
    public Path normalize() {
        if (normalized != null) return normalized;
        final Deque<String> normalizedPath = new ArrayDeque<>();
        for (final String pathPart : this.pathParts) {
            switch (pathPart) {
                case "." -> {}
                case ".." -> {
                    if (normalizedPath.isEmpty() || normalizedPath.getLast().equals("..")) {
                        // .. on an empty path is allowed, so keep it
                        normalizedPath.addLast(pathPart);
                    } else {
                        normalizedPath.removeLast();
                    }
                }
                default -> normalizedPath.addLast(pathPart);
            }
        }
        normalized = new ScriptPath(this.fileSystem, this.absolute, true, normalizedPath.toArray(new String[0]));
        return normalized;
    }

    @Override
    public Path resolve(final Path other) {
        if (other instanceof ScriptPath path) {
            if (path.isAbsolute()) return path;
            final String[] mergedParts = new String[this.pathParts.length + path.pathParts.length];
            System.arraycopy(this.pathParts, 0, mergedParts, 0, this.pathParts.length);
            System.arraycopy(path.pathParts, 0, mergedParts, this.pathParts.length, path.pathParts.length);
            return new ScriptPath(this.fileSystem, this.absolute, mergedParts);
        }
        return other;
    }

    @Override
    public Path relativize(final Path other) {
        if (other instanceof ScriptPath p && p.getFileSystem() == this.getFileSystem()) {
            final int length = Math.min(this.pathParts.length, p.pathParts.length);
            int i = 0;
            while (i < length) {
                if (!Objects.equals(this.pathParts[i], p.pathParts[i])) break;
                i++;
            }

            final int remaining = this.pathParts.length - i;
            if (remaining == 0 && i == p.pathParts.length) {
                return new ScriptPath(this.getFileSystem(), false);
            } else if (remaining == 0) {
                return p.subpath(i, p.getNameCount());
            } else {
                final String[] updots = IntStream.range(0, remaining).mapToObj(idx -> "..").toArray(String[]::new);
                if (i == p.pathParts.length) {
                    return new ScriptPath(this.getFileSystem(), false, updots);
                } else {
                    final ScriptPath subpath = p.subpath(i, p.getNameCount());
                    String[] mergedParts = new String[updots.length + subpath.pathParts.length];
                    System.arraycopy(updots, 0, mergedParts, 0, updots.length);
                    System.arraycopy(subpath.pathParts, 0, mergedParts, updots.length, subpath.pathParts.length);
                    return new ScriptPath(this.getFileSystem(), false, mergedParts);
                }
            }
        }
        throw new IllegalArgumentException("Wrong filesystem");
    }

    @Override
    public URI toUri() {
        try {
            return new URI(fileSystem.provider().getScheme(), null, toAbsolutePath().toString(), null);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Path toAbsolutePath() {
        return isAbsolute() ? this : fileSystem.getRoot().resolve(this);
    }

    @Override
    public Path toRealPath(final LinkOption... options) {
        return this.toAbsolutePath().normalize();
    }

    @Override
    public WatchKey register(final WatchService watcher, final WatchEvent.Kind<?>[] events, final WatchEvent.Modifier... modifiers) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(final Path other) {
        if (other instanceof ScriptPath path) {
            if (this.absolute && !path.absolute) return 1;
            else if (!this.absolute && path.absolute) return -1;
            else return Arrays.compare(this.pathParts, path.pathParts);
        } else {
            return 0;
        }
    }

    @Override
    public boolean equals(final Object o) {
        return o instanceof ScriptPath p &&
                p.getFileSystem() == this.getFileSystem() &&
                this.absolute == p.absolute && Arrays.equals(this.pathParts, p.pathParts);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.fileSystem) + 31 * Arrays.hashCode(this.pathParts);
    }

    @Override
    public String toString() {
        return (this.absolute ? "/" : "") + String.join("/", this.pathParts);
    }
}
