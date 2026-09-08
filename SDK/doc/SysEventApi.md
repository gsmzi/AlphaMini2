
---

# SysEventApi 类文档

## 概述
`SysEventApi` 是使用悟空事件和状态的入口。该 API 提供两个对外方法：`get()` 方法获取悟空事件 API，`getStateApi()` 方法获取悟空状态 API。

---

## 方法列表

### 1. `get()`
**描述**: 获取悟空事件 API。  
**返回值**:  
- 返回 `EventApi` 对象。

---

### 2. `getStateApi()`
**描述**: 获取悟空状态 API。  
**返回值**:  
- 返回 `AlphaStateApi` 对象。

---

## 注意事项
1. **方法调用**: 使用 `get()` 方法获取事件 API，使用 `getStateApi()` 方法获取状态 API。
2. **对象获取**: 这两个方法返回的对象分别是 `EventApi` 和 `AlphaStateApi`，用于处理相应的事件和状态操作。

---

## 示例代码

```java
// 获取悟空事件 API
EventApi eventApi = SysEventApi.get();

// 获取悟空状态 API
AlphaStateApi stateApi = SysEventApi.getStateApi();
```



以下是基于 `EventApi.java` 文件注释生成的 API 文档，使用 Markdown 格式：

---



## EventApi 接口文档
`EventApi` 用于订阅、发布系统事件，如电量、按键、活跃状态或其他事件。通过 `SysEventApi.get()` 获取单例实例。

---

## 方法列表

### 1. `subscribe(BaseEvent event, BaseReceiver receiver)`
**描述**: 订阅事件接收器。  
**参数**:  
- `event`: 事件对象，类型为 `BaseEvent`。
- `receiver`: 接收器对象，类型为 `BaseReceiver`。

---

### 2. `unsubscribe(BaseReceiver receiver)`
**描述**: 反订阅事件接收器。  
**参数**:  
- `receiver`: 接收器对象，类型为 `BaseReceiver`。

---

### 3. `unsubscribeBySkill(MasterSkill skill)`
**描述**: 反订阅所有绑定该 `MasterSkill` 的接收器。  
**参数**:  
- `skill`: 技能对象，类型为 `MasterSkill`。

---

### 4. `getCurrentBatteryInfo(GetBatteryInfoListener getBatteryInfoListener)`
**描述**: 异步获取当前电池电量信息。  
**参数**:  
- `getBatteryInfoListener`: 回调监听器，类型为 `GetBatteryInfoListener`。

---

### 5. `getCurrentBatteryInfoSync()`
**描述**: 同步获取当前电池电量信息。  
**返回值**:  
- 返回 `SysMasterEvent.BatteryStatusData` 对象，包含电池电量信息。

---

### 6. `publishSysActiveStatus(SysMasterEvent.ActivieStatusType newStatus, PublishSysActiveStatusListener listener)`
**描述**: 异步发布活跃状态。  
**参数**:  
- `newStatus`: 新的活跃状态类型，类型为 `SysMasterEvent.ActivieStatusType`。
- `listener`: 回调监听器，类型为 `PublishSysActiveStatusListener`。

---

### 7. `publishSysActiveStatusSync(SysMasterEvent.ActivieStatusType newStatus)`
**描述**: 同步发布活跃状态。  
**参数**:  
- `newStatus`: 新的活跃状态类型，类型为 `SysMasterEvent.ActivieStatusType`。

---

### 8. `publishCommSysEvent(SysMasterEvent.CommSysEvent commSysEvent, BaseEventPublishListener listener)`
**描述**: 异步发布系统事件。  
**参数**:  
- `commSysEvent`: 系统事件对象，类型为 `SysMasterEvent.CommSysEvent`。
- `listener`: 回调监听器，类型为 `BaseEventPublishListener`。

---

### 9. `publishCommSysEventSync(SysMasterEvent.CommSysEvent commSysEvent)`
**描述**: 同步发布系统事件。  
**参数**:  
- `commSysEvent`: 系统事件对象，类型为 `SysMasterEvent.CommSysEvent`。

---

### 10. `getCurrentSysActiveStatus(GetSysActivieStatusListener getSysActivieStatusListener)`
**描述**: 异步获取系统活跃状态。  
**参数**:  
- `getSysActivieStatusListener`: 回调监听器，类型为 `GetSysActivieStatusListener`。

---

### 11. `getCurrentSysActiveStatusSync()`
**描述**: 同步获取系统活跃状态。  
**返回值**:  
- 返回 `SysMasterEvent.ActiveStatusData` 对象，包含系统活跃状态信息。

---

### 12. `isCharging()`
**描述**: 检查系统是否正在充电。  
**返回值**:  
- 返回布尔值，`true` 表示正在充电，`false` 表示未充电。

---

## 回调接口

### `GetBatteryInfoListener`
**描述**: 用于获取电池电量信息的回调监听器。

#### 方法

- `onSuccess(SysMasterEvent.BatteryStatusData batteryStatusData)`
  - **描述**: 获取电池电量信息成功时调用。
  - **参数**:  
    - `batteryStatusData`: 电池电量信息，类型为 `SysMasterEvent.BatteryStatusData`。

- `onFailure(int errorCode, String errorMessage)`
  - **描述**: 获取电池电量信息失败时调用。
  - **参数**:  
    - `errorCode`: 错误代码。
    - `errorMessage`: 错误消息。

---

### `PublishSysActiveStatusListener`
**描述**: 用于发布系统活跃状态的回调监听器。

#### 方法

- `onSuccess()`
  - **描述**: 发布系统活跃状态成功时调用。

- `onFailure(int errorCode, String errorMessage)`
  - **描述**: 发布系统活跃状态失败时调用。
  - **参数**:  
    - `errorCode`: 错误代码。
    - `errorMessage`: 错误消息。

---

### `BaseEventPublishListener`
**描述**: 用于发布系统事件的回调监听器。

#### 方法

- `onSuccess()`
  - **描述**: 发布系统事件成功时调用。

- `onFailure(int errorCode, String errorMessage)`
  - **描述**: 发布系统事件失败时调用。
  - **参数**:  
    - `errorCode`: 错误代码。
    - `errorMessage`: 错误消息。

---

### `GetSysActivieStatusListener`
**描述**: 用于获取系统活跃状态的回调监听器。

#### 方法

- `onSuccess(SysMasterEvent.ActiveStatusData activeStatusData)`
  - **描述**: 获取系统活跃状态成功时调用。
  - **参数**:  
    - `activeStatusData`: 系统活跃状态信息，类型为 `SysMasterEvent.ActiveStatusData`。

- `onFailure(int errorCode, String errorMessage)`
  - **描述**: 获取系统活跃状态失败时调用。
  - **参数**:  
    - `errorCode`: 错误代码。
    - `errorMessage`: 错误消息。

---
以下是基于 `AlphaStateApi.kt` 文件注释生成的 API 文档，使用 Markdown 格式：

---


## AlphaStateApi 接口文档
`AlphaStateApi` 接口定义了发布和获取 Alpha 状态的方法。它用于在系统中发布当前的 Alpha 状态，或者同步地获取当前的 Alpha 状态信息。

---

## 方法列表

### 1. `publishAlphaState(state: SysMasterEvent.AlphaState, callback: PublishAlphaStateCallback)`
**描述**: 发布 Alpha 状态。  
**参数**:  
- `state`: 要发布的 Alpha 状态，类型为 `SysMasterEvent.AlphaState`。
- `callback`: 发布状态后的回调，类型为 `PublishAlphaStateCallback`，用于通知发布结果。

**逻辑**:  
- 异步发布 Alpha 状态，适合需要立即返回控制权的场景。

---

### 2. `publishAlphaStateSync(state: AlphaState)`
**描述**: 同步发布 Alpha 状态。  
**参数**:  
- `state`: 要发布的 Alpha 状态信息，类型为 `AlphaState`。

**逻辑**:  
- 同步地发布 Alpha 状态，会阻塞直到状态发布完成，适合需要等待发布结果的场景。

---

### 3. `getCurrentStateNow(): AlphaStateMsg`
**描述**: 获取当前的 Alpha 状态信息。  
**返回值**:  
- 返回当前的 Alpha 状态消息，类型为 `AlphaStateMsg`，包含状态详情。

**逻辑**:  
- 同步地获取当前的 Alpha 状态信息，适合需要立即获取状态的场景。

---

### 4. `getCurrentState(callback: GetCurrentStateCallback)`
**描述**: 获取当前的 Alpha 状态。  
**参数**:  
- `callback`: 获取状态后的回调，类型为 `GetCurrentStateCallback`，用于通知获取到的状态信息。

**逻辑**:  
- 异步获取当前的 Alpha 状态，适合需要在获取到状态后进行回调处理的场景。

---

## 回调接口

### `PublishAlphaStateCallback`
**描述**: 用于发布 Alpha 状态的回调监听器。

#### 方法

- `onSuccess()`
  - **描述**: 发布 Alpha 状态成功时调用。

- `onFailure(errorCode: Int, errorMessage: String)`
  - **描述**: 发布 Alpha 状态失败时调用。
  - **参数**:  
    - `errorCode`: 错误代码。
    - `errorMessage`: 错误消息。

---

### `GetCurrentStateCallback`
**描述**: 用于获取当前 Alpha 状态的回调监听器。

#### 方法

- `onSuccess(alphaStateMsg: AlphaStateMsg)`
  - **描述**: 获取当前 Alpha 状态成功时调用。
  - **参数**:  
    - `alphaStateMsg`: 当前 Alpha 状态消息，类型为 `AlphaStateMsg`。

- `onFailure(errorCode: Int, errorMessage: String)`
  - **描述**: 获取当前 Alpha 状态失败时调用。
  - **参数**:  
    - `errorCode`: 错误代码。
    - `errorMessage`: 错误消息。

---
