package FS;

import java.util.List;

public class INodeFile extends INodeWithAdditionalFields {
    String fileName;
    public String fileExtension;
    public long fileSize;
    List<String> blocks; //文件块列表

    public INodeFile(String name, String owner, int permission, long creationTime, long modificationTime, INodeDirectory parent, String fileName, String fileExtension, long fileSize, List<String> blocks) {
        super(name, owner, permission, creationTime, modificationTime, parent);
        this.fileName = fileName;
        this.fileExtension = fileExtension;
        this.fileSize = fileSize;
        this.blocks = blocks;
    }
}