import FS.FSDirectory;
import index.InvertIndex;
import utils.TimeRecorder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class TestFS {
    private FSDirectory fsDirectory;
    private InvertIndex invertedIndex;
    private Runtime runtime;
    private Random random;

    public TestFS() {
        this.fsDirectory = new FSDirectory();
        this.invertedIndex = new InvertIndex();
        this.runtime = Runtime.getRuntime();
        this.random = new Random();
    }

    // 生成测试数据
    public void generateTestData(int totalFiles) {
        String[] owners = {"owner1", "owner2", "owner3", "owner4", "owner5"};
        String[] extensions = {"txt", "jpg", "png", "doc", "pdf"};
        int maxDirs = totalFiles / 10; // 设定每个文件夹内平均文件数量为10
        List<String> createdDirs = new ArrayList<>(); //所有文件夹的路径
        createdDirs.add("/"); // 根目录

        // 先创建所有的文件夹 编号从dir0开始
        for (int i = 0; i <= maxDirs; i++) {
            String parentDir = createdDirs.get(random.nextInt(createdDirs.size())); //从已创建的文件夹中随机选择一个
            String dirPath = parentDir + "dir" + i;
            fsDirectory.createDirectory(dirPath, "owner" + random.nextInt(owners.length + 1), 755);
            createdDirs.add(dirPath); // 添加到已创建的文件夹
        }

        // 创建所有文件 从dir1开始
        for (int i = 1; i <= totalFiles; i++) {
            String parentDir = createdDirs.get(random.nextInt(createdDirs.size())); //随机选一个文件夹
            String filePath = parentDir + "/file" + i + "." + extensions[random.nextInt(extensions.length)];
            String owner = owners[random.nextInt(owners.length)];
            long fileSize = 100 + random.nextInt(10000); // 随机文件大小
            List<String> blocks = new ArrayList<>();
            for (int k = 0; k < 3; k++) {
                blocks.add("block" + k);
            }
            fsDirectory.createFile(filePath, owner, 644, "file" + i, extensions[random.nextInt(extensions.length)], fileSize, blocks);
        }

        // 测试文件 位于dir0
        String testDirPath = "/dir0";
        String testFilePath1 = testDirPath + "/file1.txt";
        String testFilePath2 = testDirPath + "/file2.txt";
        List<String> blocks = new ArrayList<>();
        for (int k = 0; k < 3; k++) {
            blocks.add("block" + k);
        }
        fsDirectory.createFile(testFilePath1, "owner1", 644, "file1", "txt", 100 + random.nextInt(10000), blocks);
        fsDirectory.createFile(testFilePath2, "owner1", 644, "file2", "txt", 100 + random.nextInt(10000), blocks);
    }

    //评估内存占用
    public void evaluateMemoryUsage(int totalFiles) {
        runtime.gc(); //回收不再使用的对象所占用的内存空间
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        generateTestData(totalFiles);

        runtime.gc();
        long memoryAfterDataGeneration = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsedForData = memoryAfterDataGeneration - initialMemory;
        System.out.println("Memory used for data generation: " + memoryUsedForData + " B");

        runtime.gc();
        long memoryBeforeIndex = runtime.totalMemory() - runtime.freeMemory();
        fsDirectory.buildInvertedIndex(fsDirectory.rootDir, invertedIndex);
        runtime.gc();
        long memoryAfterIndex = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsedForIndex = memoryAfterIndex - memoryBeforeIndex;
        System.out.println("Memory used for building index: " + memoryUsedForIndex + " B");
    }

    public void evaluateSearchPerformance(String searchMetadata, String searchValue) {
        long startTime = System.nanoTime(); //计时
        // 目录树DFS搜索
        List<Integer> treeResults = fsDirectory.searchInTree(searchMetadata, searchValue);
        long endTime = System.nanoTime();
        System.out.println("Tree search time: " + (endTime - startTime) / 1000000.0 + " ms");
        System.out.println("Tree search results count: " + treeResults.size());

        startTime = System.nanoTime();
        // 倒排索引搜索
        List<Integer> treeIndexResults = invertedIndex.search(searchMetadata, searchValue);
        endTime = System.nanoTime();
        System.out.println("Inverted index search time: " + (endTime - startTime) / 1000000.0 + " ms");
        System.out.println("Inverted index search results count: " + treeIndexResults.size());
    }

    public void evaluateRangeSearchPerformance(String searchMetadata, long minValue, long maxValue) {
        System.out.println("Current range shards for " + searchMetadata + ":");
        invertedIndex.printRangeShards(searchMetadata); // 显示分片情况

        long startTime = System.nanoTime();
        List<Integer> treeResults = fsDirectory.searchInTreeRange(searchMetadata, minValue, maxValue);
        long endTime = System.nanoTime();
        System.out.println("Tree search by range time: " + (endTime - startTime) / 1000000.0 + " ms");
        System.out.println("Tree search by range results count: " + treeResults.size());

        startTime = System.nanoTime();
        List<Integer> indexResults = invertedIndex.searchByRange(searchMetadata, minValue, maxValue);
        endTime = System.nanoTime();
        System.out.println("Inverted index search by range time: " + (endTime - startTime) / 1000000.0 + " ms");
        System.out.println("Inverted index search by range results count: " + indexResults.size());
    }

    public void evaluateInsertPerformance(int bulkInsertCount) {
        String dirPath = "/dir0";
        String fileName = "file_new";
        String fileExtension = "txt";
        String owner = "owner1";
        long fileSize = 100 + random.nextInt(10000);
        List<String> blocks = new ArrayList<>();
        for (int k = 0; k < 3; k++) {
            blocks.add("block" + k);
        }

        // 单个文件插入测试
        TimeRecorder singleInsertTimeRecorder = new TimeRecorder(); //记录创建文件过程中更新索引的时间消耗
        boolean singleInsertSuccess = fsDirectory.createFile(dirPath + "/" + fileName + "." + fileExtension, owner, 644, fileName, fileExtension, fileSize, blocks, invertedIndex, singleInsertTimeRecorder);
        System.out.println("Single file insert result: " + (singleInsertSuccess ? "Success" : "Failure"));
        System.out.println("Insert to directory tree time: " + singleInsertTimeRecorder.directoryTreeTime / 1000000.0 + " ms");
        System.out.println("Insert to inverted index time: " + singleInsertTimeRecorder.invertedIndexTime / 1000000.0 + " ms");

        // 大批量文件插入测试
        long totalDirectoryTreeTime = 0;
        long totalInvertedIndexTime = 0;
        for (int i = 0; i < bulkInsertCount; i++) {
            String bulkFileName = "bulk_file" + i;
            TimeRecorder bulkInsertTimeRecorder = new TimeRecorder();
            boolean bulkInsertSuccess = fsDirectory.createFile(dirPath + "/" + bulkFileName + "." + fileExtension, owner, 644, bulkFileName, fileExtension, fileSize, blocks, invertedIndex, bulkInsertTimeRecorder);
            if (bulkInsertSuccess) {
                totalDirectoryTreeTime += bulkInsertTimeRecorder.directoryTreeTime;
                totalInvertedIndexTime += bulkInsertTimeRecorder.invertedIndexTime;
            }
        }
        System.out.println("Bulk file insert count: " + bulkInsertCount);
        System.out.println("Total bulk file insert to directory tree time: " + totalDirectoryTreeTime / 1000000.0 + " ms");
        System.out.println("Total bulk file insert to inverted index time: " + totalInvertedIndexTime / 1000000.0 + " ms");
    }

    //并发搜索性能评估
    public void evaluateConcurrentSearchPerformance(int numQueries) throws InterruptedException {
        String searchMetadata = "ext";
        String searchValue = "txt";
        long minValue = 600;
        long maxValue = 8000;

        // 使用 CountDownLatch 进行同步
        CountDownLatch latch = new CountDownLatch(numQueries * 4); // 两种查询类型，每种类型两种方式
        //线程池
        ExecutorService executor = Executors.newFixedThreadPool(numQueries * 4);

        // 精确查询
        long[] totalTreeSearchTime = {0}; //多个线程中对一个基本类型变量进行同步访问，需要将其包装在一个可变对象中
        long[] totalIndexSearchTime = {0};

        for (int i = 0; i < numQueries; i++) {
            executor.execute(() -> {
                long startTime = System.nanoTime();
                List<Integer> treeResults = fsDirectory.searchInTree(searchMetadata, searchValue);
                long endTime = System.nanoTime();
                synchronized (totalTreeSearchTime) {
                    totalTreeSearchTime[0] += (endTime - startTime);
                }
                latch.countDown(); //计数器减1操作。当计数器减到0时，表示所有线程已经完成任务，等待的线程可以继续执行
            });

            executor.execute(() -> {
                long startTime = System.nanoTime();
                List<Integer> indexResults = invertedIndex.search(searchMetadata, searchValue);
                long endTime = System.nanoTime();
                synchronized (totalIndexSearchTime) {
                    totalIndexSearchTime[0] += (endTime - startTime);
                }
                latch.countDown();
            });
        }

        // 范围查询
        long[] totalTreeRangeSearchTime = {0};
        long[] totalIndexRangeSearchTime = {0};

        for (int i = 0; i < numQueries; i++) {
            executor.execute(() -> {
                long startTime = System.nanoTime();
                List<Integer> treeResults = fsDirectory.searchInTreeRange("size", minValue, maxValue);
                long endTime = System.nanoTime();
                synchronized (totalTreeRangeSearchTime) {
                    totalTreeRangeSearchTime[0] += (endTime - startTime);
                }
                latch.countDown();
            });

            executor.execute(() -> {
                long startTime = System.nanoTime();
                List<Integer> indexResults = invertedIndex.searchByRange("size", minValue, maxValue);
                long endTime = System.nanoTime();
                synchronized (totalIndexRangeSearchTime) {
                    totalIndexRangeSearchTime[0] += (endTime - startTime);
                }
                latch.countDown();
            });
        }

        latch.await(); //主线程阻塞
        executor.shutdown();

        System.out.println("Average tree search time (exact): " + (totalTreeSearchTime[0] / numQueries) / 1000000.0 + " ms");
        System.out.println("Average inverted index search time (exact): " + (totalIndexSearchTime[0] / numQueries) / 1000000.0 + " ms");
        System.out.println("Average tree search time (range): " + (totalTreeRangeSearchTime[0] / numQueries) / 1000000.0 + " ms");
        System.out.println("Average inverted index search time (range): " + (totalIndexRangeSearchTime[0] / numQueries) / 1000000.0 + " ms");
    }

    //并发读写性能评估
    public void testConcurrentReadWrite(int numReaders, int numWriters, int numAdders, double durationSeconds) throws InterruptedException, IOException {
        ExecutorService executor = Executors.newFixedThreadPool(numReaders + numWriters + numAdders);
        Random rand = new Random();

        // 使用第一个测试文件的路径
        String testFilePath = "/dir0/file1.txt";

        // 创建并发写线程
        for (int i = 0; i < numWriters; i++) {
            executor.execute(() -> {
                String[] extensions = {"txt", "jpg"};
                while (!Thread.currentThread().isInterrupted()) {
                    String fileName = "file1";
                    String fileExtension = extensions[rand.nextInt(extensions.length)];
                    String owner = "owner1";
                    long fileSize = 100 + rand.nextInt(10000);
                    List<String> blocks = new ArrayList<>();
                    for (int k = 0; k < 3; k++) {
                        blocks.add("block" + k);
                    }
                    boolean success = fsDirectory.updateFile(testFilePath, fileName, fileExtension, fileSize, blocks, invertedIndex);
                    if (success) {
                        System.out.println("Writer thread " + Thread.currentThread().getId() + " updated file " + testFilePath + " ext: " + fileExtension);
                    } else {
                        System.out.println("Writer thread " + Thread.currentThread().getId() + " failed to update file " + testFilePath);
                    }
                    // 阻塞随机时间
                    try {
                        Thread.sleep(rand.nextInt(1500));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }

        // 创建并发读线程
        for (int i = 0; i < numReaders; i++) {
            executor.execute(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    String searchMetadata = "ext";
                    String searchValue = "txt";
                    List<Integer> results = invertedIndex.search(searchMetadata, searchValue);
                    System.out.println("Reader thread " + Thread.currentThread().getId() + " found " + results.size() + " files with extension " + searchValue);
                    // 阻塞随机时间
                    try {
                        Thread.sleep(rand.nextInt(500));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }

        // 创建并发新增文件线程
        for (int i = 0; i < numAdders; i++) {
            executor.execute(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    String dirPath = "/dir0";
                    String fileName = "file" + rand.nextInt(100);
                    String fileExtension = "txt";
                    String owner = "owner1";
                    long fileSize = 100 + rand.nextInt(10000);
                    List<String> blocks = new ArrayList<>();
                    for (int k = 0; k < 3; k++) {
                        blocks.add("block" + k);
                    }
                    boolean success;
                    success = fsDirectory.createFile(dirPath + "/" + fileName + "." + fileExtension, owner, 644, fileName, fileExtension, fileSize, blocks,invertedIndex);
                    if (success) {
                        System.out.println("Adder thread " + Thread.currentThread().getId() + " created file " + dirPath + "/" + fileName + "." + fileExtension);
                    } else {
                        System.out.println("Adder thread " + Thread.currentThread().getId() + " failed to create file " + dirPath + "/" + fileName + "." + fileExtension);
                    }
                    // 阻塞随机时间
                    try {
                        Thread.sleep(rand.nextInt(1500));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }

        // 运行指定时间
        Thread.sleep((long) (durationSeconds * 1000));

        // 终止所有线程
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS); //等待Executor服务终止运行
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        TestFS testFS = new TestFS();

        // 内存使用评估
        testFS.evaluateMemoryUsage(20000);

        // 精确查询性能评估
        testFS.evaluateSearchPerformance("ext", "txt");

        // 范围查询性能测试
        testFS.evaluateRangeSearchPerformance("size", 600, 8000);

        // 写入文件的时间消耗评估
        testFS.evaluateInsertPerformance(1000);

        // 并发查询性能测试
        testFS.evaluateConcurrentSearchPerformance(100);

        // 并发读写测试
        testFS.testConcurrentReadWrite(5, 5, 5, 10);
    }
}
