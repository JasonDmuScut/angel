/*
 * Tencent is pleased to support the open source community by making Angel available.
 *
 * Copyright (C) 2017 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.angel.master;

import com.tencent.angel.exception.InvalidParameterException;
import com.tencent.angel.protobuf.generated.MLProtos;
import com.tencent.angel.protobuf.generated.MLProtos.MatrixPartitionLocation;
import com.tencent.angel.protobuf.generated.MLProtos.MatrixProto;
import com.tencent.angel.protobuf.generated.MLProtos.Partition;
import com.tencent.angel.protobuf.generated.PSMasterServiceProtos.MatrixPartition;
import com.tencent.angel.protobuf.generated.PSMasterServiceProtos.MatrixReport;
import com.tencent.angel.ps.ParameterServerId;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * matrix meta manager in angel application master. it contains all matrices meta and partitions for each parameter server hold
 * TODO: there are two ways to create matrix: client initialization and worker dynamic creation. We need to unify the two ways in the future
 */
public class MatrixMetaManager {
  private static final Log LOG = LogFactory.getLog(MatrixMetaManager.class);
  /** matrix inited flag, worker/ps can get matrix info. only when matrix is initialized after am received matrixinfo from client */
  private boolean matrixInited;

  /**matrix id to matrix meta proto map*/
  private final Int2ObjectOpenHashMap<MatrixProto> matrixProtoMap;

  /** inverted index, psId--->Map( matrixId---->List<PartitionKey>), used for PS */
  private final Object2ObjectOpenHashMap<ParameterServerId, Int2ObjectOpenHashMap<MatrixPartition>> matrixPartitionOnPS;

  /**uninited matrix id to matrix meta proto map*/
  private final Int2ObjectOpenHashMap<MatrixProto> uninitedMatrixProtoMap;
  
  /**matrix id generator*/
  private int maxMatrixId = -1;
  
  /**matrix name to id map*/
  private final Object2IntOpenHashMap<String> matrixNameToIdMap;
  
  /**matrix id to psId which has build partitions of this matrix map, use to add matrix dynamic*/
  private final Int2ObjectOpenHashMap<ObjectOpenHashSet<ParameterServerId>> matrixIdToInitedPSSetMap;
  
  /**matrix id to the number of ps which contains partitions of this matrix map*/
  private final Int2IntOpenHashMap matrixIdToPSNumMap;
  
  private final Lock readLock;
  private final Lock writeLock;

  public MatrixMetaManager() {
    matrixPartitionOnPS = new Object2ObjectOpenHashMap<ParameterServerId, Int2ObjectOpenHashMap<MatrixPartition>>();
    matrixProtoMap = new Int2ObjectOpenHashMap<MatrixProto>();

    uninitedMatrixProtoMap = new Int2ObjectOpenHashMap<MatrixProto>();
    matrixIdToInitedPSSetMap = new Int2ObjectOpenHashMap<ObjectOpenHashSet<ParameterServerId>>();
    matrixIdToPSNumMap = new Int2IntOpenHashMap();
    matrixNameToIdMap = new Object2IntOpenHashMap<String>();

    ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    readLock = readWriteLock.readLock();
    writeLock = readWriteLock.writeLock();
    matrixInited = false;
  }

  /**
   * get all matrices meta proto. 
   * @return List<MatrixProto> all matrices meta proto list
   */
  public final List<MatrixProto> getMatrixProtos() {
    try {
      readLock.lock();
      //if matrices meta are not received from client, just return null
      if (!matrixInited) {
        return null;
      }
      
      List<MatrixProto> matrixes = new ArrayList<MatrixProto>();
      for (Entry<Integer, MatrixProto> entry : matrixProtoMap.entrySet()) {
        matrixes.add(entry.getValue());
      }
      return matrixes;
    } finally {
      readLock.unlock();
    }
  }
  
  /**
   * get matrix meta proto use matrix name
   * @param matrixName matrix name
   * @return MatrixProto matrix meta proto of the matrix, if not found, just return null
   */
  public MatrixProto getMatrix(String matrixName) {
    try {
      readLock.lock();
      for (MatrixProto m : matrixProtoMap.values()) {
        if (m.getName().equals(matrixName)) {
          return m;
        }
      }
      return null;
    } finally {
      readLock.unlock();
    }
  }

  /**
   * get matrix meta proto use matrix id
   * @param matrixName matrix name
   * @return MatrixProto matrix meta proto of the matrix
   */
  public MatrixProto getMatrix(int id) {
    try {
      readLock.lock();
      return matrixProtoMap.get(id);
    } finally {
      readLock.unlock();
    }
  }

  /**
   * get partitions of a specific parameter server hold
   * @param psId, parameter server id
   * @return List<MatrixPartition> the partitions of the parameter server hold
   */

  public final List<MatrixPartition> getMatrixPartitions(ParameterServerId psId) {
    try {
      readLock.lock();
      if (!matrixInited) {
        return null;
      }

      Map<Integer, MatrixPartition> mpMap = matrixPartitionOnPS.get(psId);
      if (mpMap == null) {
        LOG.info("psId: " + psId + ", mpMap null ");
        return null;
      }

      List<MatrixPartition> mpList = new ArrayList<MatrixPartition>(mpMap.size());
      for (Map.Entry<Integer, MatrixPartition> entry : mpMap.entrySet()) {
        LOG.info("MatrixPartition is " + entry.getValue());
        mpList.add(entry.getValue());
      }
      return mpList;
    } finally {
      readLock.unlock();
    }
  }

  public boolean isMatrixInited() {
    try {
      readLock.lock();
      return matrixInited;
    } finally {
      readLock.unlock();
    }
  }

  /**
   * add matrices meta to matrix meta manager and dispatch the partitions to parameter servers  
   * @param matrixProtos matrices meta 
   * @throws InvalidParameterException 
   */
  public void addMatrices(List<MatrixProto> matrixProtos) throws InvalidParameterException {
    try {
      writeLock.lock();
      int size = matrixProtos.size();
      for (int i = 0; i < size; i++) {
        //check whether the matrix name conflicts with the existing matrix names, the matrix name must be only
        if(matrixNameToIdMap.containsKey(matrixProtos.get(i).getName())) {
          String errorMsg = "build matrix failed. matrix name " + matrixProtos.get(i).getName() + " has exist, you must choose a new one";
          LOG.error(errorMsg);
          throw new InvalidParameterException(errorMsg);
        } else {
          matrixNameToIdMap.put(matrixProtos.get(i).getName(), matrixProtos.get(i).getId());
        }

        LOG.info("start building MatrixPartition info. matrix id " + matrixProtos.get(i).getId());
        matrixProtoMap.put(matrixProtos.get(i).getId(), matrixProtos.get(i));
        
        //dispatch matrix partitions to parameter servers
        buildPSMatrixInvertInfo(matrixProtos.get(i));
        
        //update matrix id generator
        updateMaxMatrixId(matrixProtos.get(i).getId());
      }
      matrixInited = true;
    } finally {
      writeLock.unlock();
    }
  }
  
  /**
   * add matrix dynamic
   * @param matrixProto matrix meta proto
   * @return int matrix id
   * @throws InvalidParameterException 
   */
  public int addMatrix(MatrixProto matrixProto) throws InvalidParameterException {
    try {
      writeLock.lock();

      int matrixId = assignMatrixId();
      LOG.info("start building MatrixPartition info, matrix id " + matrixId);

      //check whether the matrix name conflicts with the existing matrix names, the matrix name must be only
      if(matrixNameToIdMap.containsKey(matrixProto.getName())) {
        String errorMsg = "build matrix failed. matrix name " + matrixProto.getName() + " has exist, you must choose a new one";
        LOG.error(errorMsg);
        throw new InvalidParameterException(errorMsg);
      } else {
        matrixNameToIdMap.put(matrixProto.getName(), matrixId);
      }
      
      //put matrix meta to uninitedMatrixProtoMap as we need to wait for all ps to initialize this matrix
      uninitedMatrixProtoMap.put(matrixProto.getId(), matrixProto);
      
      //dispatch matrix partitions to parameter servers
      buildPSMatrixInvertInfo(matrixProto);
      return matrixId;
    } finally {
      writeLock.unlock();
    }
  }

  /**
   * dispatch matrix partitions to parameter servers 
   * @param matrixProto  matrix meta proto
   */
  private void buildPSMatrixInvertInfo(MatrixProto matrixProto) {
    LOG.debug("matrixName: " + matrixProto.getName() + ", matrixParititionsNum: "
        + matrixProto.getMatrixPartLocationCount());
    List<MatrixPartitionLocation> matrixPartProtoList = matrixProto.getMatrixPartLocationList();

    int matrixId = matrixProto.getId();
    IntOpenHashSet psIdSet = new IntOpenHashSet();

    Int2ObjectOpenHashMap<MatrixPartition.Builder> builders =
        new Int2ObjectOpenHashMap<MatrixPartition.Builder>();

    List<MLProtos.Pair> pairs = matrixProto.getAttributeList();

    for (MatrixPartitionLocation matrixPartLocProto : matrixPartProtoList) {
      Partition part = matrixPartLocProto.getPart();
      int psId = matrixPartLocProto.getPsId().getPsIndex();
      psIdSet.add(psId);

      MatrixPartition.Builder mpBuilder = builders.get(psId);
      if (mpBuilder == null) {
        mpBuilder = MatrixPartition.newBuilder();
        builders.put(psId, mpBuilder);
        mpBuilder.setMatrixId(matrixId);
        mpBuilder.setMatrixName(matrixProto.getName());
        mpBuilder.setRowNum(matrixProto.getRowNum());
        mpBuilder.setColNum(matrixProto.getColNum());
        mpBuilder.setRowType(matrixProto.getRowType());
        for (MLProtos.Pair pair : pairs) {
          mpBuilder.addConfigurations(pair);
        }
      }

      mpBuilder.addPartitions(part);
    }

    for (Entry<Integer, MatrixPartition.Builder> builderEntry : builders.entrySet()) {
      Int2ObjectOpenHashMap<MatrixPartition> matrixPartMap =
          matrixPartitionOnPS.get(new ParameterServerId(builderEntry.getKey()));
      if (matrixPartMap == null) {
        matrixPartMap = new Int2ObjectOpenHashMap<MatrixPartition>();
        matrixPartitionOnPS.put(new ParameterServerId(builderEntry.getKey()), matrixPartMap);
      }
      
      matrixPartMap.put(builderEntry.getValue().getMatrixId(), builderEntry.getValue().build());
      LOG.info("ps index: " + builderEntry.getKey() + ", matrixPartition info(matrixId: " + matrixId
          + ")");
    }

    //use to record how many ps has completed the initialization of the matrix.
    matrixIdToPSNumMap.put(matrixId, psIdSet.size());
    matrixIdToInitedPSSetMap.put(matrixId, new ObjectOpenHashSet<ParameterServerId>());
  }

  private void updateMaxMatrixId(int id) {
    if (maxMatrixId < id) {
      maxMatrixId = id;
    }
    LOG.debug("update maxMatrixId  to " + maxMatrixId);
  }

  private int assignMatrixId() {
    return ++maxMatrixId;
  }

  /**
   * compare the matrix meta on the master and the matrix meta on ps to find the matrix this parameter server needs to create and delete
   * @param matrixReports parameter server matrix report, include the matrix ids this parameter server hold.
   * @param needCreateMatrixes use to return the matrix partitions this parameter server need to build
   * @param needReleaseMatrixes use to return the matrix ids this parameter server need to remove
   * @param psId parameter server id
  */
  public void syncMatrixInfos(List<MatrixReport> matrixReports,
      List<MatrixPartition> needCreateMatrixes, List<Integer> needReleaseMatrixes, ParameterServerId psId) {
    //get matrix ids in the parameter server report
    IntOpenHashSet matrixInPS = new IntOpenHashSet();
    int size = matrixReports.size();
    for (int i = 0; i < size; i++) {
      matrixInPS.add(matrixReports.get(i).getMatrixId());
    }

    //update matrixIdToInitedPSSetMap
    updateInitedMatrix(psId, matrixInPS);
    
    //get the matrices parameter server need to create and delete
    getPSNeedUpdateMatrix(matrixInPS, needCreateMatrixes, needReleaseMatrixes, psId);
  }

  private void updateInitedMatrix(ParameterServerId psId, IntOpenHashSet initedMatrixIdSet) {
    try {
      writeLock.lock();
      for (int matrixId : initedMatrixIdSet) {
        if (uninitedMatrixProtoMap.containsKey(matrixId)) {
          //update initialized parameter server set
          matrixIdToInitedPSSetMap.get(matrixId).add(psId);
          
          // If all parameter servers have completed the initialization of a matrix, it means that
          // the initialization of the matrix has been completed, we need to remove this matrix id
          // from uninitedMatrixProtoMap
          if (matrixIdToInitedPSSetMap.get(matrixId).size() == matrixIdToPSNumMap.get(matrixId)) {
            LOG.info("matrix " + matrixId + ", inited");
            matrixProtoMap.put(matrixId, uninitedMatrixProtoMap.remove(matrixId));
          }
        }
      }
    } finally {
      writeLock.unlock();
    }
  }

  private void getPSNeedUpdateMatrix(IntOpenHashSet matrixInPS,
      List<MatrixPartition> needCreateMatrixes, List<Integer> needReleaseMatrixes, ParameterServerId psId) {
    try {
      readLock.lock();
      Map<Integer, MatrixPartition> matrixIdToPartition = matrixPartitionOnPS.get(psId);

      if (matrixIdToPartition == null) {
        return;
      }

      //if a matrix exists on parameter server but not exist on master, we should notify the parameter server to remove this matrix
      for (int matrixId : matrixInPS) {
        LOG.debug("matrix in ps " + matrixId);
        if (!matrixIdToPartition.containsKey(matrixId)) {
          LOG.debug("matrix " + matrixId + " need release");
          needReleaseMatrixes.add(matrixId);
        }
      }

      //if a matrix exists on master but not exist on parameter server, this parameter server need build it.
      for (Entry<Integer, MatrixPartition> matrixPartEntry : matrixIdToPartition.entrySet()) {
        LOG.debug("matrix in master " + matrixPartEntry.getKey() + ", "
            + matrixPartEntry.getValue().getMatrixName());
        if (!matrixInPS.contains(matrixPartEntry.getKey())) {
          needCreateMatrixes.add(matrixPartEntry.getValue());
        }
      }
      return;
    } finally {
      readLock.unlock();
    }
  }

  /**
   * release a matrix. just release matrix meta on master
   * TODO:maybe we need a method that can wait all parameter servers release this matrix
   * @param matrixId the matrix need release
   */
  public void releaseMatrix(int matrixId) {
    try {
      writeLock.lock();
      LOG.info("matrix id=" + matrixId);
      matrixProtoMap.remove(matrixId);
      matrixIdToInitedPSSetMap.remove(matrixId);
      uninitedMatrixProtoMap.remove(matrixId);
      matrixIdToPSNumMap.remove(matrixId);
      
      for (Int2ObjectOpenHashMap<MatrixPartition> entry : matrixPartitionOnPS.values()) {
        entry.remove(matrixId);
      }

      ObjectIterator<Entry<String, Integer>> iter = matrixNameToIdMap.entrySet().iterator();
      while (iter.hasNext()) {
        if (iter.next().getValue() == matrixId) {
          iter.remove();
          break;
        }
      }
    } finally {
      writeLock.unlock();
    }
  }

  /**
   * write matrix meta protos to output stream
   * @param output output stream
   * @throws IOException
   */
  public void serialize(FSDataOutputStream output) throws IOException {
    try {
      readLock.lock();
      if(matrixProtoMap == null) {
        return;
      }
      
      for(it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry<MatrixProto> entry:matrixProtoMap.int2ObjectEntrySet()) {
        LOG.info("write meta for matrix " + entry.getValue());
        entry.getValue().writeDelimitedTo(output);
      }
    } finally {
      readLock.unlock();
    }
  }
  
  /**
   * read matrix meta protos from input stream
   * @param input input stream
   * @throws IOException, InvalidParameterException
   */
  public void deserialize(FSDataInputStream input) throws IOException, InvalidParameterException {
    List<MatrixProto> matrixProtos = new ArrayList<MatrixProto>();
    while(input.available() > 0) {
      MatrixProto matrixProto = MatrixProto.parseDelimitedFrom(input);
      LOG.info("deserilize from file matrixProto=" + matrixProto);
      matrixProtos.add(matrixProto);
    }
    addMatrices(matrixProtos);
  }
}
