package com.codeflow.parser;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Consumer;

public class FileWatcher implements Runnable {

    private final Path rootDirectory;
    private final Consumer<Path> onChange;
    private volatile boolean running = true;
    private WatchService watcher;

    public FileWatcher(Path rootDirectory, Consumer<Path> onChange) {
        this.rootDirectory = rootDirectory;
        this.onChange = onChange;
    }

    public void stop() {
        running = false;
        if (watcher != null) {
            try { watcher.close(); } catch (IOException ignored) {}
        }
    }

    @Override
    public void run() {
        try {
            watcher = FileSystems.getDefault().newWatchService();
            registerRecursive(rootDirectory);

            while (running) {
                WatchKey key;
                try {
                    key = watcher.poll(1, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (ClosedWatchServiceException e) {
                    return;
                }
                if (key == null) continue;

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path dir = (Path) key.watchable();
                    Path changed = dir.resolve(pathEvent.context());

                    if (Files.isDirectory(changed) && event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        registerRecursive(changed);
                    }

                    if (changed.toString().endsWith(".java")) {
                        onChange.accept(changed);
                    }
                }
                key.reset();
            }
        } catch (IOException e) {
            if (running) e.printStackTrace();
        }
    }

    private void registerRecursive(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                dir.register(watcher,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
