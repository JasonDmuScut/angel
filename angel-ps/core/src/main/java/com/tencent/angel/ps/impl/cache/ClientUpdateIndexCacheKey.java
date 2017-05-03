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

package com.tencent.angel.ps.impl.cache;

import com.tencent.angel.PartitionKey;

public class ClientUpdateIndexCacheKey {

  final PartitionKey partition;
  final int clientIndex;

  public ClientUpdateIndexCacheKey(PartitionKey partition, int clientIndex) {
    this.partition = partition;
    this.clientIndex = clientIndex;
  }

  @Override
  public String toString() {
    return "ClientUpdateIndexCacheKey [partition=" + partition + ", clientIndex=" + clientIndex
        + "]";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + clientIndex;
    result = prime * result + ((partition == null) ? 0 : partition.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    ClientUpdateIndexCacheKey other = (ClientUpdateIndexCacheKey) obj;
    if (clientIndex != other.clientIndex)
      return false;
    if (partition == null) {
      if (other.partition != null)
        return false;
    } else if (!partition.equals(other.partition))
      return false;
    return true;
  }
}
