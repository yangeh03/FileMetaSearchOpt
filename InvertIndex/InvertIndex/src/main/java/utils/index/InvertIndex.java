package utils.index;

import FS.INodeFile;
import utils.Varint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class InvertIndex {
    private static final int SHARD_SIZE_THRESHOLD = 1000;
    private Map<String, Map<String, byte[]>> index;
    private Map<String, List<RangeShard>> rangeIndex;
    private final ReadWriteLock lock;

    public InvertIndex() {
        index = new HashMap<>();
        rangeIndex = new HashMap<>();
        lock = new ReentrantReadWriteLock();
        index.put("ext", new HashMap<>());
        index.put("owner", new HashMap<>());
        rangeIndex.put("size", new ArrayList<>());
        rangeIndex.put("creation", new ArrayList<>());
        rangeIndex.put("mod", new ArrayList<>());
    }

    public void addToIndex(INodeFile file) throws IOException {
        lock.writeLock().lock();
        try {
            addToIndex("ext", file.fileExtension, file.id);
            addToIndex("owner", file.owner, file.id);
            addToRangeIndex("size", file.fileSize, file.id);
            addToRangeIndex("creation", file.creationTime, file.id);
            addToRangeIndex("mod", file.modificationTime, file.id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removeFromIndex(INodeFile file) {
        lock.writeLock().lock();
        try {
            removeFromIndex("ext", file.fileExtension, file.id);
            removeFromIndex("owner", file.owner, file.id);
            removeFromRangeIndex("size", file.fileSize, file.id);
            removeFromRangeIndex("creation", file.creationTime, file.id);
            removeFromRangeIndex("mod", file.modificationTime, file.id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void addToIndex(String key, String value, int fileId) throws IOException {
        Map<String, byte[]> subIndex = index.get(key);
        byte[] existing = subIndex.get(value);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (existing != null) {
            out.write(existing);
        }
        Varint.writeUnsignedVarInt(fileId, out);
        subIndex.put(value, out.toByteArray());
    }

    private void removeFromIndex(String key, String value, int fileId) {
        Map<String, byte[]> subIndex = index.get(key);
        byte[] existing = subIndex.get(value);
        if (existing != null) {
            List<Integer> ids = decodeVarint(existing);
            ids.remove((Integer) fileId);
            subIndex.put(value, encodeVarint(ids));
        }
    }

    private List<Integer> decodeVarint(byte[] bytes) {
        List<Integer> ids = new ArrayList<>();
        int[] offset = {0};
        while (offset[0] < bytes.length) {
            ids.add(Varint.readUnsignedVarInt(bytes, offset));
        }
        return ids;
    }

    private byte[] encodeVarint(List<Integer> ids) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            for (int id : ids) {
                Varint.writeUnsignedVarInt(id, out);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return out.toByteArray();
    }

    private void addToRangeIndex(String key, long value, int fileId) {
        List<RangeShard> shards = rangeIndex.get(key);
        for (RangeShard shard : shards) {
            if (shard.contains(value)) {
                shard.add(value, fileId);
                if (shard.getSize() > SHARD_SIZE_THRESHOLD) {
                    RangeShard newShard = shard.split();
                    shards.add(newShard);
                }
                return;
            }
        }
        RangeShard newShard = new RangeShard(value, value + SHARD_SIZE_THRESHOLD);
        newShard.add(value, fileId);
        shards.add(newShard);
        mergeShards(shards);
    }

    private void removeFromRangeIndex(String key, long value, int fileId) {
        List<RangeShard> shards = rangeIndex.get(key);
        for (RangeShard shard : shards) {
            if (shard.contains(value)) {
                shard.remove(value, fileId);
                return;
            }
        }
    }

    private void mergeShards(List<RangeShard> shards) {
        shards.sort(Comparator.comparingLong(RangeShard::getStart));
        List<RangeShard> mergedShards = new ArrayList<>();
        RangeShard current = null;
        for (RangeShard shard : shards) {
            if (current == null) {
                current = shard;
            } else if (current.getEnd() + 1 >= shard.getStart()) {
                current.merge(shard);
            } else {
                mergedShards.add(current);
                current = shard;
            }
        }
        if (current != null) {
            mergedShards.add(current);
        }
        shards.clear();
        shards.addAll(mergedShards);
    }

    public List<Integer> search(String key, String value) {
        lock.readLock().lock();
        try {
            Map<String, byte[]> subIndex = index.get(key);
            byte[] data = subIndex.get(value);
            return data == null ? Collections.emptyList() : decodeVarint(data);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Integer> searchByRange(String key, long minValue, long maxValue) {
        lock.readLock().lock();
        try {
            List<Integer> results = new ArrayList<>();
            List<RangeShard> shards = rangeIndex.get(key);
            for (RangeShard shard : shards) {
                if (shard.overlaps(minValue, maxValue)) {
                    results.addAll(shard.search(minValue, maxValue));
                }
            }
            return results;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void printRangeShards(String key) {
        lock.readLock().lock();
        try {
            List<RangeShard> shards = rangeIndex.get(key);
            if (shards == null) {
                System.out.println("No range shards for key: " + key);
                return;
            }
            for (RangeShard shard : shards) {
                System.out.println("Range shard [" + shard.getStart() + ", " + shard.getEnd() + "], size: " + shard.getSize());
            }
        } finally {
            lock.readLock().unlock();
        }
    }
}