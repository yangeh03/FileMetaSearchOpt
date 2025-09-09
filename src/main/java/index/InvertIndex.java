package index;

import FS.INodeFile;
import utils.Varint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class InvertIndex {
    private static final int SHARD_SIZE_THRESHOLD = 1000; // 分片大小阈值
    private Map<String, Map<String, byte[]>> index; // 基本倒排索引
    private Map<String, List<RangeShard>> rangeIndex; // 范围倒排索引
    private final ReadWriteLock lock; // 读写锁

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

    // 添加文件到索引
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

    // 从索引中删除文件
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

    // 添加文件到基本索引
    private void addToIndex(String key, String value, int fileId) throws IOException {
        Map<String, byte[]> subIndex = index.get(key);
        byte[] existing = subIndex.get(value); // 获取当前值对应的文件ID列表(byte类型）
        ByteArrayOutputStream out = new ByteArrayOutputStream(); // 创建字节输出流
        if (existing != null) {
            out.write(existing); // 将已有文件ID列表写入字节输出流
        }
        Varint.writeUnsignedVarInt(fileId, out); // 将新文件ID写入字节输出流
        subIndex.put(value, out.toByteArray()); // 将更新后的文件ID列表写入索引
    }

    // 从索引中删除文件
    private void removeFromIndex(String key, String value, int fileId) {
        Map<String, byte[]> subIndex = index.get(key);
        byte[] existing = subIndex.get(value);
        if (existing != null) {
            List<Integer> ids = decodeVarint(existing); // 将已有文件ID列表转换为整数列表
            ids.remove((Integer) fileId); // 删除指定文件ID
            subIndex.put(value, encodeVarint(ids)); // 将更新后的文件ID列表写入索引
        }
    }

    //解码
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
        return out.toByteArray();// ByteArrayOutputStream->字节数组
    }

    private void addToRangeIndex(String key, long value, int fileId) {
        List<RangeShard> shards = rangeIndex.get(key);
        for (RangeShard shard : shards) { //遍历分片 存在则添加分片
            if (shard.contains(value)) {
                shard.add(value, fileId);
                if (shard.getSize() > SHARD_SIZE_THRESHOLD) { //片的大小大于阈值则分片
                    RangeShard newShard = shard.split();
                    shards.add(newShard);
                }
                return;
            }
        }
        // 不存在范围内则分片
        RangeShard newShard = new RangeShard(value, value + SHARD_SIZE_THRESHOLD);
        newShard.add(value, fileId);
        shards.add(newShard);
        mergeShards(shards); // 合并分片
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
        shards.sort(Comparator.comparingLong(RangeShard::getStart)); //片升序
        List<RangeShard> mergedShards = new ArrayList<>();
        RangeShard current = null;
        for (RangeShard shard : shards) {
            if (current == null) {
                current = shard;
            } else if (current.getEnd() + 1 >= shard.getStart()) { //当前片和下一个片重叠
                current.merge(shard);
            } else {
                mergedShards.add(current);
                current = shard;
            }
        }
        if (current != null) {
            mergedShards.add(current);
        }
        //更新shards集合
        shards.clear();
        shards.addAll(mergedShards);
    }

    public List<Integer> search(String key, String value) {
        lock.readLock().lock();
        try {
            Map<String, byte[]> subIndex = index.get(key);
            byte[] data = subIndex.get(value); // 获取当前值对应的文件ID列表(byte类型）
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
            List<RangeShard> shards = rangeIndex.get(key); //获取指定元数据的分片列表
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