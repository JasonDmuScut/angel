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

package com.tencent.angel.master.psagent;

import com.tencent.angel.psagent.PSAgentId;
import org.apache.hadoop.yarn.event.AbstractEvent;

public class PSAgentManagerEvent extends AbstractEvent<PSAgentManagerEventType> {
  private final PSAgentId psAgentId;

  public PSAgentManagerEvent(PSAgentManagerEventType type) {
    super(type);
    this.psAgentId = null;
  }

  public PSAgentManagerEvent(PSAgentManagerEventType type, PSAgentId psAgentId) {
    super(type);
    this.psAgentId = psAgentId;
  }

  public PSAgentId getPsAgentId() {
    return psAgentId;
  }
}
