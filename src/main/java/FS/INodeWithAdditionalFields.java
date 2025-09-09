package FS;

public class INodeWithAdditionalFields extends INode {
    String name;
    public String owner;
    int permission; //节点权限
    public long creationTime; // 节点创建时间
    public long modificationTime; //节点修改时间

    public INodeWithAdditionalFields(String name, String owner, int permission, long creationTime, long modificationTime, INodeDirectory parent) {
        super(parent);
        this.name = name;
        this.owner = owner;
        this.permission = permission;
        this.creationTime = creationTime;
        this.modificationTime = modificationTime;
    }
}