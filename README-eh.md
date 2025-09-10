 [中文](README.md) | [English](README-eh.md)

# Metadata Query Optimization System

## 📖 Overview

This project proposes and implements an **efficient metadata query optimization system** for large-scale file systems.
 By introducing an **inverted index** outside the traditional directory tree, combined with **Varint compression**, **read-write locks**, and **thread-safe data structures**, the system significantly improves query performance and memory usage in high-concurrency environments.

## ✨ Features

- **Inverted Index for Fast Queries**: Quickly locate files by metadata without traversing the entire directory tree.
- **Varint Compression**: Reduces the storage footprint of inverted indexes.
- **High Concurrency Support**: Uses fine-grained read-write locks and `ConcurrentHashMap` to ensure data consistency and system performance.
- **Flexible Data Structures**: Supports both directory tree management and range-based indexing for various query types.
- **Validated by Experiments**: Proven effective in datasets with up to **250,000 files**.

## 🏗️ System Architecture

The system consists of two core modules:

1. **File System Management (FSDirectory)**
   - Manages directory tree structure
   - Supports create, update, delete, and search operations
2. **Inverted Index (InvertedIndex)**
   - Handles string and numeric metadata indexing
   - Uses sharding for efficient range queries
   - Optimized with Varint compression

![image-20250910112520598](./README-eh.assets/image-20250910112520598.png)

## 📂 Project Structure

```
├── src/
│   └── main/
│       └── java/
│           ├── FS/         # File system management classes / 文件系统管理相关类
│           ├── index/      # Inverted index classes / 倒排索引相关类
│           ├── utils/      # Utilities / 工具类
│           ├── TestFS.java # Test entry / 测试入口
│           └── readme.md   # Documentation / 说明文档
├── target/                 # Compiled output / 编译输出
├── pom.xml                 # Build config / 构建配置
├── README.md               # Project description / 项目说明
└── project-details.docx    # Project documentation / 项目文档
```

## ⚙️ Requirements

- **OS**: Ubuntu 20.04+ (recommended)
- **Language**: Java 11+
- **Hardware**: ≥16 GB RAM recommended

## 🚀 Quick Start

1. Clone the repository:

   ```bash
   git clone https://github.com/yangeh03/metadata-query-optimizer.git
   cd metadata-query-optimizer
   ```

2. Compile all Java files:

   ```bash
   javac -d out src/main/java/**/*.java
   ```

3. Run the main test class (includes all evaluations):

   ```bash
   java -cp out TestFS
   ```

4. Or use Maven to compile and run tests:

   ```bash
   mvn compile
   mvn test
   ```

## 📊 Experimental Results

- **Query Performance**: Inverted index achieves up to **tens of times faster queries** compared to directory tree traversal.
- **Memory Optimization**: Varint compression reduces index memory usage to **20%–25%** of the original.
- **Concurrency Performance**: Under 100K files, concurrent query latency is reduced by **over 10×**.