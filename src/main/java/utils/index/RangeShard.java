package utils.index;

import utils.Varint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

class RangeShard {
    private long start;
    private long end;
    private NavigableMap<Long, byte[]> index;

    public RangeShard(long start, long end) {
        this.start = start;
        this.end = end;
        this.index = new TreeMap<>();
    }

    public boolean contains(long value) {
        return value >= start && value <= end;
    }

    public boolean overlaps(long minValue, long maxValue) {
        return minValue <= end && maxValue >= start;
    }

    public void add(long value, int fileId) {
        byte[] existing = index.get(value);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (existing != null) {
                out.write(existing);
            }
            Varint.writeUnsignedVarInt(fileId, out);
        } catch (IOException e) {
            e.printStackTrace();
        }
        index.put(value, out.toByteArray());
    }

    public void remove(long value, int fileId) {
        byte[] existing = index.get(value);
        if (existing != null) {
            List<Integer> ids = decodeVarint(existing);
            ids.remove((Integer) fileId);
            if (ids.isEmpty()) {
                index.remove(value);
            } else {
                index.put(value, encodeVarint(ids));
            }
        }
    }

    public List<Integer> search(long minValue, long maxValue) {
        List<Integer> results = new ArrayList<>();
        for (Map.Entry<Long, byte[]> entry : index.subMap(minValue, true, maxValue, true).entrySet()) {
            results.addAll(decodeVarint(entry.getValue()));
        }
        return results;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }

    public void setEnd(long end) {
        this.end = end;
    }

    public NavigableMap<Long, byte[]> getIndex() {
        return index;
    }

    public int getSize() {
        return index.size();
    }

    public void merge(RangeShard other) {
        for (Map.Entry<Long, byte[]> entry : other.getIndex().entrySet()) {
            List<Integer> ids = decodeVarint(entry.getValue());
            for (int fileId : ids) {
                this.add(entry.getKey(), fileId);
            }
        }
        this.end = other.getEnd();
    }

    public RangeShard split() {
        long middle = (start + end) / 2;
        RangeShard newShard = new RangeShard(middle + 1, end);
        this.end = middle;

        for (Iterator<Map.Entry<Long, byte[]>> it = index.tailMap(middle + 1).entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, byte[]> entry = it.next();
            List<Integer> ids = decodeVarint(entry.getValue());
            for (int fileId : ids) {
                newShard.add(entry.getKey(), fileId);
            }
            it.remove();
        }

        return newShard;
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
}
