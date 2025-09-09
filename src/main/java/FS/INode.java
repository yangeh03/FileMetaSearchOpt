package FS;

import java.util.concurrent.atomic.AtomicInteger;

public class INode {
    static final AtomicInteger idGenerator = new AtomicInteger();
    public int id;
    INodeDirectory parent;

    public INode(INodeDirectory parent) {
        this.id = idGenerator.incrementAndGet(); //获取一个自增的唯一标识
        this.parent = parent;
    }
}
