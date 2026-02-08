# VoicePool 类文档

`VoicePool` 是一个用于管理语音播放（包括 TTS 和本地音频文件）的类，提供播放、停止等功能。**该类通过单例模式提供服务并通过VoiceListener作为回调接口。

## 类概述

`VoicePool` 封装了与语音服务交互的逻辑，提供以下功能：
- 播放 TTS 文本
- 播放本地 TTS 音频文件
- 播放本地音频文件（非 TTS）
- 停止 TTS 或本地音频播放
- 支持子通道播放和优先级控制

## 构造方法

### `VoicePool()`
- **描述**：私有构造方法，初始化语音服务代理。
- **细节**：
  - 通过 `Master` 获取全局上下文并创建语音服务代理。
  - 配置调用参数，抑制主线程同步调用的警告。

## 静态方法

### `get()`
- **描述**：获取 `VoicePool` 的单例实例。
- **返回值**：`VoicePool` 实例。

## 方法详情

### `playTTs(String text, ResourcePolicy policy, VoiceListener listener)`
- **描述**：播放 TTS 文本。
- **参数**：
  - `text`：要播放的文本内容。
  - `policy`：资源策略（`ResourcePolicy`）。
  - `listener`：播放状态回调（`VoiceListener`）。
- **行为**：
  - 如果 `text` 为空或空白，调用 `listener.onError` 并返回。
  - 通过语音服务代理异步播放 TTS。

### `playLocalTTs(File file, ResourcePolicy policy, VoiceListener listener)`
- **描述**：播放本地 TTS 音频文件。
- **参数**：
  - `file`：音频文件路径。
  - `policy`：资源策略（`ResourcePolicy`）。
  - `listener`：播放状态回调（`VoiceListener`）。
- **行为**：通过语音服务代理异步播放本地文件。

### `playLocalTTsInSubchannel(File file, ResourcePolicy policy, VoiceListener listener)`
- **描述**：在子通道中播放本地 TTS 音频文件。
- **参数**：
  - `file`：音频文件路径。
  - `policy`：资源策略（`ResourcePolicy`）。
  - `listener`：播放状态回调（`VoiceListener`）。
- **行为**：与 `playLocalTTs` 类似，但启用子通道模式。

### `playLocalTTs(String mp3FileName, ResourcePolicy policy, VoiceListener listener)`
- **描述**：通过文件名播放本地 TTS 音频文件。
- **参数**：
  - `mp3FileName`：本地 TTS 文件名（带后缀）。
  - `policy`：资源策略（`ResourcePolicy`）。
  - `listener`：播放状态回调（`VoiceListener`）。
- **行为**：调用 `PropertiesApi.findSystemTTsPath` 获取文件路径后，委托给 `playLocalTTs`。

### `playLocalTTsInSubchannel(String mp3FileName, ResourcePolicy policy, VoiceListener listener)`
- **描述**：在子通道中通过文件名播放本地 TTS 音频文件。
- **参数**：
  - `mp3FileName`：本地 TTS 文件名（带后缀）。
  - `policy`：资源策略（`ResourcePolicy`）。
  - `listener`：播放状态回调（`VoiceListener`）。
- **行为**：调用 `PropertiesApi.findSystemTTsPath` 获取文件路径后，委托给 `playLocalTTsInSubchannel`。

### `stopTTs(ResourcePolicy policy, ResponseListener<Void> listener)`
- **描述**：停止 TTS 播放。
- **参数**：
  - `policy`：资源策略（`ResourcePolicy`）。
  - `listener`：响应回调（`ResponseListener<Void>`，可为 `null`）。
- **行为**：通过语音服务代理异步停止 TTS。

### `stopLocalTTs(String ttsName, ResourcePolicy policy, ResponseListener<Void> listener)`
- **描述**：停止指定的本地 TTS 播放。
- **参数**：
  - `ttsName`：本地 TTS 文件名（带后缀）。
  - `policy`：资源策略（`ResourcePolicy`）。
  - `listener`：响应回调（`ResponseListener<Void>`，可为 `null`）。
- **行为**：通过语音服务代理异步停止指定的本地 TTS。

### `playUnsafeTTs(String mp3FileName, VoiceListener listener)`
- **描述**：播放本地 TTS 音频文件（不参与语音管理冲突）。
- **参数**：
  - `mp3FileName`：本地 TTS 文件名（带后缀）。
  - `listener`：播放状态回调（`VoiceListener`）。
- **行为**：通过语音服务代理异步播放文件，不参与语音管理冲突。

### `playUnsafeTTs(File file, VoiceListener listener)`
- **描述**：播放本地音频文件（不参与语音管理冲突）。
- **参数**：
  - `file`：音频文件路径。
  - `listener`：播放状态回调（`VoiceListener`）。
- **行为**：通过语音服务代理异步播放文件，不参与语音管理冲突。

## 内部类

### `Holder`
- **描述**：单例模式的持有者类，用于延迟初始化 `VoicePool` 实例。
- **字段**：
  - `_pool`：`VoicePool` 的单例实例。

## 回调接口

### `VoiceListener`
- **描述**：语音播放状态的回调接口（具体方法未在代码中定义，需参考实现）。
- **方法**：
  - `onError(int code, String message)`：播放错误回调。
    - **参数**：
      - `code`：错误码。
      - `message`：错误信息。
  - `onCompleted()`：播放完成回调。
    - **参数**：
      - `content`播放的内容文本

### `ResponseListener<Void>`
- **描述**：响应回调接口（用于停止操作）。
- **方法**：
  - `onResponseSuccess(Void result)`：操作成功回调。
  - `onFailure(int code, String message)`：操作失败回调。
