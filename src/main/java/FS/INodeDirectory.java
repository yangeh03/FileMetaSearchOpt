package FS;

import java.util.ArrayList;
import java.util.List;

public class INodeDirectory extends INodeWithAdditionalFields {
    List<INode> children;

    public INodeDirectory(String name, String owner, int permission, long creationTime, long modificationTime, INodeDirectory parent) {
        super(name, owner, permission, creationTime, modificationTime, parent);
        this.children = new ArrayList<>();
    }

    public boolean addChild(INodeWithAdditionalFields node) {
        if (findChild(node.name) != null) {
            return false; // 同一个目录中不能有相同名称的文件或目录
        }
        this.children.add(node);
        return true;
    }

    public boolean removeChild(String name) {
        INode child = findChild(name);
        if (child != null) {
            this.children.remove(child);
            return true;
        }
        return false;
    }

    public INode findChild(String name) {
        for (INode child : children) {
            //如果某个子节点是INodeWithAdditionalFields类型且其name属性与输入的name相同，则返回该子节点
            if (child instanceof INodeWithAdditionalFields && ((INodeWithAdditionalFields) child).name.equals(name)) {
                return child;
            }
        }
        return null;
    }

    public List<INode> getChildren() {
        return children;
    }
}
