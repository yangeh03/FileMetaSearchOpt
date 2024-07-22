package utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

// Varint 算法

public class Varint {
    // 将一个无符号的变长整数（unsigned varint）编码为字节数组，并写入到ByteArrayOutputStream中
    public static void writeUnsignedVarInt(int value, ByteArrayOutputStream out) throws IOException {
        while ((value & 0xFFFFFF80) != 0L) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value & 0x7F);
    }

    // 从字节数组中读取一个无符号的变长整数（unsigned varint）
    public static int readUnsignedVarInt(byte[] bytes, int[] offset) {
        int value = 0;
        int i = 0;
        int b;
        while (((b = bytes[offset[0]++]) & 0x80) != 0) {
            value |= (b & 0x7F) << i;
            i += 7;
        }
        return value | (b << i);
    }
}
