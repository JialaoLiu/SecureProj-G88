package devserver;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FileTransferManager {
    private static final long MAX_FILE_SIZE = 256 * 1024 * 1024; // 256MB
    private static final int MAX_CHUNK_SIZE = 512 * 1024; // 512KB
    private static final String UPLOADS_DIR = "./uploads";
    private static final String TMP_DIR = UPLOADS_DIR + "/tmp";
    private static final String FILES_DIR = UPLOADS_DIR + "/files";

    private final Map<String, FileMetadata> activeTransfers = new ConcurrentHashMap<>();

    static class FileMetadata {
        String fileId;
        String fileName;
        long totalSize;
        String expectedSha256;
        String mode;
        Set<Integer> receivedChunks;
        long startTime;

        FileMetadata(String fileId, String fileName, long totalSize, String sha256, String mode) {
            this.fileId = fileId;
            this.fileName = fileName;
            this.totalSize = totalSize;
            this.expectedSha256 = sha256;
            this.mode = mode;
            this.receivedChunks = ConcurrentHashMap.newKeySet();
            this.startTime = System.currentTimeMillis();
        }

        boolean isComplete(int totalChunks) {
            return receivedChunks.size() == totalChunks;
        }
    }

    public FileTransferManager() {
        initializeDirectories();
        startCleanupTask();
    }

    private void initializeDirectories() {
        try {
            Files.createDirectories(Paths.get(TMP_DIR));
            Files.createDirectories(Paths.get(FILES_DIR));
        } catch (IOException e) {
            System.err.println("Failed to create upload directories: " + e.getMessage());
        }
    }

    public String handleFileStart(String fileId, String fileName, long size, String sha256, String mode) {
        if (size > MAX_FILE_SIZE) {
            return "File too large. Maximum size: " + (MAX_FILE_SIZE / 1024 / 1024) + "MB";
        }

        if (fileName == null || fileName.trim().isEmpty()) {
            return "Invalid file name";
        }

        // Clean filename to prevent directory traversal
        String cleanFileName = Paths.get(fileName).getFileName().toString();

        FileMetadata metadata = new FileMetadata(fileId, cleanFileName, size, sha256, mode);
        activeTransfers.put(fileId, metadata);

        // Create temp directory for this file
        try {
            Files.createDirectories(Paths.get(TMP_DIR, fileId));
        } catch (IOException e) {
            activeTransfers.remove(fileId);
            return "Failed to create temp directory: " + e.getMessage();
        }

        System.out.println("[FileTransfer] Started: " + fileId + " (" + cleanFileName + ", " + size + " bytes)");
        return null; // Success
    }

    public String handleFileChunk(String fileId, int chunkIndex, String ciphertext) {
        FileMetadata metadata = activeTransfers.get(fileId);
        if (metadata == null) {
            return "File transfer not initialized for fileId: " + fileId;
        }

        try {
            // Decode base64 ciphertext
            byte[] chunkData = Base64.getDecoder().decode(ciphertext);

            if (chunkData.length > MAX_CHUNK_SIZE) {
                return "Chunk too large. Maximum size: " + (MAX_CHUNK_SIZE / 1024) + "KB";
            }

            // Check if chunk already received (resume support)
            if (metadata.receivedChunks.contains(chunkIndex)) {
                System.out.println("[FileTransfer] Chunk " + chunkIndex + " already received for " + fileId);
                return null; // Already have this chunk, skip
            }

            // Write chunk to temp file
            Path chunkFile = Paths.get(TMP_DIR, fileId, "chunk-" + chunkIndex);
            Files.write(chunkFile, chunkData);

            metadata.receivedChunks.add(chunkIndex);
            System.out.println("[FileTransfer] Chunk " + chunkIndex + " received for " + fileId +
                " (" + metadata.receivedChunks.size() + " chunks total)");

            return null; // Success
        } catch (IllegalArgumentException e) {
            return "Invalid base64 data in chunk";
        } catch (IOException e) {
            return "Failed to write chunk: " + e.getMessage();
        }
    }

    public String handleFileEnd(String fileId, int totalChunks) {
        FileMetadata metadata = activeTransfers.get(fileId);
        if (metadata == null) {
            return "File transfer not found for fileId: " + fileId;
        }

        try {
            if (!metadata.isComplete(totalChunks)) {
                return "Missing chunks. Expected: " + totalChunks + ", Received: " + metadata.receivedChunks.size();
            }

            // Merge chunks into final file
            Path finalFile = Paths.get(FILES_DIR, fileId + "-" + metadata.fileName);
            try (FileOutputStream fos = new FileOutputStream(finalFile.toFile())) {
                for (int i = 0; i < totalChunks; i++) {
                    Path chunkFile = Paths.get(TMP_DIR, fileId, "chunk-" + i);
                    if (!Files.exists(chunkFile)) {
                        throw new IOException("Missing chunk file: " + i);
                    }

                    byte[] chunkData = Files.readAllBytes(chunkFile);
                    fos.write(chunkData);
                }
            }

            // Verify file size
            long finalSize = Files.size(finalFile);
            if (finalSize != metadata.totalSize) {
                Files.deleteIfExists(finalFile);
                return "File size mismatch. Expected: " + metadata.totalSize + ", Got: " + finalSize;
            }

            // Optional: Verify SHA256 if provided
            if (metadata.expectedSha256 != null && !metadata.expectedSha256.isEmpty()) {
                String actualSha256 = calculateSha256(finalFile);
                if (!metadata.expectedSha256.equalsIgnoreCase(actualSha256)) {
                    Files.deleteIfExists(finalFile);
                    return "SHA256 checksum mismatch";
                }
            }

            // Clean up temp directory
            cleanupTempDir(fileId);
            activeTransfers.remove(fileId);

            System.out.println("[FileTransfer] Completed: " + fileId + " -> " + finalFile);
            return null; // Success

        } catch (IOException e) {
            return "Failed to finalize file: " + e.getMessage();
        }
    }

    private String calculateSha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];

            try (InputStream fis = Files.newInputStream(file)) {
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }

            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new IOException("Failed to calculate SHA256: " + e.getMessage());
        }
    }

    private void cleanupTempDir(String fileId) {
        try {
            Path tempDir = Paths.get(TMP_DIR, fileId);
            if (Files.exists(tempDir)) {
                Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            }
        } catch (IOException e) {
            System.err.println("Failed to cleanup temp dir for " + fileId + ": " + e.getMessage());
        }
    }

    private void startCleanupTask() {
        // Simple cleanup: remove transfers older than 1 hour
        Timer timer = new Timer("FileTransferCleanup", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                long cutoff = System.currentTimeMillis() - (60 * 60 * 1000); // 1 hour

                Iterator<Map.Entry<String, FileMetadata>> iter = activeTransfers.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<String, FileMetadata> entry = iter.next();
                    if (entry.getValue().startTime < cutoff) {
                        System.out.println("[FileTransfer] Cleaning up stale transfer: " + entry.getKey());
                        cleanupTempDir(entry.getKey());
                        iter.remove();
                    }
                }
            }
        }, 300000, 300000); // Run every 5 minutes
    }

    public Set<String> getActiveTransfers() {
        return new HashSet<>(activeTransfers.keySet());
    }

    public FileMetadata getTransferMetadata(String fileId) {
        return activeTransfers.get(fileId);
    }
}