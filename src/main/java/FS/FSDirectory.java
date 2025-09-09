package FS;

import index.InvertIndex;
import utils.TimeRecorder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FSDirectory {
    public INodeDirectory rootDir;
    private final ReadWriteLock lock; // 读写锁

    public FSDirectory() {
        rootDir = new INodeDirectory("/", "root", 755, System.currentTimeMillis(), System.currentTimeMillis(), null);
        lock = new ReentrantReadWriteLock(); //读写锁
    }

    public boolean createFile(String path, String owner, int permission, String fileName, String fileExtension, long fileSize, List<String> blocks, InvertIndex invertedIndex) {
        lock.writeLock().lock();
        try {
            String[] parts = path.split("/"); // 分割路径
            INodeDirectory parent = rootDir;
            for (int i = 1; i < parts.length - 1; i++) {
                parent = findChildDirectory(parent, parts[i]); // 查找子目录
                if (parent == null) {
                    return false;
                }
            }
            String fileFullName = parts[parts.length - 1];    // 文件名
            if (parent != null && parent.findChild(fileFullName) == null) {
                INodeFile file = new INodeFile(fileFullName, owner, permission, System.currentTimeMillis(), System.currentTimeMillis(), parent, fileName, fileExtension, fileSize, blocks);
                if (parent.addChild(file)) {
                    invertedIndex.addToIndex(file); // 添加到倒排索引
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // 插入时记录时间
    public boolean createFile(String path, String owner, int permission, String fileName, String fileExtension, long fileSize, List<String> blocks, InvertIndex invertedIndex, TimeRecorder timeRecorder) {
        lock.writeLock().lock();
        try {
            String[] parts = path.split("/");
            INodeDirectory parent = rootDir;

            for (int i = 1; i < parts.length - 1; i++) {
                parent = findChildDirectory(parent, parts[i]);
                if (parent == null) {
                    return false;
                }
            }
            String fileFullName = parts[parts.length - 1];
            if (parent != null && parent.findChild(fileFullName) == null) {
                INodeFile file = new INodeFile(fileFullName, owner, permission, System.currentTimeMillis(), System.currentTimeMillis(), parent, fileName, fileExtension, fileSize, blocks);

                // 记录插入目录树的时间
                long startTime = System.nanoTime();
                boolean success = parent.addChild(file);
                long endTime = System.nanoTime();
                timeRecorder.directoryTreeTime = endTime - startTime;
                if (success) {
                    // 记录插入倒排索引的时间
                    startTime = System.nanoTime();
                    invertedIndex.addToIndex(file);
                    endTime = System.nanoTime();
                    timeRecorder.invertedIndexTime = endTime - startTime;
                }
                return success;
            }
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // 插入时不更新索引
    public boolean createFile(String path, String owner, int permission, String fileName, String fileExtension, long fileSize, List<String> blocks) {
        lock.writeLock().lock();
        try {
            String[] parts = path.split("/");
            INodeDirectory parent = rootDir;

            for (int i = 1; i < parts.length - 1; i++) {
                parent = findChildDirectory(parent, parts[i]);
                if (parent == null) {
                    return false;
                }
            }
            String fileFullName = parts[parts.length - 1];
            if (parent != null && parent.findChild(fileFullName) == null) {
                INodeFile file = new INodeFile(fileFullName, owner, permission, System.currentTimeMillis(), System.currentTimeMillis(), parent, fileName, fileExtension, fileSize, blocks);
                if (parent.addChild(file)) {
                    return true;
                }
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean createDirectory(String path, String owner, int permission) {
        lock.writeLock().lock();
        try {
            String[] parts = path.split("/"); // 分割路径
            INodeDirectory parent = rootDir;

            for (int i = 1; i < parts.length - 1; i++) { // 遍历路径
                parent = findChildDirectory(parent, parts[i]); // 查找子目录
                if (parent == null) {
                    return false;
                }
            }
            String dirName = parts[parts.length - 1];
            if (parent != null && parent.findChild(dirName) == null) {
                INodeDirectory dir = new INodeDirectory(dirName, owner, permission, System.currentTimeMillis(), System.currentTimeMillis(), parent);
                return parent.addChild(dir);
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // 删除结点
    public boolean deleteNode(String path, InvertIndex invertedIndex) {
        lock.writeLock().lock();
        try {
            String[] parts = path.split("/");
            INodeDirectory parent = rootDir;

            for (int i = 1; i < parts.length - 1; i++) {
                parent = findChildDirectory(parent, parts[i]);
                if (parent == null) {
                    return false;
                }
            }
            String nodeName = parts[parts.length - 1];
            INode node = parent.findChild(nodeName);
            if (node != null) {
                if (node instanceof INodeFile) {
                    invertedIndex.removeFromIndex((INodeFile) node);
                }
                return parent.removeChild(nodeName);
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // 更新文件
    public boolean updateFile(String path, String newFileName, String newFileExtension, long newFileSize, List<String> newBlocks, InvertIndex invertedIndex) {
        lock.writeLock().lock();
        try {
            String[] parts = path.split("/");
            INodeDirectory parent = rootDir;

            for (int i = 1; i < parts.length - 1; i++) {
                parent = findChildDirectory(parent, parts[i]);
                if (parent == null) {
                    return false;
                }
            }
            String fileFullName = parts[parts.length - 1];
            INode node = parent.findChild(fileFullName);
            if (node instanceof INodeFile) {
                INodeFile file = (INodeFile) node;
                //更新目录树和索引
                invertedIndex.removeFromIndex(file);
                file.fileName = newFileName;
                file.fileExtension = newFileExtension;
                file.fileSize = newFileSize;
                file.blocks = newBlocks;
                invertedIndex.addToIndex(file);
                return true;
            }
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // 列出目录下的文件
    public void listFiles(String path) {
        lock.readLock().lock();
        try {
            INodeDirectory dir = getDirectory(path);
            if (dir == null) {
                System.out.println("Invalid directory path");
                return;
            }
            for (INode child : dir.getChildren()) {
                if (child instanceof INodeWithAdditionalFields) {
                    System.out.println(((INodeWithAdditionalFields) child).name);
                }
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    // 查找子目录
    private INodeDirectory findChildDirectory(INodeDirectory dir, String name) {
        INode child = dir.findChild(name);
        if (child instanceof INodeDirectory) {
            return (INodeDirectory) child;
        }
        return null;
    }

    // 获取目录
    public INodeDirectory getDirectory(String path) {
        String[] parts = path.split("/");
        INodeDirectory current = rootDir;

        for (int i = 1; i < parts.length; i++) {
            current = findChildDirectory(current, parts[i]);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    // 显示文件信息
    public void showFileInfo(String path) {
        lock.readLock().lock();
        try {
            INode node = getNode(path);
            if (node == null) {
                System.out.println("Invalid file or directory path");
                return;
            }
            System.out.println("ID: " + node.id);
            if (node instanceof INodeWithAdditionalFields) {
                INodeWithAdditionalFields nodeWithFields = (INodeWithAdditionalFields) node;
                System.out.println("Name: " + nodeWithFields.name);
                System.out.println("Owner: " + nodeWithFields.owner);
                System.out.println("Permission: " + nodeWithFields.permission);
                if (node instanceof INodeFile) {
                    INodeFile file = (INodeFile) node;
                    System.out.println("File Name: " + file.fileName);
                    System.out.println("File Extension: " + file.fileExtension);
                    System.out.println("File Size: " + file.fileSize + " bytes");
                    System.out.println("Blocks: " + file.blocks);
                }
                if (node.parent != null) {
                    System.out.println("Parent Directory: " + node.parent.name);
                }
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    // 获取节点
    public INode getNode(String path) {
        String[] parts = path.split("/");
        INode current = rootDir;

        for (int i = 1; i < parts.length; i++) {
            if (current instanceof INodeDirectory) {
                current = ((INodeDirectory) current).findChild(parts[i]);
            } else {
                return null;
            }
        }
        return current;
    }

    // 搜索文件DFS
    public List<Integer> searchInTree(String metadata, String value) {
        lock.readLock().lock();
        try {
            List<Integer> results = new ArrayList<>();
            searchInTree(rootDir, metadata, value, results);
            return results;
        } finally {
            lock.readLock().unlock();
        }
    }

    private void searchInTree(INodeDirectory dir, String metadata, String value, List<Integer> results) {
        for (INode child : dir.getChildren()) {
            if (child instanceof INodeDirectory) {
                searchInTree((INodeDirectory) child, metadata, value, results);
            } else if (child instanceof INodeFile) {
                INodeFile file = (INodeFile) child;
                switch (metadata) {
                    case "ext":
                        if (file.fileExtension.equals(value)) {
                            results.add(file.id);
                        }
                        break;
                    case "owner":
                        if (file.owner.equals(value)) {
                            results.add(file.id);
                        }
                        break;
                }
            }
        }
    }

    // 范围搜索文件
    public List<Integer> searchInTreeRange(String metadata, long minValue, long maxValue) {
        lock.readLock().lock();
        try {
            List<Integer> results = new ArrayList<>();
            searchInTreeRange(rootDir, metadata, minValue, maxValue, results);
            return results;
        } finally {
            lock.readLock().unlock();
        }
    }

    private void searchInTreeRange(INodeDirectory dir, String metadata, long minValue, long maxValue, List<Integer> results) {
        for (INode child : dir.getChildren()) {
            if (child instanceof INodeDirectory) {
                searchInTreeRange((INodeDirectory) child, metadata, minValue, maxValue, results);
            } else if (child instanceof INodeFile) {
                INodeFile file = (INodeFile) child;
                switch (metadata) {
                    case "size":
                        if (file.fileSize >= minValue && file.fileSize <= maxValue) {
                            results.add(file.id);
                        }
                        break;
                    case "creation":
                        if (file.creationTime >= minValue && file.creationTime <= maxValue) {
                            results.add(file.id);
                        }
                        break;
                    case "mod":
                        if (file.modificationTime >= minValue && file.modificationTime <= maxValue) {
                            results.add(file.id);
                        }
                        break;
                }
            }
        }
    }

    // 建立倒排索引
    public void buildInvertedIndex(INodeDirectory dir, InvertIndex invertedIndex) {
        lock.readLock().lock();
        try {
            for (INode child : dir.getChildren()) { //DFS
                if (child instanceof INodeDirectory) {
                    buildInvertedIndex((INodeDirectory) child, invertedIndex);
                } else if (child instanceof INodeFile) {
                    invertedIndex.addToIndex((INodeFile) child);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            lock.readLock().unlock();
        }
    }
}
